package com.syaru.ae2craftingoptimizer.api.batch;

import net.minecraft.resources.ResourceLocation;

/**
 * Accepts ownership of one or more complete processing-pattern executions.
 *
 * <p>The adapter contract is deliberately stricter than AE2's
 * {@code ICraftingProvider.pushPattern} return value:</p>
 *
 * <ul>
 *     <li>Returning zero must not mutate the target or retain any input.</li>
 *     <li>Returning N means exactly N complete executions were durably accepted.</li>
 *     <li>An insertion simulation or partial inventory insertion is not acceptance.</li>
 *     <li>The adapter must never report more than {@code maximumExecutions}.</li>
 * </ul>
 *
 * <p>ACO remains responsible for energy, crafting-task progress, and expected-output accounting.</p>
 */
public interface PatternBatchAdapter {
    ResourceLocation id();

    default int priority() {
        return 0;
    }

    /**
     * Native adapters should keep this false unless they preserve the provider's own multi-side
     * routing semantics. ACO will otherwise offer them only deterministic single-target contexts.
     */
    default boolean supportsMultipleProviderTargets() {
        return false;
    }

    boolean supports(PatternBatchContext context);

    /**
     * Narrows the number of executions ACO prepares before ownership transfer. Implementations
     * should expose inexpensive static or queue-capacity limits here to avoid extracting inputs
     * that will immediately be returned. The result must be between zero and {@code offeredExecutions}.
     */
    default long limitExecutions(PatternBatchContext context, long offeredExecutions) {
        return offeredExecutions;
    }

    PatternBatchResult commit(PatternBatchContext context, PatternBatchBudget budget);
}
