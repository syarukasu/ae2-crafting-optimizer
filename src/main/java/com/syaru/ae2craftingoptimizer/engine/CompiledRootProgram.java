package com.syaru.ae2craftingoptimizer.engine;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToLongFunction;

/**
 * 一つの完成品から到達する決定的なPattern DAGを、世代中に再利用できる配列プログラムへ変換する。
 * 実行中はキー検索用Mapを使わず、必要量、在庫、実行回数、不足量をノード番号付き配列で管理する。
 */
public final class CompiledRootProgram<K> {
    /** 異常なデータパックから巨大配列を確保しないための、ルート一つ当たりの固定上限。 */
    private static final int MAXIMUM_PROGRAM_NODES = 1_048_576;
    /** 一つのPatternに多数の重複入力がある場合も含めた、入力辺の固定上限。 */
    private static final int MAXIMUM_PROGRAM_INPUT_EDGES = 4_194_304;
    /** 正のsigned longへ無損失変換できる最大bit長。 */
    private static final int SIGNED_LONG_MAGNITUDE_BITS = Long.SIZE - 1;
    /** 一つのslotを全量満たせる在庫候補を最優先する順位。 */
    private static final int ALTERNATIVE_RANK_AVAILABLE = 0;
    /** 下位PatternまたはEmitterで不足を解決できる候補の順位。 */
    private static final int ALTERNATIVE_RANK_CRAFTABLE = 1;
    /** 終端在庫を一部だけ使える候補の順位。 */
    private static final int ALTERNATIVE_RANK_PARTIAL = 2;
    /** 在庫も供給経路もない候補の順位。 */
    private static final int ALTERNATIVE_RANK_MISSING = 3;
    /** 作業台限定経路へProcessing PatternやEmitterを混入させないための最低順位。 */
    private static final int ALTERNATIVE_RANK_UNSUPPORTED = 4;

    private final long generation;
    private final K root;
    private final int rootIndex;
    private final List<K> keys;
    private final Map<K, Integer> indexByKey;
    private final Object[] patterns;
    private final String[] patternIds;
    private final long[] outputAmounts;
    private final int[] inputOffsets;
    private final int[] alternativeOffsets;
    private final int[] inputIndices;
    private final long[] inputAmounts;
    private final boolean[] emittable;
    private final Map<K, CompiledPattern<K>> patternsByOutput;
    private final Set<K> emittableKeys;
    private final int patternCount;

    private CompiledRootProgram(
            long generation,
            K root,
            int rootIndex,
            List<K> keys,
            Map<K, Integer> indexByKey,
            Object[] patterns,
            String[] patternIds,
            long[] outputAmounts,
            int[] inputOffsets,
            int[] alternativeOffsets,
            int[] inputIndices,
            long[] inputAmounts,
            boolean[] emittable,
            Map<K, CompiledPattern<K>> patternsByOutput,
            Set<K> emittableKeys,
            int patternCount) {
        this.generation = generation;
        this.root = root;
        this.rootIndex = rootIndex;
        this.keys = List.copyOf(keys);
        this.indexByKey = Map.copyOf(indexByKey);
        /*
         * 全配列はcompile内で新規作成され、このprivate constructorへ所有権を移す。
         * 外部参照が存在しない配列を再cloneすると、cold compileのメモリ帯域だけを倍増させる。
         */
        this.patterns = patterns;
        this.patternIds = patternIds;
        this.outputAmounts = outputAmounts;
        this.inputOffsets = inputOffsets;
        this.alternativeOffsets = alternativeOffsets;
        this.inputIndices = inputIndices;
        this.inputAmounts = inputAmounts;
        this.emittable = emittable;
        this.patternsByOutput = Map.copyOf(patternsByOutput);
        this.emittableKeys = Set.copyOf(emittableKeys);
        this.patternCount = patternCount;
    }

    /**
     * 単一Pattern、単一候補、単一出力、非循環という条件を証明できるルートだけをコンパイルする。
     * EmitterはAE2と同じくレシピより先に解決し、その先の依存関係を展開しない。
     */
    public static <K> Optional<CompiledRootProgram<K>> tryCompile(
            CompiledCraftingGraph<K> graph,
            K root,
            Predicate<? super K> canEmit) {
        return tryCompile(graph, root, canEmit, PlanningGuard.none());
    }

