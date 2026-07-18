package com.syaru.ae2craftingoptimizer.optimization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DeterministicMissingProofTest {
    @Test
    void reportsAnUncraftableTerminalKey() {
        TestGraph graph = new TestGraph();

        assertEquals(
                new DeterministicMissingProof.Missing<>("water", 1_000L),
                find(graph, "water", 1_000L));
    }

    @Test
    void preservesAe2FallbackWhenOneOutputPatternCouldWork() {
        TestGraph graph = new TestGraph();
        graph.available.put("iron", 3L);
        graph.add("machine", pattern("machine", 1, input(requirement("missing_gas", 1))));
        graph.add("machine", pattern("machine", 1, input(requirement("iron", 3))));

        assertNull(find(graph, "machine", 1));
    }

    @Test
    void provesAllOutputPatternsBlocked() {
        TestGraph graph = new TestGraph();
        graph.add("machine", pattern("machine", 1, input(requirement("missing_fluid", 100))));
        graph.add("machine", pattern("machine", 1, input(requirement("missing_gas", 200))));

        assertEquals(
                new DeterministicMissingProof.Missing<>("missing_fluid", 100L),
                find(graph, "machine", 1));
    }

    @Test
    void treatsTagCandidatesAsAlternatives() {
        TestGraph graph = new TestGraph();
        graph.available.put("oxygen", 1_000L);
        graph.add(
                "oxidized",
                pattern("oxidized", 1, input(requirement("air", 1_000), requirement("oxygen", 1_000))));

        assertNull(find(graph, "oxidized", 1));
    }

    @Test
    void blocksOnlyWhenEveryTagCandidateIsMissing() {
        TestGraph graph = new TestGraph();
        graph.add(
                "oxidized",
                pattern("oxidized", 1, input(requirement("air", 1_000), requirement("oxygen", 1_000))));

        assertEquals(
                new DeterministicMissingProof.Missing<>("air", 1_000L),
                find(graph, "oxidized", 1));
    }

    @Test
    void scalesMissingAmountByExecutionsWithoutExpandingTheOrder() {
        TestGraph graph = new TestGraph();
        graph.add("crystal", pattern("crystal", 2, input(requirement("slurry", 3))));

        assertEquals(
                new DeterministicMissingProof.Missing<>("slurry", 9L),
                find(graph, "crystal", 5));
    }

    @Test
    void cyclesAlwaysFallBackToAe2() {
        TestGraph graph = new TestGraph();
        graph.add("a", pattern("a", 1, input(requirement("b", 1))));
        graph.add("b", pattern("b", 1, input(requirement("a", 1))));

        assertNull(find(graph, "a", 1));
    }

    @Test
    void emittersAlwaysFallBackToAe2() {
        TestGraph graph = new TestGraph();
        graph.emitters.add("matter");

        assertNull(find(graph, "matter", Long.MAX_VALUE));
    }

    private static DeterministicMissingProof.Missing<String> find(TestGraph graph, String key, long amount) {
        return DeterministicMissingProof.find(graph, key, amount, 64, 4_096);
    }

    private static Pattern pattern(String output, long amount, Input... inputs) {
        return new Pattern(output, amount, List.of(inputs));
    }

    private static Input input(PerExecutionRequirement... alternatives) {
        return new Input(List.of(alternatives));
    }

    private static PerExecutionRequirement requirement(String key, long amount) {
        return new PerExecutionRequirement(key, amount);
    }

    private record Pattern(String output, long outputAmount, List<Input> inputs) {
    }

    private record Input(List<PerExecutionRequirement> alternatives) {
    }

    private record PerExecutionRequirement(String key, long amount) {
    }

    private static final class TestGraph
            implements DeterministicMissingProof.Graph<String, Pattern, Input> {
        private final Map<String, Long> available = new HashMap<>();
        private final Map<String, List<Pattern>> patterns = new HashMap<>();
        private final Set<String> emitters = new HashSet<>();

        private void add(String key, Pattern pattern) {
            patterns.computeIfAbsent(key, ignored -> new ArrayList<>()).add(pattern);
        }

        @Override
        public long available(String key) {
            return available.getOrDefault(key, 0L);
        }

        @Override
        public boolean canEmit(String key) {
            return emitters.contains(key);
        }

        @Override
        public Collection<Pattern> patterns(String key) {
            return patterns.getOrDefault(key, List.of());
        }

        @Override
        public long outputAmountPerExecution(Pattern pattern, String requestedKey) {
            return pattern.output.equals(requestedKey) ? pattern.outputAmount : 0;
        }

        @Override
        public Collection<Input> inputs(Pattern pattern) {
            return pattern.inputs;
        }

        @Override
        public Collection<DeterministicMissingProof.Requirement<String>> alternatives(
                Input input,
                long executions) {
            List<DeterministicMissingProof.Requirement<String>> result = new ArrayList<>();
            for (PerExecutionRequirement alternative : input.alternatives) {
                result.add(new DeterministicMissingProof.Requirement<>(
                        alternative.key,
                        DeterministicMissingProof.saturatingMultiply(alternative.amount, executions)));
            }
            return result;
        }
    }
}
