package com.syaru.ae2craftingoptimizer.engine.parallel;

import com.syaru.ae2craftingoptimizer.engine.BigCountMath;
import java.math.BigInteger;
import java.util.Map;

/** ACOのcanonical DAG結果からAE2のstack、Pattern、node byte式を正確な有理数で評価する。 */
final class ParallelExactByteCounter {
    private ParallelExactByteCounter() {
    }

    static <K> BigInteger calculate(
            ParallelPlanGraph<K> graph,
            BigInteger requestedAmount,
            BigInteger[] patternExecutions,
            BigInteger[] edgeContributions,
            Map<K, Long> amountPerByte,
            int maximumBits) {
        MutableFraction total = new MutableFraction();
        long nodeCount = 1L;

        total.addStack(
                requestedAmount,
                divisor(graph.root(), amountPerByte),
                maximumBits);
        for (int node = 0; node < graph.nodeCount(); node++) {
            BigInteger executions = zero(patternExecutions[node]);
            if (executions.signum() == 0) {
                continue;
            }
            total.addInteger(executions, maximumBits);
            nodeCount = Math.addExact(nodeCount, graph.inputCountAt(node));
        }
        for (int edge = 0; edge < graph.edgeCount(); edge++) {
            BigInteger contribution = zero(edgeContributions[edge]);
            if (contribution.signum() == 0) {
                continue;
            }
            K child = graph.keyAt(graph.childAtEdge(edge));
            total.addStack(
                    contribution,
                    divisor(child, amountPerByte),
                    maximumBits);
        }
        total.addInteger(
                BigCountMath.multiply(
                        BigInteger.valueOf(nodeCount),
                        BigInteger.valueOf(8L),
                        "parallel/bytes/node-overhead",
                        maximumBits),
                maximumBits);
        return total.ceil(maximumBits);
    }

    private static <K> long divisor(K key, Map<K, Long> amountPerByte) {
        Long divisor = amountPerByte.get(key);
        if (divisor == null || divisor <= 0L) {
            throw new IllegalArgumentException("missing amountPerByte for " + key);
        }
        return divisor;
    }

    private static BigInteger zero(BigInteger value) {
        return value == null ? BigInteger.ZERO : value;
    }

    private static final class MutableFraction {
        private BigInteger numerator = BigInteger.ZERO;
        private BigInteger denominator = BigInteger.ONE;

        private void addStack(
                BigInteger amount,
                long amountPerByte,
                int maximumBits) {
            BigInteger stackNumerator = BigCountMath.multiply(
                    amount,
                    BigInteger.valueOf(8L),
                    "parallel/bytes/stack",
                    maximumBits);
            add(
                    stackNumerator,
                    BigInteger.valueOf(amountPerByte),
                    maximumBits);
        }

        private void addInteger(
                BigInteger amount,
                int maximumBits) {
            if (denominator.equals(BigInteger.ONE)) {
                numerator = BigCountMath.add(
                        numerator,
                        amount,
                        "parallel/bytes/integer",
                        maximumBits);
                return;
            }
            numerator = BigCountMath.add(
                    numerator,
                    BigCountMath.multiply(
                            amount,
                            denominator,
                            "parallel/bytes/integer-scale",
                            maximumBits),
                    "parallel/bytes/integer",
                    maximumBits);
        }

        private void add(
                BigInteger addNumerator,
                BigInteger addDenominator,
                int maximumBits) {
            BigInteger[] integral = addNumerator.divideAndRemainder(addDenominator);
            if (integral[1].signum() == 0) {
                addInteger(integral[0], maximumBits);
                return;
            }
            BigInteger gcd = denominator.gcd(addDenominator);
            BigInteger leftMultiplier = addDenominator.divide(gcd);
            BigInteger rightMultiplier = denominator.divide(gcd);
            BigInteger nextNumerator = BigCountMath.add(
                    BigCountMath.multiply(
                            numerator,
                            leftMultiplier,
                            "parallel/bytes/left",
                            maximumBits),
                    BigCountMath.multiply(
                            addNumerator,
                            rightMultiplier,
                            "parallel/bytes/right",
                            maximumBits),
                    "parallel/bytes/add",
                    maximumBits);
            BigInteger nextDenominator = BigCountMath.multiply(
                    denominator,
                    leftMultiplier,
                    "parallel/bytes/denominator",
                    maximumBits);
            BigInteger reduction = nextNumerator.gcd(nextDenominator);
            numerator = nextNumerator.divide(reduction);
            denominator = nextDenominator.divide(reduction);
        }

        private BigInteger ceil(int maximumBits) {
            BigInteger[] quotient = numerator.divideAndRemainder(denominator);
            BigInteger result = quotient[1].signum() == 0
                    ? quotient[0]
                    : BigCountMath.add(
                            quotient[0],
                            BigInteger.ONE,
                            "parallel/bytes/final-ceil",
                            maximumBits);
            return BigCountMath.requireMaximumBits(
                    result,
                    "parallel/bytes/result",
                    maximumBits);
        }
    }
}
