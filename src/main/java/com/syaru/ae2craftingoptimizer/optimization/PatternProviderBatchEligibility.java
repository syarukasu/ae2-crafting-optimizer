package com.syaru.ae2craftingoptimizer.optimization;

import appeng.api.config.LockCraftingMode;
import appeng.api.config.Settings;
import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.util.IConfigManager;
import appeng.helpers.patternprovider.PatternProviderLogic;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

public final class PatternProviderBatchEligibility {
    private static final String ADVANCED_PROVIDER_CLASS =
            "net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogic";

    private static final ClassValue<ProviderAccess> ACCESS = new ClassValue<>() {
        @Override
        protected ProviderAccess computeValue(Class<?> type) {
            return ProviderAccess.create(type);
        }
    };

    private PatternProviderBatchEligibility() {
    }

    @Nullable
    public static BatchTarget inspect(ICraftingProvider provider, IPatternDetails pattern, Level level) {
        if (!ACOConfig.enablePatternMicroBatching()
                || provider == null
                || pattern == null
                || level == null
                || !isSupportedProvider(provider)
                || !isSafeProcessingPattern(pattern)) {
            return null;
        }

        try {
            ProviderAccess access = ACCESS.get(provider.getClass());
            if (!access.supported()) {
                return null;
            }
            if (access.isBlocking(provider)) {
                return null;
            }
            IConfigManager configManager = access.configManager(provider);
            if (configManager == null
                    || configManager.getSetting(Settings.LOCK_CRAFTING_MODE) != LockCraftingMode.NONE) {
                return null;
            }
            if (directionalAdvancedPattern(pattern)) {
                return null;
            }

            Object host = access.host(provider);
            if (host == null) {
                return null;
            }
            BlockEntity providerBlockEntity = access.blockEntity(host);
            Collection<?> rawTargets = access.targets(host);
            if (providerBlockEntity == null
                    || providerBlockEntity.getLevel() != level
                    || rawTargets == null
                    || rawTargets.isEmpty()) {
                return null;
            }

            List<Direction> targets = new ArrayList<>(rawTargets.size());
            for (Object rawTarget : rawTargets) {
                if (rawTarget instanceof Direction direction) {
                    targets.add(direction);
                }
            }
            if (targets.isEmpty()
                    || (ACOConfig.requireSinglePatternProviderTarget() && targets.size() != 1)) {
                return null;
            }

            List<String> allowedNamespaces = ACOConfig.getPatternMicroBatchTargetNamespaces();
            if (allowedNamespaces.isEmpty()) {
                return null;
            }
            BlockPos providerPos = providerBlockEntity.getBlockPos();
            for (Direction providerSide : targets) {
                BlockPos targetPos = providerPos.relative(providerSide);
                BlockEntity target = level.getBlockEntity(targetPos);
                Direction targetSide = providerSide.getOpposite();
                if (target == null
                        || !hasAllowedNamespace(target, allowedNamespaces)
                        || isDedicatedCraftingMachine(level, targetPos, targetSide, target)) {
                    return null;
                }
            }

            return new BatchTarget(targets.size() == 1 ? targets.get(0) : null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isSupportedProvider(ICraftingProvider provider) {
        return provider instanceof PatternProviderLogic || hasClassName(provider.getClass(), ADVANCED_PROVIDER_CLASS);
    }

    private static boolean hasClassName(Class<?> type, String expectedName) {
        Class<?> current = type;
        while (current != null) {
            if (current.getName().equals(expectedName)) {
                return true;
            }
            current = current.getSuperclass();
        }
        return false;
    }

    private static boolean isSafeProcessingPattern(IPatternDetails pattern) {
        if (!pattern.supportsPushInputsToExternalInventory()) {
            return false;
        }
        for (IPatternDetails.IInput input : pattern.getInputs()) {
            if (input.getMultiplier() <= 0) {
                return false;
            }
            for (var possibleInput : input.getPossibleInputs()) {
                if (possibleInput.amount() <= 0 || input.getRemainingKey(possibleInput.what()) != null) {
                    return false;
                }
            }
        }
        for (var output : pattern.getOutputs()) {
            if (output.amount() <= 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean directionalAdvancedPattern(IPatternDetails pattern) {
        try {
            Method method = pattern.getClass().getMethod("directionalInputsSet");
            return Boolean.TRUE.equals(method.invoke(pattern));
        } catch (NoSuchMethodException ignored) {
            return false;
        } catch (ReflectiveOperationException ignored) {
            return true;
        }
    }

    private static boolean hasAllowedNamespace(BlockEntity target, List<String> allowedNamespaces) {
        ResourceLocation blockEntityType = ForgeRegistries.BLOCK_ENTITY_TYPES.getKey(target.getType());
        if (blockEntityType != null && allowedNamespaces.contains(blockEntityType.getNamespace())) {
            return true;
        }
        ResourceLocation block = ForgeRegistries.BLOCKS.getKey(target.getBlockState().getBlock());
        return block != null && allowedNamespaces.contains(block.getNamespace());
    }

    private static boolean isDedicatedCraftingMachine(
            Level level,
            BlockPos targetPos,
            Direction targetSide,
            BlockEntity target) {
        ICraftingMachine craftingMachine = ICraftingMachine.of(level, targetPos, targetSide, target);
        return craftingMachine != null && craftingMachine.acceptsPlans();
    }

    public record BatchTarget(@Nullable Direction providerSide) {
    }

    private record ProviderAccess(
            @Nullable Field hostField,
            @Nullable Method getBlockEntity,
            @Nullable Method getTargets,
            @Nullable Method isBlocking,
            @Nullable Method getConfigManager) {

        static ProviderAccess create(Class<?> providerType) {
            try {
                Field host = findField(providerType, "host");
                host.setAccessible(true);
                Class<?> hostType = host.getType();
                Method blockEntity = hostType.getMethod("getBlockEntity");
                Method targets = hostType.getMethod("getTargets");
                Method blocking = providerType.getMethod("isBlocking");
                Method configManager = providerType.getMethod("getConfigManager");
                return new ProviderAccess(host, blockEntity, targets, blocking, configManager);
            } catch (ReflectiveOperationException ignored) {
                return new ProviderAccess(null, null, null, null, null);
            }
        }

        boolean supported() {
            return hostField != null
                    && getBlockEntity != null
                    && getTargets != null
                    && isBlocking != null
                    && getConfigManager != null;
        }

        @Nullable
        Object host(Object provider) throws ReflectiveOperationException {
            return hostField == null ? null : hostField.get(provider);
        }

        @Nullable
        BlockEntity blockEntity(Object host) throws ReflectiveOperationException {
            Object value = getBlockEntity == null ? null : getBlockEntity.invoke(host);
            return value instanceof BlockEntity blockEntity ? blockEntity : null;
        }

        @Nullable
        Collection<?> targets(Object host) throws ReflectiveOperationException {
            Object value = getTargets == null ? null : getTargets.invoke(host);
            return value instanceof Collection<?> collection ? collection : null;
        }

        boolean isBlocking(Object provider) throws ReflectiveOperationException {
            return isBlocking != null && Boolean.TRUE.equals(isBlocking.invoke(provider));
        }

        @Nullable
        IConfigManager configManager(Object provider) throws ReflectiveOperationException {
            Object value = getConfigManager == null ? null : getConfigManager.invoke(provider);
            return value instanceof IConfigManager configManager ? configManager : null;
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
}
