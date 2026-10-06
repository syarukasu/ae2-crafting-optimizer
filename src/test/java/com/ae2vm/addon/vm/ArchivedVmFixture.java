package com.ae2vm.addon.vm;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import com.ae2vm.addon.compiler.PatternCompiler;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;

/** Historical arithmetic comparison only. Never included in runtime artifacts. */
public final class ArchivedVmFixture {
    private ArchivedVmFixture() { }

    public record Result(VmCraftingPlan plan, long elapsedNs, long work, long branches) { }

    public static Result run(boolean historical, AEKey output, BigInteger quantity,
            Map<AEKey, BigInteger> stock, Map<AEKey, List<IPatternDetails>> patterns) {
        long start = System.nanoTime();
        try (var scope = PatternCompiler.openScope()) {
            var request = PatternCompiler.compileRequest(output, quantity);
            var inventory = new VmSimulationState(stock);
            if (historical) {
                var vm = new ArchivedInstructionExecution(request, inventory,
                        key -> patterns.getOrDefault(key, List.of()), key -> false, ignored -> { });
                var plan = vm.execute(quantity, false);
                return new Result(plan, System.nanoTime() - start, vm.work(), vm.branchTrials());
            }
            var vm = new VmInstructionExecution(request, inventory,
                    key -> patterns.getOrDefault(key, List.of()), key -> false, ignored -> { });
            var plan = vm.execute(quantity, false);
            return new Result(plan, System.nanoTime() - start, vm.work(), vm.branchTrials());
        }
    }
}
