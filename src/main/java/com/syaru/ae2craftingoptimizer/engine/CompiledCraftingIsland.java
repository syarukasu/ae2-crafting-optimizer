package com.syaru.ae2craftingoptimizer.engine;

import com.syaru.ae2craftingoptimizer.util.StableFingerprint;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 機械Patternで区切られた、決定的な作業台Patternだけの連結成分。
 *
 * <p>島内部の中間素材は実体化せず、全Patternの入出力をBigIntegerで相殺する。
 * 外から必要な入力と、外へ返す出力だけをAE2在庫へ反映する。</p>
 */
public final class CompiledCraftingIsland<K, P> {
    /** 異常なJobから巨大な隣接配列を作らないための一Job当たり固定上限。 */
    private static final int MAXIMUM_TASKS_PER_JOB = 1_048_576;
    private final List<Task<K, P>> tasks;
    private final Map<K, BigInteger> boundaryInputs;
    private final Map<K, BigInteger> boundaryOutputs;
    private final Map<K, BigInteger> internalOutputs;
    private final BigInteger sinkExecutions;
    private final BigInteger logicalExecutions;
    private final int criticalPathStages;
    private final String fingerprint;

    private CompiledCraftingIsland(
            List<Task<K, P>> tasks,
            Map<K, BigInteger> boundaryInputs,
            Map<K, BigInteger> boundaryOutputs,
            Map<K, BigInteger> internalOutputs,
            BigInteger sinkExecutions,
            BigInteger logicalExecutions,
            int criticalPathStages,
            String fingerprint) {
        this.tasks = List.copyOf(tasks);
        this.boundaryInputs = Map.copyOf(boundaryInputs);
        this.boundaryOutputs = Map.copyOf(boundaryOutputs);
        this.internalOutputs = Map.copyOf(internalOutputs);
        this.sinkExecutions = sinkExecutions;
        this.logicalExecutions = logicalExecutions;
        this.criticalPathStages = criticalPathStages;
        this.fingerprint = fingerprint;
    }

    /**
     * 安全判定済み作業台Taskを、直接依存するPattern同士の島へ分割する。
     *
     * <p>呼出側は処理Pattern、Fluid/Chemical、NBT、返却物、代替素材をTask一覧へ
     * 入れない。それらはTaskが欠けることで自然に島の外部境界になる。</p>
     */
    public static <K, P> Optional<List<CompiledCraftingIsland<K, P>>> tryCompile(
            List<Task<K, P>> sourceTasks,
            int maximumBits) {
        /*
         * 入出力が一意な一段Patternも数量非依存の島として成立する。
         * 段数は安全性と無関係なので、通常入口でも単一ノードを除外しない。
         */
        return tryCompile(sourceTasks, maximumBits, true);
    }

    /**
     * Exact Vector標準Job用に、単一Patternも決定的な一段の島として返す。
     */
    public static <K, P> Optional<List<CompiledCraftingIsland<K, P>>>
            tryCompileIncludingSingletons(
                    List<Task<K, P>> sourceTasks,
                    int maximumBits) {
        return tryCompile(sourceTasks, maximumBits, true);
    }

