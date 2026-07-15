package com.syaru.ae2craftingoptimizer.intent;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class PatternIntentCapture {
    private PatternIntentCapture() {
    }

    public static void captureSuccessfulPush(PatternProviderLogicHost host, IPatternDetails pattern, KeyCounter[] inputHolder) {
        if (!ACOConfig.capturePatternProviderRecipeIntents()) {
            return;
        }
        if (host == null || pattern == null) {
            return;
        }
        BlockEntity provider = host.getBlockEntity();
        if (provider == null) {
            return;
        }
        Level level = provider.getLevel();
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        EnumSet<Direction> targets = host.getTargets();
        if (targets == null || targets.isEmpty()) {
            return;
        }

        ResourceLocation dimension = serverLevel.dimension().location();
        BlockPos providerPos = provider.getBlockPos();
        long now = serverLevel.getGameTime();
        long expires = now + ACOConfig.getRecipeIntentTtlTicks();
        String patternDefinitionId = PatternIntentExtractor.patternDefinitionId(pattern);
        List<InputIntent> inputs = PatternIntentExtractor.inputs(pattern);
        List<StackIntent> concreteInputs = PatternIntentExtractor.concreteInputs(inputHolder);
        List<StackIntent> outputs = PatternIntentExtractor.outputs(pattern);

        for (Direction providerSide : targets) {
            BlockPos targetPos = providerPos.relative(providerSide);
            Direction targetSide = providerSide.getOpposite();
            RecipeIntentRegistry.record(new RecipeIntent(
                    dimension,
                    providerPos,
                    providerSide,
                    targetPos,
                    targetSide,
                    patternDefinitionId,
                    inputs,
                    concreteInputs,
                    outputs,
                    now,
                    expires));
        }
    }
}
