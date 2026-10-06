package com.syaru.ae2craftingoptimizer.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ae2vm.addon.compiler.PatternCompiler;
import com.ae2vm.addon.nativeengine.NativeVmHook;
import com.syaru.ae2vm.exact.ExactBytecode;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

class RetiredRuntimeCodeTest {
    @Test
    void nativeEntryDoesNotMaintainTheRetiredGraphCatalog() throws Exception {
        assertEquals(Set.of("vm$execute"), Arrays.stream(NativeVmHook.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName).collect(java.util.stream.Collectors.toSet()));
        try (var input = getClass().getClassLoader().getResourceAsStream(
                "com/ae2vm/addon/nativeengine/mixin/NativeCraftingServiceMixin.class")) {
            var node = new ClassNode();
            new ClassReader(input).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            assertFalse(node.fields.stream().anyMatch(field -> field.name.equals("vm$catalog")));
            assertFalse(node.methods.stream().anyMatch(method -> method.name.startsWith("vm$provider")));
            assertTrue(node.methods.stream().anyMatch(method -> method.name.equals("vm$calculate")));
        }
    }

    @Test
    void exactVmLibraryDoesNotShipComparisonGraphOrSolver() throws Exception {
        // #218: comparison fixtures must not pull their solver into the runtime library.
        var library = Path.of(ExactBytecode.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        try (var jar = new JarFile(library.toFile())) {
            assertFalse(jar.stream().anyMatch(entry ->
                    entry.getName().startsWith("com/syaru/ae2vm/exact/graph/")
                            || entry.getName().startsWith("de/uni_freiburg/informatik/ultimate/")));
        }
    }

    @Test
    void symbolicFacadeIsNotShipped() {
        assertNull(getClass().getClassLoader().getResource(
                "com/syaru/ae2craftingoptimizer/engine/SymbolicCraftingPlanner.class"));
    }

    @Test
    void retiredNeoEcoSelectorIsNotShipped() {
        assertNull(getClass().getClassLoader().getResource(
                "com/syaru/ae2craftingoptimizer/integration/NeoEcoVersionCompatibility.class"));
    }

    @Test
    void compilerDoesNotAllocateRetiredGlobalCollections() {
        Set<String> retired = Set.of("COMPILED_PATTERNS", "FUZZY_GROUPS",
                "PROCESSING_INPUT_KEYS", "EXACT_PROCESSING_KEYS");
        assertTrue(Arrays.stream(PatternCompiler.class.getDeclaredFields())
                .noneMatch(field -> retired.contains(field.getName())));
    }
}
