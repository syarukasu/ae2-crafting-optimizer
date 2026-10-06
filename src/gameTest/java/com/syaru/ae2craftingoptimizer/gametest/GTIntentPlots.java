package com.syaru.ae2craftingoptimizer.gametest;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.server.testplots.TestPlot;
import appeng.server.testworld.PlotBuilder;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.WorkableTieredMachine;
import com.syaru.ae2craftingoptimizer.intent.IntentSearchIterator;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

/** #179: native GT selection, energy and item consumption, not a simulated executor. */
public final class GTIntentPlots {
    @TestPlot("aco_gt_intent_completion")
    public static void completion(PlotBuilder plot) {
        plot.creativeEnergyCell("0 -1 0");
        plot.blockEntity("0 0 0", AEBlocks.PATTERN_PROVIDER, provider ->
                provider.getLogic().getPatternInv().addItems(PatternDetailsHelper.encodeProcessingPattern(
                        new GenericStack[]{new GenericStack(AEItemKey.of(Items.RAW_IRON), 1)},
                        new GenericStack[]{new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1)})));
        plot.block("1 0 0", ForgeRegistries.BLOCKS.getValue(new ResourceLocation("gtceu", "lv_electric_furnace")));
        plot.test(helper -> {
            var machine = new java.util.concurrent.atomic.AtomicReference<WorkableTieredMachine>();
            helper.startSequence().thenIdle(10).thenExecute(() -> {
                var provider = (PatternProviderBlockEntity) helper.getBlockEntity(BlockPos.ZERO);
                var tile = helper.getBlockEntity(new BlockPos(1, 0, 0));
                var furnace = (WorkableTieredMachine) MetaMachine.getMachine(tile.getLevel(), tile.getBlockPos());
                machine.set(furnace);
                var input = new KeyCounter(); input.add(AEItemKey.of(Items.RAW_IRON), 1);
                helper.check(provider.getLogic().pushPattern(provider.getLogic().getAvailablePatterns().get(0),
                        new KeyCounter[]{input}), "Real provider rejected GT furnace input");
                var candidates = furnace.getRecipeLogic().searchRecipe();
                helper.check(candidates instanceof IntentSearchIterator<?>, "GT native search was not deferred");
                helper.check(candidates.hasNext(), "GT hint did not resolve a recipe");
                var selected = candidates.next();
                helper.check(selected.getType() == furnace.getRecipeType(), "Hint selected another machine's recipe type");
            }).thenIdle(20).thenExecute(() -> {
                var furnace = machine.get();
                helper.check(furnace.exportItems.isEmpty(), "GT hint completed without energy");
                furnace.energyContainer.setEnergyStored(furnace.energyContainer.getEnergyCapacity());
            }).thenIdle(5).thenExecute(() -> helper.check(machine.get().exportItems.isEmpty(),
                    "GT hint skipped processing ticks"))
                    .thenWaitUntil(() -> {
                        var furnace = machine.get();
                        var output = furnace.exportItems.getStackInSlot(0);
                        helper.check(output.is(Items.IRON_INGOT) && output.getCount() == 1,
                                "GT furnace has not completed the real recipe");
                        helper.check(furnace.importItems.isEmpty(), "GT did not consume its input");
                        helper.check(furnace.energyContainer.getEnergyStored() < furnace.energyContainer.getEnergyCapacity(),
                                "GT did not consume energy");
                    }).thenSucceed();
        }).maxTicks(800);
    }
}
