package com.syaru.ae2craftingoptimizer.engine;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.ae2vm.addon.nativeengine.NativeVmResult;
import com.ae2vm.addon.nativeengine.VmAccounting;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.integration.PlanningExactInventorySnapshot;
import com.syaru.ae2craftingoptimizer.optimization.ProviderPatternGenerationTracker;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.level.Level;

/** Exact inventory and completed-result ownership only. Never feeds patterns into the VM. */
public final class VmBigIntegerAccounting implements VmAccounting {
    @Override public BigInteger maximumCount() {
        return BigInteger.ONE.shiftLeft(ACOConfig.getBigIntegerMaximumBits()).subtract(BigInteger.ONE);
    }

    @Override public Stock exactStock(IGrid grid) {
        var counter = PlanningExactInventorySnapshot.capture(grid);
        var exact = BigKeyCounterSidecars.snapshot(counter).orElseGet(() -> BigKeyCounterSidecars.fromFacade(counter));
        var known = new java.util.LinkedHashSet<AEKey>();
        exact.amounts().keySet().forEach(key -> { if (exact.isExact(key)) known.add(key); });
        return new Stock(exact.amounts(), exact.complete(), known);
    }

    @Override public ICraftingPlan wideResult(IGrid grid, Level level, NativeVmResult result) {
        var amounts = result.amounts();
        int bits = ACOConfig.getBigIntegerMaximumBits();
        BigCountMath.requireMaximumBits(result.bytes(), "vm/bytes", bits);
        Map<IPatternDetails, BigInteger> times = new LinkedHashMap<>();
        amounts.crafts().forEach((id, count) -> times.merge(
                java.util.Objects.requireNonNull(result.bindings().get(id), "missing VM pattern binding: " + id),
                count, BigInteger::add));
        var display = new GenericStack(amounts.root(), BigIntegerPlanProjection.saturatedLong(amounts.requested()));
        long patterns = ProviderPatternGenerationTracker.generation();
        long recipes = RecipeGenerationTracker.generation();
        if (!result.wideQuantity()) {
            Map<IPatternDetails, Long> normalTimes = new LinkedHashMap<>();
            times.forEach((pattern, count) -> normalTimes.put(pattern, count.longValueExact()));
            return Ae2CraftingPlanSidecars.expose(new BigCapacityCraftingPlan(display,
                    !amounts.missing().isEmpty(), amounts.multiplePaths(),
                    BigIntegerPlanProjection.projectKeyCounter(amounts.used()),
                    BigIntegerPlanProjection.projectKeyCounter(amounts.emitted()),
                    BigIntegerPlanProjection.projectKeyCounter(amounts.missing()), normalTimes,
                    result.bytes(), patterns, recipes));
        }
        var trace = new ArrayList<CraftingPlanTrace.Charge<AEKey>>();
        trace.add(new CraftingPlanTrace.Charge<>(null, amounts.integerCharges(), 1));
        amounts.stackCharges().forEach((key, count) -> trace.add(new CraftingPlanTrace.Charge<>(key, count, 1)));
        var plan = new BigCraftingPlan<>(amounts.root(), amounts.requested(), amounts.crafts(),
                amounts.used(), amounts.emitted(), amounts.missing(), amounts.crafts().size(), new CraftingPlanTrace<>(trace));
        if (!plan.craftable()) return Ae2CraftingPlanSidecars.expose(new BigIntegerSimulationPlan(
                display, plan, times, result.bytes(), bits, amounts.multiplePaths()));

        return Ae2CraftingPlanSidecars.expose(new VmCalculatedCraftingPlan(plan, times, result.bytes(),
                patterns, recipes, amounts.multiplePaths(), bits));
    }
}
