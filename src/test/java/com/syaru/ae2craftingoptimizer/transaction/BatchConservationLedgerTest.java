package com.syaru.ae2craftingoptimizer.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BatchConservationLedgerTest {
    private static final Map<String, Long> INPUTS = Map.of(
            "item:iron", 64L,
            "fluid:water", 1_000L,
            "chemical:oxygen", 2_000L);
    private static final Map<String, Long> OUTPUTS = Map.of("item:result", 16L);

    @Test
    void exactMultiMediumTransactionCompletes() {
        UUID id = UUID.randomUUID();
        BatchConservationLedger<String> ledger = new BatchConservationLedger<>(id, 16L, INPUTS, OUTPUTS);

        ledger.sourceExtracted(INPUTS);
        ledger.targetAccepted(id, 16L, INPUTS);
        ledger.sourceAccounted(16L);
        ledger.outputsObserved(OUTPUTS);

        assertEquals(BatchConservationLedger.Phase.COMPLETE, ledger.phase());
    }

    @Test
    void partialTargetAcceptanceIsRejected() {
        UUID id = UUID.randomUUID();
        BatchConservationLedger<String> ledger = new BatchConservationLedger<>(id, 16L, INPUTS, OUTPUTS);
        ledger.sourceExtracted(INPUTS);

        assertThrows(IllegalStateException.class, () -> ledger.targetAccepted(id, 15L, INPUTS));
        assertEquals(BatchConservationLedger.Phase.SOURCE_OWNED, ledger.phase());
    }

    @Test
    void missingChemicalOwnershipIsRejected() {
        UUID id = UUID.randomUUID();
        BatchConservationLedger<String> ledger = new BatchConservationLedger<>(id, 16L, INPUTS, OUTPUTS);
        ledger.sourceExtracted(INPUTS);

        assertThrows(IllegalStateException.class, () -> ledger.targetAccepted(
                id, 16L, Map.of("item:iron", 64L, "fluid:water", 1_000L)));
    }

    @Test
    void extractedInputsCanBeRolledBackExactly() {
        BatchConservationLedger<String> ledger =
                new BatchConservationLedger<>(UUID.randomUUID(), 16L, INPUTS, OUTPUTS);
        ledger.sourceExtracted(INPUTS);
        ledger.rolledBack(INPUTS);
        assertEquals(BatchConservationLedger.Phase.ROLLED_BACK, ledger.phase());
    }

    @Test
    void acceptedInputsCannotBeRolledBackBySource() {
        UUID id = UUID.randomUUID();
        BatchConservationLedger<String> ledger = new BatchConservationLedger<>(id, 16L, INPUTS, OUTPUTS);
        ledger.sourceExtracted(INPUTS);
        ledger.targetAccepted(id, 16L, INPUTS);
        assertThrows(IllegalStateException.class, () -> ledger.rolledBack(INPUTS));
    }

    @Test
    void faultAfterExtractionCanBeQuarantinedWithoutClaimingCompletion() {
        BatchConservationLedger<String> ledger =
                new BatchConservationLedger<>(UUID.randomUUID(), 16L, INPUTS, OUTPUTS);
        ledger.sourceExtracted(INPUTS);
        ledger.quarantine();
        assertEquals(BatchConservationLedger.Phase.QUARANTINED, ledger.phase());
    }
}