    public static <K> Optional<CompiledRootProgram<K>> tryCompile(
            CompiledCraftingGraph<K> graph,
            K root,
            Predicate<? super K> canEmit,
            PlanningGuard workBudget) {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(canEmit, "canEmit");
        Objects.requireNonNull(workBudget, "workBudget");

        Map<K, CompiledPattern<K>> selected = new LinkedHashMap<>();
        Map<K, Set<K>> dependencies = new LinkedHashMap<>();
        Set<K> emitterKeys = new LinkedHashSet<>();
        Set<K> reachable = new LinkedHashSet<>();
        ArrayDeque<K> discover = new ArrayDeque<>();
        discover.push(root);

        // ルートから到達するノードだけを一度ずつ探索し、曖昧性を見つけた時点でFallbackする。
        while (!discover.isEmpty()) {
            K key = discover.pop();
            // 複数の親から共有される中間素材は、最初の探索でだけ構造を登録する。
            if (!reachable.add(key)) {
                continue;
            }
            workBudget.checkpoint(reachable.size());
            // Emitterや終端だけが大量に並ぶ場合も、固定ノード上限を必ず適用する。
            if (reachable.size() > MAXIMUM_PROGRAM_NODES) {
                return Optional.empty();
            }
            // SCCに属するキーは数式一巡では安全に解けないため、AE2標準計算へ戻す。
            if (graph.isCyclic(key)) {
                return Optional.empty();
            }
            // Emitterで供給できるキーは終端として扱い、その先のPatternを展開しない。
            if (canEmit.test(key)) {
                emitterKeys.add(key);
                dependencies.put(key, Set.of());
                continue;
            }

            List<CompiledPattern<K>> candidates = graph.patternsFor(key);
            // Patternがないキーは在庫または不足一覧で解決する終端ノードになる。
            if (candidates.isEmpty()) {
                dependencies.put(key, Set.of());
                continue;
            }
            // 複数Patternの優先順位は在庫状態に依存するため、数式経路では選択しない。
            if (candidates.size() != 1) {
                return Optional.empty();
            }

            CompiledPattern<K> pattern = candidates.get(0);
            // 副産物や複数出力は余剰在庫会計が必要なため、単一路線から除外する。
            if (pattern.outputs().size() != 1 || pattern.outputAmount(key) <= 0L) {
                return Optional.empty();
            }

            Set<K> children = new LinkedHashSet<>();
            // 各入力slotの明示候補を全てDAGへ含め、注文時に在庫と下位Patternから一つを選ぶ。
            for (CompiledPattern.InputSlot<K> slot : pattern.inputs()) {
                // 候補を省略せず探索し、選ばれなかった枝は実行時の需要0として飛ばす。
                for (CompiledPattern.Stack<K> alternative : slot.alternatives()) {
                    K child = alternative.key();
                    children.add(child);
                    discover.push(child);
                }
            }
            selected.put(key, pattern);
            dependencies.put(key, Set.copyOf(children));
        }

        List<K> order = topologicalOrder(reachable, dependencies, workBudget);
        // 全ノードを並べられなかった場合は、探索中に見えなかった循環があるためFallbackする。
        if (order.size() != reachable.size()) {
            return Optional.empty();
        }

        Map<K, Integer> indexByKey = new LinkedHashMap<>();
        // 実行時にMap検索をせずに済むよう、トポロジカル順へ連番を付ける。
        for (int index = 0; index < order.size(); index++) {
            workBudget.checkpoint(index + 1);
            indexByKey.put(order.get(index), index);
        }

        int slotCount = 0;
        int alternativeCount = 0;
        int countedNodes = 0;
        // slot配列と候補配列を一回だけ確保するため、Patternごとの件数を先に合計する。
        for (K key : order) {
            workBudget.checkpoint(++countedNodes);
            CompiledPattern<K> pattern = selected.get(key);
            // 終端ノードには入力辺がないため、Patternノードだけを数える。
            if (pattern != null) {
                try {
                    slotCount = Math.addExact(slotCount, pattern.inputs().size());
                    // 各slotの候補数も別配列の確保前にchecked加算する。
                    for (CompiledPattern.InputSlot<K> slot : pattern.inputs()) {
                        alternativeCount = Math.addExact(
                                alternativeCount,
                                slot.alternatives().size());
                    }
                } catch (ArithmeticException overflow) {
                    return Optional.empty();
                }
                // slotまたは候補辺の固定上限を超えるデータは、配列確保前にFallbackする。
                if (slotCount > MAXIMUM_PROGRAM_INPUT_EDGES
                        || alternativeCount > MAXIMUM_PROGRAM_INPUT_EDGES) {
                    return Optional.empty();
                }
            }
        }

        int nodeCount = order.size();
        Object[] patterns = new Object[nodeCount];
        String[] patternIds = new String[nodeCount];
        long[] outputAmounts = new long[nodeCount];
        int[] inputOffsets = new int[nodeCount + 1];
        int[] alternativeOffsets = new int[slotCount + 1];
        int[] inputIndices = new int[alternativeCount];
        long[] inputAmounts = new long[alternativeCount];
        boolean[] emitters = new boolean[nodeCount];
        int slotCursor = 0;
        int alternativeCursor = 0;
        int compiledPatterns = 0;

        // Map中心のGraphを、実行時に直接添字アクセスできる不変配列へ変換する。
        for (int node = 0; node < nodeCount; node++) {
            workBudget.checkpoint(node + 1);
            K key = order.get(node);
            CompiledPattern<K> pattern = selected.get(key);
            emitters[node] = emitterKeys.contains(key);
            inputOffsets[node] = slotCursor;
            // 在庫、Emitter、不足だけで解決する終端ノードにはPattern情報を書き込まない。
            if (pattern == null) {
                continue;
            }

            patterns[node] = pattern;
            String patternId = pattern.id();
            patternIds[node] = patternId;
            outputAmounts[node] = pattern.outputAmount(key);
            compiledPatterns++;
            // slot境界を保ったまま、明示された全候補を候補配列へ平坦化する。
            for (CompiledPattern.InputSlot<K> slot : pattern.inputs()) {
                alternativeOffsets[slotCursor] = alternativeCursor;
                // 一つのslot内の候補順はAE2 Patternが提示した順序のまま保存する。
                for (CompiledPattern.Stack<K> input : slot.alternatives()) {
                    Integer childIndex = indexByKey.get(input.key());
                    /*
                     * 子ノードが未登録または親より先なら、全候補を含む
                     * トポロジカル順が壊れているためFallbackする。
                     */
                    if (childIndex == null || childIndex <= node) {
                        return Optional.empty();
                    }
                    inputIndices[alternativeCursor] = childIndex;
                    inputAmounts[alternativeCursor] = input.amount();
                    alternativeCursor++;
                }
                slotCursor++;
            }
        }
        inputOffsets[nodeCount] = slotCursor;
        alternativeOffsets[slotCount] = alternativeCursor;

        Integer rootIndex = indexByKey.get(root);
        // ルートは必ず到達集合へ入るが、防御的に欠落を検出してFallbackする。
        if (rootIndex == null) {
            return Optional.empty();
        }
        return Optional.of(new CompiledRootProgram<>(
                graph.generation(),
                root,
                rootIndex,
                order,
                indexByKey,
                patterns,
                patternIds,
                outputAmounts,
                inputOffsets,
                alternativeOffsets,
                inputIndices,
                inputAmounts,
                emitters,
                selected,
                emitterKeys,
                compiledPatterns));
    }

    private static <K> List<K> topologicalOrder(
            Set<K> reachable,
            Map<K, Set<K>> dependencies,
            PlanningGuard workBudget) {
        Map<K, Integer> indegree = new HashMap<>();
        // 到達した全ノードをindegree 0で初期化する。
        for (K key : reachable) {
            indegree.put(key, 0);
        }
        // 親から子へ張られた辺ごとに、子の未処理親数を加算する。
        for (Map.Entry<K, Set<K>> entry : dependencies.entrySet()) {
            for (K child : entry.getValue()) {
                indegree.merge(child, 1, Math::addExact);
            }
        }

        Queue<K> ready = new ArrayDeque<>();
        // 親を持たないルート候補を、安定した探索順のままキューへ入れる。
        for (K key : reachable) {
            // indegree 0のノードだけが現時点で安全に評価できる。
            if (indegree.getOrDefault(key, 0) == 0) {
                ready.add(key);
            }
        }

        List<K> order = new ArrayList<>(reachable.size());
        // 親を先、共有される中間素材を全親の後に置くKahn法で一巡順を作る。
        while (!ready.isEmpty()) {
            K key = ready.remove();
            order.add(key);
            workBudget.checkpoint(order.size());
            // 現在ノードを処理したので、各子の未処理親数を一つ減らす。
            for (K child : dependencies.getOrDefault(key, Set.of())) {
                int remaining = indegree.compute(child, (ignored, value) -> value - 1);
                // 全ての親が先に並んだ子だけを実行キューへ追加する。
                if (remaining == 0) {
                    ready.add(child);
                }
            }
        }
        return List.copyOf(order);
    }

