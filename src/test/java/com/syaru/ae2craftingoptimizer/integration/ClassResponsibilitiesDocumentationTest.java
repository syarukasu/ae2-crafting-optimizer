package com.syaru.ae2craftingoptimizer.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Issue #87: 本番トップレベル型の責務が文書から静かに消えることを防ぐ。 */
class ClassResponsibilitiesDocumentationTest {
    private static final Path PRODUCTION_ROOT = Path.of("src", "main", "java");
    private static final Path RESPONSIBILITIES =
            Path.of("docs", "CLASS_RESPONSIBILITIES.md");
    private static final Pattern PACKAGE_DECLARATION =
            Pattern.compile("(?m)^package\\s+([A-Za-z0-9_.]+);");
    private static final Pattern RESPONSIBILITY_ROW = Pattern.compile(
            "^\\| `(?<type>com\\.syaru\\.ae2craftingoptimizer"
                    + "(?:\\.[a-z][A-Za-z0-9_]*)*\\.[A-Z][A-Za-z0-9_]*)` "
                    + "\\| (?<role>.+) \\|$");

    @Test
    void everyProductionTopLevelTypeHasExactlyOneConcreteResponsibility() throws IOException {
        Set<String> sourceTypes = productionTypes();
        Map<String, String> documentedTypes = documentedTypes();

        assertEquals(
                sourceTypes,
                documentedTypes.keySet(),
                () -> "責務一覧と本番型が一致しない。source only="
                        + difference(sourceTypes, documentedTypes.keySet())
                        + ", docs only="
                        + difference(documentedTypes.keySet(), sourceTypes));
    }

    @Test
    void generatedDocumentDoesNotContainPlaceholdersOrLocalPaths() {
        String document = read(RESPONSIBILITIES);

        assertFalse(document.contains("クラス名どおり"));
        assertFalse(document.contains("TODO"));
        assertFalse(document.contains("C:\\Users\\"));
        assertFalse(document.contains("file://"));
    }

    private static Set<String> productionTypes() throws IOException {
        Set<String> result = new TreeSet<>();
        try (Stream<Path> files = Files.walk(PRODUCTION_ROOT)) {
            files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.getFileName().toString().equals("package-info.java"))
                    .forEach(path -> result.add(productionType(path)));
        }
        return result;
    }

    private static String productionType(Path path) {
        String source = read(path);
        Matcher packageMatcher = PACKAGE_DECLARATION.matcher(source);
        assertTrue(
                packageMatcher.find(),
                () -> "package宣言がない本番型: " + path);
        String fileName = path.getFileName().toString();
        String simpleName = fileName.substring(0, fileName.length() - ".java".length());
        Pattern topLevelType = Pattern.compile(
                "(?m)^\\s*(?:public\\s+)?(?:(?:final|abstract|sealed|non-sealed)\\s+)*"
                        + "(?:class|record|interface|enum|@interface)\\s+"
                        + Pattern.quote(simpleName)
                        + "\\b");
        assertTrue(
                topLevelType.matcher(source).find(),
                () -> "ファイル名と一致するトップレベル型がない: " + path);
        return packageMatcher.group(1) + "." + simpleName;
    }

    private static Map<String, String> documentedTypes() {
        Map<String, String> result = new LinkedHashMap<>();
        // 全クラス表のFQCN行だけを読み、上部のレビュー表や本文中の参照は数えない。
        for (String line : read(RESPONSIBILITIES).lines().toList()) {
            Matcher row = RESPONSIBILITY_ROW.matcher(line);
            // 表形式以外の行は責務entryではないため読み飛ばす。
            if (!row.matches()) {
                continue;
            }
            String type = row.group("type");
            String role = row.group("role").trim();
            assertFalse(role.isBlank(), () -> "責務が空: " + type);
            assertFalse(role.endsWith("担当する。") && role.contains("クラス名どおり"));
            String previous = result.put(type, role);
            assertNull(previous, () -> "責務一覧に重複した型がある: " + type);
        }
        return result;
    }

    private static Set<String> difference(Set<String> left, Set<String> right) {
        Set<String> result = new TreeSet<>(left);
        result.removeAll(right);
        return result;
    }

    private static String read(Path path) {
        try {
            return Files.readString(path);
        } catch (IOException exception) {
            throw new UncheckedIOException(path.toString(), exception);
        }
    }
}
