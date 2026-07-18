package com.syaru.ae2craftingoptimizer.engine;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class BigCraftingPlanner<K> {
    private static final int MAX_EXPANDED_REQUESTS = 1_048_576;
    private final int maximumBits;

    public BigCraftingPlanner() {
        this(BigCountMath.HARD_MAXIMUM_BITS);
    }

    public BigCraftingPlanner(int maximumBits) {
        BigCountMath.requireMaximumBits(BigInteger.ZERO, "planner maximum", maximumBits);
        this.maximumBits = maximumBits;
    }

    public BigCraftingPlan<K> plan(
            CompiledCraftingGraph<K> graph,
            K requestedKey,
            BigInteger requestedAmount,
            Map<K, BigInteger> inventory) {
        return plan(graph, requestedKey, requestedAmount, inventory, Set.of());
    }

    public BigCraftingPlan<K> plan(
            CompiledCraftingGraph<K> graph,
            K requestedKey,
            BigInteger requestedAmount,
            Map<K, BigInteger> inventory,
            Set<K> emittable) {
        return plan(graph, requestedKey, requestedAmount, inventory, emittable, PlanningGuard.none());
    }

    public BigCraftingPlan<K> plan(
            CompiledCraftingGraph<K> graph,
            K requestedKey,
            BigInteger requestedAmount,
            Map<K, BigInteger> inventory,
            Set<K> emittable,
            PlanningGuard guard) {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(requestedKey, "requestedKey");
        BigCountMath.requireMaximumBits(requestedAmount, "request", maximumBits);
        State<K> state = new State<>(graph, inventory, emittable, guard, maximumBits);
        state.satisfy(requestedKey, requestedAmount, "request/" + requestedKey);
        return new BigCraftingPlan<>(
                requestedKey,
                requestedAmount,
                state.patternExecutions,
                state.usedInventory,
                state.emitted,
                state.missing,
                state.expandedRequests);
    }

    private static final class State<K> {
        private final CompiledCraftingGraph<K> graph;
        private final Map<K, BigInteger> available = new HashMap<>();
        private final Map<String, BigInteger> patternExecutions = new LinkedHashMap<>();
        private final Map<K, BigInteger> usedInventory = new LinkedHashMap<>();
        private final Map<K, BigInteger> emitted = new LinkedHashMap<>();
        private final Map<K, BigInteger> missing = new LinkedHashMap<>();
        private final Map<K, CompiledPattern<K>> selectedPattern = new HashMap<>();
        private final Set<K> visiting = new HashSet<>();
        private final Set<K> emittable;
        private final PlanningGuard guard;
        private final int maximumBits;
        private int expandedRequests;

        private State(
                CompiledCraftingGraph<K> graph,
                Map<K, BigInteger> inventory,
                Set<K> emittable,
                PlanningGuard guard,
                int maximumBits) {
            this.graph = graph;
            this.emittable = Set.copyOf(Objects.requireNonNull(emittable, "emittable"));
            this.guard = Objects.requireNonNull(guard, "guard");
            this.maximumBits = maximumBits;
            Objects.requireNonNull(inventory, "inventory").forEach((key, amount) -> {
                Objects.requireNonNull(key, "inventory key");
                BigCountMath.requireMaximumBits(amount, "inventory", maximumBits);
                if (amount.signum() != 0) {
                    available.put(key, amount);
                }
            });
        }

        private void satisfy(K key, BigInteger amount, String path) {
            Deque<RequestFrame<K>> pending = new ArrayDeque<>();
            pending.push(new RequestFrame<>(key, amount, path));
            while (!pending.isEmpty()) {
                RequestFrame<K> frame = pending.peek();
                if (frame.stage == Stage.INITIAL) {
                    if (++expandedRequests > MAX_EXPANDED_REQUESTS) {
                        throw new IllegalStateException(
                                "BigInteger crafting plan exceeded " + MAX_EXPANDED_REQUESTS + " requests");
                    }
                    guard.checkpoint(expandedRequests);
                    if (frame.amount.signum() == 0) {
                        pending.pop();
                        continue;
                    }
                    BigInteger fromInventory = takeAvailable(frame.key, frame.amount);
                    BigCountMath.merge(
                            usedInventory,
                            frame.key,
                            fromInventory,
                            frame.path + "/inventory",
                            maximumBits);
                    frame.deficit = frame.amount.subtract(fromInventory);
                    if (frame.deficit.signum() == 0) {
                        pending.pop();
                        continue;
                    }
                    if (emittable.contains(frame.key)) {
                        BigCountMath.merge(
                                emitted,
                                frame.key,
                                frame.deficit,
                                frame.path + "/emitter",
                                maximumBits);
                        pending.pop();
                        continue;
                    }
                    if (!visiting.add(frame.key)) {
                        BigCountMath.merge(
                                missing,
                                frame.key,
                                frame.deficit,
                                frame.path + "/cycle",
                                maximumBits);
                        pending.pop();
                        continue;
                    }
                    frame.ownsVisit = true;
                    frame.pattern = selectPattern(frame.key);
                    if (frame.pattern == null) {
                        BigCountMath.merge(
                                missing,
                                frame.key,
                                frame.deficit,
                                frame.path + "/missing",
                                maximumBits);
                        visiting.remove(frame.key);
                        pending.pop();
                        continue;
                    }
                    frame.executions = BigCountMath.ceilDiv(
                            frame.deficit,
                            BigInteger.valueOf(frame.pattern.outputAmount(frame.key)),
                            frame.path + "/executions");
                    BigCountMath.requireMaximumBits(
                            frame.executions, frame.path + "/executions", maximumBits);
                    frame.stage = Stage.INPUTS;
                    continue;
                }

                if (frame.stage == Stage.INPUTS && frame.inputIndex < frame.pattern.inputs().size()) {
                    int slotIndex = frame.inputIndex++;
                    CompiledPattern.Stack<K> input = selectAlternative(
                            frame.pattern.inputs().get(slotIndex),
                            frame.executions);
                    BigInteger required = BigCountMath.multiply(
                            BigInteger.valueOf(input.amount()),
                            frame.executions,
                            frame.path + "/input[" + slotIndex + "]",
                            maximumBits);
                    pending.push(new RequestFrame<>(
                            input.key(),
                            required,
                            "pattern/" + frame.pattern.id() + "/input[" + slotIndex + "]/" + input.key()));
                    continue;
                }

                BigCountMath.merge(
                        patternExecutions,
                        frame.pattern.id(),
                        frame.executions,
                        frame.path + "/patternExecutions",
                        maximumBits);
                for (Map.Entry<K, Long> output : frame.pattern.outputs().entrySet()) {
                    BigInteger produced = BigCountMath.multiply(
                            BigInteger.valueOf(output.getValue()),
                            frame.executions,
                            frame.path + "/availableOutput",
                            maximumBits);
                    BigCountMath.merge(
                            available,
                            output.getKey(),
                            produced,
                            frame.path + "/availableOutput",
                            maximumBits);
                }
                BigInteger crafted = takeAvailable(frame.key, frame.deficit);
                if (crafted.compareTo(frame.deficit) < 0) {
                    BigCountMath.merge(
                            missing,
                            frame.key,
                            frame.deficit.subtract(crafted),
                            frame.path + "/shortOutput",
                            maximumBits);
                }
                if (frame.ownsVisit) {
                    visiting.remove(frame.key);
                }
                pending.pop();
            }
        }

        private CompiledPattern<K> selectPattern(K key) {
            return selectedPattern.computeIfAbsent(key, ignored -> {
                List<CompiledPattern<K>> candidates = graph.patternsFor(key);
                return candidates.isEmpty() ? null : candidates.get(0);
            });
        }

        private CompiledPattern.Stack<K> selectAlternative(
                CompiledPattern.InputSlot<K> slot,
                BigInteger executions) {
            CompiledPattern.Stack<K> selected = null;
            int selectedRank = Integer.MAX_VALUE;
            String selectedKey = null;
            for (CompiledPattern.Stack<K> candidate : slot.alternatives()) {
                BigInteger required = BigCountMath.multiply(
                        BigInteger.valueOf(candidate.amount()),
                        executions,
                        "alternative/" + candidate.key(),
                        maximumBits);
                int candidateRank = rank(candidate.key(), required);
                String candidateKey = String.valueOf(candidate.key());
                if (selected == null
                        || candidateRank < selectedRank
                        || (candidateRank == selectedRank && candidateKey.compareTo(selectedKey) < 0)) {
                    selected = candidate;
                    selectedRank = candidateRank;
                    selectedKey = candidateKey;
                }
            }
            return Objects.requireNonNull(selected, "input slot has no alternatives");
        }

        private int rank(K key, BigInteger amount) {
            if (available.getOrDefault(key, BigInteger.ZERO).compareTo(amount) >= 0) {
                return 0;
            }
            return graph.patternsFor(key).isEmpty() ? 2 : 1;
        }

        private BigInteger takeAvailable(K key, BigInteger amount) {
            BigInteger present = available.getOrDefault(key, BigInteger.ZERO);
            BigInteger taken = present.min(amount);
            BigInteger remaining = present.subtract(taken);
            if (remaining.signum() == 0) {
                available.remove(key);
            } else {
                available.put(key, remaining);
            }
            return taken;
        }

        private enum Stage {
            INITIAL,
            INPUTS
        }

        private static final class RequestFrame<K> {
            private final K key;
            private final BigInteger amount;
            private final String path;
            private Stage stage = Stage.INITIAL;
            private BigInteger deficit = BigInteger.ZERO;
            private BigInteger executions = BigInteger.ZERO;
            private int inputIndex;
            private boolean ownsVisit;
            private CompiledPattern<K> pattern;

            private RequestFrame(K key, BigInteger amount, String path) {
                this.key = key;
                this.amount = amount;
                this.path = path;
            }
        }
    }
}