    /** 参照対象キーだけをsigned long在庫ベクトルへ固定する。 */
    public InventorySnapshot<K> captureLongInventory(ToLongFunction<? super K> amountReader) {
        Objects.requireNonNull(amountReader, "amountReader");
        long[] amounts = new long[keys.size()];
        // コンパイル済みルートが参照するキーだけを一度ずつ取得する。
        for (int index = 0; index < keys.size(); index++) {
            amounts[index] = CheckedLongMath.requireNonNegative(
                    amountReader.applyAsLong(keys.get(index)),
                    "compiled-root/inventory/" + index);
        }
        return new InventorySnapshot<>(this, amounts);
    }

    /** BigInteger在庫を受け取る汎用API用Snapshot。通常のAE2在庫はlong版を使用する。 */
    public BigInventorySnapshot<K> captureBigInventory(
            Function<? super K, BigInteger> amountReader,
            int maximumBits) {
        Objects.requireNonNull(amountReader, "amountReader");
        BigInteger[] amounts = new BigInteger[keys.size()];
        // コンパイル済みルートが参照するキーだけを一度ずつ取得し、上限を検証する。
        for (int index = 0; index < keys.size(); index++) {
            BigInteger amount = Objects.requireNonNull(
                    amountReader.apply(keys.get(index)),
                    "inventory amount");
            amounts[index] = BigCountMath.requireMaximumBits(
                    amount,
                    "compiled-root/big-inventory/" + index,
                    maximumBits);
        }
        return new BigInventorySnapshot<>(this, amounts);
    }

    /** 計算終了時に、参照したキーだけが同じ在庫量を保っているか検証する。 */
    public boolean inventoryMatches(
            InventorySnapshot<K> snapshot,
            ToLongFunction<? super K> amountReader) {
        requireSnapshot(snapshot);
        Objects.requireNonNull(amountReader, "amountReader");
        // 参照対象以外の巨大ME在庫は走査せず、計画結果へ影響するキーだけを比較する。
        for (int index = 0; index < keys.size(); index++) {
            long current = CheckedLongMath.requireNonNegative(
                    amountReader.applyAsLong(keys.get(index)),
                    "compiled-root/live-inventory/" + index);
            // 一つでも変化した場合は古い計算結果を破棄する。
            if (current != snapshot.amountAt(index)) {
                return false;
            }
        }
        return true;
    }

    /** BigInteger計画終了時に、参照キーの正確な在庫量だけを再検証する。 */
    public boolean inventoryMatches(
            BigInventorySnapshot<K> snapshot,
            Function<? super K, BigInteger> amountReader,
            int maximumBits) {
        requireSnapshot(snapshot);
        Objects.requireNonNull(amountReader, "amountReader");
        // 無関係なME在庫は走査せず、コンパイル済みルートが読んだキーだけを比較する。
        for (int index = 0; index < keys.size(); index++) {
            BigInteger current = BigCountMath.requireMaximumBits(
                    Objects.requireNonNull(
                            amountReader.apply(keys.get(index)),
                            "inventory amount"),
                    "compiled-root/big-live-inventory/" + index,
                    maximumBits);
            // 一つでも変化した場合は、古いBigInteger計画を提出しない。
            if (!current.equals(snapshot.amountAt(index))) {
                return false;
            }
        }
        return true;
    }

    /** BigInteger Snapshotの全参照値がsigned longへ無損失変換できるかを返す。 */
    boolean inventoryFitsSignedLong(BigInventorySnapshot<K> snapshot) {
        requireSnapshot(snapshot);
        // 通常在庫だけならlong高速経路を維持し、BigInteger配列演算を避ける。
        for (int index = 0; index < keys.size(); index++) {
            // signed longの正数範囲を越える最初のキーでBigInteger経路を選択する。
            if (snapshot.amountAt(index).bitLength() > Long.SIZE - 1) {
                return false;
            }
        }
        return true;
    }

    /** 検証済みBigInteger Snapshotをlong高速経路用Snapshotへ無損失変換する。 */
    InventorySnapshot<K> narrowInventory(BigInventorySnapshot<K> snapshot) {
        requireSnapshot(snapshot);
        long[] amounts = new long[keys.size()];
        // 呼出側のfits判定後もlongValueExactを使い、将来の変更で暗黙wrapを起こさない。
        for (int index = 0; index < keys.size(); index++) {
            amounts[index] = snapshot.amountAt(index).longValueExact();
        }
        return new InventorySnapshot<>(this, amounts);
    }

    /** checked longだけで配列プログラムを一巡する高速経路。 */
    public LongCraftingPlan<K> planLong(
            long requestedAmount,
            InventorySnapshot<K> inventory,
            PlanningGuard guard) {
        CheckedLongMath.requireNonNegative(requestedAmount, "compiled-root/request");
        requireSnapshot(inventory);
        Objects.requireNonNull(guard, "guard");

        int nodeCount = keys.size();
        long[] demand = new long[nodeCount];
        long[] patternExecutions = new long[nodeCount];
        long[] used = new long[nodeCount];
        demand[rootIndex] = requestedAmount;

        // 全親の要求が集約済みになるトポロジカル順で、各固有キーを一度だけ処理する。
        for (int node = 0; node < nodeCount; node++) {
            guard.checkpoint(node + 1);
            long required = demand[node];
            // 処理済みノードの需要欄は、Emitterまたは不足終端の残量格納へ再利用する。
            demand[node] = 0L;
            // この注文から到達しなかった枝は配列上に存在しても計算しない。
            if (required == 0L) {
                continue;
            }

            long taken = Math.min(required, inventory.amountAt(node));
            used[node] = taken;
            long deficit = required - taken;
            // 在庫だけで満たせたキーはPatternや不足へ伝播させない。
            if (deficit == 0L) {
                continue;
            }
            // EmitterはAE2と同じく不足量を直接供給し、その先を展開しない。
            if (emittable[node]) {
                demand[node] = deficit;
                continue;
            }
            // Patternがない終端は全件を不足一覧へ残し、他の枝の計算は継続する。
            if (patterns[node] == null) {
                demand[node] = deficit;
                continue;
            }

            long executions = CheckedLongMath.ceilDivIndexed(
                    deficit,
                    outputAmounts[node],
                    "compiled-root/executions",
                    node);
            patternExecutions[node] = executions;
            // 各slotで具体候補を一つ選び、実行回数を掛けて子ノード需要へ加算する。
            for (int slot = inputOffsets[node]; slot < inputOffsets[node + 1]; slot++) {
                int edge = selectLongAlternative(
                        slot,
                        executions,
                        inventory);
                long requiredInput = CheckedLongMath.multiplyIndexed(
                        inputAmounts[edge],
                        executions,
                        "compiled-root/input",
                        edge);
                int child = inputIndices[edge];
                demand[child] = CheckedLongMath.addIndexed(
                        demand[child],
                        requiredInput,
                        "compiled-root/demand",
                        child);
            }
        }
        LongResultMaps<K> resultMaps = longResultMaps(
                patternExecutions,
                used,
                demand);
        return new LongCraftingPlan<>(
                root,
                requestedAmount,
                resultMaps.patternExecutions(),
                resultMaps.usedInventory(),
                resultMaps.emitted(),
                resultMaps.missing());
    }