    /**
     * 世代検証済みの数式Programへ、現在Jobに残っているTask回数だけを投影する。
     *
     * <p>Patternの入出力式はProgramから読み、注文個数を反復しない。各Patternを一度だけ
     * {@code 入力量 * 実行回数}へ変換し、既存の島会計へ渡す。</p>
     */
    public static <K, P> Optional<CompiledCraftingIsland<K, P>>
            tryCompileProgramTasks(
                    CompiledRootProgram<K> program,
                    List<ProgramTask<P>> sourceTasks,
                    int maximumBits) {
        Objects.requireNonNull(program, "program");
        Objects.requireNonNull(sourceTasks, "sourceTasks");
        // 実行TaskがないJobや固定上限超過Jobは、配列を確保せず標準経路へ戻す。
        if (sourceTasks.isEmpty()
                || sourceTasks.size() > MAXIMUM_TASKS_PER_JOB) {
            return Optional.empty();
        }

        List<Task<K, P>> projected = new ArrayList<>(sourceTasks.size());
        Set<String> activePatternIds = new LinkedHashSet<>();
        // 現在Jobの残回数を、同じ世代の固定入出力式へ一件ずつ投影する。
        for (ProgramTask<P> sourceTask : sourceTasks) {
            ProgramTask<P> active =
                    Objects.requireNonNull(sourceTask, "sourceTask");
            // 同一Patternが二件に分裂したJobは、完了時のTask会計を一意に戻せない。
            if (!activePatternIds.add(active.patternId())) {
                return Optional.empty();
            }
            int node = program.indexOfPatternId(active.patternId());
            // 現在Programに存在しないTaskは標準経路へ戻す。
            if (node < 0) {
                return Optional.empty();
            }
            CompiledPattern<K> pattern = program.patternAt(node);
            // Processing Patternは機械時間を持つため、一括作業台会計へ混入させない。
            if (pattern == null || pattern.externalPush()) {
                return Optional.empty();
            }

            List<Input<K>> inputs =
                    new ArrayList<>(program.inputCountAt(node));
            // 一回実行当たりの固定入力を配列Programからそのまま復元する。
            for (int input = 0;
                    input < program.inputCountAt(node);
                    input++) {
                inputs.add(new Input<>(
                        program.inputKeyAt(node, input),
                        program.inputAmountAt(node, input)));
            }
            projected.add(new Task<>(
                    active.pattern(),
                    active.patternId(),
                    program.keyAt(node),
                    program.outputAmountAt(node),
                    inputs,
                    active.executions()));
        }

        Optional<List<CompiledCraftingIsland<K, P>>> compiled =
                tryCompileIncludingSingletons(projected, maximumBits);
        // 全Taskが一つの連結成分にならないJobは、一つの原子的会計へ結合しない。
        if (compiled.isEmpty()
                || compiled.orElseThrow().size() != 1) {
            return Optional.empty();
        }
        CompiledCraftingIsland<K, P> island =
                compiled.orElseThrow().get(0);
        // 投影中にTaskが欠落した場合は部分採用せず、Job全体を標準経路へ戻す。
        if (island.tasks().size() != sourceTasks.size()) {
            return Optional.empty();
        }
        return Optional.of(island);
    }

