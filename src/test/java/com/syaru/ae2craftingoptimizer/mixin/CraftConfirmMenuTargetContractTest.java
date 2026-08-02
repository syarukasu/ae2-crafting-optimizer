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

class CraftConfirmMenuTargetContractTest {
    private static final String TARGET_CLASS =
            "appeng.menu.me.crafting.CraftConfirmMenu";

    @Test
    void productionMenuExposesNamedBroadcastTarget() throws Exception {
        String resourceName = TARGET_CLASS.replace('.', '/') + ".class";
        InputStream classBytes =
                CraftConfirmMenuTargetContractTest.class
                        .getClassLoader()
                        .getResourceAsStream(resourceName);
        assertNotNull(classBytes, "AE2 CraftConfirmMenu is missing");

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

        // remap=falseのMixinは、実行時に存在する名前を直接指定する。
        assertTrue(methods.contains("broadcastChanges()V"));
    }
}
