package com.syaru.ae2craftingoptimizer.client;

import appeng.api.client.AEKeyRendering;
import appeng.api.config.SortDir;
import appeng.api.config.SortOrder;
import appeng.api.stacks.AEKey;
import appeng.core.AEConfig;
import appeng.menu.me.common.GridInventoryEntry;
import appeng.util.Platform;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.ChatFormatting;

/** Builds client-thread-safe terminal projections and evaluates only immutable data off-thread. */
public final class AsyncTerminalView {
    private AsyncTerminalView() {
    }

    public static List<Projection> project(List<GridInventoryEntry> entries, String query) {
        Set<String> tagTerms = prefixedTerms(query, '$');
        boolean needsTooltip = !prefixedTerms(query, '#').isEmpty();
        List<Projection> result = new ArrayList<>(entries.size());
        // AEKeyと可変Entryはclient threadで読み、workerへ不変値だけを渡す。
        for (GridInventoryEntry entry : entries) {
            AEKey key = Objects.requireNonNull(entry.getWhat());
            String name = normalize(key.getDisplayName().getString());
            String modId = normalize(key.getModId());
            String modName = normalize(Platform.getModName(key.getModId()));
            String id = normalize(key.getId().toString());
            String tooltip = needsTooltip ? tooltip(key) : "";
            double normalizedAmount = (double) entry.getStoredAmount()
                    / (double) entry.getWhat().getAmountPerUnit();
            Set<String> matchingTagTerms = new HashSet<>();
            // Queryに含まれるtag語だけをclient threadで解決し、workerでRegistryへ触れない。
            for (String term : tagTerms) {
                boolean matches = key.getType().getTagNames().anyMatch(tag -> {
                    var location = tag.location();
                    boolean idMatches = term.contains(":")
                            ? location.toString().contains(term)
                            : location.getNamespace().contains(term) || location.getPath().contains(term);
                    return idMatches && key.isTagged(tag);
                });
                // 一致したQuery語だけをworkerへ渡す集合に保存する。
                if (matches) {
                    matchingTagTerms.add(term);
                }
            }
            result.add(new Projection(
                    entry,
                    name,
                    modId,
                    modName,
                    id,
                    tooltip,
                    Set.copyOf(matchingTagTerms),
                    normalizedAmount));
        }
        return result;
    }

    public static List<GridInventoryEntry> filterAndSort(
            List<Projection> projections, String query, SortOrder order, SortDir direction) {
        return filterAndSortProjections(projections, query, order, direction).stream()
                .map(Projection::entry)
                .toList();
    }

    static List<Projection> filterAndSortProjections(
            List<Projection> projections, String query, SortOrder order, SortDir direction) {
        List<Projection> visible = new ArrayList<>();
        // AE2と同じAND/OR検索条件を、不変Projectionだけで評価する。
        for (Projection projection : projections) {
            // Query全体に一致する候補だけを表示対象へ残す。
            if (matches(projection, query)) {
                visible.add(projection);
            }
        }

        Comparator<Projection> comparator = switch (order) {
            case AMOUNT -> Comparator.comparingDouble(Projection::normalizedAmount);
            case MOD -> Comparator.comparing(Projection::modName, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(Projection::name, String.CASE_INSENSITIVE_ORDER);
            case NAME -> Comparator.comparing(Projection::name, String.CASE_INSENSITIVE_ORDER);
        };
        // AE2は降順時にComparator全体を反転するため、同じ順序で反転する。
        if (direction != SortDir.ASCENDING) {
            comparator = comparator.reversed();
        }
        visible.sort(comparator);
        return List.copyOf(visible);
    }

    private static boolean matches(Projection projection, String query) {
        // `|`で区切られた候補のどれか一つが成立すれば表示する。
        for (String orPart : query.split("\\|", -1)) {
            boolean all = true;
            // 同じ候補内の空白区切り語はすべて一致する必要がある。
            for (String raw : orPart.toLowerCase(Locale.ROOT).trim().split("\\s+")) {
                String term = raw;
                // 一語でも不一致なら、このAND候補の残りを評価しない。
                if (matchesTerm(projection, term)) {
                    continue;
                }
                all = false;
                break;
            }
            // 現在のOR候補がすべて一致した時点で表示を確定する。
            if (all) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesTerm(Projection projection, String term) {
        // `@`はmod IDと表示名のどちらにも一致させる。
        if (term.startsWith("@")) {
            String modTerm = term.substring(1);
            return projection.modId.contains(modTerm) || projection.modName.contains(modTerm);
        }
        // `#`は空白を除いたtooltip文字列を検索する。
        if (term.startsWith("#")) {
            return projection.tooltip.contains(normalizeTooltip(term.substring(1)));
        }
        // `$`はclient threadで解決済みのtag語だけを検索する。
        if (term.startsWith("$")) {
            return projection.matchingTagTerms.contains(term.substring(1));
        }
        // `*`はnamespaceを含むRegistry IDを検索する。
        if (term.startsWith("*")) {
            return projection.id.contains(term.substring(1));
        }
        return projection.name.contains(term);
    }

    private static Set<String> prefixedTerms(String query, char prefix) {
        Set<String> terms = new HashSet<>();
        // OR候補をまたいで必要なtag/tooltip語を一度だけ収集する。
        for (String orPart : query.toLowerCase(Locale.ROOT).split("\\|", -1)) {
            // 各候補の空白区切り語から、指定prefixだけを抽出する。
            for (String term : orPart.trim().split("\\s+")) {
                // prefixだけの空語を除き、実際の検索語だけを保存する。
                if (term.length() > 1 && term.charAt(0) == prefix) {
                    terms.add(term.substring(1));
                }
            }
        }
        return terms;
    }

    private static String tooltip(AEKey key) {
        var lines = AEKeyRendering.getTooltip(key);
        var result = new StringBuilder();
        // AE2のtooltip検索と同じ表示行を、client thread上で文字列化する。
        for (int i = 0; i < lines.size(); i++) {
            var line = lines.get(i);
            if (i > 0 && i == lines.size() - 1 && !AEConfig.instance().isSearchModNameInTooltips()) {
                String text = line.getString();
                boolean formatted;
                if (text.indexOf(ChatFormatting.PREFIX_CODE) >= 0) {
                    text = ChatFormatting.stripFormatting(text);
                    formatted = true;
                } else {
                    formatted = !line.getStyle().isEmpty();
                }
                if (!formatted || !Objects.equals(text, Platform.getModName(key.getModId()))) {
                    result.append('\n').append(text);
                }
            } else {
                if (i > 0) {
                    result.append('\n');
                }
                line.visit(text -> {
                    result.append(text.indexOf(ChatFormatting.PREFIX_CODE) >= 0
                            ? ChatFormatting.stripFormatting(text)
                            : text);
                    return Optional.empty();
                });
            }
        }
        return normalizeTooltip(result.toString());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static String normalizeTooltip(String value) {
        return normalize(value).replace(" ", "");
    }

    public record Projection(
            GridInventoryEntry entry,
            String name,
            String modId,
            String modName,
            String id,
            String tooltip,
            Set<String> matchingTagTerms,
            double normalizedAmount) {
    }
}