    /** long SnapshotをBigIntegerへ昇格し、同じ配列プログラムを一巡する。 */
    public BigCraftingPlan<K> planBig(
            BigInteger requestedAmount,
            InventorySnapshot<K> inventory,
            PlanningGuard guard,
            int maximumBits) {
        requireSnapshot(inventory);
        BigInteger[] amounts = new BigInteger[keys.size()];
        // AE2在庫は各キーlongなので、参照分だけを無損失でBigIntegerへ変換する。
        for (int index = 0; index < amounts.length; index++) {
            amounts[index] = BigInteger.valueOf(inventory.amountAt(index));
        }
        return planBigInternal(requestedAmount, amounts, guard, maximumBits);
    }

    /** BigInteger在庫を保持する汎用Snapshotで同じ配列プログラムを一巡する。 */
    public BigCraftingPlan<K> planBig(
            BigInteger requestedAmount,
            BigInventorySnapshot<K> inventory,
            PlanningGuard guard,
            int maximumBits) {
        requireSnapshot(inventory);
        return planBigInternal(requestedAmount, inventory.amounts, guard, maximumBits);
    }

    /**
     * 確定作業台Patternだけで構成されたDAGを、需要配列一つと直接乗算で計画する。
     *
     * <p>注文数量に比例する反復や中間素材の実体化は行わない。各固有Patternを一度だけ読み、
     * 入力需要、実行回数、ME在庫との正味境界、最長依存段数を同じ一巡で確定する。</p>
     */
    public Optional<DeterministicCraftingBigPlan<K>>
            tryPlanDeterministicCraftingBig(
            BigInteger requestedAmount,
            BigInventorySnapshot<K> inventory,
            int maximumBits) {
        requireSnapshot(inventory);
        BigInteger request = BigCountMath.requireMaximumBits(
                requestedAmount,
                "compiled-root/deterministic/request",
                maximumBits);
        /*
         * Exact Vector親は完成品を新規作成する契約なので、0注文と既存在庫を使うrootは
         * AE2標準経路へ戻す。これにより完成品の取り出しと再投入を数式上で相殺しない。
         */
        if (request.signum() == 0
                || inventory.amountAt(rootIndex).signum() != 0) {
            return Optional.empty();
        }

        int nodeCount = keys.size();
        BigInteger[] demand = new BigInteger[nodeCount];
        int[] patternDepth = new int[nodeCount];
        demand[rootIndex] = request;
        patternDepth[rootIndex] = 1;
        Map<K, BigInteger> boundaryInputs = new LinkedHashMap<>();
        Map<K, BigInteger> boundaryOutputs = new LinkedHashMap<>();
        Map<K, BigInteger> missing = new LinkedHashMap<>();
        Set<String> requiredPatterns = new LinkedHashSet<>(patternCount);
        List<DeterministicPatternStep<K>> patternSteps =
                new ArrayList<>(patternCount);
        BigInteger logicalExecutions = BigInteger.ZERO;
        int logicalStageCount = 0;

        // 親から子へ並んだDAGを一巡し、同じ中間素材の需要を全親から先に集約する。
        for (int node = 0; node < nodeCount; node++) {
            BigInteger required = zeroIfNull(demand[node]);
            // 在庫にもPatternにも触れない未到達ノードは演算と割り当てを行わない。
            if (required.signum() == 0) {
                continue;
            }

            BigInteger taken = required.min(inventory.amountAt(node));
            BigInteger deficit = required.subtract(taken);
            BigInteger produced = BigInteger.ZERO;

            // 在庫だけでは足りないノードだけ、確定Patternを一度の除算と乗算で展開する。
            if (deficit.signum() > 0) {
                // Emitterは外部供給であり、ME在庫だけを原子的に会計する経路では扱わない。
                if (emittable[node]) {
                    return Optional.empty();
                }
                CompiledPattern<K> pattern = patternAt(node);
                // Patternがない終端素材は不足量を記録し、他の独立枝の確認を継続する。
                if (pattern == null) {
                    missing.put(keys.get(node), deficit);
                } else {
                    // Processing Patternは機械時間を持つため、作業台一括経路へ混入させない。
                    if (pattern.externalPush()) {
                        return Optional.empty();
                    }
                    BigInteger executions = BigCountMath.requireMaximumBits(
                            BigCountMath.ceilDiv(
                                    deficit,
                                    BigInteger.valueOf(outputAmounts[node]),
                                    "compiled-root/deterministic/executions/"
                                            + node),
                            "compiled-root/deterministic/executions/" + node,
                            maximumBits);
                    produced = BigCountMath.multiply(
                            BigInteger.valueOf(outputAmounts[node]),
                            executions,
                            "compiled-root/deterministic/output/" + node,
                            maximumBits);
                    logicalExecutions = BigCountMath.add(
                            logicalExecutions,
                            executions,
                            "compiled-root/deterministic/logical-executions",
                            maximumBits);
                    requiredPatterns.add(patternIds[node]);
                    int depth = patternDepth[node];
                    // 到達したPatternに深度がない場合はDAG配列の内部不整合として拒否する。
                    if (depth <= 0) {
                        throw new IllegalStateException(
                                "active deterministic pattern has no dependency depth");
                    }
                    logicalStageCount = Math.max(logicalStageCount, depth);
                    List<DeterministicInput<K>> selectedInputs =
                            new ArrayList<>(
                                    inputOffsets[node + 1]
                                            - inputOffsets[node]);
                    // 各slotで選んだ具体入力を保存し、同じ選択を物理assembleまで維持する。
                    for (int slot = inputOffsets[node];
                            slot < inputOffsets[node + 1];
                            slot++) {
                        int edge = selectBigAlternative(
                                slot,
                                executions,
                                inventory.amounts,
                                maximumBits,
                                true);
                        selectedInputs.add(new DeterministicInput<>(
                                keys.get(inputIndices[edge]),
                                inputAmounts[edge]));
                        BigInteger requiredInput = BigCountMath.multiply(
                                BigInteger.valueOf(inputAmounts[edge]),
                                executions,
                                "compiled-root/deterministic/input/"
                                        + node
                                        + '/'
                                        + edge,
                                maximumBits);
                        int child = inputIndices[edge];
                        demand[child] = BigCountMath.add(
                                zeroIfNull(demand[child]),
                                requiredInput,
                                "compiled-root/deterministic/demand/" + child,
                                maximumBits);
                        int childDepth = Math.addExact(depth, 1);
                        patternDepth[child] = Math.max(
                                patternDepth[child],
                                childDepth);
                    }
                    patternSteps.add(new DeterministicPatternStep<>(
                            patternIds[node],
                            depth,
                            executions,
                            selectedInputs));
                }
            }

            /*
             * 不足終端では実在庫から使える分だけを境界入力へ残す。
             * 計画自体は後で拒否されるが、存在しない数量をReceiptへ混ぜない。
             */
            if (deficit.signum() > 0 && patterns[node] == null) {
                // 部分在庫が正数の時だけ、診断可能な正確な境界入力として保持する。
                if (taken.signum() > 0) {
                    boundaryInputs.put(keys.get(node), taken);
                }
                continue;
            }

            /*
             * 中間素材は「この段で作った量 - 親段が要求した量」だけをME境界へ出す。
             * これにより内部素材を実体化せず、正味の搬入出だけを原子的に会計できる。
             */
            BigInteger netOutput = produced.subtract(required);
            // rootは最終成果物なので、要求数と丸め余剰をまとめた生成量を出力側へ残す。
            if (node == rootIndex) {
                // root Patternが要求量を生成できた場合だけ出力境界へ登録する。
                if (produced.compareTo(request) >= 0) {
                    boundaryOutputs.put(keys.get(node), produced);
                }
                continue;
            }
            // 負の正味量はME在庫から取り出す入力、正の正味量は余剰出力になる。
            if (netOutput.signum() < 0) {
                boundaryInputs.put(keys.get(node), netOutput.negate());
            } else if (netOutput.signum() > 0) {
                boundaryOutputs.put(keys.get(node), netOutput);
            }
        }

        /*
         * Workerは材料側から完成品側へ進む必要がある。
         * 親から子へ走査した計画を深度降順へ並べ替え、同じ深度の安定順は維持する。
         */
        patternSteps.sort((left, right) ->
                Integer.compare(right.depth(), left.depth()));
        return Optional.of(new DeterministicCraftingBigPlan<>(
                boundaryInputs,
                boundaryOutputs,
                missing,
                List.copyOf(requiredPatterns),
                List.copyOf(patternSteps),
                logicalExecutions,
                logicalStageCount));
    }

