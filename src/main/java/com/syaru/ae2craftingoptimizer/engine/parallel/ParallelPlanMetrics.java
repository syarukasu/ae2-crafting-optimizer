package com.syaru.ae2craftingoptimizer.engine.parallel;

/** 一件のsessionで実測したGraph、数量pass、worker利用状況。 */
public record ParallelPlanMetrics(
        int graphExpandedNodes,
        long duplicateExpansionsPrevented,
        int graphWorkersUsed,
        int amountProcessedNodes,
        int amountWorkersUsed,
        int maximumActiveWorkers,
        boolean promotedFromLong,
        long graphNanos,
        long amountNanos,
        long queueWaitNanos) {
    static ParallelPlanMetrics graphOnly(
            ParallelPlanGraph.BuildMetrics graph,
            long queueWaitNanos) {
        return new ParallelPlanMetrics(
                graph.expandedNodes(),
                graph.duplicateDiscoveries(),
                graph.workersUsed(),
                0,
                0,
                graph.maximumActiveWorkers(),
                false,
                graph.elapsedNanos(),
                0L,
                queueWaitNanos);
    }

    static ParallelPlanMetrics completed(
            ParallelPlanGraph.BuildMetrics graph,
            ParallelAmountPlanner.AmountOutcome<?> amount,
            long queueWaitNanos) {
        return new ParallelPlanMetrics(
                graph.expandedNodes(),
                graph.duplicateDiscoveries(),
                graph.workersUsed(),
                amount.metrics().processedNodes(),
                amount.metrics().workersUsed(),
                Math.max(
                        graph.maximumActiveWorkers(),
                        amount.metrics().maximumActiveWorkers()),
                amount.promotedFromLong(),
                graph.elapsedNanos(),
                amount.metrics().elapsedNanos(),
                queueWaitNanos);
    }
}
