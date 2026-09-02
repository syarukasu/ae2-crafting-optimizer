package com.syaru.ae2craftingoptimizer.engine.parallel;

import com.syaru.ae2craftingoptimizer.engine.RootProgramFailure;
import java.util.Objects;
import java.util.Optional;

/** 成功blueprintまたは正確な辞退理由のどちらか一方を返すsession結果。 */
public record ParallelPlanResult<K>(
        Optional<ParallelPlanBlueprint<K>> blueprint,
        ParallelPlanFailure failure,
        RootProgramFailure graphFailure,
        ParallelPlanMetrics metrics) {
    public ParallelPlanResult {
        Objects.requireNonNull(blueprint, "blueprint");
        Objects.requireNonNull(failure, "failure");
        Objects.requireNonNull(graphFailure, "graphFailure");
        Objects.requireNonNull(metrics, "metrics");
        if (blueprint.isPresent() != (failure == ParallelPlanFailure.NONE)) {
            throw new IllegalArgumentException("parallel result must contain a blueprint or a failure");
        }
    }

    static <K> ParallelPlanResult<K> completed(
            ParallelPlanBlueprint<K> blueprint,
            ParallelPlanMetrics metrics) {
        return new ParallelPlanResult<>(
                Optional.of(blueprint),
                ParallelPlanFailure.NONE,
                RootProgramFailure.NONE,
                metrics);
    }

    static <K> ParallelPlanResult<K> unsupported(
            RootProgramFailure graphFailure,
            ParallelPlanMetrics metrics) {
        return new ParallelPlanResult<>(
                Optional.empty(),
                ParallelPlanFailure.UNSUPPORTED_GRAPH,
                graphFailure,
                metrics);
    }

    static <K> ParallelPlanResult<K> rejected(ParallelPlanFailure failure) {
        if (failure == ParallelPlanFailure.NONE
                || failure == ParallelPlanFailure.UNSUPPORTED_GRAPH) {
            throw new IllegalArgumentException("queue/lifecycle failure is required");
        }
        return new ParallelPlanResult<>(
                Optional.empty(),
                failure,
                RootProgramFailure.NONE,
                new ParallelPlanMetrics(0, 0L, 0, 0, 0, 0, false, 0L, 0L, 0L));
    }
}
