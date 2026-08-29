package com.syaru.ae2craftingoptimizer.optimization;

/** Stable, bounded reason codes for returning a calculation to AE2. */
public enum FallbackReasonCode {
    NO_COMPILED_PROGRAM,
    INCOMPLETE_GRAPH_SNAPSHOT,
    AMBIGUOUS_PRODUCER,
    MULTIPLE_PATHS,
    DYNAMIC_PATTERN,
    PROCESSING_BOUNDARY,
    TAG_SELECTION_UNPROVEN,
    CYCLE,
    GENERATION_CHANGED,
    INVENTORY_CHANGED,
    UNSUPPORTED_PATTERN,
    SHADOW_NOT_QUALIFIED,
    COUNT_OVERFLOW,
    PROGRAM_TOO_LARGE,
    CANCELLED,
    DISABLED,
    UNKNOWN
}
