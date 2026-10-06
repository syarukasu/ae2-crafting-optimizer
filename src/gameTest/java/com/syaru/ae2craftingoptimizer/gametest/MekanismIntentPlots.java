package com.syaru.ae2craftingoptimizer.gametest;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.server.testplots.TestPlot;
import appeng.server.testworld.PlotBuilder;
import com.syaru.ae2craftingoptimizer.optimization.OptimizationMetrics;
import mekanism.common.tile.prefab.TileEntityElectricMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

/** Real provider and powered smelter. Registered only in the optional Mekanism fixture. */
public final class MekanismIntentPlots {
    @TestPlot("aco_mekanism_intent_completion")
    public static void completion(PlotBuilder plot) {
        plot.creativeEnergyCell("0 -1 0");
        plot.blockEntity("0 0 0", AEBlocks.PATTERN_PROVIDER, provider ->
                provider.getLogic().getPatternInv().addItems(PatternDetailsHelper.encodeProcessingPattern(
                        new GenericStack[]{new GenericStack(AEItemKey.of(Items.RAW_IRON), 1)},
                        new GenericStack[]{new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1)})));
        plot.block("1 0 0", ForgeRegistries.BLOCKS.getValue(new ResourceLocation("mekanism", "energized_smelter")));
        plot.test(helper -> {
            var machine = new java.util.concurrent.atomic.AtomicReference<TileEntityElectricMachine>();
            helper.startSequence().thenIdle(10).thenExecute(() -> {
                var provider = (PatternProviderBlockEntity) helper.getBlockEntity(BlockPos.ZERO);
                var smelter = (TileEntityElectricMachine) helper.getBlockEntity(new BlockPos(1, 0, 0));
                machine.set(smelter);
                var transmission = mekanism.common.lib.transmitter.TransmissionType.ITEM;
                smelter.getConfig().getConfig(transmission).setDataType(
                        mekanism.common.tile.component.config.DataType.INPUT, mekanism.api.RelativeSide.values());
                for (var side : mekanism.api.RelativeSide.values()) smelter.getConfig().sideChanged(transmission, side);
                var input = new KeyCounter(); input.add(AEItemKey.of(Items.RAW_IRON), 1);
                helper.check(provider.getLogic().pushPattern(provider.getLogic().getAvailablePatterns().get(0),
                        new KeyCounter[]{input}), "Real provider rejected smelter input");
                long before = OptimizationMetrics.mekanismIntentMatches();
                var recipe = smelter.getRecipe(0);
                helper.check(recipe != null && recipe.getOutput(Items.RAW_IRON.getDefaultInstance()).is(Items.IRON_INGOT),
                        "Recipe intent selected the wrong native recipe");
                helper.check(OptimizationMetrics.mekanismIntentMatches() > before,
                        "Real getRecipe call did not bypass native input search");
            }).thenIdle(20).thenExecute(() -> {
                var smelter = machine.get();
                helper.check(smelter.getInventorySlots(null).stream().noneMatch(s -> s.getStack().is(Items.IRON_INGOT)),
                        "Recipe hint processed without power");
                smelter.getEnergyContainer().setEnergy(smelter.getEnergyContainer().getMaxEnergy());
            }).thenIdle(5).thenExecute(() -> helper.check(machine.get().getInventorySlots(null).stream()
                    .noneMatch(s -> s.getStack().is(Items.IRON_INGOT)), "Recipe hint skipped processing ticks"))
                    .thenWaitUntil(() -> {
                        var smelter = machine.get();
                        int output = smelter.getInventorySlots(null).stream().filter(s -> s.getStack().is(Items.IRON_INGOT))
                                .mapToInt(s -> s.getStack().getCount()).sum();
                        helper.check(output == 1, "Smelter has not produced one real ingot");
                        helper.check(smelter.getInventorySlots(null).stream().noneMatch(s -> s.getStack().is(Items.RAW_IRON)),
                                "Smelter output was created without consuming the input");
                        helper.check(smelter.getEnergyContainer().getEnergy().smallerThan(smelter.getEnergyContainer().getMaxEnergy()),
                                "Smelter did not consume energy");
                    }).thenSucceed();
        }).maxTicks(800);
    }
}
