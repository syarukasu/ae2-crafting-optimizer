package com.syaru.ae2craftingoptimizer.optimization;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;
import appeng.me.service.CraftingService;
import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import net.minecraft.world.level.Level;

public final class DeterministicCraftingPreflight {
    private DeterministicCraftingPreflight() {
    }

    public static Future<ICraftingPlan> tryFastFail(
            CraftingService craftingService,
            IGrid grid,
            Level level,
            ICraftingSimulationRequester requester,
            AEKey output,
            long amount,
            CalculationStrategy strategy) {
        if (!ACOConfig.fastFailMissingCrafts()
                || strategy != CalculationStrategy.REPORT_MISSING_ITEMS
                || amount < ACOConfig.getMinimumRequestedAmountForFastFail()) {
            return null;
        }

        PreflightState state = new PreflightState(grid.getStorageService().getCachedInventory());
        MissingResult missing = state.require(craftingService, level, output, amount, 0, new HashSet<>());
        if (missing == null || missing == MissingResult.UNKNOWN) {
            return null;
        }

        long simulatedAvailable = grid.getStorageService().getInventory().extract(
                missing.key,
                missing.amount,
                Actionable.SIMULATE,
                requester.getActionSource());
        long verifiedMissing = saturatingSubtract(missing.amount, simulatedAvailable);
        if (verifiedMissing <= 0) {
            return null;
        }

        if (ACOConfig.logFastFailMissingCrafts()) {
            AE2CraftingOptimizer.LOGGER.info(
                    "Fast-failed deterministic AE2 craft for {} x{}: missing {} x{}",
                    output.getId(),
                    amount,
                    missing.key.getId(),
                    verifiedMissing);
        }

        return CompletableFuture.completedFuture(new MissingOnlyCraftingPlan(output, amount, missing.key, verifiedMissing));
    }

    private static long ceilDiv(long value, long divisor) {
        if (value <= 0 || divisor <= 0) {
            return 0;
        }
        return 1 + (value - 1) / divisor;
    }

    private static long saturatingMultiply(long left, long right) {
        if (left <= 0 || right <= 0) {
            return 0;
        }
        if (left > Long.MAX_VALUE / right) {
            return Long.MAX_VALUE;
        }
        return left * right;
    }

    private static long saturatingSubtract(long left, long right) {
        if (right >= left) {
            return 0;
        }
        return left - right;
    }

    private static final class PreflightState {
        private final KeyCounter available;
        private int visitedNodes;

        private PreflightState(KeyCounter cachedInventory) {
            this.available = new KeyCounter();
            this.available.addAll(cachedInventory);
        }

        private MissingResult require(
                CraftingService craftingService,
                Level level,
                AEKey key,
                long amount,
                int depth,
                Set<AEKey> stack) {
            if (amount <= 0) {
                return null;
            }
            if (depth > ACOConfig.getDeterministicPreflightMaxDepth()
                    || ++visitedNodes > ACOConfig.getDeterministicPreflightMaxNodes()
                    || !stack.add(key)) {
                return MissingResult.UNKNOWN;
            }

            long stored = available.get(key);
            long used = Math.min(stored, amount);
            if (used > 0) {
                available.remove(key, used);
                amount -= used;
            }
            if (amount <= 0) {
                stack.remove(key);
                return null;
            }

            if (craftingService.canEmitFor(key)) {
                stack.remove(key);
                return MissingResult.UNKNOWN;
            }

            Collection<IPatternDetails> patterns = craftingService.getCraftingFor(key);
            if (patterns.isEmpty()) {
                stack.remove(key);
                return new MissingResult(key, amount);
            }
            if (patterns.size() != 1) {
                stack.remove(key);
                return MissingResult.UNKNOWN;
            }

            IPatternDetails pattern = patterns.iterator().next();
            GenericStack[] outputs = pattern.getOutputs();
            if (outputs.length != 1 || !key.matches(outputs[0]) || outputs[0].amount() <= 0) {
                stack.remove(key);
                return MissingResult.UNKNOWN;
            }

            long batches = ceilDiv(amount, outputs[0].amount());
            for (IPatternDetails.IInput input : pattern.getInputs()) {
                GenericStack[] possibleInputs = input.getPossibleInputs();
                if (possibleInputs.length != 1 || possibleInputs[0].amount() <= 0 || input.getMultiplier() <= 0) {
                    stack.remove(key);
                    return MissingResult.UNKNOWN;
                }
                long needed = saturatingMultiply(possibleInputs[0].amount(), input.getMultiplier());
                needed = saturatingMultiply(needed, batches);
                MissingResult missing = require(craftingService, level, possibleInputs[0].what(), needed, depth + 1, stack);
                if (missing != null) {
                    stack.remove(key);
                    return missing;
                }
            }

            stack.remove(key);
            return null;
        }
    }

    private record MissingResult(AEKey key, long amount) {
        private static final MissingResult UNKNOWN = new MissingResult(null, -1);
    }
}
