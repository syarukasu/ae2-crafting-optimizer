package com.syaru.ae2craftingoptimizer.intent;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.KeyCounter;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.optimization.PatternPushContext;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class PatternIntentCapture {
    private PatternIntentCapture() {
    }

    public static void captureSuccessfulPush(
            PatternProviderLogicHost host,
            IPatternDetails pattern,
            KeyCounter[] inputHolder) {
        if (!ACOConfig.capturePatternProviderRecipeIntents() || host == null || pattern == null) {
            return;
        }
        captureSuccessfulPush(host.getBlockEntity(), host.getTargets(), pattern, inputHolder);
    }

    public static void captureSuccessfulPushFromLogic(
            Object providerLogic,
            IPatternDetails pattern,
            KeyCounter[] inputHolder) {
        if (!ACOConfig.capturePatternProviderRecipeIntents() || providerLogic == null || pattern == null) {
            return;
        }
        try {
            Field hostField = findField(providerLogic.getClass(), "host");
            hostField.setAccessible(true);
            Object host = hostField.get(providerLogic);
            if (host == null) {
                return;
            }
            Method getBlockEntity = host.getClass().getMethod("getBlockEntity");
            Method getTargets = host.getClass().getMethod("getTargets");
            getBlockEntity.setAccessible(true);
            getTargets.setAccessible(true);
            Object provider = getBlockEntity.invoke(host);
            Object targets = getTargets.invoke(host);
            if (provider instanceof BlockEntity providerBlockEntity && targets instanceof Collection<?> collection) {
                captureSuccessfulPush(providerBlockEntity, collection, pattern, inputHolder);
            }
        } catch (ReflectiveOperationException ignored) {
            // Advanced AE is optional; a changed provider implementation simply disables intent capture for it.
        }
    }

    private static void captureSuccessfulPush(
            BlockEntity provider,
            Collection<?> rawTargets,
            IPatternDetails pattern,
            KeyCounter[] inputHolder) {
        if (provider == null || pattern == null || rawTargets == null || rawTargets.isEmpty()) {
            return;
        }
        if (!(provider.getLevel() instanceof ServerLevel serverLevel)) {
            return;
        }

        Direction forcedProviderSide = PatternPushContext.providerSide();
        EnumSet<Direction> targets = EnumSet.noneOf(Direction.class);
        for (Object rawTarget : rawTargets) {
            if (rawTarget instanceof Direction direction
                    && (forcedProviderSide == null || forcedProviderSide == direction)) {
                targets.add(direction);
            }
        }
        if (targets.isEmpty()) {
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
        long patternExecutions = PatternPushContext.patternExecutions();

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
                    patternExecutions,
                    now,
                    expires));
        }
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(type.getName() + "." + name);
    }
}
