package com.syaru.ae2craftingoptimizer.engine;

import java.util.Objects;
import java.util.function.LongSupplier;

public record PlanningGenerationSnapshot(
        long patternGeneration,
        long inventoryGeneration,
        long recipeGeneration) {
    public PlanningGenerationSnapshot {
        if (patternGeneration < 0L || inventoryGeneration < 0L || recipeGeneration < 0L) {
            throw new IllegalArgumentException("planning generations must not be negative");
        }
    }

    public PlanningGuard guard(
            LongSupplier currentPatternGeneration,
            LongSupplier currentInventoryGeneration,
            LongSupplier currentRecipeGeneration,
            PlanningCancellationToken cancellation) {
        Objects.requireNonNull(currentPatternGeneration, "currentPatternGeneration");
        Objects.requireNonNull(currentInventoryGeneration, "currentInventoryGeneration");
        Objects.requireNonNull(currentRecipeGeneration, "currentRecipeGeneration");
        Objects.requireNonNull(cancellation, "cancellation");
        return expandedRequests -> {
            cancellation.checkpoint(expandedRequests);
            if (!isCurrent(
                    currentPatternGeneration.getAsLong(),
                    currentInventoryGeneration.getAsLong(),
                    currentRecipeGeneration.getAsLong())) {
                throw new StalePlanningSnapshotException(this, expandedRequests);
            }
        };
    }

    public boolean isCurrent(long patterns, long inventory, long recipes) {
        return patternGeneration == patterns
                && inventoryGeneration == inventory
                && recipeGeneration == recipes;
    }
}
