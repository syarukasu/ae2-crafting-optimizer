package com.ae2vm.addon.nativeengine;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class NativeVmSchedulingTest {
    @Test void schedulerConstructorBypassesLegacyCaptureBeforeItsConfigurationReads() throws Exception {
        var owner = com.syaru.ae2craftingoptimizer.mixin.CraftingCalculationDiagnosticsMixin.class;
        var type = new org.objectweb.asm.tree.ClassNode();
        try (var stream = owner.getResourceAsStream("/" + owner.getName().replace('.', '/') + ".class")) {
            assertNotNull(stream);
            new org.objectweb.asm.ClassReader(stream).accept(type, 0);
        }
        var method = type.methods.stream().filter(m -> m.name.equals("aco$captureStorageGenerationBeforeSnapshot")).findFirst().orElseThrow();
        boolean recognized = false, returned = false;
        for (var instruction : method.instructions) {
            if (instruction instanceof org.objectweb.asm.tree.TypeInsnNode test
                    && test.getOpcode() == org.objectweb.asm.Opcodes.INSTANCEOF
                    && test.desc.equals("com/ae2vm/addon/nativeengine/NativeVmCalculation")) recognized = true;
            if (recognized && instruction.getOpcode() == org.objectweb.asm.Opcodes.ARETURN) returned = true;
            if (instruction instanceof org.objectweb.asm.tree.MethodInsnNode call
                    && call.owner.equals("com/syaru/ae2craftingoptimizer/config/ACOConfig")) {
                assertTrue(returned, "VM construction must leave before legacy planner configuration/capture");
                return;
            }
        }
        fail("Missing constructor boundary/configuration probe");
    }

    @Test void nativeJobDoesNotInheritTheBlockingWholeCalculationTickHook() throws Exception {
        // #218: an inherited simulateFor waits for arithmetic as well as world access.
        assertEquals(boolean.class, NativeVmCalculation.class.getDeclaredMethod("simulateFor", int.class).getReturnType());
    }
}
