package com.syaru.ae2craftingoptimizer.optimization;

import appeng.api.config.LockCraftingMode;
import appeng.api.config.Settings;
import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.util.IConfigManager;
import com.syaru.ae2craftingoptimizer.access.PatternProviderTransactionAccess;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.integration.AdvancedAePatternProviderAccess;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;

public final class PatternProviderBatchEligibility {
    private PatternProviderBatchEligibility() {
    }

    @Nullable
    public static BatchTarget inspect(ICraftingProvider provider, IPatternDetails pattern, Level level) {
        if (!ACOConfig.enableTransactionalPatternBatching()) {
            return null;
        }
        return inspect(provider, pattern, level, ACOConfig.requireSingleTransactionalBatchTarget(), true);
    }

    @Nullable
    public static BatchTarget inspectV2(ICraftingProvider provider, IPatternDetails pattern, Level level) {
        if (!ACOConfig.enableTransactionalBatchingV2()) {
            return null;
        }
        return inspect(provider, pattern, level, true, false);
    }

    @Nullable
    private static BatchTarget inspect(
            ICraftingProvider provider,
            IPatternDetails pattern,
            Level level,
            boolean requireSingleTarget,
            boolean enforceLegacyNamespaces) {
        if (provider == null
                || pattern == null
                || level == null
                || !(provider instanceof PatternProviderTransactionAccess access)
                || !isSafeProcessingPattern(pattern)) {
            return null;
        }

        try {
            if (!access.aco$isProviderBlocking()) {
                return null;
            }
            IConfigManager configManager = access.aco$getProviderConfigManager();
            if (configManager == null
                    || configManager.getSetting(Settings.LOCK_CRAFTING_MODE) != LockCraftingMode.NONE) {
                return null;
            }
            if (directionalAdvancedPattern(pattern)) {
                return null;
            }

            BlockEntity providerBlockEntity = access.aco$getProviderBlockEntity();
            Collection<Direction> rawTargets = access.aco$getProviderTargets();
            if (providerBlockEntity == null
                    || providerBlockEntity.getLevel() != level
                    || rawTargets == null
                    || rawTargets.isEmpty()) {
                return null;
            }

            List<Direction> targets = new ArrayList<>(rawTargets.size());
            targets.addAll(rawTargets);
            if (targets.isEmpty()
                    || (requireSingleTarget && targets.size() != 1)) {
                return null;
            }

            List<String> allowedNamespaces = ACOConfig.getTransactionalBatchTargetNamespaces();
            if (enforceLegacyNamespaces && allowedNamespaces.isEmpty()) {
                return null;
            }
            BlockPos providerPos = providerBlockEntity.getBlockPos();
            for (Direction providerSide : targets) {
                BlockPos targetPos = providerPos.relative(providerSide);
                BlockEntity target = level.getBlockEntity(targetPos);
                Direction targetSide = providerSide.getOpposite();
                if (target == null
                        || (enforceLegacyNamespaces && !hasAllowedNamespace(target, allowedNamespaces))
                        || isDedicatedCraftingMachine(level, targetPos, targetSide, target)) {
                    return null;
                }
            }

            Direction selectedSide = targets.get(0);
            BlockEntity selectedTarget = level.getBlockEntity(providerPos.relative(selectedSide));
            if (selectedTarget == null) {
                return null;
            }
            return new BatchTarget(
                    selectedSide,
                    selectedSide.getOpposite(),
                    selectedTarget,
                    targets.size() == 1);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isSafeProcessingPattern(IPatternDetails pattern) {
        if (!pattern.supportsPushInputsToExternalInventory()) {
            return false;
        }
        for (IPatternDetails.IInput input : pattern.getInputs()) {
            if (input.getMultiplier() <= 0) {
                return false;
            }
            var possibleInputs = input.getPossibleInputs();
            if (possibleInputs.length != 1) {
                return false;
            }
            for (var possibleInput : possibleInputs) {
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
        return ModList.get().isLoaded("advanced_ae")
                && AdvancedAePatternProviderAccess.hasDirectionalInputs(pattern);
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

    public record BatchTarget(
            Direction providerSide,
            Direction targetSide,
            BlockEntity target,
            boolean deterministicTarget) {
    }

}