    private BigCraftingPlan<K> planBigInternal(
            BigInteger requestedAmount,
            BigInteger[] inventory,
            PlanningGuard guard,
            int maximumBits) {
        BigCountMath.requireMaximumBits(requestedAmount, "compiled-root/request", maximumBits);
        Objects.requireNonNull(guard, "guard");

        int nodeCount = keys.size();
        BigInteger[] demand = new BigInteger[nodeCount];
        BigInteger[] patternExecutions = new BigInteger[nodeCount];
        BigInteger[] used = new BigInteger[nodeCount];
        demand[rootIndex] = requestedAmount;

        // 注文桁数に関係なく、long経路と同じ固有ノード数だけを一巡する。
        for (int node = 0; node < nodeCount; node++) {
            guard.checkpoint(node + 1);
            BigInteger required = zeroIfNull(demand[node]);
            // 処理済みノードの需要欄は、Emitterまたは不足終端の残量格納へ再利用する。
            demand[node] = null;
            // この注文から到達しなかった枝はBigInteger演算を割り当てない。
            if (required.signum() == 0) {
                continue;
            }

            BigInteger taken = required.min(inventory[node]);
            used[node] = taken;
            BigInteger deficit = required.subtract(taken);
            // 在庫だけで満たせたキーはPatternや不足へ伝播させない。
            if (deficit.signum() == 0) {
                continue;
            }
            // EmitterはAE2と同じく不足量を直接供給する。
            if (emittable[node]) {
                demand[node] = deficit;
                continue;
            }
            // Patternがない全終端を不足配列へ記録し、最初の不足で打ち切らない。
            if (patterns[node] == null) {
                demand[node] = deficit;
                continue;
            }

            BigInteger executions = BigCountMath.ceilDiv(
                    deficit,
                    BigInteger.valueOf(outputAmounts[node]),
                    "compiled-root/executions/" + node);
            patternExecutions[node] = BigCountMath.requireMaximumBits(
                    executions,
                    "compiled-root/executions/" + node,
                    maximumBits);
            // 各slotで具体候補を一つ選び、BigInteger需要を子ノードへ加算する。
            for (int slot = inputOffsets[node]; slot < inputOffsets[node + 1]; slot++) {
                int edge = selectBigAlternative(
                        slot,
                        executions,
                        inventory,
                        maximumBits,
                        false);
                BigInteger requiredInput = BigCountMath.multiply(
                        BigInteger.valueOf(inputAmounts[edge]),
                        executions,
                        "compiled-root/input/" + node + '/' + edge,
                        maximumBits);
                int child = inputIndices[edge];
                demand[child] = BigCountMath.add(
                        zeroIfNull(demand[child]),
                        requiredInput,
                        "compiled-root/demand/" + child,
                        maximumBits);
            }
        }
        BigResultMaps<K> resultMaps = bigResultMaps(
                patternExecutions,
                used,
                demand);
        return new BigCraftingPlan<>(
                root,
                requestedAmount,
                resultMaps.patternExecutions(),
                resultMaps.usedInventory(),
                resultMaps.emitted(),
                resultMaps.missing(),
                nodeCount);
    }

