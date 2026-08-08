package com.syaru.ae2craftingoptimizer.api.big;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.stacks.AEKey;
import com.syaru.ae2craftingoptimizer.engine.BigCraftingKeyCodec;
import java.math.BigInteger;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class BigCraftingHostRegistryLifecycleTest {
    private static final BigCraftingKeyCodec<AEKey> UNUSED_CODEC = new BigCraftingKeyCodec<>() {
        @Override
        public CompoundTag encode(AEKey key) {
            throw new UnsupportedOperationException("not used by registry lifecycle tests");
        }

        @Override
        public AEKey decode(CompoundTag tag) {
            throw new UnsupportedOperationException("not used by registry lifecycle tests");
        }
    };

    @AfterEach
    void clearRegistry() {
        BigCraftingHostRegistry.clear();
    }

    @Test
    void closeIsIdempotentAndReleasesTheOwner() {
        Object owner = new Object();
        BigCraftingHostRuntime<AEKey> host = host();
        BigCraftingHostRegistration registration = BigCraftingHostRegistry.register(owner, host);

        assertEquals(1, BigCraftingHostRegistry.registrationCount());
        assertEquals(1000, registration.snapshot(1, BigCraftingHostBackendState.ACTIVE)
                .available().intValue());
        registration.close();
        registration.close();

        assertTrue(registration.isClosed());
        assertTrue(host.isClosed());
        assertEquals(0, BigCraftingHostRegistry.registrationCount());
        assertTrue(BigCraftingHostRegistry.find(owner).isEmpty());
        assertThrows(IllegalStateException.class,
                () -> host.reserveExternal(UUID.randomUUID(), BigInteger.ONE));
    }

    @Test
    void anOldHandleCannotCloseAReplacementGeneration() {
        Object owner = new Object();
        BigCraftingHostRuntime<AEKey> oldHost = host();
        BigCraftingHostRuntime<AEKey> newHost = host();

        BigCraftingHostRegistration old = BigCraftingHostRegistry.register(owner, oldHost);
        BigCraftingHostRegistration current = BigCraftingHostRegistry.register(owner, newHost);

        assertTrue(old.isClosed());
        assertNotEquals(old.generation(), current.generation());
        old.close();
        assertSame(newHost, BigCraftingHostRegistry.find(owner).orElseThrow());
        assertFalse(newHost.isClosed());

        current.close();
        assertTrue(newHost.isClosed());
    }

    @Test
    void repeatedFormAndDestroyReturnsTheRegistryToZero() {
        Object owner = new Object();
        for (int i = 0; i < 1000; i++) {
            BigCraftingHostRegistration registration = BigCraftingHostRegistry.register(owner, host());
            registration.close();
        }
        assertEquals(0, BigCraftingHostRegistry.registrationCount());
    }

    @Test
    void snapshotDerivesAvailableAndMarksOvercommitAtomically() {
        BigCraftingHostSnapshot snapshot = BigCraftingHostSnapshot.of(
                UUID.randomUUID(),
                9,
                BigInteger.valueOf(100),
                BigInteger.valueOf(120),
                BigInteger.valueOf(60),
                BigInteger.valueOf(60),
                2,
                3,
                1,
                BigCraftingHostBackendState.DEGRADED);

        assertEquals(BigInteger.ZERO, snapshot.available());
        assertTrue(snapshot.overcommitted());
        assertEquals(9, snapshot.revision());
        assertEquals(2, snapshot.standardJobCount());
        assertEquals(BigCraftingHostBackendState.DEGRADED, snapshot.backendState());
        assertThrows(IllegalArgumentException.class, () -> BigCraftingHostSnapshot.of(
                UUID.randomUUID(), 1, BigInteger.TEN, BigInteger.ONE,
                BigInteger.TEN, BigInteger.ONE, 0, 0, 0,
                BigCraftingHostBackendState.ACTIVE));
    }

    private static BigCraftingHostRuntime<AEKey> host() {
        return new BigCraftingHostRuntime<>(
                BigInteger.valueOf(1000), UNUSED_CODEC, 256, 64, 64, 4L * 1024L * 1024L);
    }
}
