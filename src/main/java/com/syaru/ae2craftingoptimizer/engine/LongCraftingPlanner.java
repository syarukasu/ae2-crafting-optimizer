package com.syaru.ae2craftingoptimizer.engine;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 通常規模の注文をchecked long演算で展開するPlanner。
 * 加算・乗算がlongを超えた場合は値を丸めず例外を返し、上位層にBigIntegerでの再計算を任せる。
 */
public final class LongCraftingPlanner<K> {
    private static final int MAX_EXPANDED_REQUESTS = 1_048_576;

    public LongCraftingPlan<K> plan(
            CompiledCraftingGraph<K> graph,
            K requestedKey,
            long requestedAmount,
            Map<K, Long> inventory) {
        return plan(graph, requestedKey, requestedAmount, inventory, Set.of());
    }

    public LongCraftingPlan<K> plan(
            CompiledCraftingGraph<K> graph,
            K requestedKey,
            long requestedAmount,
            Map<K, Long> inventory,
            Set<K> emittable) {
        return plan(graph, requestedKey, requestedAmount, inventory, emittable, PlanningGuard.none());
    }

    public LongCraftingPlan<K> plan(
            CompiledCraftingGraph<K> graph,
            K requestedKey,
            long requestedAmount,
            Map<K, Long> inventory,
            Set<K> emittable,
            PlanningGuard guard) {
        Objects.requireNonNull(graph, "graph");
        Objects.requireNonNull(requestedKey, "requestedKey");
        CheckedLongMath.requireNonNegative(requestedAmount, "request");
        State<K> state = new State<>(graph, inventory, emittable, guard);
        state.satisfy(requestedKey, requestedAmount, "request/" + requestedKey);
        return new LongCraftingPlan<>(
                requestedKey,
                requestedAmount,
                state.patternExecutions,
                state.usedInventory,
                state.emitted,
                state.missing);
    }

    private static final class State<K> {
        private final CompiledCraftingGraph<K> graph;
        private final Map<K, Long> available = new HashMap<>();
        private final Map<String, Long> patternExecutions = new LinkedHashMap<>();
        private final Map<K, Long> usedInventory = new LinkedHashMap<>();
        private final Map<K, Long> emitted = new LinkedHashMap<>();
        private final Map<K, Long> missing = new LinkedHashMap<>();
        private final Map<K, CompiledPattern<K>> selectedPattern = new HashMap<>();
        private final Set<K> visiting = new HashSet<>();
        private final Set<K> emittable;
        private final PlanningGuard guard;

        private State(
                CompiledCraftingGraph<K> graph,
                Map<K, Long> inventory,
                Set<K> emittable,
                PlanningGuard guard) {
            this.graph = graph;
            this.emittable = Set.copyOf(Objects.requireNonNull(emittable, "emittable"));
            this.guard = Objects.requireNonNull(guard, "guard");
            Objects.requireNonNull(inventory, "inventory").forEach((key, amount) -> {
                Objects.requireNonNull(key, "inventory key");
                if (amount == null || amount < 0L) {
                    throw new IllegalArgumentException("inventory amounts must not be negative");
                }
                if (amount > 0L) {
                    available.put(key, amount);
                }
            });
        }

