package com.syaru.ae2craftingoptimizer.optimization;

/** Stable, bounded reason codes for returning a calculation to AE2. */
public enum FallbackReasonCode {
    AMBIGUOUS_PRODUCER,
    MULTIPLE_PATHS,
    DYNAMIC_PATTERN,
    PROCESSING_BOUNDARY,
    TAG_SELECTION_UNPROVEN,
    CYCLE,
    /** ノード数または入力辺数が固定上限を超えた。 */
    PROGRAM_TOO_LARGE,
    /**
     * その世代のコンパイル済みGraphが揃わず、Root Programを取れなかった。
     *
     * <p>構成が曖昧なわけではないため、プレイヤーがPatternを直しても解けない。</p>
     */
    INCOMPLETE_GRAPH_SNAPSHOT,
    /** Root Programが無いのに具体的な理由も付いていなかった。 */
    NO_COMPILED_PROGRAM,
    GENERATION_CHANGED,
    INVENTORY_CHANGED,
    UNSUPPORTED_PATTERN,
    SHADOW_NOT_QUALIFIED,
    COUNT_OVERFLOW,
    CANCELLED,
    DISABLED,
    UNKNOWN
}
