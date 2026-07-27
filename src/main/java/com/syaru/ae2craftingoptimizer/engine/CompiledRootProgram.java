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

    private final long generation;
    private final K root;
    private final int rootIndex;
    private final List<K> keys;
    private final Map<K, Integer> indexByKey;
    private final Map<String, Integer> indexByPatternId;
    private final Object[] patterns;
    private final String[] patternIds;
    private final long[] outputAmounts;
    private final int[] inputOffsets;
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
            Map<String, Integer> indexByPatternId,
            Object[] patterns,
            String[] patternIds,
            long[] outputAmounts,
            int[] inputOffsets,
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
        this.indexByPatternId = Map.copyOf(indexByPatternId);
        this.patterns = patterns.clone();
        this.patternIds = patternIds.clone();
        this.outputAmounts = outputAmounts.clone();
        this.inputOffsets = inputOffsets.clone();
        this.inputIndices = inputIndices.clone();
        this.inputAmounts = inputAmounts.clone();
        this.emittable = emittable.clone();
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
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(canEmit, "canEmit");

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
            // 各入力slotが一つの確定キーだけを受け入れることを検証する。
            for (CompiledPattern.InputSlot<K> slot : pattern.inputs()) {
                // タグ、代替素材、ファジー候補を含むslotはAE2標準計算へ戻す。
                if (slot.alternatives().size() != 1) {
                    return Optional.empty();
                }
                K child = slot.alternatives().get(0).key();
                children.add(child);
                discover.push(child);
            }
            selected.put(key, pattern);
            dependencies.put(key, Set.copyOf(children));
        }

        List<K> order = topologicalOrder(reachable, dependencies);
        // 全ノードを並べられなかった場合は、探索中に見えなかった循環があるためFallbackする。
        if (order.size() != reachable.size()) {
            return Optional.empty();
        }

        Map<K, Integer> indexByKey = new LinkedHashMap<>();
        // 実行時にMap検索をせずに済むよう、トポロジカル順へ連番を付ける。
        for (int index = 0; index < order.size(); index++) {
            indexByKey.put(order.get(index), index);
        }

        int edgeCount = 0;
        // 入力配列を一回だけ確保するため、Patternごとのslot数を先に合計する。
        for (K key : order) {
            CompiledPattern<K> pattern = selected.get(key);
            // 終端ノードには入力辺がないため、Patternノードだけを数える。
            if (pattern != null) {
                try {
                    edgeCount = Math.addExact(edgeCount, pattern.inputs().size());
                } catch (ArithmeticException overflow) {
                    return Optional.empty();
                }
                // 入力辺の固定上限を超えるデータは、配列確保前にFallbackする。
                if (edgeCount > MAXIMUM_PROGRAM_INPUT_EDGES) {
                    return Optional.empty();
                }
            }
        }

        int nodeCount = order.size();
        Object[] patterns = new Object[nodeCount];
        String[] patternIds = new String[nodeCount];
        long[] outputAmounts = new long[nodeCount];
        int[] inputOffsets = new int[nodeCount + 1];
        int[] inputIndices = new int[edgeCount];
        long[] inputAmounts = new long[edgeCount];
        boolean[] emitters = new boolean[nodeCount];
        Map<String, Integer> indexByPatternId =
                new LinkedHashMap<>();
        int edgeCursor = 0;
        int compiledPatterns = 0;

        // Map中心のGraphを、実行時に直接添字アクセスできる不変配列へ変換する。
        for (int node = 0; node < nodeCount; node++) {
            K key = order.get(node);
            CompiledPattern<K> pattern = selected.get(key);
            emitters[node] = emitterKeys.contains(key);
            inputOffsets[node] = edgeCursor;
            // 在庫、Emitter、不足だけで解決する終端ノードにはPattern情報を書き込まない。
            if (pattern == null) {
                continue;
            }

            patterns[node] = pattern;
            String patternId = pattern.id();
            Integer previousPatternNode =
                    indexByPatternId.putIfAbsent(patternId, node);
            // 同じIDが複数出力を指すGraphでは、実行回数Mapを一意なノードへ戻せない。
            if (previousPatternNode != null) {
                return Optional.empty();
            }
            patternIds[node] = patternId;
            outputAmounts[node] = pattern.outputAmount(key);
            compiledPatterns++;
            // 各slotはコンパイル条件により候補が一つなので、そのキーと量だけを平坦化する。
            for (CompiledPattern.InputSlot<K> slot : pattern.inputs()) {
                CompiledPattern.Stack<K> input = slot.alternatives().get(0);
                Integer childIndex = indexByKey.get(input.key());
                // 子ノードが未登録または親より先なら、トポロジカル順が壊れているためFallbackする。
                if (childIndex == null || childIndex <= node) {
                    return Optional.empty();
                }
                inputIndices[edgeCursor] = childIndex;
                inputAmounts[edgeCursor] = input.amount();
                edgeCursor++;
            }
        }
        inputOffsets[nodeCount] = edgeCursor;

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
                indexByPatternId,
                patterns,
                patternIds,
                outputAmounts,
                inputOffsets,
                inputIndices,
                inputAmounts,
                emitters,
                selected,
                emitterKeys,
                compiledPatterns));
    }

    private static <K> List<K> topologicalOrder(
            Set<K> reachable,
            Map<K, Set<K>> dependencies) {
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
        long[] emitted = new long[nodeCount];
        long[] missing = new long[nodeCount];
        demand[rootIndex] = requestedAmount;

        // 全親の要求が集約済みになるトポロジカル順で、各固有キーを一度だけ処理する。
        for (int node = 0; node < nodeCount; node++) {
            guard.checkpoint(node + 1);
            long required = demand[node];
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
                emitted[node] = deficit;
                continue;
            }
            // Patternがない終端は全件を不足一覧へ残し、他の枝の計算は継続する。
            if (patterns[node] == null) {
                missing[node] = deficit;
                continue;
            }

            long executions = CheckedLongMath.ceilDiv(
                    deficit,
                    outputAmounts[node],
                    "compiled-root/executions/" + node);
            patternExecutions[node] = executions;
            // 一回のPattern入力へ実行回数を掛け、共有中間素材の需要配列へ加算する。
            for (int edge = inputOffsets[node]; edge < inputOffsets[node + 1]; edge++) {
                long requiredInput = CheckedLongMath.multiply(
                        inputAmounts[edge],
                        executions,
                        "compiled-root/input/" + node + '/' + edge);
                int child = inputIndices[edge];
                demand[child] = CheckedLongMath.add(
                        demand[child],
                        requiredInput,
                        "compiled-root/demand/" + child);
            }
        }
        return new LongCraftingPlan<>(
                root,
                requestedAmount,
                longPatternMap(patternExecutions),
                longKeyMap(used),
                longKeyMap(emitted),
                longKeyMap(missing));
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
        return planBigInternal(requestedAmount, inventory.copyAmounts(), guard, maximumBits);
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

                    // 各固定入力へ実行回数を直接掛け、子ノード需要へ一度だけ加算する。
                    for (int edge = inputOffsets[node];
                            edge < inputOffsets[node + 1];
                            edge++) {
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

        return Optional.of(new DeterministicCraftingBigPlan<>(
                boundaryInputs,
                boundaryOutputs,
                missing,
                List.copyOf(requiredPatterns),
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
        BigInteger[] emitted = new BigInteger[nodeCount];
        BigInteger[] missing = new BigInteger[nodeCount];
        demand[rootIndex] = requestedAmount;

        // 注文桁数に関係なく、long経路と同じ固有ノード数だけを一巡する。
        for (int node = 0; node < nodeCount; node++) {
            guard.checkpoint(node + 1);
            BigInteger required = zeroIfNull(demand[node]);
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
                emitted[node] = deficit;
                continue;
            }
            // Patternがない全終端を不足配列へ記録し、最初の不足で打ち切らない。
            if (patterns[node] == null) {
                missing[node] = deficit;
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
            // 各入力量をBigIntegerで掛け、子ノードの需要へ上限検査付きで加算する。
            for (int edge = inputOffsets[node]; edge < inputOffsets[node + 1]; edge++) {
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
        return new BigCraftingPlan<>(
                root,
                requestedAmount,
                bigPatternMap(patternExecutions),
                bigKeyMap(used),
                bigKeyMap(emitted),
                bigKeyMap(missing),
                nodeCount);
    }

    private Map<String, Long> longPatternMap(long[] counts) {
        Map<String, Long> result = new LinkedHashMap<>();
        // 実行されたPatternだけを最終AE2計画用Mapへ物質化する。
        for (int node = 0; node < counts.length; node++) {
            // 0回のPatternは計画へ含めない。
            if (counts[node] > 0L) {
                CheckedLongMath.merge(
                        result,
                        patternIds[node],
                        counts[node],
                        "compiled-root/result-pattern");
            }
        }
        return Map.copyOf(result);
    }

    private Map<K, Long> longKeyMap(long[] counts) {
        Map<K, Long> result = new LinkedHashMap<>();
        // 0でないキーだけをAE2のKeyCounterへ渡す最終Mapへ変換する。
        for (int node = 0; node < counts.length; node++) {
            // 0量は計画サイズと後続同期量を増やすだけなので除外する。
            if (counts[node] > 0L) {
                result.put(keys.get(node), counts[node]);
            }
        }
        return Map.copyOf(result);
    }

    private Map<String, BigInteger> bigPatternMap(BigInteger[] counts) {
        Map<String, BigInteger> result = new LinkedHashMap<>();
        // 実行されたPatternだけをBigInteger計画用Mapへ物質化する。
        for (int node = 0; node < counts.length; node++) {
            BigInteger amount = zeroIfNull(counts[node]);
            // 0回のPatternは計画へ含めない。
            if (amount.signum() != 0) {
                result.put(patternIds[node], amount);
            }
        }
        return Map.copyOf(result);
    }

    private Map<K, BigInteger> bigKeyMap(BigInteger[] counts) {
        Map<K, BigInteger> result = new LinkedHashMap<>();
        // 0でないキーだけをBigInteger計画の最終Mapへ変換する。
        for (int node = 0; node < counts.length; node++) {
            BigInteger amount = zeroIfNull(counts[node]);
            // 0量は保存・同期対象から除外する。
            if (amount.signum() != 0) {
                result.put(keys.get(node), amount);
            }
        }
        return Map.copyOf(result);
    }

    private static BigInteger zeroIfNull(BigInteger value) {
        return value == null ? BigInteger.ZERO : value;
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

    /** 世代内安定Pattern IDを、数量非依存のO(1)索引でノードへ戻す。 */
    public int indexOfPatternId(String patternId) {
        return indexByPatternId.getOrDefault(
                Objects.requireNonNull(patternId, "patternId"),
                -1);
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
        return inputOffsets[node + 1] - inputOffsets[node];
    }

    public K inputKeyAt(int node, int input) {
        int edge = checkedEdge(node, input);
        return keys.get(inputIndices[edge]);
    }

    public long inputAmountAt(int node, int input) {
        return inputAmounts[checkedEdge(node, input)];
    }

    private int checkedEdge(int node, int input) {
        Objects.checkIndex(node, keys.size());
        int count = inputCountAt(node);
        Objects.checkIndex(input, count);
        return inputOffsets[node] + input;
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

    /** ルートプログラムと同じ添字順で保持するlong在庫Snapshot。 */
    public static final class InventorySnapshot<K> {
        private final CompiledRootProgram<K> owner;
        private final long[] amounts;

        private InventorySnapshot(CompiledRootProgram<K> owner, long[] amounts) {
            this.owner = owner;
            this.amounts = amounts.clone();
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
            this.amounts = amounts.clone();
        }

        private CompiledRootProgram<K> owner() {
            return owner;
        }

        private BigInteger[] copyAmounts() {
            return amounts.clone();
        }

        private BigInteger amountAt(int index) {
            return amounts[index];
        }

        public int size() {
            return amounts.length;
        }
    }
}
