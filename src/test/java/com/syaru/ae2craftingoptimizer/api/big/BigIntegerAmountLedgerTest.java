package com.syaru.ae2craftingoptimizer.api.big;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.syaru.ae2craftingoptimizer.engine.BigCraftingKeyCodec;
import java.math.BigInteger;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

class BigIntegerAmountLedgerTest {
    private static final BigCraftingKeyCodec<String> CODEC = new BigCraftingKeyCodec<>() {
        @Override
        public CompoundTag encode(String key) {
            CompoundTag tag = new CompoundTag();
            tag.putString("key", key);
            return tag;
        }

        @Override
        public String decode(CompoundTag tag) {
            return tag.getString("key");
        }
    };

    @Test
    void drainsLongWindowsWithoutClampingTheRemainder() {
        BigIntegerAmountLedger<String> ledger = new BigIntegerAmountLedger<>(CODEC, 256);
        BigInteger amount = BigInteger.TEN.pow(40);
        ledger.add("output", amount);

        long firstWindow = ledger.drain("output", Long.MAX_VALUE);

        assertEquals(Long.MAX_VALUE, firstWindow);
        assertEquals(amount.subtract(BigInteger.valueOf(Long.MAX_VALUE)), ledger.get("output"));
    }

    @Test
    void persistsExactAmountsAsNbtAndRestoresThem() {
        BigIntegerAmountLedger<String> source = new BigIntegerAmountLedger<>(CODEC, 256);
        BigInteger amount = BigInteger.ONE.shiftLeft(200).add(BigInteger.valueOf(17));
        source.add("output", amount);

        BigIntegerAmountLedger<String> restored = new BigIntegerAmountLedger<>(CODEC, 256);
        restored.load(source.save());

        assertEquals(amount, restored.get("output"));
        assertTrue(restored.snapshot().containsKey("output"));
    }
}
