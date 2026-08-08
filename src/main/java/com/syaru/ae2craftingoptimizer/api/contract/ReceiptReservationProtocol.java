package com.syaru.ae2craftingoptimizer.api.contract;

import java.util.Objects;

/**
 * Pure, idempotent reservation transition contract. AAC may implement durable storage around
 * these transitions without exposing its internal ledger to ACO.
 */
public final class ReceiptReservationProtocol {
    private ReceiptReservationProtocol() {
    }

    public static ReceiptReservation reserve(
            String transactionId,
            byte[] payloadDigest,
            ExactCountLimits limits) {
        validateIdentity(transactionId, payloadDigest, limits);
        return new ReceiptReservation(transactionId, payloadDigest, ReceiptReservationState.RESERVED);
    }

    public static ReceiptReservation reserve(
            ReceiptReservation current,
            String transactionId,
            byte[] payloadDigest,
            ExactCountLimits limits) {
        if (current == null) {
            return reserve(transactionId, payloadDigest, limits);
        }
        requireMatch(current, transactionId, payloadDigest, limits);
        if (current.state() == ReceiptReservationState.FORGOTTEN
                || current.state() == ReceiptReservationState.QUARANTINED) {
            throw new IllegalStateException("cannot reserve a terminal or quarantined receipt");
        }
        return current;
    }

    public static ReceiptReservation commitRunning(
            ReceiptReservation current,
            String transactionId,
            byte[] payloadDigest,
            ExactCountLimits limits) {
        requireMatch(current, transactionId, payloadDigest, limits);
        return switch (current.state()) {
            case RESERVED -> current.withState(ReceiptReservationState.RUNNING);
            case RUNNING -> current;
            default -> throw invalidTransition(current, ReceiptReservationState.RUNNING);
        };
    }

    public static ReceiptReservation markOutputReady(
            ReceiptReservation current,
            String transactionId,
            byte[] payloadDigest,
            ExactCountLimits limits) {
        requireMatch(current, transactionId, payloadDigest, limits);
        return switch (current.state()) {
            case RUNNING -> current.withState(ReceiptReservationState.OUTPUT_READY);
            case OUTPUT_READY -> current;
            default -> throw invalidTransition(current, ReceiptReservationState.OUTPUT_READY);
        };
    }

    public static ReceiptReservation cancelReservation(
            ReceiptReservation current,
            String transactionId,
            byte[] payloadDigest,
            ExactCountLimits limits) {
        requireMatch(current, transactionId, payloadDigest, limits);
        return switch (current.state()) {
            case RESERVED, RUNNING -> current.withState(ReceiptReservationState.CANCELLED);
            case CANCELLED -> current;
            default -> throw invalidTransition(current, ReceiptReservationState.CANCELLED);
        };
    }

    public static ReceiptReservation acknowledge(
            ReceiptReservation current,
            String transactionId,
            byte[] payloadDigest,
            ExactCountLimits limits) {
        requireMatch(current, transactionId, payloadDigest, limits);
        return switch (current.state()) {
            case OUTPUT_READY -> current.withState(ReceiptReservationState.ACKNOWLEDGED);
            case ACKNOWLEDGED -> current;
            default -> throw invalidTransition(current, ReceiptReservationState.ACKNOWLEDGED);
        };
    }

    public static ReceiptReservation forget(
            ReceiptReservation current,
            String transactionId,
            byte[] payloadDigest,
            ExactCountLimits limits) {
        requireMatch(current, transactionId, payloadDigest, limits);
        return switch (current.state()) {
            case ACKNOWLEDGED, CANCELLED -> current.withState(ReceiptReservationState.FORGOTTEN);
            case FORGOTTEN -> current;
            default -> throw invalidTransition(current, ReceiptReservationState.FORGOTTEN);
        };
    }

    public static ReceiptReservation quarantine(
            ReceiptReservation current,
            String transactionId,
            byte[] payloadDigest,
            ExactCountLimits limits) {
        requireMatch(current, transactionId, payloadDigest, limits);
        if (current.state() == ReceiptReservationState.FORGOTTEN) {
            throw invalidTransition(current, ReceiptReservationState.QUARANTINED);
        }
        if (current.state() == ReceiptReservationState.QUARANTINED) {
            return current;
        }
        return current.withState(ReceiptReservationState.QUARANTINED);
    }

    private static void validateIdentity(String transactionId, byte[] payloadDigest, ExactCountLimits limits) {
        Objects.requireNonNull(limits, "limits").validateRequiredIdentifier(transactionId);
        limits.validateDigest(payloadDigest);
    }

    private static void requireMatch(
            ReceiptReservation current,
            String transactionId,
            byte[] payloadDigest,
            ExactCountLimits limits) {
        Objects.requireNonNull(current, "current");
        validateIdentity(transactionId, payloadDigest, limits);
        if (!current.matches(transactionId, payloadDigest)) {
            throw new IllegalArgumentException("receipt transaction or payload digest mismatch");
        }
    }

    private static IllegalStateException invalidTransition(
            ReceiptReservation current,
            ReceiptReservationState target) {
        return new IllegalStateException(
                "invalid receipt transition from " + current.state() + " to " + target);
    }
}
