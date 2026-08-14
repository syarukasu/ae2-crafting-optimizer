package com.syaru.ae2craftingoptimizer.mixin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** 公開Neo ECO JARの実バイトコードに、版別Mixinの対象記述子が存在することを検証する。 */
class NeoEcoRuntimeMethodContractTest {
    private static final String TARGET_CLASS =
            "cn/dancingsnow/neoecoae/api/me/ECOCraftingCPULogic.class";
    private static final String TICK_DESCRIPTOR =
            "tickCraftingLogic(Lappeng/api/networking/energy/IEnergyService;Lappeng/me/service/CraftingService;)V";
    private static final String OPERATION_LIMIT_DESCRIPTOR = "getOperationLimit()I";
    private static final String FAST_PATH_LIMIT_DESCRIPTOR = "effectiveFastPathTickLimit()I";
    private static final String EXECUTE_20_3_DESCRIPTOR =
            "executeCrafting(IILappeng/me/service/CraftingService;Lappeng/api/networking/energy/IEnergyService;"
                    + "Lnet/minecraft/world/level/Level;"
                    + "Lcn/dancingsnow/neoecoae/api/me/ECOCraftingCPULogic$FastPathBatchBudget;)I";
    private static final String EXECUTE_20_4_DESCRIPTOR =
            "executeCrafting(ILappeng/me/service/CraftingService;Lappeng/api/networking/energy/IEnergyService;"
                    + "Lnet/minecraft/world/level/Level;)I";

    @Test
    void neoEco20_3ExposesOnlyTheOldExecutionContract() throws IOException {
        Path jarPath = configuredJar("aco.neoEco20_3Jar");
        assumeTrue(jarPath != null, "Neo ECO 20.3 contract JAR was not supplied");

        Set<String> methods = readMethods(jarPath);
        assertTrue(methods.contains(TICK_DESCRIPTOR));
        assertTrue(methods.contains(OPERATION_LIMIT_DESCRIPTOR));
        assertTrue(methods.contains(FAST_PATH_LIMIT_DESCRIPTOR));
        assertTrue(methods.contains(EXECUTE_20_3_DESCRIPTOR));
        assertFalse(methods.contains(EXECUTE_20_4_DESCRIPTOR));
    }

    @Test
    void neoEco20_4ExposesOnlyTheNewExecutionContract() throws IOException {
        Path jarPath = configuredJar("aco.neoEco20_4Jar");
        assumeTrue(jarPath != null, "Neo ECO 20.4 contract JAR was not supplied");

        Set<String> methods = readMethods(jarPath);
        assertTrue(methods.contains(TICK_DESCRIPTOR));
        assertTrue(methods.contains(OPERATION_LIMIT_DESCRIPTOR));
        assertFalse(methods.contains(FAST_PATH_LIMIT_DESCRIPTOR));
        assertFalse(methods.contains(EXECUTE_20_3_DESCRIPTOR));
        assertTrue(methods.contains(EXECUTE_20_4_DESCRIPTOR));
    }

    private static Path configuredJar(String propertyName) {
        String configuredPath = System.getProperty(propertyName);
        // 通常の開発ビルドでは任意契約JARが未指定でもよく、CIとリリース検証では必ず指定する。
        if (configuredPath == null || configuredPath.isBlank()) {
            return null;
        }
        Path jarPath = Path.of(configuredPath);
        assertTrue(Files.isRegularFile(jarPath), "Neo ECO contract JAR is missing: " + jarPath);
        return jarPath;
    }

    private static Set<String> readMethods(Path jarPath) throws IOException {
        try (JarFile jarFile = new JarFile(jarPath.toFile())) {
            JarEntry entry = jarFile.getJarEntry(TARGET_CLASS);
            assertNotNull(entry, "Neo ECO CPU class is missing from " + jarPath);
            try (InputStream input = jarFile.getInputStream(entry)) {
                Set<String> methods = new HashSet<>();
                ClassReader reader = new ClassReader(input);
                reader.accept(new ClassVisitor(Opcodes.ASM9) {
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
                }, ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                return methods;
            }
        }
    }
}
