package com.syaru.ae2craftingoptimizer.issue125;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

/** Issue #151: 正常動作基準に指定されたPR #127の実行責務を無言で変更させない。 */
class Pr127StableBaselineSourceTest {
    private static final Path MAIN = Path.of("src", "main", "java");
    private static final Path TEST = Path.of("src", "test", "java");

    @Test
    void preservesPr127PhysicalExecutionSourcesByteForByte() throws IOException {
        // 各hashはPR #127 HEAD df01aaeの実ファイルを正本として固定しています。
        assertHash(
                MAIN.resolve("com/syaru/ae2craftingoptimizer/engine/craftingtable/PhysicalCraftingTreeTransaction.java"),
                "44C4390CF8BC3FCAA0E81E9635DE3EDD447C95FCE6B7591B7EF9029C50BB5AD0");
        assertHash(
                MAIN.resolve("com/syaru/ae2craftingoptimizer/engine/craftingtable/CraftingTableBatchTargetResolver.java"),
                "F5E997AE908601395EE8C43B12B5DA37E9CE4DA6D604ECF5F195CB3A326C4690");
        assertHash(
                MAIN.resolve("com/syaru/ae2craftingoptimizer/integration/Ae2BigCraftingExecutionManager.java"),
                "DCC16D9573E59184B7FC9DCB4A7BAF89C480D3E258770B588C98C9488EC77D40");
        assertHash(
                TEST.resolve("com/syaru/ae2craftingoptimizer/issue125/Issue125RegressionSourceTest.java"),
                "7ED3F0CA8C737A6834470452686EE29E836710B75840C8EEF4721A82DC5D3172");
    }

    private static void assertHash(Path path, String expected) throws IOException {
        assertEquals(expected, sha256(path), "PR #127安定基準が変更されています: " + path);
    }

    private static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().withUpperCase().formatHex(digest.digest(Files.readAllBytes(path)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Java runtime does not provide SHA-256", exception);
        }
    }
}
