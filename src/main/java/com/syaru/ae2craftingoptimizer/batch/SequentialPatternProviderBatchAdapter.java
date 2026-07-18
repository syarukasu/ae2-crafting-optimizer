package com.syaru.ae2craftingoptimizer.batch;

import appeng.api.stacks.KeyCounter;
import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import com.syaru.ae2craftingoptimizer.api.batch.PatternBatchAdapter;
import com.syaru.ae2craftingoptimizer.api.batch.PatternBatchBudget;
import com.syaru.ae2craftingoptimizer.api.batch.PatternBatchContext;
import com.syaru.ae2craftingoptimizer.api.batch.PatternBatchResult;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import net.minecraft.resources.ResourceLocation;

/**
 * 保守的な標準Adapter。入力抽出とCPU会計はまとめるが、受理一回ごとに元の
 * {@code pushPattern}を一回呼び、AE2がBackpressureを返した時点で停止する。
 */
public final class SequentialPatternProviderBatchAdapter implements PatternBatchAdapter {
    public static final SequentialPatternProviderBatchAdapter INSTANCE = new SequentialPatternProviderBatchAdapter();
    private static final ResourceLocation ID =
            ResourceLocation.fromNamespaceAndPath(AE2CraftingOptimizer.MODID, "sequential_pattern_provider");

    private SequentialPatternProviderBatchAdapter() {
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    @Override
    public boolean supportsMultipleProviderTargets() {
        return true;
    }

    @Override
    public boolean supports(PatternBatchContext context) {
        return ACOConfig.enableSequentialPatternProviderBatchAdapter();
    }

    @Override
    public long limitExecutions(PatternBatchContext context, long offeredExecutions) {
        return Math.min(offeredExecutions, ACOConfig.getMaxSequentialProviderExecutionsPerCall());
    }

    @Override
    public PatternBatchResult commit(PatternBatchContext context, PatternBatchBudget budget) {
        long limit = limitExecutions(context, budget.maximumExecutions());
        long accepted = 0L;
        while (accepted < limit && budget.canStartAnother(accepted)) {
            if (context.provider().isBusy()) {
                break;
            }
            KeyCounter[] oneExecution = context.copyInputsPerExecution();
            if (!context.provider().pushPattern(context.pattern(), oneExecution)) {
                break;
            }
            accepted++;
        }
        return PatternBatchResult.accepted(accepted);
    }
}
