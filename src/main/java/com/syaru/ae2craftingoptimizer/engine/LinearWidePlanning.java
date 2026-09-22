package com.syaru.ae2craftingoptimizer.engine;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;

/** Issue #202: proven linear wide demands, with logical AE2 byte overhead retained. */
final class LinearWidePlanning {
    private LinearWidePlanning() {}

    static <K> BigCraftingPlan<K> tryPlan(CompiledRootProgram<K> program, BigInteger requested,
            BigInteger[] initial, PlanningGuard guard, int bits) {
        // Keep the ordered legacy double byte trace, even when its internal counts grow wide.
        if (requested.bitLength() <= 63) return null;
        int size = program.nodeCount();
        int work = 0;
        for (int node = 0; node < size; node++) {
            guard.checkpoint(++work);
            var pattern = program.patternAt(node);
            if (pattern == null) continue;
            if (initial[node].signum() != 0 || pattern.outputs().size() != 1
                    || pattern.outputAmount(program.keyAt(node)) != 1) return null;
            for (var slot : pattern.inputs()) {
                guard.checkpoint(++work);
                if (slot.alternatives().size() != 1 || slot.templateAmount() != 1) return null;
            }
        }

        BigInteger[] demand = new BigInteger[size];
        BigInteger[] occurrences = new BigInteger[size];
        Arrays.fill(demand, BigInteger.ZERO);
        Arrays.fill(occurrences, BigInteger.ZERO);
        int root = program.indexOf(program.root());
        demand[root] = requested;
        occurrences[root] = BigInteger.ONE;
        var executions = new LinkedHashMap<String, BigInteger>();
        var used = new LinkedHashMap<K, BigInteger>();
        var emitted = new LinkedHashMap<K, BigInteger>();
        var missing = new LinkedHashMap<K, BigInteger>();
        var charges = new ArrayList<CraftingPlanTrace.Charge<K>>();
        BigInteger logicalNodes = BigInteger.ZERO;
        int visited = 0;
        for (int node = 0; node < size; node++) {
            guard.checkpoint(++work);
            BigInteger required = demand[node];
            if (required.signum() == 0) continue;
            visited++;
            K key = program.keyAt(node);
            logicalNodes = BigCountMath.add(logicalNodes, occurrences[node], "linear-wide/nodes", bits);
            charges.add(new CraftingPlanTrace.Charge<>(key, required, 1));
            var pattern = program.patternAt(node);
            if (pattern == null) {
                BigInteger taken = required.min(initial[node]);
                if (taken.signum() > 0) used.put(key, taken);
                BigInteger deficit = required.subtract(taken);
                if (deficit.signum() > 0) {
                    (program.isEmittableAt(node) ? emitted : missing).put(key, deficit);
                }
                continue;
            }
            BigCountMath.merge(executions, pattern.id(), required, "linear-wide/executions", bits);
            charges.add(new CraftingPlanTrace.Charge<>(null, required, 1));
            for (var slot : pattern.inputs()) {
                guard.checkpoint(++work);
                var input = slot.alternatives().get(0);
                int child = program.indexOf(input.key());
                // Compression may exceed a limit that separate ordered stock/missing charges respect.
                // These bounded temporaries are at most bits + 64; never publish or clamp them.
                BigInteger amount = required.multiply(BigInteger.valueOf(input.amount()));
                BigInteger combined = demand[child].add(amount);
                if (!withinLimit(combined, bits)) return null;
                demand[child] = combined;
                // Occurrences count paths, not items: each input slot adds one child request per path.
                occurrences[child] = BigCountMath.add(occurrences[child], occurrences[node],
                        "linear-wide/occurrences", bits);
            }
        }
        charges.add(new CraftingPlanTrace.Charge<>(null, BigCountMath.multiply(logicalNodes,
                BigInteger.valueOf(8), "linear-wide/nodeBytes", bits), 1));
        return new BigCraftingPlan<>(program.root(), requested, executions, used, emitted, missing,
                visited, new CraftingPlanTrace<>(charges));
    }

    private static boolean withinLimit(BigInteger value, int bits) {
        return value.bitLength() <= bits && value.compareTo(BigCountMath.hardMaximumValue()) <= 0;
    }
}