        private void satisfy(K key, long amount, String path) {
            Deque<RequestFrame<K>> pending = new ArrayDeque<>();
            pending.push(new RequestFrame<>(key, amount, path));
            int expanded = 0;
            while (!pending.isEmpty()) {
                RequestFrame<K> frame = pending.peek();
                if (frame.stage == Stage.INITIAL) {
                    if (++expanded > MAX_EXPANDED_REQUESTS) {
                        throw new IllegalStateException(
                                "experimental crafting plan exceeded " + MAX_EXPANDED_REQUESTS + " requests");
                    }
                    guard.checkpoint(expanded);
                    if (frame.amount == 0L) {
                        pending.pop();
                        continue;
                    }
                    long fromInventory = takeAvailable(frame.key, frame.amount);
                    if (fromInventory > 0L) {
                        CheckedLongMath.merge(
                                usedInventory, frame.key, fromInventory, frame.path + "/inventory");
                    }
                    frame.deficit = frame.amount - fromInventory;
                    if (frame.deficit == 0L) {
                        pending.pop();
                        continue;
                    }
                    if (emittable.contains(frame.key)) {
                        CheckedLongMath.merge(
                                emitted, frame.key, frame.deficit, frame.path + "/emitter");
                        pending.pop();
                        continue;
                    }
                    if (!visiting.add(frame.key)) {
                        CheckedLongMath.merge(
                                missing, frame.key, frame.deficit, frame.path + "/cycle");
                        pending.pop();
                        continue;
                    }
                    frame.ownsVisit = true;
                    frame.pattern = selectPattern(frame.key);
                    if (frame.pattern == null) {
                        CheckedLongMath.merge(
                                missing, frame.key, frame.deficit, frame.path + "/missing");
                        visiting.remove(frame.key);
                        pending.pop();
                        continue;
                    }
                    frame.executions = CheckedLongMath.ceilDiv(
                            frame.deficit,
                            frame.pattern.outputAmount(frame.key),
                            frame.path + "/executions");
                    frame.stage = Stage.INPUTS;
                    continue;
                }

                if (frame.stage == Stage.INPUTS
                        && frame.inputIndex < frame.pattern.inputs().size()) {
                    int slotIndex = frame.inputIndex++;
                    CompiledPattern.Stack<K> input = selectAlternative(
                            frame.pattern.inputs().get(slotIndex),
                            frame.executions,
                            frame.path + "/input[" + slotIndex + "]");
                    long required = CheckedLongMath.multiply(
                            input.amount(),
                            frame.executions,
                            frame.path + "/input[" + slotIndex + "]");
                    pending.push(new RequestFrame<>(
                            input.key(),
                            required,
                            "pattern/" + frame.pattern.id() + "/input[" + slotIndex + "]/" + input.key()));
                    continue;
                }

                CheckedLongMath.merge(
                        patternExecutions,
                        frame.pattern.id(),
                        frame.executions,
                        frame.path + "/patternExecutions");
                for (Map.Entry<K, Long> output : frame.pattern.outputs().entrySet()) {
                    long produced = CheckedLongMath.multiply(
                            output.getValue(),
                            frame.executions,
                            frame.path + "/output/" + output.getKey());
                    CheckedLongMath.merge(
                            available, output.getKey(), produced, frame.path + "/availableOutput");
                }
                long crafted = takeAvailable(frame.key, frame.deficit);
                if (crafted < frame.deficit) {
                    CheckedLongMath.merge(
                            missing,
                            frame.key,
                            frame.deficit - crafted,
                            frame.path + "/shortOutput");
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
                long executions,
                String path) {
            CompiledPattern.Stack<K> selected = null;
            int selectedRank = Integer.MAX_VALUE;
            String selectedKey = null;
            CountOverflowException firstOverflow = null;
            for (CompiledPattern.Stack<K> candidate : slot.alternatives()) {
                long required;
                try {
                    required = CheckedLongMath.multiply(
                            candidate.amount(), executions, path + "/alternative/" + candidate.key());
                } catch (CountOverflowException overflow) {
                    if (firstOverflow == null) {
                        firstOverflow = overflow;
                    }
                    continue;
                }
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
            if (selected != null) {
                return selected;
            }
            if (firstOverflow != null) {
                throw firstOverflow;
            }
            throw new IllegalStateException("input slot has no alternatives");
        }

        private int rank(K key, long amount) {
            if (available.getOrDefault(key, 0L) >= amount) {
                return 0;
            }
            return graph.patternsFor(key).isEmpty() ? 2 : 1;
        }

        private long takeAvailable(K key, long amount) {
            long present = available.getOrDefault(key, 0L);
            long taken = Math.min(present, amount);
            long remaining = present - taken;
            if (remaining == 0L) {
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
            private final long amount;
            private final String path;
            private Stage stage = Stage.INITIAL;
            private long deficit;
            private long executions;
            private int inputIndex;
            private boolean ownsVisit;
            private CompiledPattern<K> pattern;

            private RequestFrame(K key, long amount, String path) {
                this.key = key;
                this.amount = amount;
                this.path = path;
            }
        }
    }
}
