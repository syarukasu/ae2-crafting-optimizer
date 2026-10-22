package com.syaru.ae2craftingoptimizer.api.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ReceiptReservationProtocolTest {
    private static final ExactCountLimits LIMITS = ExactCountLimits.defaults();

    @Test
    void transitionsAreIdempotentAndDigestBound() {
        byte[] digest = {1, 2, 3};
        ReceiptReservation reservation = ReceiptReservationProtocol.reserve("tx-1", digest, LIMITS);
        reservation = ReceiptReservationProtocol.reserve(reservation, "tx-1", digest, LIMITS);
        reservation = ReceiptReservationProtocol.commitRunning(reservation, "tx-1", digest, LIMITS);
        reservation = ReceiptReservationProtocol.commitRunning(reservation, "tx-1", digest, LIMITS);
        reservation = ReceiptReservationProtocol.markOutputReady(reservation, "tx-1", digest, LIMITS);
        reservation = ReceiptReservationProtocol.acknowledge(reservation, "tx-1", digest, LIMITS);
        reservation = ReceiptReservationProtocol.acknowledge(reservation, "tx-1", digest, LIMITS);
        assertEquals(ReceiptReservationState.ACKNOWLEDGED, reservation.state());
        ReceiptReservation acknowledged = reservation;
        reservation = ReceiptReservationProtocol.forget(acknowledged, "tx-1", digest, LIMITS);
        assertEquals(ReceiptReservationState.FORGOTTEN, reservation.state());
        ReceiptReservation forgotten = reservation;
        assertThrows(IllegalStateException.class,
                () -> ReceiptReservationProtocol.reserve(forgotten, "tx-1", digest, LIMITS));
        assertThrows(IllegalArgumentException.class,
                () -> ReceiptReservationProtocol.acknowledge(
                        acknowledged, "tx-1", new byte[] {9}, LIMITS));
    }

    @Test
    void unknownOrActiveReceiptsCannotBeForgotten() {
        ReceiptReservation reservation = ReceiptReservationProtocol.reserve("tx-2", new byte[0], LIMITS);
        ReceiptReservation active = reservation;
        assertThrows(IllegalStateException.class,
                () -> ReceiptReservationProtocol.forget(active, "tx-2", new byte[0], LIMITS));
        reservation = ReceiptReservationProtocol.quarantine(reservation, "tx-2", new byte[0], LIMITS);
        assertEquals(ReceiptReservationState.QUARANTINED, reservation.state());
    }
}
