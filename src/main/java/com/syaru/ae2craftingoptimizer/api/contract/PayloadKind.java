package com.syaru.ae2craftingoptimizer.api.contract;

/** Payload owners that must share the exact-count validator and codec. */
public enum PayloadKind {
    REQUEST(1),
    VECTOR_PLAN(2),
    HOST(3),
    JOURNAL(4),
    RECEIPT(5);

    private final int wireId;

    PayloadKind(int wireId) {
        this.wireId = wireId;
    }

    int wireId() {
        return wireId;
    }

    static PayloadKind fromWireId(int wireId) {
        for (PayloadKind kind : values()) {
            if (kind.wireId == wireId) {
                return kind;
            }
        }
        throw new IllegalArgumentException("unknown exact-count payload kind " + wireId);
    }
}
