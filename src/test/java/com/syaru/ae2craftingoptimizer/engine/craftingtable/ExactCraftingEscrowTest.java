package com.syaru.ae2craftingoptimizer.engine.craftingtable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.syaru.ae2craftingoptimizer.api.contract.ExactCountLimits;
import com.syaru.ae2craftingoptimizer.api.contract.ReceiptReservation;
import com.syaru.ae2craftingoptimizer.api.contract.ReceiptReservationProtocol;
import com.syaru.ae2craftingoptimizer.api.contract.ReceiptReservationState;
import java.math.BigInteger;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ExactCraftingEscrowTest {
    @Test
    void parentRecipeWaitsForThePhysicalChildOutput() {
        ExactCraftingEscrow<String> escrow =
                new ExactCraftingEscrow<>(
                        Map.of(
                                "raw",
                                BigInteger.TEN));

        escrow.debitExact(
                Map.of(
                        "raw",
                        BigInteger.TEN));
        // 子Threadの出力Receiptを反映する前は、親段の入力を予約できない。
        assertFalse(
                escrow.containsAll(
                        Map.of(
                                "middle",
                                BigInteger.TEN)));

        escrow.credit(
                Map.of(
                        "middle",
                        BigInteger.TEN));
        // 実出力をEscrowへ入れた後だけ、親段が実行可能になる。
        assertTrue(
                escrow.containsAll(
                        Map.of(
                                "middle",
                                BigInteger.TEN)));
    }

    @Test
    void cancelledInflightRecipeRestoresItsReservedInputsExactlyOnce() {
        BigInteger amount =
                BigInteger.TEN.pow(
                        64)
                        .subtract(
                                BigInteger.ONE);
        ExactCraftingEscrow<String> escrow =
                new ExactCraftingEscrow<>(
                        Map.of(
                                "raw",
                                amount));
        Map<String, BigInteger> reservation =
                Map.of(
                        "raw",
                        amount);
        byte[] digest = {1, 2, 5};
        ExactCountLimits limits = ExactCountLimits.defaults();
        ReceiptReservation receipt = ReceiptReservationProtocol.reserve(
                "issue-125-cancel",
                digest,
                limits);

        escrow.debitExact(
                reservation);
        receipt = ReceiptReservationProtocol.commitRunning(
                receipt,
                "issue-125-cancel",
                digest,
                limits);
        receipt = ReceiptReservationProtocol.cancelReservation(
                receipt,
                "issue-125-cancel",
                digest,
                limits);
        escrow.credit(
                reservation);
        receipt = ReceiptReservationProtocol.forget(
                receipt,
                "issue-125-cancel",
                digest,
                limits);

        assertEquals(
                Map.of(
                        "raw",
                        amount),
                escrow.snapshot());
        assertEquals(ReceiptReservationState.FORGOTTEN, receipt.state());
    }

    @Test
    void twentyStagesUseTwentyLedgerStepsRegardlessOfOrderMagnitude() {
        int physicalStages =
                20;
        assertEquals(
                physicalStages,
                executeLinearTree(
                        BigInteger.ONE,
                        physicalStages));
        assertEquals(
                physicalStages,
                executeLinearTree(
                        BigInteger.TEN.pow(
                                64)
                                .subtract(
                                        BigInteger.ONE),
                        physicalStages));
    }

    private static int executeLinearTree(
            BigInteger amount,
            int physicalStages) {
        ExactCraftingEscrow<String> escrow =
                new ExactCraftingEscrow<>(
                        Map.of(
                                "stage_"
                                        + physicalStages,
                                amount));
        int executed =
                0;
        // 数量ではなく依存段数だけを一巡し、各段の実出力を次段へ渡す。
        for (int stage = physicalStages;
                stage > 0;
                stage--) {
            String input =
                    "stage_"
                            + stage;
            String output =
                    "stage_"
                            + (stage - 1);
            Map<String, BigInteger> required =
                    Map.of(
                            input,
                            amount);
            // 一段でも入力が欠けた場合は、テストを失敗させる。
            assertTrue(
                    escrow.containsAll(
                            required));
            escrow.debitExact(
                    required);
            escrow.credit(
                    Map.of(
                            output,
                            amount));
            executed++;
        }
        assertEquals(
                Map.of(
                        "stage_0",
                        amount),
                escrow.snapshot());
        return executed;
    }
}
