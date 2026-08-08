package com.syaru.ae2craftingoptimizer.api.contract;

import java.util.Arrays;
import java.util.Objects;

/** Immutable reservation state. It never owns an item, fluid, or machine object. */
public final class ReceiptReservation {
    private final String transactionId;
    private final byte[] payloadDigest;
    private final ReceiptReservationState state;

    ReceiptReservation(String transactionId, byte[] payloadDigest, ReceiptReservationState state) {
        this.transactionId = transactionId;
        this.payloadDigest = payloadDigest.clone();
        this.state = Objects.requireNonNull(state, "state");
    }

    public String transactionId() {
        return transactionId;
    }

    public byte[] payloadDigest() {
        return payloadDigest.clone();
    }

    public ReceiptReservationState state() {
        return state;
    }

    boolean matches(String transactionId, byte[] payloadDigest) {
        return this.transactionId.equals(transactionId)
                && Arrays.equals(this.payloadDigest, payloadDigest);
    }

    ReceiptReservation withState(ReceiptReservationState nextState) {
        return new ReceiptReservation(transactionId, payloadDigest, nextState);
    }
}
