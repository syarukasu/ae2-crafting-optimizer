package com.syaru.ae2craftingoptimizer.api.big;

import static org.junit.jupiter.api.Assertions.*;
import appeng.api.stacks.AEItemKey;
import com.syaru.ae2craftingoptimizer.testsupport.TestKeyTypes;
import com.electronwill.nightconfig.core.CommentedConfig;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.api.vector.*;
import com.syaru.ae2craftingoptimizer.engine.craftingtable.PhysicalCraftingTreeTransaction;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.common.ForgeConfigSpec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BigCraftingPhysicalExecutionTest {
    @BeforeAll
    static void initializeRegistryAndConfig() throws Exception {
        SharedConstants.tryDetectVersion();
        // Only registries are needed for the real AEItemKey NBT codec, not Forge networking.
        var bootstrapped = Bootstrap.class.getDeclaredField("isBootstrapped");
        bootstrapped.setAccessible(true);
        bootstrapped.setBoolean(null, true);
        if (BuiltInRegistries.PAINTING_VARIANT.size() == 0) {
            BuiltInRegistries.bootStrap();
        }
        TestKeyTypes.initialize();
        var field = ACOConfig.class.getDeclaredField("SPEC");
        field.setAccessible(true);
        ForgeConfigSpec spec = (ForgeConfigSpec) field.get(null);
        CommentedConfig defaults = CommentedConfig.inMemory();
        spec.correct(defaults);
        spec.setConfig(defaults);
    }

    @Test
    void savesWidePendingCountsWithoutClaimingUnreceivedOutputs() {
        BigInteger amount = BigInteger.TEN.pow(40);
        var input = AEItemKey.of(Items.OAK_LOG);
        var output = AEItemKey.of(Items.OAK_PLANKS);
        var definition = AEItemKey.of(Items.PAPER);
        UUID job = UUID.randomUUID();
        var plan = new PreparedVectorBatch(UUID.randomUUID(), job, VectorResourceMode.NETWORK_STORAGE,
                output, amount, amount, 1,
                List.of(new ExactStack(input, amount)), List.of(new ExactStack(output, amount)), List.of(),
                List.of("one"), List.of(new ExactCraftingStep("one", 1, amount,
                        List.of(new ExactCraftingInputSlot(input, 1L)))), "test-fingerprint", 1, 1);
        var transaction = PhysicalCraftingTreeTransaction.create(plan, Map.of("one",
                new PhysicalCraftingTreeTransaction.PatternAccountingIdentity("one", definition,
                        Map.of(input, amount), Map.of(output, amount))));
        var execution = new BigCraftingPhysicalExecution(amount, transaction);
        var restored = BigCraftingPhysicalExecution.load(execution.save());
        assertEquals(job, restored.jobId());
        assertEquals(amount, restored.reservedBytes());
        assertEquals(Map.of(output, amount), restored.pendingItems());
        assertTrue(restored.waitingItems().isEmpty());
        assertTrue(restored.storedItems().isEmpty());
        assertEquals(amount, restored.remainingOutput());
        assertNotEquals("COMPLETE", restored.state());
        restored.requestCancellation();
        assertEquals(restored.save(), BigCraftingPhysicalExecution.load(restored.save()).save());
    }


    @Test
    void selectedMultiProducerReceiptsSurviveSaveAndCancellationWithoutInventingOutputs() {
        var raw = AEItemKey.of(Items.COBBLESTONE);
        var other = AEItemKey.of(Items.DIRT);
        var part = AEItemKey.of(Items.IRON_INGOT);
        var out = AEItemKey.of(Items.DIAMOND);
        var extra = AEItemKey.of(Items.GOLD_INGOT);
        BigInteger n = BigInteger.TEN.pow(64);
        var first = new com.syaru.ae2craftingoptimizer.engine.CompiledPattern<appeng.api.stacks.AEKey>("first",
                List.of(new com.syaru.ae2craftingoptimizer.engine.CompiledPattern.InputSlot<>(
                        List.of(new com.syaru.ae2craftingoptimizer.engine.CompiledPattern.Stack<>(raw, 1)))),
                Map.of(part, 1L, extra, 2L), false);
        var second = new com.syaru.ae2craftingoptimizer.engine.CompiledPattern<appeng.api.stacks.AEKey>("second",
                List.of(new com.syaru.ae2craftingoptimizer.engine.CompiledPattern.InputSlot<>(
                        List.of(new com.syaru.ae2craftingoptimizer.engine.CompiledPattern.Stack<>(other, 1)))),
                Map.of(part, 1L), false);
        var root = new com.syaru.ae2craftingoptimizer.engine.CompiledPattern<appeng.api.stacks.AEKey>("root",
                List.of(new com.syaru.ae2craftingoptimizer.engine.CompiledPattern.InputSlot<>(
                        List.of(new com.syaru.ae2craftingoptimizer.engine.CompiledPattern.Stack<>(part, 1)))),
                Map.of(out, 1L), false);
        var exact = new com.syaru.ae2craftingoptimizer.engine.BigCraftingPlan<appeng.api.stacks.AEKey>(
                out, n.add(BigInteger.ONE), Map.of("first", BigInteger.ONE, "second", n, "root", n.add(BigInteger.ONE)),
                Map.of(raw, BigInteger.ONE, other, n), Map.of(), Map.of(), 3);
        var selected = com.syaru.ae2craftingoptimizer.engine.SelectedBranchPhysicalPlan.prepare(
                exact, List.of(root, first, second), 1, 1, 4096);
        var identities = Map.of(
                "first", new PhysicalCraftingTreeTransaction.PatternAccountingIdentity("first", AEItemKey.of(Items.PAPER),
                        Map.of(raw, BigInteger.ONE), Map.of(part, BigInteger.ONE, extra, BigInteger.TWO)),
                "second", new PhysicalCraftingTreeTransaction.PatternAccountingIdentity("second", AEItemKey.of(Items.STONE),
                        Map.of(other, n), Map.of(part, n)),
                "root", new PhysicalCraftingTreeTransaction.PatternAccountingIdentity("root", AEItemKey.of(Items.GRANITE),
                        Map.of(part, n.add(BigInteger.ONE)), Map.of(out, n.add(BigInteger.ONE))));
        var execution = new BigCraftingPhysicalExecution(n, PhysicalCraftingTreeTransaction.create(selected, identities));
        var saved = execution.save();
        var restored = BigCraftingPhysicalExecution.load(saved);
        assertEquals(saved, restored.save());
        assertEquals(selected, PhysicalCraftingTreeTransaction.load(saved.getCompound("transaction")).plan());
        assertEquals(Map.of(part, n.add(BigInteger.ONE), out, n.add(BigInteger.ONE), extra, BigInteger.TWO),
                restored.pendingItems());
        assertTrue(restored.storedItems().isEmpty());
        assertTrue(restored.waitingItems().isEmpty());
        assertEquals(n.add(BigInteger.ONE), restored.remainingOutput());
        restored.requestCancellation();
        var cancelled = restored.save();
        assertEquals(cancelled, BigCraftingPhysicalExecution.load(cancelled).save());
        assertNotEquals("COMPLETE", restored.state());
    }


    @Test
    void unchangedReceiptsReuseAccountingAndCancellationSurvivesReload() throws Exception {
        var transaction = parallelTransaction(1024);
        var original = transaction.accountingSnapshot();
        for (int i = 0; i < 10000; i++) assertSame(original, transaction.accountingSnapshot());
        assertEquals(1, transaction.tickDiagnostics().accountingSnapshotRebuilds());
        assertThrows(UnsupportedOperationException.class, () -> original.expectedOutputs().clear());
        long revision = transaction.transactionRevision();
        assertTrue(transaction.requestCancellation());
        assertTrue(transaction.transactionRevision() > revision);
        assertSame(original, transaction.accountingSnapshot(), "request alone must not credit outputs");
        int ticks = 0;
        while (transaction.state() == PhysicalCraftingTreeTransaction.State.CANCELLING_THREADS) {
            invoke(transaction, "advanceCancellation", null, null, 7);
            assertTrue(transaction.lastConsumedOperations() <= 7);
            var saved = transaction.save();
            transaction = PhysicalCraftingTreeTransaction.load(saved);
            assertEquals(saved, transaction.save());
            assertTrue(++ticks <= 147);
        }
        assertEquals(147, ticks, "cancel scans only the bounded active queue");
        assertEquals(PhysicalCraftingTreeTransaction.State.RETURNING_CANCELLED_ESCROW, transaction.state());
        invoke(transaction, "returnNextCancelledStack", null, null);
        assertEquals(PhysicalCraftingTreeTransaction.State.CANCELLED, transaction.state());
        var accounting = transaction.accountingSnapshot();
        assertTrue(accounting.introducedOutputs().isEmpty());
        assertTrue(accounting.creditedOutputs().isEmpty());
        assertFalse(accounting.finalOutputReturned());
        assertTrue(transaction.escrowSnapshot().isEmpty());
    }

    @Test
    void missingProviderDoesNotDropOtherPolledCancellationSteps() throws Exception {
        var transaction = parallelTransaction(3);
        var field = PhysicalCraftingTreeTransaction.class.getDeclaredField("steps");
        field.setAccessible(true);
        var receipts = (java.util.List<?>) field.get(transaction);
        Object receipt = receipts.get(0);
        invoke(receipt, "reserveInputs", Map.of(AEItemKey.of(Items.OAK_LOG), BigInteger.TEN.pow(64)));
        invoke(receipt, "selectTarget", 0L, 0);
        invoke(receipt, "accept");
        transaction.requestCancellation();
        var snapshotType = com.syaru.ae2craftingoptimizer.engine.Ae2CompiledCraftingGraphCache.Snapshot.class;
        var constructor = snapshotType.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        var empty = com.syaru.ae2craftingoptimizer.engine.CompiledCraftingGraph
                .<appeng.api.stacks.AEKey>compile(1, List.of());
        Object snapshot = constructor.newInstance(empty, new java.util.IdentityHashMap<>(),
                Map.of(), Map.of(), Map.of(), java.util.Set.of(), java.util.Set.of(),
                java.util.Set.of(), java.util.Set.of(), 1L, 1L);
        invoke(transaction, "advanceCancellation", null, snapshot, 3);
        assertEquals(3, transaction.lastConsumedOperations());
        assertEquals("CANCELLED", invoke(receipts.get(1), "state").toString());
        assertEquals("CANCELLED", invoke(receipts.get(2), "state").toString());
        invoke(transaction, "advanceCancellation", null, snapshot, 3);
        assertEquals(1, transaction.lastConsumedOperations(), "only the unloaded owned step should remain");
        assertEquals(PhysicalCraftingTreeTransaction.State.CANCELLING_THREADS, transaction.state());
        assertTrue(transaction.accountingSnapshot().creditedOutputs().isEmpty());
    }

    private static PhysicalCraftingTreeTransaction parallelTransaction(int count) {
        var input = AEItemKey.of(Items.OAK_LOG);
        var output = AEItemKey.of(Items.OAK_PLANKS);
        var amount = BigInteger.TEN.pow(64);
        var total = amount.multiply(BigInteger.valueOf(count));
        var steps = new java.util.ArrayList<ExactCraftingStep>();
        var identities = new java.util.LinkedHashMap<String, PhysicalCraftingTreeTransaction.PatternAccountingIdentity>();
        for (int i = 0; i < count; i++) {
            String id = "parallel-" + i;
            var definitionStack = new net.minecraft.world.item.ItemStack(Items.PAPER);
            var tag = new CompoundTag();
            tag.putString("fixturePattern", id);
            definitionStack.setTag(tag);
            var definition = AEItemKey.of(definitionStack);
            steps.add(new ExactCraftingStep(id, 1, amount, List.of(new ExactCraftingInputSlot(input, 1))));
            identities.put(id, new PhysicalCraftingTreeTransaction.PatternAccountingIdentity(
                    id, definition, Map.of(input, amount), Map.of(output, amount)));
        }
        var plan = new PreparedVectorBatch(UUID.randomUUID(), UUID.randomUUID(), VectorResourceMode.NETWORK_STORAGE,
                output, total, total, 1, List.of(new ExactStack(input, total)),
                List.of(new ExactStack(output, total)), List.of(), List.copyOf(identities.keySet()), steps,
                "parallel-fixture", 1, 1);
        return PhysicalCraftingTreeTransaction.create(plan, identities);
    }

    private static Object invoke(Object target, String name, Object... args) throws Exception {
        var method = java.util.Arrays.stream(target.getClass().getDeclaredMethods())
                .filter(m -> m.getName().equals(name) && m.getParameterCount() == args.length)
                .findFirst().orElseThrow();
        method.setAccessible(true);
        try { return method.invoke(target, args); }
        catch (java.lang.reflect.InvocationTargetException failure) {
            if (failure.getCause() instanceof Exception error) throw error;
            throw failure;
        }
    }

    @Test
    void rejectsAnUnknownPersistedExecutionSchema() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema", 999);
        assertThrows(IllegalArgumentException.class, () -> BigCraftingPhysicalExecution.load(tag));
    }
}
