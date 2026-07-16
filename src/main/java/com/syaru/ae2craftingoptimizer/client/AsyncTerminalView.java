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
        for (GridInventoryEntry entry : entries) {
            AEKey key = Objects.requireNonNull(entry.getWhat());
            String name = normalize(key.getDisplayName().getString());
            String modId = normalize(key.getModId());
            String mod = modId + "\n" + normalize(Platform.getModName(key.getModId()));
            String id = normalize(key.getId().toString());
            String tooltip = needsTooltip ? tooltip(key) : "";
            Set<String> matchingTagTerms = new HashSet<>();
            for (String term : tagTerms) {
                boolean matches = key.getType().getTagNames().anyMatch(tag -> {
                    var location = tag.location();
                    boolean idMatches = term.contains(":")
                            ? location.toString().contains(term)
                            : location.getNamespace().contains(term) || location.getPath().contains(term);
                    return idMatches && key.isTagged(tag);
                });
                if (matches) {
                    matchingTagTerms.add(term);
                }
            }
            result.add(new Projection(entry, name, mod, id, tooltip, Set.copyOf(matchingTagTerms)));
        }
        return result;
    }

    public static List<GridInventoryEntry> filterAndSort(
            List<Projection> projections, String query, SortOrder order, SortDir direction) {
        List<Projection> visible = new ArrayList<>();
        for (Projection projection : projections) {
            if (matches(projection, query)) {
                visible.add(projection);
            }
        }

        Comparator<Projection> comparator = switch (order) {
            case AMOUNT -> Comparator.comparingDouble(projection ->
                    (double) projection.entry.getStoredAmount()
                            / (double) projection.entry.getWhat().getAmountPerUnit());
            case MOD -> Comparator.comparing(Projection::mod, String::compareToIgnoreCase)
                    .thenComparing(Projection::name, String::compareToIgnoreCase);
            case NAME -> Comparator.comparing(Projection::name, String::compareToIgnoreCase);
        };
        if (direction != SortDir.ASCENDING) {
            comparator = comparator.reversed();
        }
        visible.sort(comparator);
        return visible.stream().map(Projection::entry).toList();
    }

    private static boolean matches(Projection projection, String query) {
        for (String orPart : query.split("\\|", -1)) {
            boolean all = true;
            for (String raw : orPart.toLowerCase(Locale.ROOT).trim().split("\\s+")) {
                String term = raw;
                boolean termMatches;
                if (term.startsWith("@")) {
                    termMatches = projection.mod.contains(term.substring(1));
                } else if (term.startsWith("#")) {
                    termMatches = projection.tooltip.contains(normalizeTooltip(term.substring(1)));
                } else if (term.startsWith("$")) {
                    termMatches = projection.matchingTagTerms.contains(term.substring(1));
                } else if (term.startsWith("*")) {
                    termMatches = projection.id.contains(term.substring(1));
                } else {
                    termMatches = projection.name.contains(term);
                }
                if (!termMatches) {
                    all = false;
                    break;
                }
            }
            if (all) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> prefixedTerms(String query, char prefix) {
        Set<String> terms = new HashSet<>();
        for (String orPart : query.toLowerCase(Locale.ROOT).split("\\|", -1)) {
            for (String term : orPart.trim().split("\\s+")) {
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
            String mod,
            String id,
            String tooltip,
            Set<String> matchingTagTerms) {
    }
}
