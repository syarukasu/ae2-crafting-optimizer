package com.syaru.ae2craftingoptimizer.engine.parallel;

import com.syaru.ae2craftingoptimizer.engine.BigCountMath;
import java.math.BigInteger;
import java.util.Map;
import java.util.Objects;

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
        Fraction total = Fraction.ZERO;
        BigInteger nodeCount = BigInteger.ONE;

        total = total.addStack(
                requestedAmount,
                divisor(graph.root(), amountPerByte),
                maximumBits,
                "root");
        for (int node = 0; node < graph.nodeCount(); node++) {
            BigInteger executions = zero(patternExecutions[node]);
            if (executions.signum() == 0) {
                continue;
            }
            total = total.addInteger(executions, maximumBits, "pattern/" + node);
            nodeCount = BigCountMath.add(
                    nodeCount,
                    BigInteger.valueOf(graph.inputCountAt(node)),
                    "parallel/bytes/nodes",
                    maximumBits);
        }
        for (int edge = 0; edge < graph.edgeCount(); edge++) {
            BigInteger contribution = zero(edgeContributions[edge]);
            if (contribution.signum() == 0) {
                continue;
            }
            K child = graph.keyAt(graph.childAtEdge(edge));
            total = total.addStack(
                    contribution,
                    divisor(child, amountPerByte),
                    maximumBits,
                    "edge/" + edge);
        }
        total = total.addInteger(
                BigCountMath.multiply(
                        nodeCount,
                        BigInteger.valueOf(8L),
                        "parallel/bytes/node-overhead",
                        maximumBits),
                maximumBits,
                "node-overhead");
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

    private record Fraction(BigInteger numerator, BigInteger denominator) {
        private static final Fraction ZERO = new Fraction(BigInteger.ZERO, BigInteger.ONE);

        private Fraction {
            Objects.requireNonNull(numerator, "numerator");
            Objects.requireNonNull(denominator, "denominator");
            if (numerator.signum() < 0 || denominator.signum() <= 0) {
                throw new IllegalArgumentException("invalid byte fraction");
            }
        }

        private Fraction addStack(
                BigInteger amount,
                long amountPerByte,
                int maximumBits,
                String context) {
            BigInteger stackNumerator = BigCountMath.multiply(
                    amount,
                    BigInteger.valueOf(8L),
                    "parallel/bytes/stack/" + context,
                    maximumBits);
            return add(
                    stackNumerator,
                    BigInteger.valueOf(amountPerByte),
                    maximumBits,
                    context);
        }

        private Fraction addInteger(
                BigInteger amount,
                int maximumBits,
                String context) {
            return add(amount, BigInteger.ONE, maximumBits, context);
        }

        private Fraction add(
                BigInteger addNumerator,
                BigInteger addDenominator,
                int maximumBits,
                String context) {
            BigInteger gcd = denominator.gcd(addDenominator);
            BigInteger leftMultiplier = addDenominator.divide(gcd);
            BigInteger rightMultiplier = denominator.divide(gcd);
            BigInteger nextNumerator = BigCountMath.add(
                    BigCountMath.multiply(
                            numerator,
                            leftMultiplier,
                            "parallel/bytes/left/" + context,
                            maximumBits),
                    BigCountMath.multiply(
                            addNumerator,
                            rightMultiplier,
                            "parallel/bytes/right/" + context,
                            maximumBits),
                    "parallel/bytes/add/" + context,
                    maximumBits);
            BigInteger nextDenominator = BigCountMath.multiply(
                    denominator,
                    leftMultiplier,
                    "parallel/bytes/denominator/" + context,
                    maximumBits);
            BigInteger reduction = nextNumerator.gcd(nextDenominator);
            return new Fraction(
                    nextNumerator.divide(reduction),
                    nextDenominator.divide(reduction));
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
