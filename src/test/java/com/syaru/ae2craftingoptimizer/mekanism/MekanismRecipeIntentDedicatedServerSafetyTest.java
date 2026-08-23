package com.syaru.ae2craftingoptimizer.mekanism;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Issue #140で発生したDedicated Server上のclient-only型解決スパムを固定する。 */
class MekanismRecipeIntentDedicatedServerSafetyTest {
    private static final Path SOURCE = Path.of(
            "src/main/java/com/syaru/ae2craftingoptimizer/mekanism/MekanismRecipeIntentFastPath.java");

    @Test
    void inputHandlerNameGatePrecedesFieldTypeResolution() throws IOException {
        String source = Files.readString(SOURCE, StandardCharsets.UTF_8);
        int methodStart = source.indexOf("private static List<InputFieldAccessor> buildInputFieldAccessors");
        int methodEnd = source.indexOf("private static void addInputHandler", methodStart);
        String method = source.substring(methodStart, methodEnd);

        int nameGate = method.indexOf("if (!lowerName.contains(\"inputhandler\"))");
        int typeResolution = method.indexOf("field.getType().isArray()");

        assertTrue(nameGate >= 0, "入力ハンドラー名による事前選別が必要です");
        assertTrue(typeResolution >= 0, "配列入力ハンドラーの判定は維持します");
        assertTrue(
                nameGate < typeResolution,
                "Dedicated Serverでは無関係フィールドの型を解決する前に名前で除外する必要があります");
    }
}
