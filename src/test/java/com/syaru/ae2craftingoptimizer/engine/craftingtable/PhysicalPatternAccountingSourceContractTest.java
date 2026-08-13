package com.syaru.ae2craftingoptimizer.engine.craftingtable;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** live graphを履歴会計へ再導入する回帰を、headless JUnitで検出する。 */
class PhysicalPatternAccountingSourceContractTest {
    private static final Path TRANSACTION = Path.of(
            "src", "main", "java", "com", "syaru", "ae2craftingoptimizer",
            "engine", "craftingtable", "PhysicalCraftingTreeTransaction.java");
    private static final Path MANAGER = Path.of(
            "src", "main", "java", "com", "syaru", "ae2craftingoptimizer",
            "integration", "AqeBigCraftingExecutionManager.java");

    @Test
    void persistsACompleteCanonicalIdentityWithLegacyMigration() {
        String source = read(TRANSACTION);

        assertTrue(source.contains("private static final int SCHEMA_VERSION = 3;"));
        assertTrue(source.contains("private static final int LEGACY_SCHEMA_VERSION = 2;"));
        assertTrue(source.contains("encodePatternIdentities(patternIdentities)"));
        assertTrue(source.contains("decodePatternIdentities(owner, plan)"));
        assertTrue(source.contains("ensurePatternIdentities(snapshot, level)"));
        assertTrue(source.contains("PatternUnavailableException"));
        assertTrue(source.contains("PatternIdentityConflictException"));
    }

    @Test
    void buildsHistoricalAccountingOnlyFromPersistedIdentities() {
        String source = read(TRANSACTION);
        String method = between(
                source,
                "AccountingSnapshot accountingSnapshotFromPersistedIdentities()",
                "/** GUIへ渡す進捗");

        assertTrue(method.contains("patternIdentities.get(step.patternId())"));
        assertTrue(method.contains("patternDefinitions(plannedTasks)"));
        assertFalse(method.contains("snapshot.pattern("));
        assertFalse(method.contains("resolveStep("));
        assertFalse(method.contains("graph().generation()"));
    }

    @Test
    void capturesIdentityBeforeOwnershipAndNeverReResolvesTasksFromTheLiveGraph() {
        String source = read(MANAGER);

        assertTrue(count(source, "capturePatternAccounting(") >= 2L);
        assertTrue(source.contains("accounting.plannedPatternDefinitions()"));
        assertTrue(source.contains("accounting.dispatchedPatternDefinitions()"));
        assertFalse(source.contains("exact accounting references an unknown pattern"));
    }

    @Test
    void temporaryUnloadWaitsWhileAProvenReplacementConflicts() {
        String source = read(TRANSACTION);
        int retryableCatch = source.indexOf(
                "catch (PatternUnavailableException unavailable)");
        int quarantineCatch = source.indexOf(
                "catch (RuntimeException | LinkageError failure)");

        assertTrue(retryableCatch >= 0);
        assertTrue(quarantineCatch > retryableCatch);
        assertTrue(source.contains("live crafting-table pattern identity changed:"));
        assertTrue(source.contains("return TickOutcome.waiting(detail);"));
    }

    private static String between(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex);
        // 試験境界が消えた場合は、空文字で誤って通さず明示的に失敗させる。
        if (startIndex < 0 || endIndex < 0) {
            throw new IllegalArgumentException("source contract boundary is missing");
        }
        return source.substring(startIndex, endIndex);
    }

    private static long count(String source, String token) {
        long matches = 0L;
        int cursor = 0;
        // 各出現位置を一度だけ数え、同じ開始位置を再評価しない。
        while ((cursor = source.indexOf(token, cursor)) >= 0) {
            matches++;
            cursor += token.length();
        }
        return matches;
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }
}