    /** 三本の数量配列を一巡し、最終Planが必要とする四Mapを同時に物質化する。 */
    private LongResultMaps<K> longResultMaps(
            long[] patternExecutions,
            long[] used,
            long[] terminalRemainders) {
        Map<String, Long> patterns = new LinkedHashMap<>();
        Map<K, Long> usedInventory = new LinkedHashMap<>();
        Map<K, Long> emitted = new LinkedHashMap<>();
        Map<K, Long> missing = new LinkedHashMap<>();
        // 個別Mapごとの全ノード再走査を避け、各ノードを一度だけ分類する。
        for (int node = 0; node < patternExecutions.length; node++) {
            long executions = patternExecutions[node];
            // 実行されたPatternだけをAE2計画へ登録する。
            if (executions > 0L) {
                CheckedLongMath.merge(
                        patterns,
                        patternIds[node],
                        executions,
                        "compiled-root/result-pattern");
            }
            long usedAmount = used[node];
            // 在庫を実際に予約したキーだけを結果へ登録する。
            if (usedAmount > 0L) {
                usedInventory.put(keys.get(node), usedAmount);
            }
            long remainder = terminalRemainders[node];
            // 解決済みまたはPatternノードには終端残量がない。
            if (remainder == 0L) {
                continue;
            }
            // 同じ残量配列を静的ノード種別でEmitterと不足へ分離する。
            if (emittable[node]) {
                emitted.put(keys.get(node), remainder);
            } else {
                missing.put(keys.get(node), remainder);
            }
        }
        return new LongResultMaps<>(
                patterns,
                usedInventory,
                emitted,
                missing);
    }

    /** BigInteger経路も各ノードを一巡だけし、計画Mapの重複走査を避ける。 */
    private BigResultMaps<K> bigResultMaps(
            BigInteger[] patternExecutions,
            BigInteger[] used,
            BigInteger[] terminalRemainders) {
        Map<String, BigInteger> patterns = new LinkedHashMap<>();
        Map<K, BigInteger> usedInventory = new LinkedHashMap<>();
        Map<K, BigInteger> emitted = new LinkedHashMap<>();
        Map<K, BigInteger> missing = new LinkedHashMap<>();
        // nullを0として扱い、到達したノードだけを結果Mapへ物質化する。
        for (int node = 0; node < patternExecutions.length; node++) {
            BigInteger executions = zeroIfNull(patternExecutions[node]);
            // 実行されたPatternだけをBigInteger計画へ登録する。
            if (executions.signum() != 0) {
                patterns.put(patternIds[node], executions);
            }
            BigInteger usedAmount = zeroIfNull(used[node]);
            // 在庫を実際に予約したキーだけを結果へ登録する。
            if (usedAmount.signum() != 0) {
                usedInventory.put(keys.get(node), usedAmount);
            }
            BigInteger remainder = zeroIfNull(terminalRemainders[node]);
            // 解決済みまたはPatternノードには終端残量がない。
            if (remainder.signum() == 0) {
                continue;
            }
            // 同じ残量配列を静的ノード種別でEmitterと不足へ分離する。
            if (emittable[node]) {
                emitted.put(keys.get(node), remainder);
            } else {
                missing.put(keys.get(node), remainder);
            }
        }
        return new BigResultMaps<>(
                patterns,
                usedInventory,
                emitted,
                missing);
    }

    private record LongResultMaps<K>(
            Map<String, Long> patternExecutions,
            Map<K, Long> usedInventory,
            Map<K, Long> emitted,
            Map<K, Long> missing) {
    }

    private record BigResultMaps<K>(
            Map<String, BigInteger> patternExecutions,
            Map<K, BigInteger> usedInventory,
            Map<K, BigInteger> emitted,
            Map<K, BigInteger> missing) {
    }

    private static BigInteger zeroIfNull(BigInteger value) {
        return value == null ? BigInteger.ZERO : value;
    }

    private int selectLongAlternative(
            int slot,
            long executions,
            InventorySnapshot<K> inventory) {
        int firstEdge = alternativeOffsets[slot];
        int edgeLimit = alternativeOffsets[slot + 1];
        // 一意候補は順位付け不要なので、通常AE2高速経路ではそのまま返す。
        if (edgeLimit - firstEdge == 1) {
            return firstEdge;
        }
        int selected = -1;
        int selectedRank = Integer.MAX_VALUE;
        CountOverflowException firstOverflow = null;
        // Patternが提示した候補順を保ち、同順位ではAE2の先頭候補を採用する。
        for (int edge = firstEdge; edge < edgeLimit; edge++) {
            long required;
            try {
                required = CheckedLongMath.multiplyIndexed(
                        inputAmounts[edge],
                        executions,
                        "compiled-root/alternative",
                        edge);
            } catch (CountOverflowException overflow) {
                // 他候補だけがlongへ収まる場合を試すため、最初のoverflowを保留する。
                if (firstOverflow == null) {
                    firstOverflow = overflow;
                }
                continue;
            }
            int rank = alternativeRankLong(
                    inputIndices[edge],
                    required,
                    inventory.amountAt(inputIndices[edge]),
                    false);
            // より安全な候補だけへ更新し、同順位のPattern順は崩さない。
            if (rank < selectedRank) {
                selected = edge;
                selectedRank = rank;
            }
        }
        // 一つでも無損失候補を選べた場合だけ、その候補辺を返す。
        if (selected >= 0) {
            return selected;
        }
        // 全候補がoverflowした場合は、暗黙wrapせず最初の原因を上位へ返す。
        if (firstOverflow != null) {
            throw firstOverflow;
        }
        throw new IllegalStateException(
                "compiled input slot has no long alternative");
    }

    /** long経路の候補順位をprimitive比較だけで決め、候補ごとのBigInteger生成を避ける。 */
    private int alternativeRankLong(
            int child,
            long required,
            long available,
            boolean craftingTableOnly) {
        // このslotを現在在庫だけで満たせる候補を最優先する。
        if (available >= required) {
            return ALTERNATIVE_RANK_AVAILABLE;
        }
        CompiledPattern<K> childPattern = patternAt(child);
        // 作業台限定経路では、加工機へpushする下位Patternを選択対象にしない。
        if (childPattern != null
                && (!craftingTableOnly
                        || !childPattern.externalPush())) {
            return ALTERNATIVE_RANK_CRAFTABLE;
        }
        // 通常PlannerだけはEmitterをAE2と同じ供給可能候補として扱う。
        if (!craftingTableOnly && emittable[child]) {
            return ALTERNATIVE_RANK_CRAFTABLE;
        }
        // 不足を減らせる部分在庫は、完全に存在しない終端候補より先に使う。
        if (available > 0L) {
            return ALTERNATIVE_RANK_PARTIAL;
        }
        // 作業台限定経路で使えない供給経路は、通常の不足終端より後に置く。
        if (craftingTableOnly
                && (childPattern != null
                        || emittable[child])) {
            return ALTERNATIVE_RANK_UNSUPPORTED;
        }
        return ALTERNATIVE_RANK_MISSING;
    }

