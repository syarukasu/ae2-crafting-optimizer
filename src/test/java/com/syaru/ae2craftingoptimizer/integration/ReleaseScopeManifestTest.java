package com.syaru.ae2craftingoptimizer.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** 1.5.21を基準にしたパッチ版の自動回帰範囲が欠落しないことを検証します。 */
class ReleaseScopeManifestTest {
    private static final Path PROJECT_ROOT = Path.of("").toAbsolutePath().normalize();
    private static final Path SCOPE = PROJECT_ROOT.resolve("docs/issues/RELEASE_SCOPE_1.5.24.tsv");
    private static final Set<Integer> REQUIRED_ISSUES = Set.of(102, 103, 109, 115, 123);

    @Test
    void preservesBaselineAndAllPatchReleaseContracts() throws IOException {
        List<String> lines = Files.readAllLines(SCOPE, StandardCharsets.UTF_8);

        assertTrue(lines.contains("# release=1.5.24"), "対象リリースが一致しません");
        assertTrue(lines.contains("# baseline=1.5.21"), "最低動作基準は1.5.21である必要があります");

        Set<Integer> actualIssues = new HashSet<>();
        List<String> data = lines.stream()
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .toList();
        assertEquals("issue\tchange\tautomated_guarantee", data.get(0), "TSVヘッダーが一致しません");

        // 各行が、対象Issue、変更理由、自動保証を一つずつ持つことを確認します。
        for (String line : data.subList(1, data.size())) {
            String[] columns = line.split("\t", -1);
            assertEquals(3, columns.length, "リリース範囲は3列である必要があります: " + line);
            int issue = Integer.parseInt(columns[0]);
            assertTrue(actualIssues.add(issue), "Issueが重複しています: #" + issue);
            assertFalse(columns[1].isBlank(), "変更理由が空です: #" + issue);
            assertFalse(columns[2].isBlank(), "自動保証が空です: #" + issue);
        }

        assertEquals(REQUIRED_ISSUES, actualIssues, "1.5.21から1.5.24までの保証対象が一致しません");
    }
}
