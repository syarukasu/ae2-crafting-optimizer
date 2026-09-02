package com.syaru.ae2craftingoptimizer.engine.parallel;

/** MissingやCPU不足へ読み替えない、Parallel Planner自身の失敗分類。 */
public enum ParallelPlanFailure {
    NONE,
    UNSUPPORTED_GRAPH,
    QUEUE_FULL,
    CANCELLED,
    SHUTDOWN
}
