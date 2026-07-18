package com.syaru.ae2craftingoptimizer.optimization;

import java.util.Collection;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

final class DeterministicMissingProof<K, P, I> {
    interface Graph<K, P, I> {
        long available(K key);

        boolean canEmit(K key);

        Collection<P> patterns(K key);

        long outputAmountPerExecution(P pattern, K requestedKey);

        Collection<I> inputs(P pattern);

        Collection<Requirement<K>> alternatives(I input, long executions);
    }

    record Requirement<K>(K key, long amount) {
        Requirement {
            Objects.requireNonNull(key, "key");
        }
    }

    record Missing<K>(K key, long amount) {
        Missing {
            Objects.requireNonNull(key, "key");
            if (amount <= 0) {
                throw new IllegalArgumentException("amount must be positive");
            }
        }
    }

    private final Graph<K, P, I> graph;
    private final int maxDepth;
    private final int maxNodes;
    private int visitedNodes;

    private DeterministicMissingProof(Graph<K, P, I> graph, int maxDepth, int maxNodes) {
        this.graph = Objects.requireNonNull(graph, "graph");
        this.maxDepth = Math.max(0, maxDepth);
        this.maxNodes = Math.max(1, maxNodes);
    }

    static <K, P, I> Missing<K> find(
            Graph<K, P, I> graph,
            K requestedKey,
            long requestedAmount,
            int maxDepth,
            int maxNodes) {
        if (requestedAmount <= 0) {
            return null;
        }
        return new DeterministicMissingProof<>(graph, maxDepth, maxNodes)
                .require(Objects.requireNonNull(requestedKey, "requestedKey"), requestedAmount, 0, new HashSet<>())
                .missing;
    }

    private Outcome<K> require(K key, long amount, int depth, Set<K> recursionStack) {
        if (amount <= 0) {
            return Outcome.maybe();
        }
        if (depth > maxDepth || ++visitedNodes > maxNodes || !recursionStack.add(key)) {
            return Outcome.maybe();
        }

        try {
            long remaining = saturatingSubtract(amount, Math.max(0L, graph.available(key)));
            if (remaining <= 0 || graph.canEmit(key)) {
                return Outcome.maybe();
            }

            Collection<P> patterns = graph.patterns(key);
            if (patterns == null || patterns.isEmpty()) {
                return Outcome.blocked(new Missing<>(key, remaining));
            }

            Missing<K> firstBlocker = null;
            for (P pattern : patterns) {
                long outputPerExecution = graph.outputAmountPerExecution(pattern, key);
                if (outputPerExecution <= 0) {
                    return Outcome.maybe();
                }

                long executions = ceilDiv(remaining, outputPerExecution);
                Outcome<K> patternOutcome = provePatternBlocked(pattern, executions, depth, recursionStack);
                if (!patternOutcome.blocked) {
                    // 同じ出力を作るPatternは代替候補。一つでも未証明または実行可能なら、
                    // クラフト全体をAE2の正規Plannerへ残す。
                    return Outcome.maybe();
                }
                if (firstBlocker == null) {
                    firstBlocker = patternOutcome.missing;
                }
            }

            return firstBlocker == null ? Outcome.maybe() : Outcome.blocked(firstBlocker);
        } finally {
            recursionStack.remove(key);
        }
    }

    private Outcome<K> provePatternBlocked(P pattern, long executions, int depth, Set<K> recursionStack) {
        Collection<I> inputs = graph.inputs(pattern);
        if (inputs == null) {
            return Outcome.maybe();
        }

        for (I input : inputs) {
            Collection<Requirement<K>> alternatives = graph.alternatives(input, executions);
            if (alternatives == null || alternatives.isEmpty()) {
                return Outcome.maybe();
            }

            Missing<K> firstAlternativeBlocker = null;
            boolean everyAlternativeBlocked = true;
            for (Requirement<K> alternative : alternatives) {
                if (alternative == null || alternative.amount <= 0) {
                    everyAlternativeBlocked = false;
                    break;
                }
                Outcome<K> outcome = require(
                        alternative.key,
                        alternative.amount,
                        depth + 1,
                        recursionStack);
                if (!outcome.blocked) {
                    everyAlternativeBlocked = false;
                    break;
                }
                if (firstAlternativeBlocker == null) {
                    firstAlternativeBlocker = outcome.missing;
                }
            }

            // 一つのPattern内の入力はAND条件。一入力の不足を証明できればPattern全体が
            // 不可能と分かるため、残り入力を展開する必要はない。
            if (everyAlternativeBlocked && firstAlternativeBlocker != null) {
                return Outcome.blocked(firstAlternativeBlocker);
            }
        }

        return Outcome.maybe();
    }

    static long ceilDiv(long value, long divisor) {
        if (value <= 0 || divisor <= 0) {
            return 0;
        }
        return 1 + (value - 1) / divisor;
    }

    static long saturatingMultiply(long left, long right) {
        if (left <= 0 || right <= 0) {
            return 0;
        }
        if (left > Long.MAX_VALUE / right) {
            return Long.MAX_VALUE;
        }
        return left * right;
    }

    static long saturatingAdd(long left, long right) {
        if (left <= 0) {
            return Math.max(0L, right);
        }
        if (right <= 0) {
            return left;
        }
        if (left > Long.MAX_VALUE - right) {
            return Long.MAX_VALUE;
        }
        return left + right;
    }

    static long saturatingSubtract(long left, long right) {
        if (right >= left) {
            return 0;
        }
        return left - Math.max(0L, right);
    }

    private static final class Outcome<K> {
        private final boolean blocked;
        private final Missing<K> missing;

        private Outcome(boolean blocked, Missing<K> missing) {
            this.blocked = blocked;
            this.missing = missing;
        }

        private static <K> Outcome<K> maybe() {
            return new Outcome<>(false, null);
        }

        private static <K> Outcome<K> blocked(Missing<K> missing) {
            return new Outcome<>(true, Objects.requireNonNull(missing, "missing"));
        }
    }
}
