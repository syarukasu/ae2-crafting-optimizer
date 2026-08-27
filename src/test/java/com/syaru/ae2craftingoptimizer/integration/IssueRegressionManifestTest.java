package com.syaru.ae2craftingoptimizer.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Issueと回帰試験の対応表が、欠落や存在しない証拠を含まないことを検証します。 */
class IssueRegressionManifestTest {
    private static final Path PROJECT_ROOT = Path.of("").toAbsolutePath().normalize();
    private static final Path MANIFEST = PROJECT_ROOT.resolve("docs/issues/REGRESSION_MATRIX.tsv");
    private static final String HEADER =
            "issue\tkind\tloaders\trequired\tautomated_evidence\truntime_status\truntime_evidence\tsummary";

    private static final Set<Integer> EXPECTED_ISSUES = Set.of(
            1, 3, 5, 7, 8, 9, 10, 11, 12, 13, 14, 28, 29, 30, 32, 35, 37, 39, 42, 44, 46, 51, 53,
            55, 58, 61, 64, 71, 74, 75, 77, 79, 84, 87, 90, 93, 98, 101, 102, 103, 109, 115, 118,
            119, 120, 123, 125, 129, 140, 145, 148, 153, 156, 161);
    private static final Set<String> KINDS =
            Set.of("COMPATIBILITY", "REGRESSION", "FEATURE", "PERFORMANCE", "RELEASE", "ROADMAP", "DOCUMENTATION", "ARCHITECTURE");
    private static final Set<String> LOADERS = Set.of("FORGE_1_20_1", "NEOFORGE_1_21_1", "BOTH");
    private static final Set<String> REQUIRED_LEVELS = Set.of("STATIC", "UNIT", "GAMETEST", "RUNTIME");
    private static final Set<String> RUNTIME_STATUSES = Set.of("NOT_REQUIRED", "VERIFIED", "PENDING");

    @Test
    void registersEveryKnownIssueWithValidEvidence() throws IOException {
        Manifest manifest = readManifest();

        assertEquals(161, manifest.synchronizedThroughIssue(), "同期済みIssue番号が古くなっています");
        assertEquals(HEADER, manifest.header(), "TSVの列定義が変わっています");
        assertEquals(EXPECTED_ISSUES.size(), manifest.rows().size(), "Issue行数が一致しません");

        Set<Integer> actualIssues = new LinkedHashSet<>();
        int previousIssue = 0;
        // Issue番号の重複と並び順を同時に検証します。
        for (Row row : manifest.rows()) {
            assertTrue(row.issue() > previousIssue, "Issue番号は重複せず昇順である必要があります: #" + row.issue());
            assertTrue(actualIssues.add(row.issue()), "Issue番号が重複しています: #" + row.issue());
            validateRow(row);
            previousIssue = row.issue();
        }

        assertEquals(EXPECTED_ISSUES, actualIssues, "既知Issueの登録に不足または余分な行があります");
    }

    private static void validateRow(Row row) {
        assertTrue(KINDS.contains(row.kind()), "未定義のIssue種別です: #" + row.issue());
        assertTrue(LOADERS.contains(row.loaders()), "未定義のローダー指定です: #" + row.issue());
        assertTrue(REQUIRED_LEVELS.contains(row.required()), "未定義の試験レベルです: #" + row.issue());
        assertTrue(RUNTIME_STATUSES.contains(row.runtimeStatus()), "未定義の実機状態です: #" + row.issue());
        assertFalse(row.summary().isBlank(), "Issue要約が空です: #" + row.issue());
        validateAutomatedEvidence(row);
        validateRuntimeEvidence(row);
    }

    private static void validateAutomatedEvidence(Row row) {
        assertFalse(row.automatedEvidence().isBlank(), "自動証拠が空です: #" + row.issue());
        assertFalse("-".equals(row.automatedEvidence()), "自動証拠が未登録です: #" + row.issue());

        // セミコロン区切りの証拠を一件ずつ実在確認します。
        for (String evidence : row.automatedEvidence().split(";")) {
            Path evidencePath = PROJECT_ROOT.resolve(evidence).normalize();
            assertTrue(
                    evidencePath.startsWith(PROJECT_ROOT),
                    "証拠パスがプロジェクト外を参照しています: #" + row.issue() + " " + evidence);
            assertTrue(Files.exists(evidencePath), "証拠ファイルが存在しません: #" + row.issue() + " " + evidence);
        }
    }

    private static void validateRuntimeEvidence(Row row) {
        // 静的検査と単体試験だけで完結する行に実機証拠を要求しません。
        if ("NOT_REQUIRED".equals(row.runtimeStatus())) {
            assertEquals("-", row.runtimeEvidence(), "不要な実機証拠が登録されています: #" + row.issue());
            assertTrue(
                    "STATIC".equals(row.required()) || "UNIT".equals(row.required()),
                    "GameTestまたは実機試験をNOT_REQUIREDにはできません: #" + row.issue());
            return;
        }

        // 未確認行は証拠を捏造せず、専用リリースゲートの阻害要因として残します。
        if ("PENDING".equals(row.runtimeStatus())) {
            assertEquals("-", row.runtimeEvidence(), "未確認行へ実機証拠を登録しないでください: #" + row.issue());
            assertTrue(
                    "GAMETEST".equals(row.required()) || "RUNTIME".equals(row.required()),
                    "未確認にできるのはGameTestまたは実機試験だけです: #" + row.issue());
            return;
        }

        assertFalse(row.runtimeEvidence().isBlank(), "実機証拠が空です: #" + row.issue());
        assertFalse("-".equals(row.runtimeEvidence()), "検証済み行に実機証拠がありません: #" + row.issue());
        // URLは外部試験記録として扱い、ローカルパスだけ実在確認します。
        if (row.runtimeEvidence().startsWith("https://")) {
            return;
        }
        assertTrue(
                Files.exists(PROJECT_ROOT.resolve(row.runtimeEvidence()).normalize()),
                "実機証拠ファイルが存在しません: #" + row.issue());
    }

    private static Manifest readManifest() throws IOException {
        List<String> lines = Files.readAllLines(MANIFEST, StandardCharsets.UTF_8);
        int synchronizedThroughIssue = -1;
        String header = null;
        List<Row> rows = new ArrayList<>();

        // コメントから同期番号を取得し、データ行をTSVとして読み込みます。
        for (String line : lines) {
            if (line.startsWith("# synchronized_through_issue=")) {
                synchronizedThroughIssue = Integer.parseInt(line.substring(line.indexOf('=') + 1));
                continue;
            }
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            if (header == null) {
                header = line;
                continue;
            }
            rows.add(parseRow(line));
        }

        return new Manifest(synchronizedThroughIssue, header, List.copyOf(rows));
    }

    private static Row parseRow(String line) {
        String[] columns = line.split("\t", -1);
        assertEquals(8, columns.length, "TSVは8列である必要があります: " + line);
        return new Row(
                Integer.parseInt(columns[0]),
                columns[1],
                columns[2],
                columns[3],
                columns[4],
                columns[5],
                columns[6],
                columns[7]);
    }

    private record Manifest(int synchronizedThroughIssue, String header, List<Row> rows) {}

    private record Row(
            int issue,
            String kind,
            String loaders,
            String required,
            String automatedEvidence,
            String runtimeStatus,
            String runtimeEvidence,
            String summary) {}
}
