package com.syaru.ae2craftingoptimizer.api.big;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.syaru.ae2craftingoptimizer.engine.BigCountMath;
import com.syaru.ae2craftingoptimizer.engine.BigCraftingKeyCodec;
import com.syaru.ae2craftingoptimizer.engine.BigExactCraftingByteCounter;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.jar.JarFile;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.Opcodes;

/** Real ACO accounting and codecs; optional installed AQE bytecode contract, not a game launch. */
class AqeCpuCapacityContractTest {
    private static final int DEFAULT_DIGITS = 1024;
    private static final BigCraftingKeyCodec<String> STRINGS = new BigCraftingKeyCodec<>() {
        @Override public CompoundTag encode(String key) {
            CompoundTag tag = new CompoundTag();
            tag.putString("value", key);
            return tag;
        }
        @Override public String decode(CompoundTag tag) {
            return tag.getString("value");
        }
    };

    @Test void capacityProfilesMatchTheSuppliedAqeJar() throws Exception {
        String path = System.getProperty("aco.aqeCapacityJar", "");
        assumeTrue(!path.isBlank(), "Supply -PaqeCapacityJar for the installed AQE constant contract");
        Map<String, Object> constants = new HashMap<>();
        try (var jar = new JarFile(path)) {
            var entry = jar.getJarEntry("com/syaru/advancedquantumengineering/config/AQEConfig.class");
            assertNotNull(entry);
            try (var input = jar.getInputStream(entry)) {
                new ClassReader(input).accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override public FieldVisitor visitField(int access, String name, String descriptor,
                            String signature, Object value) {
                        if (value != null) constants.put(name, value);
                        return null;
                    }
                }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            }
        }
        assertEquals(DEFAULT_DIGITS, constants.get("DEFAULT_BIG_INTEGER_DECIMAL_DIGITS"));
        assertEquals(BigCountMath.HARD_MAXIMUM_DECIMAL_DIGITS,
                constants.get("MAX_EFFECTIVE_BIG_INTEGER_DECIMAL_DIGITS"));
        assertEquals(12, constants.get("BIG_INTEGER_STRUCTURE_HEADROOM_DECIMAL_DIGITS"));
    }

    @Test void exactPlanBytesFitAvailableCapacityButOneMoreByteCannotReserve() {
        for (BigInteger capacity : capacities()) {
            var host = host(capacity);
            UUID existing = UUID.randomUUID(), wide = UUID.randomUUID();
            BigInteger existingBytes = BigInteger.valueOf(Long.MAX_VALUE);
            assertTrue(host.reserveExternal(existing, existingBytes));
            BigInteger available = host.available();
            BigInteger required = BigExactCraftingByteCounter.calculate("item",
                    available.subtract(BigInteger.valueOf(8)), Map.of(), Map.of(),
                    key -> 8L, BigCountMath.HARD_MAXIMUM_BITS);
            assertEquals(available, required);
            assertTrue(host.reserveExternal(wide, required));
            assertEquals(BigInteger.ZERO, host.available());
            assertEquals(capacity, host.reserved());
            CompoundTag full = host.save();
            assertFalse(host.reserveExternal(UUID.randomUUID(), BigInteger.ONE));
            assertEquals(full, host.save(), "Rejected admission must not alter existing reservations");
            var restored = restore(full, capacity);
            assertEquals(BigInteger.ZERO, restored.available());
            assertEquals(capacity, restored.reserved());
            assertEquals(required, restored.releaseExternal(wide));
            assertEquals(BigInteger.ZERO, restored.releaseExternal(wide));
            assertEquals(existingBytes, restored.reserved());
            assertEquals(available, restored.available());
        }
    }

    @Test void currentPhysicalCapacityAfterReloadPreservesJobsAndBlocksOverbooking() {
        for (BigInteger capacity : capacities()) {
            var host = host(capacity);
            UUID job = UUID.randomUUID();
            assertTrue(host.reserveExternal(job, capacity));
            var restored = restore(host.save(), capacity.subtract(BigInteger.ONE));
            assertTrue(restored.isOvercommitted());
            assertEquals(BigInteger.ZERO, restored.available());
            assertEquals(capacity, restored.reserved());
            assertFalse(restored.reserveExternal(UUID.randomUUID(), BigInteger.ONE));
            restored.resizePhysicalCapacity(capacity);
            assertFalse(restored.isOvercommitted());
            assertEquals(capacity, restored.releaseExternal(job));
            assertEquals(capacity, restored.available());
        }
    }

    private static List<BigInteger> capacities() {
        return List.of(BigInteger.TEN.pow(DEFAULT_DIGITS).subtract(BigInteger.ONE),
                BigCountMath.hardMaximumValue());
    }

    private static BigCraftingHostRuntime<String> host(BigInteger capacity) {
        return new BigCraftingHostRuntime<>(capacity, STRINGS, BigCountMath.HARD_MAXIMUM_BITS,
                64, 64, 4L * 1024L * 1024L);
    }

    private static BigCraftingHostRuntime<String> restore(CompoundTag tag, BigInteger capacity) {
        return BigCraftingHostRuntime.load(tag, capacity, STRINGS, BigCountMath.HARD_MAXIMUM_BITS,
                64, 64, 4L * 1024L * 1024L);
    }
}
