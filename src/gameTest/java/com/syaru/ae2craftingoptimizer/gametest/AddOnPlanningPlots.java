package com.syaru.ae2craftingoptimizer.gametest;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.crafting.CraftingCalculation;
import appeng.items.storage.CreativeCellItem;
import appeng.me.helpers.MachineSource;
import appeng.server.testplots.CraftingPatternHelper;
import appeng.server.testplots.TestPlot;
import appeng.server.testworld.PlotBuilder;
import appeng.server.testworld.PlotTestHelper;
import com.ae2vm.addon.nativeengine.NativeVmHook;
import com.mojang.logging.LogUtils;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** #102 fixtures with the real add-on loaded and separate native/VM plan results. */
public final class AddOnPlanningPlots {
    private AddOnPlanningPlots() {}

    @TestPlot("aco_insaneae_vm_returned_containers")
    public static void returnedContainers(PlotBuilder plot) {
        network(plot, Items.MILK_BUCKET, Items.SUGAR, Items.EGG, Items.WHEAT);
        plot.test(helper -> {
            var provider = (PatternProviderBlockEntity) helper.getBlockEntity(new BlockPos(2, 0, 0));
            provider.getLogic().getPatternInv().addItems(CraftingPatternHelper.encodeShapelessCraftingRecipe(
                    helper.getLevel(), new ItemStack(Items.MILK_BUCKET), new ItemStack(Items.MILK_BUCKET),
                    new ItemStack(Items.MILK_BUCKET), new ItemStack(Items.SUGAR), new ItemStack(Items.EGG),
                    new ItemStack(Items.SUGAR), new ItemStack(Items.WHEAT), new ItemStack(Items.WHEAT),
                    new ItemStack(Items.WHEAT)));
            compare(helper, Items.CAKE, 4096);
        }).maxTicks(1200);
    }

    @TestPlot("aco_insaneae_vm_alternate_chain")
    public static void alternateChain(PlotBuilder plot) {
        network(plot, Items.IRON_INGOT);
        plot.test(helper -> {
            var provider = (PatternProviderBlockEntity) helper.getBlockEntity(new BlockPos(2, 0, 0));
            var patterns = provider.getLogic().getPatternInv();
            patterns.addItems(processing(Items.IRON_INGOT, 2, Items.COPPER_INGOT));
            patterns.addItems(processing(Items.GOLD_INGOT, 2, Items.COPPER_INGOT));
            patterns.addItems(processing(Items.COPPER_INGOT, 4, Items.DIAMOND));
            compare(helper, Items.DIAMOND, 512);
        }).maxTicks(1200);
    }

    private static void network(PlotBuilder plot, Item... materials) {
        plot.creativeEnergyCell("0 -1 0");
        plot.cable("[0,2] 0 0");
        plot.blockEntity("1 0 0", AEBlocks.DRIVE, drive ->
                drive.getInternalInventory().addItems(CreativeCellItem.ofItems(materials)));
        plot.block("2 0 0", AEBlocks.PATTERN_PROVIDER);
    }

    private static ItemStack processing(Item input, long amount, Item output) {
        return PatternDetailsHelper.encodeProcessingPattern(
                new GenericStack[] {new GenericStack(AEItemKey.of(input), amount)},
                new GenericStack[] {new GenericStack(AEItemKey.of(output), 1)});
    }

    private static void compare(PlotTestHelper helper, Item output, long amount) {
        class Pair {
            Future<ICraftingPlan> nativePlan;
            Future<ICraftingPlan> vmPlan;
        }
        var plans = new Pair();
        helper.startSequence().thenIdle(2).thenExecute(() -> {
            var grid = helper.getGrid(BlockPos.ZERO);
            var source = new MachineSource(grid::getPivot);
            var nativeCalculation = new CraftingCalculation(helper.getLevel(), grid, () -> source,
                    new GenericStack(AEItemKey.of(output), amount), CalculationStrategy.REPORT_MISSING_ITEMS);
            var task = new FutureTask<ICraftingPlan>(nativeCalculation::run);
            plans.nativePlan = task;
            ((NativeVmHook) grid.getCraftingService()).vm$execute(task);
            plans.vmPlan = grid.getCraftingService().beginCraftingCalculation(helper.getLevel(), () -> source,
                    AEItemKey.of(output), amount, CalculationStrategy.REPORT_MISSING_ITEMS);
        }).thenWaitUntil(() -> helper.check(plans.nativePlan.isDone() && plans.vmPlan.isDone(),
                "Native/VM plans pending")).thenExecute(() -> {
            try {
                var expected = plans.nativePlan.get();
                var actual = plans.vmPlan.get();
                helper.check(!expected.simulation() && !actual.simulation(), "Stocked fixture reported shortage");
                helper.check(expected.bytes() == actual.bytes(), "Capacity differs: " + expected.bytes() + "/" + actual.bytes());
                helper.check(counts(expected.usedItems()).equals(counts(actual.usedItems())), "Consumed inputs differ");
                helper.check(counts(expected.missingItems()).equals(counts(actual.missingItems())), "Shortages differ");
                helper.check(counts(expected.emittedItems()).equals(counts(actual.emittedItems())), "Emitted inputs differ");
                helper.check(expected.patternTimes().equals(actual.patternTimes()), "Selected pattern counts differ");
                LogUtils.getLogger().info("ACO add-on oracle output={} amount={} bytes={} used={} patterns={}",
                        AEItemKey.of(output).getId(), amount, actual.bytes(), counts(actual.usedItems()),
                        actual.patternTimes().values());
            } catch (Exception failure) {
                throw new IllegalStateException("Actual native/VM comparison failed", failure);
            }
        }).thenSucceed();
    }

    private static Map<AEKey, Long> counts(KeyCounter counter) {
        var result = new HashMap<AEKey, Long>();
        for (var entry : counter) if (entry.getLongValue() != 0) result.put(entry.getKey(), entry.getLongValue());
        return result;
    }
}
