package com.syaru.ae2craftingoptimizer.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

class MegaCellsBootstrapContractTest {
    private static final String PLATFORM = "gripe/_90/megacells/forge/ForgePlatform";
    private static final String CONTEXT = "net/minecraftforge/fml/javafmlmod/FMLJavaModLoadingContext";
    private static final String MIXIN = "com/syaru/ae2craftingoptimizer/mixin/MegaCellsForgePlatformContextMixin";

    @Test
    void installedMegaCellsHasExactlyTheRedirectedLookup() throws Exception {
        String jar = System.getProperty("aco.megaCellsJar");
        assumeTrue(jar != null, "Supply -PmegaCellsJar for the installed optional mod contract");
        try (var zip = new JarFile(jar)) {
            var entry = zip.getJarEntry(PLATFORM + ".class");
            assertNotNull(entry);
            var node = new ClassNode();
            try (var input = zip.getInputStream(entry)) {
                new ClassReader(input).accept(node, 0);
            }
            var clinit = node.methods.stream().filter(m -> m.name.equals("<clinit>"))
                    .findFirst().orElseThrow();
            var calls = Arrays.stream(clinit.instructions.toArray())
                    .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).toList();
            assertEquals(2, calls.size());
            assertEquals(Opcodes.INVOKESTATIC, calls.get(0).getOpcode());
            assertEquals(CONTEXT, calls.get(0).owner);
            assertEquals("get", calls.get(0).name);
            assertEquals("()L" + CONTEXT + ";", calls.get(0).desc);
            assertEquals("getModEventBus", calls.get(1).name);
            assertEquals(CONTEXT, calls.get(1).owner);
        }
    }

    @Test
    void optionalMixinOnlyRedirectsThatLookup() throws Exception {
        var node = new ClassNode();
        try (var input = getClass().getResourceAsStream("/" + MIXIN + ".class")) {
            assertNotNull(input);
            new ClassReader(input).accept(node, 0);
        }
        assertTrue(node.invisibleAnnotations.stream()
                .anyMatch(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/Pseudo;")));
        var mixin = node.invisibleAnnotations.stream()
                .filter(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;"))
                .findFirst().orElseThrow();
        assertTrue(mixin.values.toString().contains(PLATFORM.replace('/', '.')));
        var handler = node.methods.stream().filter(m -> m.name.equals("aco$ownedModContext"))
                .findFirst().orElseThrow();
        var redirect = handler.visibleAnnotations.stream()
                .filter(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/injection/Redirect;"))
                .findFirst().orElseThrow();
        assertEquals(java.util.List.of("<clinit>"), redirect.values.get(redirect.values.indexOf("method") + 1));
        var at = (org.objectweb.asm.tree.AnnotationNode) redirect.values.get(redirect.values.indexOf("at") + 1);
        assertEquals("L" + CONTEXT + ";get()L" + CONTEXT + ";",
                at.values.get(at.values.indexOf("target") + 1));
        assertEquals(0, redirect.values.get(redirect.values.indexOf("require") + 1));
        assertEquals(1, redirect.values.get(redirect.values.indexOf("allow") + 1));
        var calls = Arrays.stream(handler.instructions.toArray())
                .filter(MethodInsnNode.class::isInstance).map(MethodInsnNode.class::cast).toList();
        assertEquals(1, calls.size());
        assertEquals("com/syaru/ae2craftingoptimizer/integration/MegaCellsModContext", calls.get(0).owner);
        assertEquals("get", calls.get(0).name);
    }

    @Test
    void devClientAndServerLoadSamePlannerAsDistribution() throws Exception {
        String build = Files.readString(Path.of("build.gradle"));
        for (String run : java.util.List.of("client", "server")) {
            int start = build.indexOf("        " + run + " {");
            int next = build.indexOf("\n        }", start);
            assertTrue(start > 0 && next > start);
            String profile = build.substring(start, next);
            assertTrue(profile.contains("ae2vm_aco { source sourceSets.vmNative }"), run);
            assertTrue(profile.contains("args '--mixin', 'ae2vm-aco-native.mixins.json'"), run);
            assertTrue(profile.contains("args '--mixin', 'ae2_crafting_optimizer.mixins.json'"), run);
        }
    }
}
