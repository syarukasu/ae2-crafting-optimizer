package com.syaru.ae2craftingoptimizer.api;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.signature.SignatureReader;
import org.objectweb.asm.signature.SignatureVisitor;
import org.objectweb.asm.tree.ClassNode;

/** #218: freeze compiled API descriptors, not merely source-file existence. */
class PublicApiBinaryCompatibilityTest {
    private static final String PREFIX = "com/syaru/ae2craftingoptimizer/";
    private static final int EXPORTED = Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED;
    private static final int FLAGS = EXPORTED | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL
            | Opcodes.ACC_ABSTRACT | Opcodes.ACC_INTERFACE | Opcodes.ACC_ENUM;
    private static final Path BASELINE = Path.of("src/test/resources/api/aco-v3-public-abi.tsv");

    @Test void publicApiAndTransitiveValueTypesRemainBinaryCompatible() throws Exception {
        var actual = exportedSurface(Path.of("build/classes/java/main"));
        assertTrue(actual.size() > 500, "API scan must not silently inspect an empty artifact");
        if (Boolean.getBoolean("aco.captureApiBaseline")) {
            assertFalse(Files.exists(BASELINE), "Never overwrite an established API baseline");
            Files.createDirectories(BASELINE.getParent());
            Files.write(BASELINE, actual, StandardCharsets.UTF_8);
        }
        assertTrue(Files.isRegularFile(BASELINE), "Missing reviewed API baseline");
        var removed = new TreeSet<>(Files.readAllLines(BASELINE, StandardCharsets.UTF_8));
        removed.removeAll(actual);
        assertEquals(Set.of(), removed, "Public ABI removed or changed");
    }

    private static Set<String> exportedSurface(Path root) throws Exception {
        var classes = new HashMap<String, ClassNode>();
        try (var paths = Files.walk(root.resolve(PREFIX))) {
            for (var path : paths.filter(p -> p.toString().endsWith(".class")).toList()) {
                var node = new ClassNode();
                new ClassReader(Files.readAllBytes(path)).accept(node,
                        ClassReader.SKIP_CODE | ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
                classes.put(node.name, node);
            }
        }
        var pending = new ArrayDeque<String>();
        classes.values().stream().filter(c -> c.name.startsWith(PREFIX + "api/")
                && (c.access & Opcodes.ACC_PUBLIC) != 0).map(c -> c.name).sorted().forEach(pending::add);
        var visited = new TreeSet<String>();
        var result = new TreeSet<String>();
        java.util.function.Consumer<String> enqueue = name -> {
            if (name != null && name.startsWith(PREFIX)) pending.add(name);
        };
        while (!pending.isEmpty()) {
            var name = pending.remove();
            if (!visited.add(name)) continue;
            var node = classes.get(name);
            assertNotNull(node, "Public API references a missing local type: " + name);
            result.add("C\t" + name + "\t" + (node.access & FLAGS) + "\t" + node.superName
                    + "\t" + String.join(",", node.interfaces) + "\t" + node.signature);
            enqueue.accept(node.superName);
            node.interfaces.forEach(enqueue);
            signature(node.signature, false, enqueue);
            for (var field : node.fields) {
                if ((field.access & EXPORTED) == 0) continue;
                result.add("F\t" + name + "\t" + field.name + "\t" + field.desc + "\t"
                        + (field.access & FLAGS) + "\t" + field.signature + "\t" + field.value);
                referenced(Type.getType(field.desc), enqueue);
                signature(field.signature, true, enqueue);
            }
            for (var method : node.methods) {
                if ((method.access & EXPORTED) == 0) continue;
                result.add("M\t" + name + "\t" + method.name + "\t" + method.desc + "\t"
                        + (method.access & FLAGS) + "\t" + method.signature);
                referenced(Type.getReturnType(method.desc), enqueue);
                for (var type : Type.getArgumentTypes(method.desc)) referenced(type, enqueue);
                signature(method.signature, false, enqueue);
            }
        }
        return result;
    }

    private static void referenced(Type type, java.util.function.Consumer<String> found) {
        if (type.getSort() == Type.ARRAY) referenced(type.getElementType(), found);
        else if (type.getSort() == Type.OBJECT) found.accept(type.getInternalName());
    }

    private static void signature(String value, boolean type, java.util.function.Consumer<String> found) {
        if (value == null) return;
        var visitor = new SignatureVisitor(Opcodes.ASM9) {
            @Override public void visitClassType(String name) { found.accept(name); }
        };
        if (type) new SignatureReader(value).acceptType(visitor);
        else new SignatureReader(value).accept(visitor);
    }
}
