package com.syaru.ae2craftingoptimizer.engine.parallel;

/** 一件のPlanner Sessionが固定した、相互に混在させてはならない世代値。 */
public record ParallelRevisionVector(
        long storageRevision,
        long patternGeneration,
        long recipeGeneration,
        long configurationRevision,
        long runtimeIdentity) {
    public ParallelRevisionVector {
        if (storageRevision < 0L
                || patternGeneration < 0L
                || recipeGeneration < 0L
                || configurationRevision <= 0L
                || runtimeIdentity < 0L) {
            throw new IllegalArgumentException("parallel planner revisions are invalid");
        }
    }
}
