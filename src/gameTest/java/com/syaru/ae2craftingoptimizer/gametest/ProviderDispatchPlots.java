package com.syaru.ae2craftingoptimizer.gametest;

import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.core.definitions.AEBlocks;
import appeng.server.testplots.TestPlot;
import appeng.server.testworld.PlotBuilder;
import com.mojang.logging.LogUtils;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.intent.RecipeIntent;
import com.syaru.ae2craftingoptimizer.intent.RecipeIntentRegistry;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import jdk.jfr.Recording;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

public final class ProviderDispatchPlots {
    private ProviderDispatchPlots() {}

    @TestPlot("aco_provider_dispatch_load")
    public static void dispatchLoad(PlotBuilder plot) {
        plot.creativeEnergyCell("0 -1 0");
        plot.blockEntity("0 0 0", AEBlocks.PATTERN_PROVIDER, provider ->
                provider.getLogic().getPatternInv().addItems(PatternDetailsHelper.encodeProcessingPattern(
                        new GenericStack[] {new GenericStack(AEItemKey.of(Items.IRON_INGOT), 1)},
                        new GenericStack[] {new GenericStack(AEItemKey.of(Items.GOLD_INGOT), 1)})));
        plot.chest("1 0 0");
        plot.test(helper -> helper.startSequence().thenIdle(10).thenExecute(() -> {
            helper.check(ACOConfig.capturePatternProviderRecipeIntents(), "Intent capture must be exercised");
            var provider = (PatternProviderBlockEntity) helper.getBlockEntity(BlockPos.ZERO);
            var target = (ChestBlockEntity) helper.getBlockEntity(new BlockPos(1, 0, 0));
            var logic = provider.getLogic();
            var pattern = logic.getAvailablePatterns().get(0);
            long now = helper.getLevel().getGameTime();
            var dimension = helper.getLevel().dimension().location();
            // Unrelated provider hints must not make every successful push scan the registry.
            for (int i = 0; i < 1024; i++) {
                var pos = new BlockPos(10000 + i, 0, 0);
                RecipeIntentRegistry.record(new RecipeIntent(dimension, pos.west(), Direction.EAST,
                        pos, Direction.WEST, "test:other_provider", List.of(), List.of(), List.of(),
                        1, now, now + 200));
            }
            boolean profile = Boolean.getBoolean("aco.gametest.providerProfile");
            int rounds = profile ? 256 : 6;
            try (var recording = profile ? new Recording() : null) {
                if (recording != null) {
                    recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(1));
                    recording.enable("jdk.ObjectAllocationSample").with("throttle", "300/s");
                    recording.start();
                }
                for (int round = 0; round < rounds; round++) {
                    target.clearContent();
                    long start = System.nanoTime();
                    for (int i = 0; i < 1024; i++) {
                        var input = new KeyCounter();
                        input.add(AEItemKey.of(Items.IRON_INGOT), 1);
                        helper.check(logic.pushPattern(pattern, new KeyCounter[] {input}),
                                "Native push was capped or rejected at " + i);
                    }
                    long elapsed = System.nanoTime() - start;
                    int delivered = 0;
                    for (int slot = 0; slot < target.getContainerSize(); slot++) {
                        var stack = target.getItem(slot);
                        helper.check(stack.isEmpty() || stack.is(Items.IRON_INGOT), "Wrong delivered item");
                        delivered += stack.getCount();
                    }
                    helper.check(delivered == 1024, "Lost or duplicated inputs: " + delivered);
                    if (!profile || round % 32 == 0) {
                        LogUtils.getLogger().info("ACO provider probe round={} delivered={} elapsedNs={} profile={}",
                                round, delivered, elapsed, profile);
                    }
                }
                if (recording != null) {
                    recording.stop();
                    recording.dump(Path.of("provider-dispatch.jfr"));
                }
            } catch (IOException e) {
                throw new IllegalStateException("Provider profile could not be saved", e);
            }
            var hints = RecipeIntentRegistry.find(dimension, target.getBlockPos(), Direction.WEST, now);
            helper.check(hints.stream().mapToLong(RecipeIntent::patternExecutions).sum() == (long) rounds * 1024,
                    "Successful native pushes did not reach the real intent hook");
            for (int slot = 0; slot < target.getContainerSize(); slot++) {
                target.setItem(slot, new ItemStack(Items.IRON_INGOT, 64));
            }
            var input = new KeyCounter();
            input.add(AEItemKey.of(Items.IRON_INGOT), 1);
            helper.check(!logic.pushPattern(pattern, new KeyCounter[] {input}), "Full target must reject");
            helper.check(input.get(AEItemKey.of(Items.IRON_INGOT)) == 1, "Rejected input was consumed");
            target.clearContent();
            target.setItem(0, new ItemStack(Items.IRON_INGOT));
            logic.getConfigManager().putSetting(Settings.BLOCKING_MODE, YesNo.YES);
            helper.check(!logic.pushPattern(pattern, new KeyCounter[] {input}), "Blocking mode was bypassed");
            helper.check(input.get(AEItemKey.of(Items.IRON_INGOT)) == 1, "Blocked input was consumed");
            target.clearContent();
            helper.check(logic.pushPattern(pattern, new KeyCounter[] {input}), "Empty target must resume");
            helper.check(target.getItem(0).getCount() == 1, "Resumed delivery lost or duplicated input");
        }).thenSucceed()).maxTicks(200);
    }
}
