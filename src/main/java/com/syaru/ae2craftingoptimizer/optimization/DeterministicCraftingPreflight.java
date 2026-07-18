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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
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

        PreflightGraph graph = new PreflightGraph(craftingService, grid.getStorageService().getCachedInventory());
        DeterministicMissingProof.Missing<AEKey> missing = DeterministicMissingProof.find(
                graph,
                output,
                amount,
                ACOConfig.getDeterministicPreflightMaxDepth(),
                ACOConfig.getDeterministicPreflightMaxNodes());
        if (missing == null) {
            return null;
        }

        long simulatedAvailable = grid.getStorageService().getInventory().extract(
                missing.key(),
                missing.amount(),
                Actionable.SIMULATE,
                requester.getActionSource());
        long verifiedMissing = DeterministicMissingProof.saturatingSubtract(missing.amount(), simulatedAvailable);
        if (verifiedMissing <= 0) {
            return null;
        }

        if (ACOConfig.logFastFailMissingCrafts()) {
            AE2CraftingOptimizer.LOGGER.info(
                    "Fast-failed deterministic AE2 craft for {} x{}: missing {} ({}) x{}",
                    output.getId(),
                    amount,
                    missing.key().getId(),
                    missing.key().getClass().getSimpleName(),
                    verifiedMissing);
        }

        return CompletableFuture.completedFuture(
                MissingOnlyCraftingPlan.create(output, amount, missing.key(), verifiedMissing));
    }

    private static final class PreflightGraph
            implements DeterministicMissingProof.Graph<AEKey, IPatternDetails, IPatternDetails.IInput> {
        private final CraftingService craftingService;
        private final KeyCounter available;

        private PreflightGraph(CraftingService craftingService, KeyCounter cachedInventory) {
            this.craftingService = craftingService;
            this.available = new KeyCounter();
            this.available.addAll(cachedInventory);
        }

        @Override
        public long available(AEKey key) {
            return available.get(key);
        }

        @Override
        public boolean canEmit(AEKey key) {
            return craftingService.canEmitFor(key);
        }

        @Override
        public Collection<IPatternDetails> patterns(AEKey key) {
            return craftingService.getCraftingFor(key);
        }

        @Override
        public long outputAmountPerExecution(IPatternDetails pattern, AEKey requestedKey) {
            long total = 0;
            for (GenericStack output : pattern.getOutputs()) {
                if (requestedKey.matches(output) && output.amount() > 0) {
                    total = DeterministicMissingProof.saturatingAdd(total, output.amount());
                }
            }
            return total;
        }

        @Override
        public Collection<IPatternDetails.IInput> inputs(IPatternDetails pattern) {
            return List.of(pattern.getInputs());
        }

        @Override
        public Collection<DeterministicMissingProof.Requirement<AEKey>> alternatives(
                IPatternDetails.IInput input,
                long executions) {
            if (input.getMultiplier() <= 0) {
                return List.of();
            }
            GenericStack[] possibleInputs = input.getPossibleInputs();
            List<DeterministicMissingProof.Requirement<AEKey>> alternatives = new ArrayList<>(possibleInputs.length);
            for (GenericStack possibleInput : possibleInputs) {
                if (possibleInput == null || possibleInput.what() == null || possibleInput.amount() <= 0) {
                    return List.of();
                }
                long amount = DeterministicMissingProof.saturatingMultiply(
                        possibleInput.amount(),
                        input.getMultiplier());
                amount = DeterministicMissingProof.saturatingMultiply(amount, executions);
                alternatives.add(new DeterministicMissingProof.Requirement<>(possibleInput.what(), amount));
            }
            return alternatives;
        }
    }
}
