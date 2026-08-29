package com.syaru.ae2craftingoptimizer.optimization;

import appeng.api.config.LockCraftingMode;
import appeng.api.config.Settings;
import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.util.IConfigManager;
import com.syaru.ae2craftingoptimizer.access.PatternProviderTargetAccess;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.integration.AdvancedAePatternProviderAccess;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.fml.ModList;
import org.jetbrains.annotations.Nullable;

/** V2取引へ渡せるPattern Providerと配送先を、状態変更前に検証する。 */
public final class PatternProviderBatchEligibility {
    private PatternProviderBatchEligibility() {
    }

    @Nullable
    public static BatchTarget inspectV2(
            ICraftingProvider provider,
            IPatternDetails pattern,
            Level level) {
        if (!ACOConfig.enableTransactionalBatchingV2()) {
            return null;
        }
        if (provider == null
                || pattern == null
                || level == null
                || !(provider instanceof PatternProviderTargetAccess access)
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

            List<Direction> targets = new ArrayList<>(rawTargets);
            // V2取引は一つのTargetだけを所有し、曖昧な配送先へは介入しない。
            if (targets.size() != 1) {
                return null;
            }

            BlockPos providerPos = providerBlockEntity.getBlockPos();
            Direction providerSide = targets.get(0);
            BlockPos targetPos = providerPos.relative(providerSide);
            // Provider ReceiptとTargetを同じChunk保存単位へ閉じ込める。
            if ((providerPos.getX() >> 4) != (targetPos.getX() >> 4)
                    || (providerPos.getZ() >> 4) != (targetPos.getZ() >> 4)) {
                return null;
            }

            BlockEntity target = level.getBlockEntity(targetPos);
            Direction targetSide = providerSide.getOpposite();
            if (target == null || isDedicatedCraftingMachine(level, targetPos, targetSide, target)) {
                return null;
            }
            return new BatchTarget(providerSide, targetSide, target, true);
        } catch (RuntimeException | LinkageError ignored) {
            // 外部Providerの照会が失敗した時点では所有権がないため、AE2標準経路へ返す。
            return null;
        }
    }

    private static boolean isSafeProcessingPattern(IPatternDetails pattern) {
        if (!pattern.supportsPushInputsToExternalInventory()) {
            return false;
        }
        // 各入力が単一候補かつ返却物なしであることを証明する。
        for (IPatternDetails.IInput input : pattern.getInputs()) {
            if (input.getMultiplier() <= 0 || !hasOneConsumableInput(input)) {
                return false;
            }
        }
        // 宣言出力が正の数量だけで構成されることを証明する。
        for (var output : pattern.getOutputs()) {
            if (output.amount() <= 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean hasOneConsumableInput(IPatternDetails.IInput input) {
        var possibleInputs = input.getPossibleInputs();
        if (possibleInputs.length != 1) {
            return false;
        }
        // 単一候補でも触媒・容器返却を含む入力はV2へ渡さない。
        for (var possibleInput : possibleInputs) {
            if (possibleInput.amount() <= 0 || input.getRemainingKey(possibleInput.what()) != null) {
                return false;
            }
        }
        return true;
    }

    private static boolean directionalAdvancedPattern(IPatternDetails pattern) {
        return ModList.get().isLoaded("advanced_ae")
                && AdvancedAePatternProviderAccess.hasDirectionalInputs(pattern);
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