    private int selectBigAlternative(
            int slot,
            BigInteger executions,
            BigInteger[] inventory,
            int maximumBits,
            boolean craftingTableOnly) {
        int selected = -1;
        int selectedRank = Integer.MAX_VALUE;
        IllegalArgumentException firstOverflow = null;
        // BigIntegerでも候補数だけを走査し、注文数量ぶんの反復は行わない。
        for (int edge = alternativeOffsets[slot];
                edge < alternativeOffsets[slot + 1];
                edge++) {
            BigInteger required;
            try {
                required = BigCountMath.multiply(
                        BigInteger.valueOf(inputAmounts[edge]),
                        executions,
                        "compiled-root/big-alternative/" + slot + '/' + edge,
                        maximumBits);
            } catch (IllegalArgumentException overflow) {
                // より少量の別候補が上限内に収まる可能性があるため、残り候補を確認する。
                if (firstOverflow == null) {
                    firstOverflow = overflow;
                }
                continue;
            }
            int child = inputIndices[edge];
            int rank = alternativeRank(
                    child,
                    required,
                    inventory[child],
                    craftingTableOnly);
            // 最小順位だけを採用し、同順位ではPattern内の候補順を維持する。
            if (rank < selectedRank) {
                selected = edge;
                selectedRank = rank;
            }
        }
        // 選択済み候補があれば、その具体キーを後続の需要計算へ固定する。
        if (selected >= 0) {
            return selected;
        }
        // 全候補が設定上限を越えた場合は、縮小せず正確な失敗を返す。
        if (firstOverflow != null) {
            throw firstOverflow;
        }
        throw new IllegalStateException(
                "compiled input slot has no BigInteger alternative");
    }

    private int alternativeRank(
            int child,
            BigInteger required,
            BigInteger available,
            boolean craftingTableOnly) {
        // このslotを現在在庫だけで満たせる候補を最優先する。
        if (available.compareTo(required) >= 0) {
            return ALTERNATIVE_RANK_AVAILABLE;
        }
        CompiledPattern<K> childPattern = patternAt(child);
        // 作業台限定経路では、加工機へpushする下位Patternを選択対象にしない。
        if (childPattern != null
                && (!craftingTableOnly
                        || !childPattern.externalPush())) {
            return ALTERNATIVE_RANK_CRAFTABLE;
        }
        // 通常PlannerだけはEmitterをAE2と同じ供給可能候補として扱う。
        if (!craftingTableOnly && emittable[child]) {
            return ALTERNATIVE_RANK_CRAFTABLE;
        }
        // 不足を減らせる部分在庫は、完全に存在しない終端候補より先に使う。
        if (available.signum() > 0) {
            return ALTERNATIVE_RANK_PARTIAL;
        }
        // 作業台限定経路で使えない供給経路は、通常の不足終端より後に置く。
        if (craftingTableOnly
                && (childPattern != null
                        || emittable[child])) {
            return ALTERNATIVE_RANK_UNSUPPORTED;
        }
        return ALTERNATIVE_RANK_MISSING;
    }

    private void requireSnapshot(InventorySnapshot<K> snapshot) {
        Objects.requireNonNull(snapshot, "inventory");
        // 別のルートプログラムで採取した添字配列を誤用するとキーがずれるため拒否する。
        if (snapshot.owner() != this) {
            throw new IllegalArgumentException("inventory snapshot belongs to another compiled root program");
        }
    }

    private void requireSnapshot(BigInventorySnapshot<K> snapshot) {
        Objects.requireNonNull(snapshot, "inventory");
        // 別のルートプログラムで採取した添字配列を誤用するとキーがずれるため拒否する。
        if (snapshot.owner() != this) {
            throw new IllegalArgumentException("inventory snapshot belongs to another compiled root program");
        }
    }

    public long generation() {
        return generation;
    }

    public K root() {
        return root;
    }

    public int nodeCount() {
        return keys.size();
    }

    public int patternCount() {
        return patternCount;
    }

    public K keyAt(int node) {
        return keys.get(node);
    }

    public int indexOf(K key) {
        return indexByKey.getOrDefault(key, -1);
    }

    @SuppressWarnings("unchecked")
    public CompiledPattern<K> patternAt(int node) {
        return (CompiledPattern<K>) patterns[node];
    }

    /** Vector Plannerが実行Mapを配列走査へ戻すための安定Pattern ID。 */
    public String patternIdAt(int node) {
        Objects.checkIndex(node, keys.size());
        return patternIds[node];
    }

    /** 一Pattern実行で生成される、このノードの主出力量。 */
    public long outputAmountAt(int node) {
        Objects.checkIndex(node, keys.size());
        return outputAmounts[node];
    }

    public boolean isEmittableAt(int node) {
        return emittable[node];
    }

    public int inputCountAt(int node) {
        Objects.checkIndex(node, keys.size());
        return inputOffsets[node + 1] - inputOffsets[node];
    }

    public K inputKeyAt(int node, int input) {
        int edge = checkedSingleAlternative(node, input);
        return keys.get(inputIndices[edge]);
    }

    public long inputAmountAt(int node, int input) {
        return inputAmounts[checkedSingleAlternative(node, input)];
    }

    public int inputAlternativeCountAt(int node, int input) {
        int slot = checkedSlot(node, input);
        return alternativeOffsets[slot + 1]
                - alternativeOffsets[slot];
    }

    public K inputAlternativeKeyAt(
            int node,
            int input,
            int alternative) {
        int edge = checkedAlternative(
                node,
                input,
                alternative);
        return keys.get(inputIndices[edge]);
    }

    public long inputAlternativeAmountAt(
            int node,
            int input,
            int alternative) {
        return inputAmounts[checkedAlternative(
                node,
                input,
                alternative)];
    }

    private int checkedSlot(int node, int input) {
        Objects.checkIndex(node, keys.size());
        int count = inputCountAt(node);
        Objects.checkIndex(input, count);
        return inputOffsets[node] + input;
    }