    private static <K, P> Optional<List<CompiledCraftingIsland<K, P>>> tryCompile(
            List<Task<K, P>> sourceTasks,
            int maximumBits,
            boolean includeSingletons) {
        Objects.requireNonNull(sourceTasks, "sourceTasks");
        // 0件は有効だが実行対象がないJobとして扱う。
        if (sourceTasks.isEmpty()) {
            return Optional.of(List.of());
        }
        // 固定上限を超えるJobは配列確保前に標準経路へ戻す。
        if (sourceTasks.size() > MAXIMUM_TASKS_PER_JOB) {
            return Optional.empty();
        }
        // BigInteger演算上限は少なくともsigned longを表現できる値を要求する。
        if (maximumBits < Long.SIZE) {
            throw new IllegalArgumentException("maximumBits must be at least " + Long.SIZE);
        }

        List<Task<K, P>> tasks = List.copyOf(sourceTasks);
        Map<K, Integer> producerByOutput = new LinkedHashMap<>();
        Set<String> patternIds = new LinkedHashSet<>();
        // 同一Patternまたは同一出力の複数生産者は、選択順で結果が変わるため拒否する。
        for (int index = 0; index < tasks.size(); index++) {
            Task<K, P> task = Objects.requireNonNull(tasks.get(index), "task");
            // 同じfingerprintが二回現れるJobはTask会計を一意に戻せない。
            if (!patternIds.add(task.patternId())) {
                return Optional.empty();
            }
            Integer previous = producerByOutput.putIfAbsent(task.output(), index);
            // 同一キーへ二種類のPatternがある場合はAE2本来の選択結果を維持する。
            if (previous != null) {
                return Optional.empty();
            }
        }

        List<Set<Integer>> undirected = new ArrayList<>(tasks.size());
        List<List<Integer>> dependents = new ArrayList<>(tasks.size());
        int[] dependencyCounts = new int[tasks.size()];
        // Pattern数ぶんの空隣接表を先に確保する。
        for (int index = 0; index < tasks.size(); index++) {
            undirected.add(new LinkedHashSet<>());
            dependents.add(new ArrayList<>());
        }

        // 作業台Pattern同士で直接受け渡すキーだけを島内部の辺として登録する。
        for (int consumer = 0; consumer < tasks.size(); consumer++) {
            Task<K, P> task = tasks.get(consumer);
            // 同じ生産者を複数slotから参照しても依存辺は一件にまとめる。
            Set<Integer> uniqueProducers = new LinkedHashSet<>();
            for (Input<K> input : task.inputs()) {
                Integer producer = producerByOutput.get(input.key());
                // 生産Taskがない入力は在庫または機械出力を待つ外部境界になる。
                if (producer == null) {
                    continue;
                }
                // 自分の出力を自分で入力するPatternも循環なので後段Kahn判定へ渡す。
                if (uniqueProducers.add(producer)) {
                    dependencyCounts[consumer]++;
                    dependents.get(producer).add(consumer);
                    undirected.get(consumer).add(producer);
                    undirected.get(producer).add(consumer);
                }
            }
        }

        // 再帰を使わないKahn法で循環を検出し、巨大ツリーでもJava stackを消費しない。
        if (hasCycle(dependents, dependencyCounts)) {
            return Optional.empty();
        }

        boolean[] assigned = new boolean[tasks.size()];
        List<CompiledCraftingIsland<K, P>> islands = new ArrayList<>();
        // 無向連結成分ごとに、機械境界で分離された一つの島を構築する。
        for (int seed = 0; seed < tasks.size(); seed++) {
            // 既に別の島へ割り当てたTaskは再処理しない。
            if (assigned[seed]) {
                continue;
            }
            List<Integer> component = collectComponent(seed, undirected, assigned);
            // 呼出側が明示的に単一ノードを除外する場合だけ、従来経路へ残す。
            if (!includeSingletons && component.size() < 2) {
                continue;
            }
            CompiledCraftingIsland<K, P> island =
                    buildIsland(tasks, component, maximumBits);
            // BigInteger上限内へ証明できなかった成分が一つでもあればJob全体をFallbackする。
            if (island == null) {
                return Optional.empty();
            }
            islands.add(island);
        }
        return Optional.of(List.copyOf(islands));
    }

    private static boolean hasCycle(
            List<List<Integer>> dependents,
            int[] sourceDependencyCounts) {
        int[] remainingDependencies = sourceDependencyCounts.clone();
        ArrayDeque<Integer> ready = new ArrayDeque<>();
        // 依存先を持たない生産側Patternを、最初に確定できるノードとして登録する。
        for (int node = 0; node < remainingDependencies.length; node++) {
            if (remainingDependencies[node] == 0) {
                ready.addLast(node);
            }
        }
        int visited = 0;
        // 確定済み生産Patternを除き、依存数が0になった消費Patternを順番に解放する。
        while (!ready.isEmpty()) {
            int producer = ready.removeFirst();
            visited++;
            for (int consumer : dependents.get(producer)) {
                int remaining = --remainingDependencies[consumer];
                // 最後の依存が解決したPatternだけを一度キューへ追加する。
                if (remaining == 0) {
                    ready.addLast(consumer);
                }
            }
        }
        // 全ノードを取り出せなければ、依存数が残った部分に有向循環がある。
        return visited != remainingDependencies.length;
    }

    private static List<Integer> collectComponent(
            int seed,
            List<Set<Integer>> undirected,
            boolean[] assigned) {
        ArrayDeque<Integer> pending = new ArrayDeque<>();
        List<Integer> component = new ArrayList<>();
        pending.add(seed);
        assigned[seed] = true;
        // 直接入出力で接続された全作業台Patternを一度ずつ集める。
        while (!pending.isEmpty()) {
            int node = pending.removeFirst();
            component.add(node);
            for (int adjacent : undirected.get(node)) {
                // 未割当の隣接Patternだけを同じ島の探索待ちへ追加する。
                if (!assigned[adjacent]) {
                    assigned[adjacent] = true;
                    pending.addLast(adjacent);
                }
            }
        }
        return List.copyOf(component);
    }

