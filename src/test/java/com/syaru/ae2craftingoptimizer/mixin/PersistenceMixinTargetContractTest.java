package com.syaru.ae2craftingoptimizer.mixin;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

class PersistenceMixinTargetContractTest {
    /** Minecraft 1.21.1のAE2保存APIはRegistry Providerを必須引数にする。 */
    private static final String PERSISTENCE_DESCRIPTOR =
            "(Lnet/minecraft/nbt/CompoundTag;Lnet/minecraft/core/HolderLookup$Provider;)V";

    @Test
    void ae2PersistenceTargetsUseRegistryAwareSignature() throws Exception {
        assertPersistenceTargets("appeng.helpers.patternprovider.PatternProviderLogic");
        assertPersistenceTargets("appeng.crafting.execution.CraftingCpuLogic");
    }

    @Test
    void advancedAePersistenceTargetsUseRegistryAwareSignature() throws Exception {
        assertPersistenceTargets("net.pedroksl.advanced_ae.common.logic.AdvPatternProviderLogic");
        assertPersistenceTargets("net.pedroksl.advanced_ae.common.logic.AdvCraftingCPULogic");
    }

    private static void assertPersistenceTargets(String className) throws Exception {
        String resourceName = className.replace('.', '/') + ".class";
        InputStream classBytes =
                PersistenceMixinTargetContractTest.class
                        .getClassLoader()
                        .getResourceAsStream(resourceName);
        assertNotNull(classBytes, "persistence target is missing: " + className);

        Set<String> methods = new HashSet<>();
        try (InputStream input = classBytes) {
            new ClassReader(input)
                    .accept(
                            new ClassVisitor(Opcodes.ASM9) {
                                @Override
                                public MethodVisitor visitMethod(
                                        int access,
                                        String name,
                                        String descriptor,
                                        String signature,
                                        String[] exceptions) {
                                    methods.add(name + descriptor);
                                    return null;
                                }
                            },
                            ClassReader.SKIP_CODE
                                    | ClassReader.SKIP_DEBUG
                                    | ClassReader.SKIP_FRAMES);
        }

        assertTrue(
                methods.contains("writeToNBT" + PERSISTENCE_DESCRIPTOR),
                className + " does not expose the registry-aware write target");
        assertTrue(
                methods.contains("readFromNBT" + PERSISTENCE_DESCRIPTOR),
                className + " does not expose the registry-aware read target");
    }
}