    private int checkedAlternative(
            int node,
            int input,
            int alternative) {
        int slot = checkedSlot(node, input);
        int count = alternativeOffsets[slot + 1]
                - alternativeOffsets[slot];
        Objects.checkIndex(alternative, count);
        return alternativeOffsets[slot] + alternative;
    }

    private int checkedSingleAlternative(
            int node,
            int input) {
        int count = inputAlternativeCountAt(
                node,
                input);
        // 単一候補専用APIで複数候補を暗黙に先頭へ縮退させない。
        if (count != 1) {
            throw new IllegalStateException(
                    "compiled input slot has "
                            + count
                            + " alternatives");
        }
        return checkedAlternative(
                node,
                input,
                0);
    }

    public Map<K, CompiledPattern<K>> patternsByOutput() {
        return patternsByOutput;
    }

    public Set<K> emittableKeys() {
        return emittableKeys;
    }

    /** 確定作業台DAG一巡の最小結果。汎用Pattern実行Mapや五本の数量配列を持たない。 */
    public record DeterministicCraftingBigPlan<K>(
            Map<K, BigInteger> boundaryInputs,
            Map<K, BigInteger> boundaryOutputs,
            Map<K, BigInteger> missing,
            List<String> requiredPatternIds,
            List<DeterministicPatternStep<K>> patternSteps,
            BigInteger logicalExecutions,
            int logicalStageCount) {
        public DeterministicCraftingBigPlan {
            boundaryInputs = immutablePositiveCounts(
                    boundaryInputs,
                    "boundaryInputs");
            boundaryOutputs = immutablePositiveCounts(
                    boundaryOutputs,
                    "boundaryOutputs");
            missing = immutablePositiveCounts(
                    missing,
                    "missing");
            requiredPatternIds = List.copyOf(
                    Objects.requireNonNull(
                            requiredPatternIds,
                            "requiredPatternIds"));
            patternSteps = List.copyOf(
                    Objects.requireNonNull(
                            patternSteps,
                            "patternSteps"));
            // Pattern ID一覧と実行Step一覧は同じ固有Pattern集合を表す。
            if (patternSteps.size() != requiredPatternIds.size()) {
                throw new IllegalArgumentException(
                        "pattern step count does not match required patterns");
            }
            BigCountMath.requireNonNegative(
                    logicalExecutions,
                    "deterministic-crafting/logicalExecutions");
            // 実行Patternがない計画だけが0段を取り、負数の段数は常に不正とする。
            if (logicalStageCount < 0) {
                throw new IllegalArgumentException(
                        "logicalStageCount must be non-negative");
            }
        }

        public boolean craftable() {
            return missing.isEmpty();
        }

        private static <K> Map<K, BigInteger> immutablePositiveCounts(
                Map<K, BigInteger> source,
                String name) {
            Objects.requireNonNull(source, name);
            Map<K, BigInteger> copy = new LinkedHashMap<>(source.size());
            // Receiptへ渡る順序をDAG順のまま固定し、全境界値が正数であることも検証する。
            for (Map.Entry<K, BigInteger> entry : source.entrySet()) {
                K key = Objects.requireNonNull(
                        entry.getKey(),
                        name + " key");
                BigInteger amount = BigCountMath.requireNonNegative(
                        entry.getValue(),
                        name + " amount");
                // 0件は境界リストへ保存せず、同一キーの二重表現を作らない。
                if (amount.signum() == 0) {
                    throw new IllegalArgumentException(
                            name + " must not contain zero amounts");
                }
                copy.put(key, amount);
            }
            return Collections.unmodifiableMap(copy);
        }
    }

    /**
     * 一つの作業台Patternを数量非依存の物理仕事へ変換するための実行係数。
     *
     * <p>depthが大きいほど材料側であり、実行時は深度降順に処理する。</p>
     */
    public record DeterministicPatternStep<K>(
            String patternId,
            int depth,
            BigInteger executions,
            List<DeterministicInput<K>> selectedInputs) {
        public DeterministicPatternStep {
            patternId = Objects.requireNonNull(
                    patternId,
                    "patternId").trim();
            selectedInputs = List.copyOf(
                    Objects.requireNonNull(
                            selectedInputs,
                            "selectedInputs"));
            if (patternId.isEmpty()
                    || depth <= 0
                    || BigCountMath.requireNonNegative(
                                    executions,
                                    "pattern step executions")
                            .signum() == 0
                    || selectedInputs.isEmpty()) {
                throw new IllegalArgumentException(
                        "invalid deterministic pattern step");
            }
            // 各slotはPlannerが選択した正数の具体キーを一件だけ保持する。
            for (DeterministicInput<K> input : selectedInputs) {
                Objects.requireNonNull(
                        input,
                        "selected input");
            }
        }
    }

    /** Pattern slot一件で選択された、一回実行当たりの具体入力。 */
    public record DeterministicInput<K>(
            K key,
            long amount) {
        public DeterministicInput {
            Objects.requireNonNull(
                    key,
                    "key");
            // 一回入力量はAE2 Pattern APIのsigned long正数だけを保存する。
            if (amount <= 0L) {
                throw new IllegalArgumentException(
                        "deterministic input amount must be positive");
            }
        }
    }

    /** ルートプログラムと同じ添字順で保持するlong在庫Snapshot。 */
    public static final class InventorySnapshot<K> {
        private final CompiledRootProgram<K> owner;
        private final long[] amounts;

        private InventorySnapshot(CompiledRootProgram<K> owner, long[] amounts) {
            this.owner = owner;
            // private生成元が新規確保した配列の所有権を受け取り、同内容の再copyを避ける。
            this.amounts = amounts;
        }

        private CompiledRootProgram<K> owner() {
            return owner;
        }

        private long amountAt(int index) {
            return amounts[index];
        }

        public int size() {
            return amounts.length;
        }
    }

    /** ルートプログラムと同じ添字順で保持するBigInteger在庫Snapshot。 */
    public static final class BigInventorySnapshot<K> {
        private final CompiledRootProgram<K> owner;
        private final BigInteger[] amounts;

        private BigInventorySnapshot(CompiledRootProgram<K> owner, BigInteger[] amounts) {
            this.owner = owner;
            // private生成元が新規確保した配列の所有権を受け取り、同内容の再copyを避ける。
            this.amounts = amounts;
        }

        private CompiledRootProgram<K> owner() {
            return owner;
        }

        private BigInteger amountAt(int index) {
            return amounts[index];
        }

        public int size() {
            return amounts.length;
        }
    }
}