    private static <K, P> CompiledCraftingIsland<K, P> buildIsland(
            List<Task<K, P>> allTasks,
            List<Integer> component,
            int maximumBits) {
        List<Task<K, P>> tasks = new ArrayList<>(component.size());
        Map<K, BigInteger> produced = new LinkedHashMap<>();
        Map<K, BigInteger> consumed = new LinkedHashMap<>();
        Set<K> internallyConsumedKeys = new LinkedHashSet<>();
        BigInteger logicalExecutions = BigInteger.ZERO;

        // 各Patternの全実行量を数量ループなしで一回ずつ集計する。
        for (int taskIndex : component) {
            Task<K, P> task = allTasks.get(taskIndex);
            tasks.add(task);
            logicalExecutions = checkedAdd(
                    logicalExecutions,
                    task.executions(),
                    maximumBits);
            // bit上限超過はnullで上位へ返し、島だけを部分採用しない。
            if (logicalExecutions == null) {
                return null;
            }

            BigInteger outputTotal = checkedMultiply(
                    BigInteger.valueOf(task.outputAmount()),
                    task.executions(),
                    maximumBits);
            // 一つの出力合計でも上限を超えた場合は標準Execution Windowへ戻す。
            if (outputTotal == null
                    || !mergeChecked(produced, task.output(), outputTotal, maximumBits)) {
                return null;
            }
            // 同じ入力キーが複数slotにある場合もBigInteger Map上で正確に合算する。
            for (Input<K> input : task.inputs()) {
                BigInteger inputTotal = checkedMultiply(
                        BigInteger.valueOf(input.amount()),
                        task.executions(),
                        maximumBits);
                // 入力合計が上限外なら、wrapや飽和をせず島全体をFallbackする。
                if (inputTotal == null
                        || !mergeChecked(consumed, input.key(), inputTotal, maximumBits)) {
                    return null;
                }
                internallyConsumedKeys.add(input.key());
            }
        }
        int criticalPathStages = calculateCriticalPath(tasks);

        Map<K, BigInteger> boundaryInputs = new LinkedHashMap<>();
        Map<K, BigInteger> boundaryOutputs = new LinkedHashMap<>();
        Map<K, BigInteger> internalOutputs = new LinkedHashMap<>();
        Set<K> allKeys = new LinkedHashSet<>(produced.keySet());
        allKeys.addAll(consumed.keySet());
        // 生産量と消費量をキー単位で相殺し、外部差分だけを物質化する。
        for (K key : allKeys) {
            BigInteger producedAmount = produced.getOrDefault(key, BigInteger.ZERO);
            BigInteger consumedAmount = consumed.getOrDefault(key, BigInteger.ZERO);
            BigInteger internal = producedAmount.min(consumedAmount);
            // 島内部で一個以上消費される中間出力だけを進捗短縮対象へ記録する。
            if (internal.signum() > 0) {
                internalOutputs.put(key, internal);
            }
            int comparison = producedAmount.compareTo(consumedAmount);
            // 消費超過分は、島を起動するためCPU在庫に必要な境界入力。
            if (comparison < 0) {
                boundaryInputs.put(key, consumedAmount.subtract(producedAmount));
            // 生産超過分は、後続機械または最終Requesterへ返す境界出力。
            } else if (comparison > 0) {
                boundaryOutputs.put(key, producedAmount.subtract(consumedAmount));
            }
        }

        BigInteger sinkExecutions = BigInteger.ZERO;
        // 島の外へ結果を出すPattern回数だけをAACの一Wave容量判定へ使用する。
        for (Task<K, P> task : tasks) {
            // 別の島内Patternが出力を消費するTaskは中間段なのでroot容量へ数えない。
            if (internallyConsumedKeys.contains(task.output())) {
                continue;
            }
            sinkExecutions = checkedAdd(sinkExecutions, task.executions(), maximumBits);
            // root実行数が上限外なら安全なAAC Waveへ渡せない。
            if (sinkExecutions == null) {
                return null;
            }
        }
        // 外部出力を持たない成分は実行完了を観測できないため採用しない。
        if (sinkExecutions.signum() <= 0 || boundaryOutputs.isEmpty()) {
            return null;
        }

        StringBuilder fingerprintSource = new StringBuilder(tasks.size() * 96);
        // Pattern fingerprintと実行回数を安定順で連結し、再検証用IDを作る。
        for (Task<K, P> task : tasks) {
            fingerprintSource.append(task.patternId())
                    .append('@')
                    .append(task.executions())
                    .append(';');
        }
        return new CompiledCraftingIsland<>(
                tasks,
                boundaryInputs,
                boundaryOutputs,
                internalOutputs,
                sinkExecutions,
                logicalExecutions,
                criticalPathStages,
                StableFingerprint.sha256(fingerprintSource));
    }

