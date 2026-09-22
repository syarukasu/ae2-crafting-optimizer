package com.syaru.ae2craftingoptimizer.engine;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEKey;
import appeng.crafting.CraftingPlan;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.level.Level;

/** Issue #208: optional existing receipt executor preparation, never a VM calculation stage. */
public final class VmPhysicalPlanBinding {
    private VmPhysicalPlanBinding() {}

    public static BigIntegerCraftingPlan resolve(ICraftingPlan facade, IGrid grid, Level level) {
        var metadata = Ae2CraftingPlanSidecars.metadata(facade).orElse(null);
        if (!(metadata instanceof VmCalculatedCraftingPlan vm))
            return metadata instanceof BigIntegerCraftingPlan exact ? exact : null;
        if (level.getServer() == null || !level.getServer().isSameThread())
            throw new IllegalStateException("physical preparation requires the server thread");
        if (!ExactPlanPatternRevalidator.validate(grid, vm.patternGeneration(), vm.recipeGeneration(),
                vm.exactPatternTimes().keySet()).valid())
            throw new IllegalArgumentException("VM plan patterns changed before submission");
        var prepared = vm.prepared();
        if (prepared == null) {
            prepared = prepare(vm, level);
            vm.rememberPrepared(prepared);
        }
        if (facade instanceof CraftingPlan vanilla) Ae2CraftingPlanSidecars.attach(vanilla, prepared);
        return prepared;
    }

    private static BigIntegerCraftingPlan prepare(VmCalculatedCraftingPlan vm, Level level) {
        if (!ACOConfig.enableExactBigIntegerPhysicalExecution())
            throw new IllegalStateException("Exact physical execution is disabled");
        int bits = ACOConfig.getBigIntegerMaximumBits();
        Map<String, BigInteger> executions = new LinkedHashMap<>();
        Map<String, IPatternDetails> bindings = new LinkedHashMap<>();
        Map<String, CompiledPattern<AEKey>> formulas = new LinkedHashMap<>();
        for (var entry : vm.exactPatternTimes().entrySet()) {
            var captured = Ae2CompiledPatternFactory.capture(entry.getKey(), level);
            if (captured == null || !captured.exactInputDomain())
                throw new IllegalArgumentException("the receipt executor does not support this VM pattern's input domain");
            String id = captured.fingerprint();
            executions.merge(id, entry.getValue(), BigInteger::add);
            bindings.putIfAbsent(id, entry.getKey());
            formulas.putIfAbsent(id, captured.compile(id));
        }
        var calculated = vm.exactPlan();
        var plan = new BigCraftingPlan<>(calculated.requestedKey(), calculated.requestedAmount(), executions,
                calculated.usedInventory(), calculated.emitted(), calculated.missing(),
                calculated.expandedRequests(), calculated.trace());
        var physical = SelectedBranchPhysicalPlan.prepare(plan, formulas.values(),
                vm.patternGeneration(), vm.recipeGeneration(), bits);
        com.syaru.ae2craftingoptimizer.engine.vector.VectorBatchPlanValidator.validate(physical, bits,
                ACOConfig.getExactVectorMaximumPatternNodes(), ACOConfig.getExactVectorMaximumInputKeys(),
                ACOConfig.getExactVectorMaximumOutputKeys());
        SelectedBranchPhysicalPlan.validateBindings(physical, bindings::get, level, bits);
        var root = new Ae2BigCraftingPlanFactory.PreparedBigRootPlan(null, plan, vm.exactBytes(),
                vm.patternGeneration(), vm.recipeGeneration(), Ae2BigCraftingPlanFactory.ExecutionMode.EXACT_PATTERN_EXECUTOR,
                0L, PlanningRuntimeEpoch.current(), physical.programFingerprint());
        return new BigIntegerCraftingPlan(vm.finalOutput(), plan, vm.exactPatternTimes(), root,
                true, physical, vm.multiplePaths());
    }
}
