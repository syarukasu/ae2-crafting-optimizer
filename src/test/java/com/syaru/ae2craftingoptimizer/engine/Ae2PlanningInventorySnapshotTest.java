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
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
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

    @Test
    void strictTopologyAdoptsCoupledOutputsOnlyWithCurrentExactCapture() {
        AEKey root = new TestKey("circuit"), part = new TestKey("part"),
                gas = new TestKey("chemical"), raw = new TestKey("raw");
        var assembly = new CompiledPattern<AEKey>("assemble", List.of(
                new CompiledPattern.InputSlot<>(List.of(new CompiledPattern.Stack<>(part, 1L))),
                new CompiledPattern.InputSlot<>(List.of(new CompiledPattern.Stack<>(gas, 1000L)))),
                Map.of(root, 1L), true);
        var split = new CompiledPattern<AEKey>("split", List.of(new CompiledPattern.InputSlot<>(
                List.of(new CompiledPattern.Stack<>(raw, 1L)))), Map.of(part, 1L, gas, 1000L), true);
        var graph = CompiledCraftingGraph.compile(1L, List.of(assembly, split));
        var program = CompiledRootProgram.tryCompile(graph, root, k -> false).orElseThrow();
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
        org.junit.jupiter.api.Assertions.assertNotNull(Ae2StrictCraftingTopology.compile(snapshot, program));
        var plan = program.planLong(5, program.captureLongInventory(k -> k == raw ? 5 : 0), PlanningGuard.none());
        assertEquals(Map.of("assemble", 5L, "split", 5L), plan.patternExecutions());
        assertEquals(Map.of(raw, 5L), plan.usedInventory());
        exact.set(false);
        org.junit.jupiter.api.Assertions.assertNull(Ae2StrictCraftingTopology.compile(snapshot, program));
        var stale = CompiledRootProgram.tryCompile(CompiledCraftingGraph.compile(2L, List.of(assembly, split)),
                root, k -> false).orElseThrow();
        exact.set(true);
        org.junit.jupiter.api.Assertions.assertNull(Ae2StrictCraftingTopology.compile(snapshot, stale));
    }

    @Test
    void strictTopologyAcceptsSharedSingleOutputInputsWithExactByteTrace() {
        AEKey root = new TestKey("root"), left = new TestKey("left"), right = new TestKey("right"),
                shared = new TestKey("shared"), raw = new TestKey("raw");
        var graph = CompiledCraftingGraph.compile(1L, List.of(
                recipe("root", root, 1, left, right), recipe("left", left, 1, shared),
                recipe("right", right, 1, shared), recipe("shared", shared, 3, raw)));
        var program = CompiledRootProgram.tryCompile(graph, root, k -> false).orElseThrow();
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
        org.junit.jupiter.api.Assertions.assertNotNull(Ae2StrictCraftingTopology.compile(snapshot, program));
        assertTrue(program.usesOrderedAccounting());
        var plan = program.planLong(5, program.captureLongInventory(k -> k == raw ? 4 : 0), PlanningGuard.none());
        assertEquals(Map.of("root", 5L, "left", 5L, "right", 5L, "shared", 4L), plan.patternExecutions());
        assertEquals(Map.of(raw, 4L), plan.usedInventory());
        assertTrue(plan.missing().isEmpty());
        org.junit.jupiter.api.Assertions.assertNotNull(plan.trace());
        exact.set(false);
        org.junit.jupiter.api.Assertions.assertNull(Ae2StrictCraftingTopology.compile(snapshot, program));
    }

    @Test
    void realSnapshotDoesNotRevalidateUnusedEmitterProducers() throws Exception {
        AEKey root = new TestKey("root"), feed = new TestKey("feed");
        var graph = CompiledCraftingGraph.compile(1L, List.of(new CompiledPattern<AEKey>("root",
                List.of(new CompiledPattern.InputSlot<>(List.of(new CompiledPattern.Stack<>(feed, 2)))),
                Map.of(root, 1L, feed, 1L), true)));
        for (int count : new int[] {0, 2}) {
            var snapshot = snapshot(graph, Map.of(root, 1, feed, count), Set.of(feed), Set.of(feed));
            var outcome = snapshot.rootProgramOutcome(root);
            assertEquals(RootProgramFailure.NONE, outcome.failure());
            var program = outcome.program().orElseThrow();
            org.junit.jupiter.api.Assertions.assertNotNull(snapshot.strictTopology(program).orElseThrow());
            var plan = program.planLong(5, program.captureLongInventory(k -> 0L), PlanningGuard.none());
            assertEquals(Map.of(feed, 10L), plan.emitted());
            assertEquals(Map.of("root", 5L), plan.patternExecutions());
            assertTrue(plan.usedInventory().isEmpty());
        }
    }

    @Test
    void realSnapshotStillRejectsIncompleteAndAmbiguousNonEmitters() throws Exception {
        AEKey root = new TestKey("root"), raw = new TestKey("raw");
        var graph = CompiledCraftingGraph.compile(1L, List.of(recipe("root", root, 1, raw)));
        assertEquals(RootProgramFailure.INCOMPLETE_PATTERN_SNAPSHOT,
                snapshot(graph, Map.of(root, 1, raw, 1), Set.of(), Set.of(raw))
                        .rootProgramOutcome(root).failure());
        assertEquals(RootProgramFailure.MULTIPLE_PRODUCERS,
                snapshot(graph, Map.of(root, 2, raw, 0), Set.of(), Set.of())
                        .rootProgramOutcome(root).failure());
    }

    private static CompiledPattern<AEKey> recipe(String id, AEKey out, long count, AEKey... inputs) {
        return new CompiledPattern<>(id, java.util.Arrays.stream(inputs).map(k ->
                new CompiledPattern.InputSlot<AEKey>(List.of(new CompiledPattern.Stack<>(k, 1))))
                .toList(), Map.of(out, count), true);
    }

    private static Ae2PlanningGraphSnapshot snapshot(CompiledCraftingGraph<AEKey> graph,
            Map<AEKey, Integer> registered, Set<AEKey> emitters, Set<AEKey> incomplete) throws Exception {
        // Exercise the production snapshot checks, not a proxy that bypasses compileRootOutcome.
        var type = Class.forName(Ae2ImmutablePlanningGraphCache.class.getName() + "$Snapshot");
        var ctor = type.getDeclaredConstructor(CompiledCraftingGraph.class, IdentityHashMap.class,
                Map.class, Map.class, Set.class, Set.class, Set.class, long.class);
        ctor.setAccessible(true);
        var exact = graph.patterns().stream().map(CompiledPattern::id).collect(java.util.stream.Collectors.toSet());
        return (Ae2PlanningGraphSnapshot) ctor.newInstance(graph, new IdentityHashMap<>(), Map.of(),
                registered, incomplete, emitters, exact, 1L);
    }

    /** Minecraft Registry初期化なしでKeyCounterの参照キーを分離する最小AEKey。 */
    private static final class TestKey extends AEKey {
        private final ResourceLocation id;

        private TestKey(String path) {
            id = ResourceLocation.fromNamespaceAndPath("ae2_crafting_optimizer", path);
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
        public CompoundTag toTag(HolderLookup.Provider registries) {
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
        public void writeToPacket(RegistryFriendlyByteBuf buffer) {
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

        @Override
        public boolean hasComponents() {
            return false;
        }
    }
}
