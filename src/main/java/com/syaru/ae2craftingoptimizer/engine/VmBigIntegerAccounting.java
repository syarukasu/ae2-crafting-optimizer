package com.syaru.ae2craftingoptimizer.engine;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import com.ae2vm.addon.nativeengine.NativeVmResult;
import com.ae2vm.addon.nativeengine.VmAccounting;
import com.syaru.ae2craftingoptimizer.api.big.BigCraftingEngineApi;
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
        // #218: the binary bit ceiling must not advertise values above the exact decimal API limit.
        return BigCraftingEngineApi.maximumSupportedAmount();
    }

    @Override public Stock exactStock(IGrid grid) {
        var amounts = new LinkedHashMap<AEKey, BigInteger>();
        var known = new java.util.LinkedHashSet<AEKey>();
        var saturated = new ArrayList<AEKey>();
        // AE2's NetworkCraftingSimulationState starts from this cached list.
        for (var entry : grid.getStorageService().getCachedInventory()) {
            long listed = entry.getLongValue();
            if (listed < 0) throw new IllegalStateException("Negative AE2 cached stock: " + entry.getKey().getId());
            if (listed == 0) continue;
            AEKey key = entry.getKey();
            amounts.put(key, BigInteger.valueOf(listed));
            if (listed == Long.MAX_VALUE) saturated.add(key);
            else known.add(key);
        }
        if (saturated.isEmpty()) return new Stock(amounts, true, known);

        // Only a saturated long facade needs the mounted BigInteger source. Never revive a key
        // absent from AE2's visible list, and never infer a wide quantity from Long.MAX_VALUE.
        var counter = PlanningExactInventorySnapshot.capture(grid);
        var exact = BigKeyCounterSidecars.snapshot(counter).orElseGet(() -> BigKeyCounterSidecars.fromFacade(counter));
        boolean complete = exact.complete();
        for (AEKey key : saturated) {
            BigInteger mounted = exact.amounts().get(key);
            if (mounted != null && mounted.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) >= 0
                    && exact.isExact(key)) {
                amounts.put(key, mounted);
                known.add(key);
            } else complete = false;
        }
        return new Stock(amounts, complete, known);
    }

    @Override public BigInteger simulateExtraction(IGrid grid, AEKey key, BigInteger amount,
            appeng.api.networking.security.IActionSource source) {
        if (amount.bitLength() <= 63) return VmAccounting.super.simulateExtraction(grid, key, amount, source);
        if (com.syaru.ae2craftingoptimizer.integration.ExactNetworkStorageBridge.canExtract(grid, key, amount, source))
            return amount;
        throw new IllegalStateException("Exact simulated extraction cannot prove the available quantity for " + key.getId());
    }

    @Override public ICraftingPlan wideResult(IGrid grid, Level level, NativeVmResult result) {
        return wideResult(grid,level,result,orderContext(grid,level));
    }

    private record OrderRevision(long patterns,long recipes) { }

    @Override public Object orderContext(IGrid grid, Level level) {
        return new OrderRevision(ProviderPatternGenerationTracker.generation(),RecipeGenerationTracker.generation());
    }

    @Override public ICraftingPlan wideResult(IGrid grid, Level level, NativeVmResult result, Object context) {
        var amounts = result.amounts();
        int bits = ACOConfig.getBigIntegerMaximumBits();
        BigCountMath.requireMaximumBits(result.bytes(), "vm/bytes", bits);
        Map<IPatternDetails, BigInteger> times = new LinkedHashMap<>();
        amounts.crafts().forEach((id, count) -> times.merge(
                java.util.Objects.requireNonNull(result.bindings().get(id), "missing VM pattern binding: " + id),
                count, BigInteger::add));
        var display = new GenericStack(amounts.root(), BigIntegerPlanProjection.saturatedLong(amounts.requested()));
        // #218: do not bless an old snapshot with a newer generation after asynchronous planning.
        var revision = context instanceof OrderRevision captured ? captured : (OrderRevision) orderContext(grid,level);
        long patterns = revision.patterns();
        long recipes = revision.recipes();
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
