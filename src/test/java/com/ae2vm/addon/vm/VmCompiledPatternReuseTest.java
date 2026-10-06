package com.ae2vm.addon.vm;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class VmCompiledPatternReuseTest {
    private static final BigInteger ZERO = BigInteger.ZERO, ONE = BigInteger.ONE;
    private static final int USES = 32;
    @BeforeAll static void bootstrap() throws Exception { VmBulkStateTest.bootstrap(); }

    @Test @Timeout(60) void finiteToolAccountingAndCompilationCostDoNotGrowWithOrderSize() throws Exception {
        var rows = new ArrayList<Map<String, Object>>();
        for (var order : List.of(ONE, BigInteger.TWO, BigInteger.valueOf(1000),
                BigInteger.valueOf(Long.MAX_VALUE), ONE.shiftLeft(63), BigInteger.TEN.pow(1024).subtract(ONE))) {
            var tools = order.add(BigInteger.valueOf(USES - 1)).divide(BigInteger.valueOf(USES));
            for (var mode : List.of("empty", "partial", "funded")) {
                var supplied = mode.equals("empty") ? ZERO : mode.equals("partial") ? tools.divide(BigInteger.TWO) : tools;
                var pattern = new ToolPattern();
                var stock = Map.<AEKey, BigInteger>of(tool(0), supplied, pattern.raw, order);
                var patterns = Map.<AEKey, List<IPatternDetails>>of(pattern.output, List.of(pattern));
                ArchivedVmFixture.Result previous = null;
                for (boolean historical : new boolean[]{true, false}) {
                    pattern.primaryReads = 0;
                    var result = ArchivedVmFixture.run(historical, pattern.output, order, stock, patterns);
                    var plan = result.plan();
                    assertEquals(order, plan.finalOutput().amount());
                    assertEquals(order, plan.patternTimes().get(pattern));
                    assertEquals(supplied, plan.usedItems().get(tool(0)));
                    assertEquals(tools.subtract(supplied), plan.missingItems().get(tool(0)));
                    assertEquals(order, plan.usedItems().get(pattern.raw));
                    assertEquals(ZERO, plan.missingItems().get(pattern.raw));
                    if (previous != null) {
                        assertEquals(previous.plan().bytes(), plan.bytes());
                        assertEquals(previous.plan().usedItems().quantities(), plan.usedItems().quantities());
                        assertEquals(previous.plan().missingItems().quantities(), plan.missingItems().quantities());
                        assertEquals(previous.plan().patternTimes(), plan.patternTimes());
                    }
                    rows.add(Map.of("version", historical ? "archived-arithmetic" : "candidate",
                            "quantity", order.toString(), "stock", mode, "planNs", result.elapsedNs(),
                            "primaryReads", pattern.primaryReads, "work", result.work(), "quantitiesVerified", true));
                    previous = result;
                }
            }
        }
        var report = Path.of("build/reports/issue218-vm-reuse/tool-after.json");
        Files.createDirectories(report.getParent());
        try (var writer = Files.newBufferedWriter(report)) {
            new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(rows, writer);
        }
        System.out.println("Finite tool quantity/metadata comparison: " + report);
        long maximum = rows.stream().filter(row -> row.get("version").equals("candidate"))
                .mapToLong(row -> ((Number) row.get("primaryReads")).longValue()).max().orElseThrow();
        assertTrue(maximum <= 3, "Repeated compiled blocks must not revalidate immutable output: " + maximum);
    }

    private static AEItemKey tool(int damage) {
        var stack = new ItemStack(Items.NETHERITE_PICKAXE);
        stack.setDamageValue(damage);
        return AEItemKey.of(stack);
    }

    private static final class ToolPattern implements IPatternDetails {
        final AEKey output = AEItemKey.of(Items.DIAMOND), raw = AEItemKey.of(Items.IRON_INGOT);
        final GenericStack[] outputs = { new GenericStack(output, 1) };
        final IInput[] inputs = { new IInput() {
            public GenericStack[] getPossibleInputs() { return new GenericStack[]{new GenericStack(tool(0), 1)}; }
            public long getMultiplier() { return 1; }
            public boolean isValid(AEKey key, Level level) {
                return key instanceof AEItemKey item && item.getItem() == Items.NETHERITE_PICKAXE;
            }
            public AEKey getRemainingKey(AEKey key) {
                int damage = ((AEItemKey) key).toStack().getDamageValue();
                return damage + 1 < USES ? tool(damage + 1) : null;
            }
        }, new IInput() {
            public GenericStack[] getPossibleInputs() { return new GenericStack[]{new GenericStack(raw, 1)}; }
            public long getMultiplier() { return 1; }
            public boolean isValid(AEKey key, Level level) { return raw.equals(key); }
            public AEKey getRemainingKey(AEKey key) { return null; }
        } };
        int primaryReads;
        public AEItemKey getDefinition() { return AEItemKey.of(Items.PAPER); }
        public IInput[] getInputs() { return inputs; }
        public GenericStack[] getOutputs() { return outputs; }
        public GenericStack getPrimaryOutput() { primaryReads++; return outputs[0]; }
    }
}