    private static <K, P> int calculateCriticalPath(
            List<Task<K, P>> tasks) {
        Map<K, Integer> producerByOutput = new HashMap<>();
        List<List<Integer>> dependents = new ArrayList<>(tasks.size());
        int[] dependencies = new int[tasks.size()];
        int[] depths = new int[tasks.size()];
        // 各出力の生産Taskと逆辺Listを一度だけ準備する。
        for (int index = 0; index < tasks.size(); index++) {
            producerByOutput.put(tasks.get(index).output(), index);
            dependents.add(new ArrayList<>());
        }
        // 同じ生産Taskを複数slotから参照しても、段数の依存辺は一件にまとめる。
        for (int consumer = 0; consumer < tasks.size(); consumer++) {
            Set<Integer> uniqueProducers = new LinkedHashSet<>();
            for (Input<K> input : tasks.get(consumer).inputs()) {
                Integer producer = producerByOutput.get(input.key());
                if (producer != null && uniqueProducers.add(producer)) {
                    dependencies[consumer]++;
                    dependents.get(producer).add(consumer);
                }
            }
        }

        ArrayDeque<Integer> ready = new ArrayDeque<>();
        // 外部入力だけを読むPatternを論理第1段として開始する。
        for (int node = 0; node < tasks.size(); node++) {
            if (dependencies[node] == 0) {
                depths[node] = 1;
                ready.addLast(node);
            }
        }
        int visited = 0;
        int maximumDepth = 0;
        // Kahn順で最長親深度を伝播し、要求数量とは無関係に各Patternを一回だけ処理する。
        while (!ready.isEmpty()) {
            int producer = ready.removeFirst();
            visited++;
            maximumDepth = Math.max(maximumDepth, depths[producer]);
            for (int consumer : dependents.get(producer)) {
                depths[consumer] = Math.max(
                        depths[consumer],
                        Math.addExact(depths[producer], 1));
                int remaining = --dependencies[consumer];
                if (remaining == 0) {
                    ready.addLast(consumer);
                }
            }
        }
        if (visited != tasks.size() || maximumDepth <= 0) {
            throw new IllegalArgumentException(
                    "crafting island does not form a non-empty DAG");
        }
        return maximumDepth;
    }

    private static BigInteger checkedAdd(
            BigInteger left,
            BigInteger right,
            int maximumBits) {
        BigInteger result = left.add(right);
        return result.signum() >= 0 && result.bitLength() <= maximumBits
                ? result
                : null;
    }

    private static BigInteger checkedMultiply(
            BigInteger left,
            BigInteger right,
            int maximumBits) {
        BigInteger result = left.multiply(right);
        return result.signum() >= 0 && result.bitLength() <= maximumBits
                ? result
                : null;
    }

