package com.syaru.ae2craftingoptimizer.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import appeng.api.config.SortDir;
import appeng.api.config.SortOrder;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AsyncTerminalViewTest {
    private static final String IRON_ID = "minecraft:iron_ingot";
    private static final String GOLD_ID = "minecraft:gold_ingot";
    private static final String INGOT_TAG = "forge:ingots";

    @Test
    void searchOperatorsPreserveAndOrSemantics() {
        var iron = projection(
                "iron ingot",
                "minecraft",
                "minecraft",
                IRON_ID,
                "heavy metal",
                Set.of(INGOT_TAG),
                64.0D);
        var gold = projection(
                "gold ingot",
                "minecraft",
                "minecraft",
                GOLD_ID,
                "precious metal",
                Set.of(INGOT_TAG),
                32.0D);
        var projections = List.of(iron, gold);

        assertEquals(List.of(iron), filter(projections, "iron #heavy"));
        assertEquals(List.of(gold, iron), filter(projections, "@minecraft $forge:ingots"));
        assertEquals(List.of(gold), filter(projections, "*minecraft:gold_ingot"));
        assertEquals(List.of(iron), filter(projections, "missing|iron"));
    }

    @Test
    void modSortUsesDisplayNameThenEntryName() {
        var zinc = projection("zinc", "addon_z", "alpha machines", "addon_z:zinc", "", Set.of(), 1.0D);
        var copper = projection("copper", "addon_a", "beta machines", "addon_a:copper", "", Set.of(), 1.0D);
        var iron = projection("iron", "addon_i", "alpha machines", "addon_i:iron", "", Set.of(), 1.0D);

        assertEquals(
                List.of(iron, zinc, copper),
                AsyncTerminalView.filterAndSortProjections(
                        List.of(zinc, copper, iron),
                        "",
                        SortOrder.MOD,
                        SortDir.ASCENDING));
    }

    @Test
    void amountSortSupportsBothDirections() {
        var low = projection("low", "test", "test", "test:low", "", Set.of(), 1.0D);
        var high = projection("high", "test", "test", "test:high", "", Set.of(), 9.0D);

        assertEquals(
                List.of(low, high),
                AsyncTerminalView.filterAndSortProjections(
                        List.of(high, low),
                        "",
                        SortOrder.AMOUNT,
                        SortDir.ASCENDING));
        assertEquals(
                List.of(high, low),
                AsyncTerminalView.filterAndSortProjections(
                        List.of(low, high),
                        "",
                        SortOrder.AMOUNT,
                        SortDir.DESCENDING));
    }

    private static List<AsyncTerminalView.Projection> filter(
            List<AsyncTerminalView.Projection> projections,
            String query) {
        return AsyncTerminalView.filterAndSortProjections(
                projections,
                query,
                SortOrder.NAME,
                SortDir.ASCENDING);
    }

    private static AsyncTerminalView.Projection projection(
            String name,
            String modId,
            String modName,
            String id,
            String tooltip,
            Set<String> tags,
            double amount) {
        return new AsyncTerminalView.Projection(
                null,
                name,
                modId,
                modName,
                id,
                tooltip,
                tags,
                amount);
    }
}
