package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AEKeyType;
import appeng.api.stacks.KeyCounter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.Test;

class Ae2PlanningInventorySnapshotTest {
    @Test
    void capturesOnlyRootReferencedKeysAndExcludesRequestedOutput() {
        TestKey requestedOutput = new TestKey("requested_output");
        TestKey referenced = new TestKey("referenced");
        TestKey unrelated = new TestKey("unrelated");
        KeyCounter network = new KeyCounter();
        network.add(requestedOutput, 11L);
        network.add(referenced, 22L);
        network.add(unrelated, 33L);

        Ae2PlanningInventorySnapshot snapshot =
                Ae2PlanningInventorySnapshot.captureReferenced(
                        network,
                        List.of(requestedOutput, referenced),
                        requestedOutput);

        assertEquals(0L, snapshot.amount(requestedOutput));
        assertEquals(22L, snapshot.amount(referenced));
        assertEquals(0L, snapshot.amount(unrelated));
    }

    @Test
    void warmRootCaptureUsesOnlyItsCachedProgramWithoutCompilingOrReusingStock() {
        AEKey root = new TestKey("root");
        AEKey raw = new TestKey("raw");
        AEKey unrelated = new TestKey("other_recipe");
        var program = CompiledRootProgram.tryCompile(CompiledCraftingGraph.compile(1L, List.of(
                new CompiledPattern<AEKey>("root", List.of(new CompiledPattern.InputSlot<>(
                        List.of(new CompiledPattern.Stack<>(raw, 2L)))), Map.of(root, 1L), false))),
                root, ignored -> false).orElseThrow();
        var outcome = new AtomicReference<>(Optional.of(CompiledRootProgram.Outcome.compiled(program)));
        var index = (Ae2PlanningGraphSnapshot) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] { Ae2PlanningGraphSnapshot.class },
                (proxy, method, args) -> {
                    assertEquals("cachedRootProgramOutcome", method.getName());
                    assertEquals(root, args[0]);
                    return outcome.get();
                });
        var allKeys = List.of(root, raw, unrelated);
        var keys = Ae2ImmutablePlanningGraphCache.referencedKeys(index, root, allKeys);
        assertEquals(program.referencedKeys(), keys);
        var network = new KeyCounter();
        network.set(raw, Long.MAX_VALUE);
        network.set(unrelated, 17L);
        var captured = Ae2PlanningInventorySnapshot.captureReferenced(network, keys, root);
        network.set(raw, 5L);
        assertEquals(Long.MAX_VALUE, captured.amount(raw));
        assertEquals(0L, captured.amount(unrelated));
        assertEquals(5L, Ae2PlanningInventorySnapshot.captureReferenced(network, keys, root).amount(raw));
        outcome.set(Optional.empty());
        assertEquals(allKeys, Ae2ImmutablePlanningGraphCache.referencedKeys(index, root, allKeys));
        outcome.set(Optional.of(CompiledRootProgram.Outcome.failed(RootProgramFailure.MULTIPLE_PRODUCERS)));
        assertEquals(allKeys, Ae2ImmutablePlanningGraphCache.referencedKeys(index, root, allKeys));
        assertEquals(allKeys, Ae2ImmutablePlanningGraphCache.referencedKeys(null, root, allKeys));
    }

    @Test
    void nativePlannerKeepsBothProducersButDeclinesAnIncompleteDependency() {
        AEKey root = new TestKey("output");
        AEKey raw = new TestKey("raw");
        var slot = new CompiledPattern.InputSlot<AEKey>(
                List.of(new CompiledPattern.Stack<>(raw, 1L)));
        var first = new CompiledPattern<AEKey>("first", List.of(slot), Map.of(root, 1L), false);
        var second = new CompiledPattern<AEKey>("second", List.of(slot), Map.of(root, 2L), false);
        var graph = CompiledCraftingGraph.compile(0L, List.of(first, second));
        var incomplete = new AtomicBoolean();
        var snapshot = (Ae2PlanningGraphSnapshot) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] { Ae2PlanningGraphSnapshot.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "graph" -> graph;
                    case "isEmittable" -> false;
                    case "isIncompletelyCompiled" -> incomplete.get() && args[0] == raw;
                    case "registeredPatternCount" -> graph.patternsFor((AEKey) args[0]).size();
                    case "hasExactInputDomain" -> true;
                    default -> throw new AssertionError(method.getName());
                });

        assertTrue(Ae2ImmutablePlanningGraphCache.supportsDetachedAe2Planning(
                snapshot, root, PlanningGuard.none()));
        assertEquals(List.of(first, second), graph.patternsFor(root));
        incomplete.set(true);
        assertFalse(Ae2ImmutablePlanningGraphCache.supportsDetachedAe2Planning(
                snapshot, root, PlanningGuard.none()));
    }

    @Test
    void strictTopologyAdoptsIndependentProcessingOutputsButStillChecksInputProof() {
        AEKey root = new TestKey("circuit");
        AEKey raw = new TestKey("raw");
        AEKey gas = new TestKey("gas");
        var pattern = new CompiledPattern<AEKey>("industrial", List.of(new CompiledPattern.InputSlot<>(
                List.of(new CompiledPattern.Stack<>(raw, 2L)))), Map.of(root, 4L, gas, 1000L), true);
        var graph = CompiledCraftingGraph.compile(1L, List.of(pattern));
        var program = CompiledRootProgram.tryCompile(graph, root, ignored -> false).orElseThrow();
        var exact = new AtomicBoolean(true);
        var snapshot = (Ae2PlanningGraphSnapshot) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] { Ae2PlanningGraphSnapshot.class },
                (proxy, method, args) -> switch (method.getName()) {
                    case "graph" -> graph;
                    case "recipeGeneration" -> 1L;
                    case "isEmittable", "isIncompletelyCompiled" -> false;
                    case "registeredPatternCount" -> graph.patternsFor((AEKey) args[0]).size();
                    case "hasExactlyOneFullyCompiledPattern" -> graph.patternsFor((AEKey) args[0]).size() == 1;
                    case "hasExactInputDomain" -> exact.get();
                    default -> throw new AssertionError(method.getName());
                });
        var topology = Ae2StrictCraftingTopology.compile(snapshot, program);
        org.junit.jupiter.api.Assertions.assertNotNull(topology);
        org.junit.jupiter.api.Assertions.assertSame(pattern, topology.patternByOutput().get(root));
        exact.set(false);
        org.junit.jupiter.api.Assertions.assertNull(Ae2StrictCraftingTopology.compile(snapshot, program));
    }

    /** Minecraft Registry初期化なしでKeyCounterの参照キーを分離する最小AEKey。 */
    private static final class TestKey extends AEKey {
        private final ResourceLocation id;

        private TestKey(String path) {
            id = new ResourceLocation("ae2_crafting_optimizer", path);
        }

        @Override
        public AEKeyType getType() {
            return null;
        }

        @Override
        public AEKey dropSecondary() {
            return this;
        }

        @Override
        public CompoundTag toTag() {
            return new CompoundTag();
        }

        @Override
        public Object getPrimaryKey() {
            return id;
        }

        @Override
        public ResourceLocation getId() {
            return id;
        }

        @Override
        public void writeToPacket(FriendlyByteBuf buffer) {
            // Packetを使わない在庫Snapshot単体試験である。
        }

        @Override
        protected Component computeDisplayName() {
            return Component.literal(id.toString());
        }

        @Override
        public void addDrops(long amount, List<ItemStack> drops, Level level, BlockPos pos) {
            // ワールド内ドロップを作らない在庫Snapshot単体試験である。
        }
    }
}
