package com.syaru.ae2craftingoptimizer.engine;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.ToLongFunction;

/** 通常計画へBigInteger Plannerを重ねる前に、全量クラフト時の安全な上限だけを調べる。 */
final class WideArithmeticPreflight {
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    private WideArithmeticPreflight() {
    }

    /**
     * 配列化済みRoot Programを一巡し、個別値・総量・CPU byteのどこかがlongを超えるか調べる。
     * 在庫0を使うため、実在庫で途中停止する計画以上の安全な上限になる。
     */
    static <K> boolean requiresWideArithmetic(
            K root,
            BigInteger requestedAmount,
            CompiledRootProgram<K> program,
            ToLongFunction<K> amountPerByte,
            int maximumBits) {
        return requiresWideArithmetic(
                root,
                requestedAmount,
                program,
                amountPerByte,
                maximumBits,
                PlanningGuard.none());
    }

    static <K> boolean requiresWideArithmetic(
            K root,
            BigInteger requestedAmount,
            CompiledRootProgram<K> program,
            ToLongFunction<K> amountPerByte,
            int maximumBits,
            PlanningGuard guard) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(requestedAmount, "requestedAmount");
        Objects.requireNonNull(program, "program");
        Objects.requireNonNull(amountPerByte, "amountPerByte");
        Objects.requireNonNull(guard, "guard");
        // 別RootのProgramを誤用すると、需要式とCPU byte式の双方が変わるため明示的に拒否する。
        if (!program.root().equals(root)) {
            throw new IllegalArgumentException("root does not match the compiled program");
        }
        CompiledRootProgram.BigInventorySnapshot<K> emptyInventory =
                program.captureBigInventory(
                        ignored -> BigInteger.ZERO,
                        maximumBits,
                        guard);
        BigCraftingPlan<K> fullPlan = program.planBig(
                requestedAmount,
                emptyInventory,
                guard,
                maximumBits);
        // Pattern回数または各AEKey量が個別にlongを超える場合はWide計画が必須になる。
        if (containsValuePastLong(fullPlan.patternExecutions())
                || containsValuePastLong(fullPlan.usedInventory())
                || containsValuePastLong(fullPlan.emitted())
                || containsValuePastLong(fullPlan.missing())) {
            return true;
        }
        // 個別値が収まっても、複数キーの合計がlongを超える計画はAE2内部集計を保護する。
        if (sumExceedsLong(fullPlan.usedInventory())
                || sumExceedsLong(fullPlan.emitted())
                || sumExceedsLong(fullPlan.missing())) {
            return true;
        }
        BigInteger bytes = BigExactCraftingByteCounter.calculate(
                root,
                requestedAmount,
                program.patternsByOutput(),
                fullPlan.patternExecutions(),
                amountPerByte,
                maximumBits);
        return bytes.compareTo(LONG_MAX) > 0;
    }

    /** Root Programの世代内で再利用する、単調なlong安全証明器を作る。 */
    static <K> LongSafetyCertificate<K> longSafetyCertificate(
            CompiledRootProgram<K> program,
            ToLongFunction<K> amountPerByte,
            int maximumBits) {
        return new LongSafetyCertificate<>(program, amountPerByte, maximumBits);
    }

    private static <K> boolean potentiallyRequiresWideAtLong(
            CompiledRootProgram<K> program,
            K root,
            long requestedAmount,
            ToLongFunction<K> amountPerByte,
            PlanningGuard guard) {
        int rootIndex = program.indexOf(root);
        // RootがProgramにない場合は、呼出側が安全にAE2へ戻せるよう失敗させる。
        if (rootIndex < 0) {
            throw new IllegalArgumentException("root is not present in compiled program");
        }

        long[] demand = new long[program.nodeCount()];
        demand[rootIndex] = requestedAmount;
        long[] visitUpperBound = new long[program.nodeCount()];
        visitUpperBound[rootIndex] = 1L;
        Map<String, Long> executionsByPattern = new LinkedHashMap<>();
        // CompiledRootProgramのトポロジカル順を一度だけ走査して全候補の上界を作る。
        for (int node = 0; node < program.nodeCount(); node++) {
            guard.checkpoint(node + 1);
            // 未到達ノードは需要もPattern回数も増やさない。
            if (demand[node] == 0L) {
                continue;
            }
            // SATURATED値は正確なlong上限でも安全側にwide扱いする。
            if (demand[node] == Long.MAX_VALUE) {
                return true;
            }
            // EmitterはAE2と同じく外部供給で終端になるため、入力を展開しない。
            if (program.isEmittableAt(node)) {
                continue;
            }
            CompiledPattern<K> pattern = program.patternAt(node);
            // Patternなし終端は不足上界として需要だけを保持する。
            if (pattern == null) {
                continue;
            }
            long executions = saturatedCeilDiv(
                    demand[node],
                    program.outputAmountAt(node));
            // Pattern回数をlongへ戻せない場合はexact経路が必要になる。
            if (executions == Long.MAX_VALUE) {
                return true;
            }
            executionsByPattern.merge(
                    pattern.id(),
                    executions,
                    WideArithmeticPreflight::saturatedAdd);

            // 同じslotの候補は相互排他的でも、全候補を足して安全側の上界を作る。
            for (int input = 0; input < program.inputCountAt(node); input++) {
                // 候補ごとの需要を同じ子へ集約し、候補漏れによるfalse negativeを防ぐ。
                for (int alternative = 0;
                        alternative < program.inputAlternativeCountAt(node, input);
                        alternative++) {
                    K childKey = program.inputAlternativeKeyAt(
                            node,
                            input,
                            alternative);
                    int child = program.indexOf(childKey);
                    // トポロジカル順に反する候補は、上界を推測せず構造エラーにする。
                    if (child <= node) {
                        throw new IllegalArgumentException(
                                "compiled program input is not topologically ordered");
                    }
                    visitUpperBound[child] = saturatedAdd(
                            visitUpperBound[child],
                            visitUpperBound[node]);
                    // 再帰byte式の訪問回数が飽和した時点でlong安全とは証明しない。
                    if (visitUpperBound[child] == Long.MAX_VALUE) {
                        return true;
                    }
                    long inputDemand = saturatedMultiply(
                            program.inputAlternativeAmountAt(node, input, alternative),
                            executions);
                    demand[child] = saturatedAdd(demand[child], inputDemand);
                    // 子需要が飽和したら、残りの候補を走査せずwideを確定する。
                    if (demand[child] == Long.MAX_VALUE) {
                        return true;
                    }
                }
            }
        }
        long totalDemand = 0L;
        // 異なるKeyの需要合計もAE2内部集計へ入るため、aggregate overflowを先に検出する。
        for (int node = 0; node < demand.length; node++) {
            guard.checkpoint(node + 1);
            long amount = demand[node];
            // 未到達ノードは合計へ加えない。
            if (amount == 0L) {
                continue;
            }
            totalDemand = saturatedAdd(totalDemand, amount);
            // 合計が上限へ届いた場合は残りのノードを走査せずwideを確定する。
            if (totalDemand == Long.MAX_VALUE) {
                return true;
            }
        }
        long bytes = 0L;
        // 各参照キーを個別に切り上げ、AE2の有理数合計以上の安全なbyte上界を作る。
        for (int node = 0; node < demand.length; node++) {
            guard.checkpoint(node + 1);
            // 未到達ノードのstackとnode overheadは計上しない。
            if (demand[node] == 0L) {
                continue;
            }
            long divisor = amountPerByte.applyAsLong(program.keyAt(node));
            // AEKey種別が無効な場合は、近似値を作らず計画を拒否する。
            if (divisor <= 0L) {
                throw new IllegalArgumentException("amountPerByte must be positive");
            }
            /*
             * AE2のbyte式は合流DAGを経路ごとに再帰訪問する。
             * 需要上界を訪問回数でも掛け、共有子の再訪を過小評価しない。
             */
            long repeatedDemand = saturatedMultiply(
                    demand[node],
                    Math.max(1L, visitUpperBound[node]));
            long stackBytes = saturatedMultiply(repeatedDemand, 8L);
            bytes = saturatedAdd(
                    bytes,
                    saturatedCeilDiv(stackBytes, divisor));
            // 各再帰訪問につき8-byteのnode overheadを計上する。
            bytes = saturatedAdd(
                    bytes,
                    saturatedMultiply(visitUpperBound[node], 8L));
            // bytesが飽和した場合は以降のノードとPatternを計算しない。
            if (bytes == Long.MAX_VALUE) {
                return true;
            }
        }
        // 同じPatternの全実行数が各再帰訪問で加算されるAE2 byte式を上から評価する。
        for (int node = 0; node < program.nodeCount(); node++) {
            guard.checkpoint(node + 1);
            CompiledPattern<K> pattern = program.patternAt(node);
            // Patternを持たない終端と未到達ノードは実行byteを持たない。
            if (pattern == null || visitUpperBound[node] == 0L) {
                continue;
            }
            long executions = executionsByPattern.getOrDefault(pattern.id(), 0L);
            bytes = saturatedAdd(
                    bytes,
                    saturatedMultiply(executions, visitUpperBound[node]));
            // Pattern byte合計が飽和した場合はwideを確定する。
            if (bytes == Long.MAX_VALUE) {
                return true;
            }
        }
        return false;
    }

    private static long saturatedMultiply(long left, long right) {
        // 0との積はoverflowせず、早期判定用のSATURATED値も維持しない。
        if (left == 0L || right == 0L) {
            return 0L;
        }
        // 正のlong同士の乗算が上限に届く場合はSATURATEDへ丸める。
        if (left > Long.MAX_VALUE / right) {
            return Long.MAX_VALUE;
        }
        long product = left * right;
        return product == Long.MAX_VALUE ? Long.MAX_VALUE : product;
    }

    private static long saturatedAdd(long left, long right) {
        // どちらかがSATURATEDなら、加算結果も安全側にSATURATEDとする。
        if (left == Long.MAX_VALUE || right == Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        if (left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        long sum = left + right;
        return sum == Long.MAX_VALUE ? Long.MAX_VALUE : sum;
    }

    private static long saturatedCeilDiv(long dividend, long divisor) {
        long quotient = dividend / divisor;
        // 余りがある場合の繰り上げが上限へ届く場合はSATURATEDにする。
        if (dividend % divisor != 0L && quotient == Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return dividend % divisor == 0L ? quotient : quotient + 1L;
    }

    static final class LongSafetyCertificate<K> {
        private final CompiledRootProgram<K> program;
        private final ToLongFunction<K> amountPerByte;
        private final int maximumBits;
        private volatile long maximumCertifiedRequest;

        private LongSafetyCertificate(
                CompiledRootProgram<K> program,
                ToLongFunction<K> amountPerByte,
                int maximumBits) {
            this.program = Objects.requireNonNull(program, "program");
            this.amountPerByte = Objects.requireNonNull(amountPerByte, "amountPerByte");
            this.maximumBits = maximumBits;
        }

        int maximumBits() {
            return maximumBits;
        }

        boolean certifiesCached(BigInteger requestedAmount) {
            validateRequest(requestedAmount);
            // long API外の数量は、この証明器のキャッシュ対象にしない。
            if (requestedAmount.compareTo(LONG_MAX) > 0) {
                return false;
            }
            long requestedLong = requestedAmount.longValueExact();
            return requestedLong <= maximumCertifiedRequest;
        }

        boolean certify(BigInteger requestedAmount) {
            return certify(requestedAmount, PlanningGuard.none());
        }

        boolean certify(
                BigInteger requestedAmount,
                PlanningGuard guard) {
            // 既存の最大安全量以下なら、DAGを再走査せず証明を再利用する。
            if (certifiesCached(requestedAmount)) {
                return true;
            }
            // long API外の数量は正確なBigInteger preflightへ委ねる。
            if (requestedAmount.compareTo(LONG_MAX) > 0) {
                return false;
            }
            long requestedLong = requestedAmount.longValueExact();
            boolean potentiallyWide = potentiallyRequiresWideAtLong(
                    program,
                    program.root(),
                    requestedLong,
                    amountPerByte,
                    guard);
            // 保守的上界で安全を証明できない注文は、wideと断定せず正確判定へ渡す。
            if (potentiallyWide) {
                return false;
            }
            recordSafeRequest(requestedLong);
            return true;
        }

        void recordExactSafe(BigInteger requestedAmount) {
            validateRequest(requestedAmount);
            // long API外の量は非wideとして記録できないため、呼出側の判定矛盾を拒否する。
            if (requestedAmount.compareTo(LONG_MAX) > 0) {
                throw new IllegalArgumentException("exact long-safe request exceeds Long.MAX_VALUE");
            }
            recordSafeRequest(requestedAmount.longValueExact());
        }

        private void recordSafeRequest(long requestedLong) {
            synchronized (this) {
                // 安全性は注文量に対して単調なので、より大きい証明済み数量だけを保持する。
                if (requestedLong > maximumCertifiedRequest) {
                    maximumCertifiedRequest = requestedLong;
                }
            }
        }

        private void validateRequest(BigInteger requestedAmount) {
            BigCountMath.requireMaximumBits(
                    requestedAmount,
                    "wide-preflight/certificate-request",
                    maximumBits);
            // 0以下の注文はAE2の有効なRoot注文ではなく、単調安全証明へ登録しない。
            if (requestedAmount.signum() <= 0) {
                throw new IllegalArgumentException("requested amount must be positive");
            }
        }
    }

    static <K> boolean requiresWideArithmetic(
            K root,
            BigInteger requestedAmount,
            Map<K, CompiledPattern<K>> patterns,
            ToLongFunction<K> amountPerByte,
            int maximumBits) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(patterns, "patterns");
        Objects.requireNonNull(amountPerByte, "amountPerByte");
        Map<String, BigInteger> fullExecutions = new LinkedHashMap<>();
        BigInteger leafDemand = collectFullDemand(
                root, requestedAmount, patterns, fullExecutions, maximumBits);
        // 個別Keyへ分けても、葉素材の合計がlongを超える計画はWide経路の候補になる。
        if (leafDemand.compareTo(LONG_MAX) > 0) {
            return true;
        }
        BigInteger bytes = BigExactCraftingByteCounter.calculate(
                root,
                requestedAmount,
                patterns,
                fullExecutions,
                amountPerByte,
                maximumBits);
        return bytes.compareTo(LONG_MAX) > 0;
    }

    private static <K> BigInteger collectFullDemand(
            K key,
            BigInteger requestedAmount,
            Map<K, CompiledPattern<K>> patterns,
            Map<String, BigInteger> executions,
            int maximumBits) {
        CompiledPattern<K> pattern = patterns.get(key);
        // Patternを持たない素材とEmitter出力は、Wide判定上の葉需要として数える。
        if (pattern == null) {
            return BigCountMath.requireMaximumBits(
                    requestedAmount, "wide-preflight/leaf", maximumBits);
        }
        BigInteger executionCount = BigCountMath.requireMaximumBits(
                BigCountMath.ceilDiv(
                        requestedAmount,
                        BigInteger.valueOf(pattern.outputAmount(key)),
                        "wide-preflight/executions/" + pattern.id()),
                "wide-preflight/executions/" + pattern.id(),
                maximumBits);
        // 合流するDAGでは同じPatternへ複数経路から到達するため、最後の経路で上書きしない。
        executions.merge(pattern.id(), executionCount, BigInteger::add);

        BigInteger total = BigInteger.ZERO;
        // 各入力を全量クラフトする上限需要を辿り、在庫で途中停止する実計画以上の安全な上限を作る。
        for (CompiledPattern.InputSlot<K> slot : pattern.inputs()) {
            CompiledPattern.Stack<K> input = slot.alternatives().get(0);
            BigInteger inputDemand = BigCountMath.multiply(
                    BigInteger.valueOf(input.amount()),
                    executionCount,
                    "wide-preflight/input/" + pattern.id(),
                    maximumBits);
            total = BigCountMath.add(
                    total,
                    collectFullDemand(
                            input.key(), inputDemand, patterns, executions, maximumBits),
                    "wide-preflight/leaf-total",
                    maximumBits);
        }
        return total;
    }

    private static boolean containsValuePastLong(Map<?, BigInteger> counts) {
        // 個別カウンタをAE2のlong APIへ無損失変換できるかだけを確認する。
        for (BigInteger amount : counts.values()) {
            // 一つでも上限を超えればBigInteger経路へ切り替える。
            if (amount.compareTo(LONG_MAX) > 0) {
                return true;
            }
        }
        return false;
    }

    private static boolean sumExceedsLong(Map<?, BigInteger> counts) {
        BigInteger total = BigInteger.ZERO;
        // 加算自体もBigIntegerで行い、preflight中にlong overflowを起こさない。
        for (BigInteger amount : counts.values()) {
            total = total.add(amount);
            // 上限を超えた時点で残りを走査せずWide判定を確定する。
            if (total.compareTo(LONG_MAX) > 0) {
                return true;
            }
        }
        return false;
    }

}