    private static <K> boolean mergeChecked(
            Map<K, BigInteger> target,
            K key,
            BigInteger amount,
            int maximumBits) {
        BigInteger merged = checkedAdd(
                target.getOrDefault(key, BigInteger.ZERO),
                amount,
                maximumBits);
        // nullは上限超過を表すためMapへ不完全値を書き込まない。
        if (merged == null) {
            return false;
        }
        target.put(key, merged);
        return true;
    }

    /** 実行時のAE2 long APIへ、全境界値を無損失で渡せるかを返す。 */
    public boolean fitsSignedLongRuntime() {
        // 入力、出力、内部進捗のどれか一つでもlong外ならExecution Windowへ戻す。
        for (Map<K, BigInteger> amounts :
                List.of(boundaryInputs, boundaryOutputs, internalOutputs)) {
            for (BigInteger amount : amounts.values()) {
                // 正のsigned longへ変換できない値は直接AE2在庫へ渡さない。
                if (amount.signum() < 0 || amount.bitLength() > Long.SIZE - 1) {
                    return false;
                }
            }
        }
        return true;
    }

    public List<Task<K, P>> tasks() {
        return tasks;
    }

    public Map<K, BigInteger> boundaryInputs() {
        return boundaryInputs;
    }

    public Map<K, BigInteger> boundaryOutputs() {
        return boundaryOutputs;
    }

    public Map<K, BigInteger> internalOutputs() {
        return internalOutputs;
    }

    public BigInteger sinkExecutions() {
        return sinkExecutions;
    }

    public BigInteger logicalExecutions() {
        return logicalExecutions;
    }

    public int criticalPathStages() {
        return criticalPathStages;
    }

    public String fingerprint() {
        return fingerprint;
    }

    /** 現在JobのPattern参照、世代内安定ID、残実行回数。 */
    public record ProgramTask<P>(
            P pattern,
            String patternId,
            BigInteger executions) {
        public ProgramTask {
            Objects.requireNonNull(pattern, "pattern");
            String normalizedId =
                    Objects.requireNonNull(patternId, "patternId").trim();
            // 空IDでは数式Programのノードへ一意に戻せない。
            if (normalizedId.isEmpty()) {
                throw new IllegalArgumentException(
                        "patternId must not be blank");
            }
            patternId = normalizedId;
            Objects.requireNonNull(executions, "executions");
            // 完了済みまたは負数のTaskは実行Jobとして扱わない。
            if (executions.signum() <= 0) {
                throw new IllegalArgumentException(
                        "executions must be positive");
            }
        }
    }

    /** 一Patternの固定式と、現在Jobに残っている実行回数。 */
    public record Task<K, P>(
            P pattern,
            String patternId,
            K output,
            long outputAmount,
            List<Input<K>> inputs,
            BigInteger executions) {
        public Task {
            Objects.requireNonNull(pattern, "pattern");
            String normalizedId = Objects.requireNonNull(patternId, "patternId").trim();
            // 空IDでは永続再検証時にPatternを識別できない。
            if (normalizedId.isEmpty()) {
                throw new IllegalArgumentException("patternId must not be blank");
            }
            patternId = normalizedId;
            Objects.requireNonNull(output, "output");
            // Pattern出力量はAE2 APIと同じ正のsigned longだけを受け付ける。
            if (outputAmount <= 0L) {
                throw new IllegalArgumentException("outputAmount must be positive");
            }
            inputs = List.copyOf(Objects.requireNonNull(inputs, "inputs"));
            // null入力はキー会計を証明できないため構築時に拒否する。
            for (Input<K> input : inputs) {
                Objects.requireNonNull(input, "input");
            }
            Objects.requireNonNull(executions, "executions");
            // 0以下のTaskは実行Jobへ存在しないため受け付けない。
            if (executions.signum() <= 0) {
                throw new IllegalArgumentException("executions must be positive");
            }
        }
    }

    /** 一Pattern実行当たりの、代替候補を含まない確定入力。 */
    public record Input<K>(K key, long amount) {
        public Input {
            Objects.requireNonNull(key, "key");
            // 入力0以下は相殺計算で意味を持たないため拒否する。
            if (amount <= 0L) {
                throw new IllegalArgumentException("amount must be positive");
            }
        }
    }
}
