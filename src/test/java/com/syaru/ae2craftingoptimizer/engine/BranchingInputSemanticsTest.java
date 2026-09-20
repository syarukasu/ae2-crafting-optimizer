package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.*;
import appeng.api.config.FuzzyMode;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.*;
import appeng.api.storage.AEKeyFilter;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BranchingInputSemanticsTest {
    static AEKey OUT, A, B, RAW, BUCKET;
    @org.junit.jupiter.api.BeforeEach void installRegistryAccess() {
        com.syaru.ae2craftingoptimizer.lifecycle.ACORegistryAccess.install(
                net.minecraft.core.RegistryAccess.fromRegistryOfRegistries(net.minecraft.core.registries.BuiltInRegistries.REGISTRY));
    }
    @org.junit.jupiter.api.AfterEach void clearRegistryAccess() {
        com.syaru.ae2craftingoptimizer.lifecycle.ACORegistryAccess.clear();
    }
    @BeforeAll static void bootstrap() throws Exception {
        ReusableByproductAe2OracleTest.bootstrap();
        com.syaru.ae2craftingoptimizer.TestRegistries.initializeAe2KeyTypes();
        OUT = AEItemKey.of(Items.DIAMOND);
        A = AEItemKey.of(Items.IRON_INGOT);
        B = AEItemKey.of(Items.GOLD_INGOT);
        RAW = AEItemKey.of(Items.COBBLESTONE);
        BUCKET = AEItemKey.of(Items.BUCKET);
    }

    @Test void capturesGenericAddonInputsWithoutClassWhitelist() {
        var p = pattern("generic", List.of(input(List.of(stack(A, 1), stack(B, 1)), 2, k -> true, k -> BUCKET)),
                Map.of(OUT, 1L));
        var result = Ae2CompiledPatternFactory.capture(p, null);
        assertNotNull(result);
        assertFalse(result.exactInputDomain());
        assertSame(p, result.details());
        assertEquals(2, result.compile("p").inputs().get(0).alternatives().size());
    }

    @Test void substitutesStoredInputsAndFuzzyCraftedPrimaryInAe2Order() throws Exception {
        var root = pattern("root", List.of(input(List.of(stack(A, 1), stack(B, 1)), 2, k -> true, k -> null)), Map.of(OUT, 1L));
        var makeB = pattern("b", List.of(exact(RAW, 3)), Map.of(B, 5L));
        for (long a : new long[] {0, 1, 8}) for (long b : new long[] {0, 1, 11}) {
            compare(List.of(root, makeB), Map.of(A, a, B, b, RAW, 30L), 13, false);
            compare(List.of(root, makeB), Map.of(A, a, B, b, RAW, 0L), 13, true);
        }
    }

    @Test void preservesNbtFuzzyInventoryOrderAndRejectsInvalidVariants() throws Exception {
        var keys = new ArrayList<AEKey>();
        for (int i = 0; i < 12; i++) {
            ItemStack item = new ItemStack(Items.PAPER);
            var tag = new net.minecraft.nbt.CompoundTag();
            tag.putInt("variant", i);
            item.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
            keys.add(AEItemKey.of(item));
        }
        var plain = AEItemKey.of(Items.PAPER);
        var p = pattern("nbt", List.of(input(List.of(stack(plain, 1)), 3,
                k -> keys.indexOf(k) % 3 != 0, k -> null)), Map.of(OUT, 1L));
        for (int seed = 0; seed < 40; seed++) {
            Collections.shuffle(keys, new Random(seed));
            Map<AEKey, Long> stock = new LinkedHashMap<>();
            for (int i = 0; i < keys.size(); i++) stock.put(keys.get(i), (long) (i % 5));
            compare(List.of(p), stock, 7, false);
        }
    }

    @Test void preservesFluidAndItemTemplateUnits() throws Exception {
        AEKey water = AEFluidKey.of(Fluids.WATER), filled = AEItemKey.of(Items.WATER_BUCKET);
        var root = pattern("mixed", List.of(input(List.of(stack(water, 1000), stack(filled, 1)), 3,
                k -> true, k -> k.equals(filled) ? BUCKET : null), exact(A, 1)), Map.of(OUT, 1L));
        for (long n : new long[] {0, 750, 1750, 10000}) {
            compare(List.of(root), Map.of(water, n, filled, 7L, A, 99L), 5, false);
        }
    }

    @Test void returnsContainersOnlyAfterAllInputsAndMatchesReuse() throws Exception {
        var reusable = input(List.of(stack(BUCKET, 1)), 1, k -> k.equals(BUCKET), k -> BUCKET);
        var root = pattern("reuse", List.of(reusable, exact(A, 2), exact(BUCKET, 1)), Map.of(OUT, 1L));
        for (long n : new long[] {0, 1, 2, 7}) {
            compare(List.of(root), Map.of(BUCKET, n, A, 100L), 12, false);
        }
        var cheap = pattern("cheap", List.of(reusable, exact(RAW, 1)), Map.of(OUT, 1L));
        compare(List.of(root, cheap), Map.of(BUCKET, 2L, A, 3L, RAW, 30000L), 20000, false);
    }

    @Test void reusesReturnedToolAtTenToThe64WithoutTruncation() {
        var tool = input(List.of(stack(BUCKET, 1)), 1, k -> k.equals(BUCKET), k -> BUCKET);
        var fixture = new Fixture(List.of(pattern("wide", List.of(tool, exact(A, 1)), Map.of(OUT, 1L))),
                Map.of(BUCKET, 1L, A, 1L));
        var n = BigInteger.TEN.pow(64);
        var result = new OrderedBranchingPlanner<>(OUT, fixture.graph::patternsFor, k -> false,
                k -> k.equals(A) ? n : BigInteger.ONE, k -> k.getType().getAmountPerByte(),
                PlanningGuard.none(), 4096, fixture.rules).plan(n, false);
        assertTrue(result.plan().craftable());
        assertEquals(BigInteger.ONE, result.plan().usedInventory().get(BUCKET));
        assertEquals(n, result.plan().usedInventory().get(A));
        assertEquals(n, result.plan().patternExecutions().values().iterator().next());
        assertTrue(result.skippedIterations().compareTo(n.divide(BigInteger.TWO)) > 0);
        assertTrue(fixture.calls.get() < 20, "API observations must not scale with craft count");
    }

    @Test void revalidationDetectsChangedInputSemantics() {
        boolean[] valid = {true};
        var fixture = new Fixture(List.of(pattern("change", List.of(input(List.of(stack(A, 1)), 1,
                k -> valid[0], k -> null)), Map.of(OUT, 1L))), Map.of(A, 1L));
        var p = fixture.graph.patternsFor(OUT).get(0);
        assertTrue(fixture.rules.input(p, 0).observe().apply(A).valid());
        valid[0] = false;
        assertThrows(IllegalStateException.class, fixture.rules::revalidate);
    }


    @Test void preservesChangingToolDamageAndGeneratedFuzzyMembers() throws Exception {
        List<AEKey> tools = new ArrayList<>();
        for (int damage = 0; damage < 4; damage++) {
            ItemStack item = new ItemStack(Items.IRON_PICKAXE);
            item.setDamageValue(damage);
            tools.add(AEItemKey.of(item));
        }
        var tool = input(List.of(stack(tools.get(0), 1)), 1, tools::contains,
                key -> tools.indexOf(key) < 3 ? tools.get(tools.indexOf(key) + 1) : null);
        var root = pattern("damage", List.of(tool, exact(RAW, 1)), Map.of(OUT, 1L));
        var makeTool = pattern("new_tool", List.of(exact(A, 3)), Map.of(tools.get(0), 1L));
        for (int damage = 0; damage < 4; damage++) {
            for (long amount : new long[] {1, 12, 20000}) {
                compare(List.of(root, makeTool),
                        Map.of(tools.get(damage), 1L, A, 16000L, RAW, 20000L), amount, false);
            }
        }
    }

    @Test void freezesReferencedFuzzyInventoryAndExcludesOnlyTheRequestedOutput() {
        ItemStack item = new ItemStack(Items.IRON_PICKAXE);
        item.setDamageValue(5);
        AEKey variant = AEItemKey.of(item), encoded = AEItemKey.of(Items.IRON_PICKAXE);
        KeyCounter stock = new KeyCounter();
        stock.add(encoded, 2);
        stock.add(variant, 7);
        stock.add(RAW, 99);
        var order = stock.findFuzzy(encoded, FuzzyMode.IGNORE_ALL).stream().map(Map.Entry::getKey).toList();
        var frozen = Ae2PlanningInventorySnapshot.captureReferenced(stock, List.of(encoded), encoded);
        stock.set(variant, 100);
        assertEquals(order, frozen.fuzzyKeys(encoded));
        assertEquals(0, frozen.amount(encoded));
        assertEquals(7, frozen.amount(variant));
        assertEquals(0, frozen.amount(RAW));
    }

    @Test void neverAsksInvalidFuzzyStockForItsRemainder() throws Exception {
        ItemStack damaged = new ItemStack(Items.IRON_PICKAXE);
        damaged.setDamageValue(5);
        AEKey invalid = AEItemKey.of(damaged), valid = AEItemKey.of(Items.IRON_PICKAXE);
        var input = input(List.of(stack(valid, 1)), 1, valid::equals, key -> {
            if (!key.equals(valid)) throw new AssertionError("invalid remainder callback");
            return null;
        });
        compare(List.of(pattern("invalid", List.of(input), Map.of(OUT, 1L))),
                Map.of(invalid, 10L, valid, 1L), 3, false);
    }

    @Test void rejectsChangedPatternShapeBeforeAdoption() {
        Map<AEKey, Long> outputs = new LinkedHashMap<>(Map.of(OUT, 1L));
        var fixture = new Fixture(List.of(pattern("shape", List.of(exact(A, 1)), outputs)), Map.of(A, 1L));
        fixture.rules.input(fixture.graph.patternsFor(OUT).get(0), 0);
        outputs.put(OUT, 2L);
        assertThrows(IllegalStateException.class, fixture.rules::revalidate);
    }

    @Test void routesLiveInputCallbacksThroughTheServerBoundary() throws Exception {
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            Thread serverThread = executor.submit(Thread::currentThread).get();
            var callback = input(List.of(stack(A, 1)), 1, key -> {
                assertSame(serverThread, Thread.currentThread());
                return true;
            }, key -> {
                assertSame(serverThread, Thread.currentThread());
                return BUCKET;
            });
            var fixture = new Fixture(List.of(pattern("thread", List.of(callback), Map.of(OUT, 1L))), Map.of(A, 1L));
            var rules = new Ae2BranchingInputRules(fixture.snapshot,
                    Ae2PlanningInventorySnapshot.capture(new KeyCounter()), () -> fixture.service, null,
                    new Ae2BranchingInputRules.ServerCall() {
                        public <T> T call(Supplier<T> action) {
                            try { return executor.submit(action::get).get(); }
                            catch (Exception failure) { throw new RuntimeException(failure); }
                        }
                    }, PlanningGuard.none());
            var captured = rules.input(fixture.graph.patternsFor(OUT).get(0), 0);
            assertEquals(BUCKET, captured.observe().apply(A).remainder());
            rules.revalidate();
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS));
        }
    }

    @Test void fixedProcessingInputsStayOnWorkerAndValidateOnceBeforeAdoption() throws Exception {
        AEKey water = AEFluidKey.of(net.minecraft.world.level.material.Fluids.WATER);
        var pattern = processing(List.of(stack(A, 9), stack(water, 2000)));
        var fixture = new Fixture(List.of(pattern), Map.of(A, 90L, water, 20000L));
        var compiled = fixture.graph.patternsFor(OUT).get(0);
        assertTrue(fixture.snapshot.hasExactInputDomain(compiled.id()));
        for (int repeat = 0; repeat < 1000; repeat++) {
            for (int slot = 0; slot < compiled.inputs().size(); slot++) {
                var rule = fixture.rules.input(compiled, slot);
                AEKey key = rule.templates().get(0).key();
                var actual = pattern.getInputs()[slot];
                assertEquals(actual.getPossibleInputs()[0].amount(), rule.templates().get(0).amount());
                assertEquals(actual.isValid(key, null), rule.observe().apply(key).valid());
                assertEquals(actual.getRemainingKey(key), rule.observe().apply(key).remainder());
                assertFalse(rule.observe().apply(OUT).valid());
            }
        }
        assertEquals(0, fixture.calls.get(), "fixed snapshot calculation must not call the server");
        fixture.rules.revalidate();
        assertEquals(1, fixture.calls.get(), "two fixed slots share one revalidation batch");
        compare(List.of(pattern), Map.of(A, 90L, water, 20000L), 12, false);
        pattern.getInputs()[0].getPossibleInputs()[0] = stack(OUT, 1);
        assertThrows(IllegalStateException.class, fixture.rules::revalidate);
    }

    private static appeng.crafting.pattern.AEProcessingPattern processing(List<GenericStack> inputs) {
        ItemStack definition = new ItemStack(Items.PAPER);
        appeng.crafting.pattern.AEProcessingPattern.encode(definition, inputs, List.of(stack(OUT, 1)));
        return new appeng.crafting.pattern.AEProcessingPattern(AEItemKey.of(definition));
    }

    private static void compare(List<IPatternDetails> patterns, Map<AEKey, Long> stock, long amount,
            boolean craftLess) throws Exception {
        var fixture = new Fixture(patterns, stock);
        var strategy = craftLess ? CalculationStrategy.CRAFT_LESS : CalculationStrategy.REPORT_MISSING_ITEMS;
        var expected = ReusableByproductAe2OracleTest.oracleService(fixture.service, OUT, amount, stock, strategy);
        var result = Ae2AuthoritativeCraftingPlanner.evaluateBranching(fixture.snapshot, OUT, amount, strategy,
                k -> BigInteger.valueOf(stock.getOrDefault(k, 0L)), PlanningGuard.none(), 4096, fixture.rules);
        Map<String, BigInteger> crafts = new HashMap<>();
        expected.patternTimes().forEach((p, n) -> crafts.put(fixture.ids.get(p), BigInteger.valueOf(n)));
        assertEquals(crafts, result.plan().patternExecutions(), "crafts");
        assertEquals(counts(expected.usedItems()), result.plan().usedInventory(), "used");
        assertEquals(counts(expected.missingItems()), result.plan().missing(), "missing");
        assertEquals(counts(expected.emittedItems()), result.plan().emitted(), "emitted");
        assertEquals(expected.finalOutput().amount(), result.plan().requestedAmount().longValueExact());
        assertEquals(expected.bytes(), result.ae2Bytes(), "bytes");
        assertEquals(expected.multiplePaths(), result.multiplePaths());
        fixture.rules.revalidate();
    }

    private static Map<AEKey, BigInteger> counts(KeyCounter values) {
        Map<AEKey, BigInteger> result = new HashMap<>();
        for (var e : values) if (e.getLongValue() > 0) result.put(e.getKey(), BigInteger.valueOf(e.getLongValue()));
        return result;
    }

    static final class Fixture {
        final Map<IPatternDetails, String> ids = new IdentityHashMap<>();
        final Map<String, IPatternDetails> bindings = new HashMap<>();
        final CompiledCraftingGraph<AEKey> graph;
        final ICraftingService service;
        final Ae2PlanningGraphSnapshot snapshot;
        final Ae2BranchingInputRules rules;
        final AtomicInteger calls = new AtomicInteger();
        Fixture(List<IPatternDetails> patterns, Map<AEKey, Long> stock) {
            var compiled = new ArrayList<CompiledPattern<AEKey>>();
            Set<String> exactDomains = new HashSet<>();
            var byKey = new LinkedHashMap<AEKey, List<IPatternDetails>>();
            KeyCounter craftables = new KeyCounter();
            for (var pattern : patterns) {
                var capture = Ae2CompiledPatternFactory.capture(pattern, null);
                assertNotNull(capture);
                String id = capture.fingerprint();
                ids.put(pattern, id); bindings.put(id, pattern);
                if (capture.exactInputDomain()) exactDomains.add(id);
                compiled.add(capture.compile(id));
                for (var output : pattern.getOutputs()) {
                    byKey.computeIfAbsent(output.what(), k -> new ArrayList<>()).add(pattern);
                    craftables.add(output.what(), 1);
                }
            }
            graph = CompiledCraftingGraph.compile(1, compiled);
            service = proxy(ICraftingService.class, (method, args) -> switch (method) {
                case "canEmitFor" -> false;
                case "getCraftingFor" -> byKey.getOrDefault(args[0], List.of());
                case "getFuzzyCraftable" -> craftables.findFuzzy((AEKey) args[0], FuzzyMode.IGNORE_ALL).stream()
                        .map(Map.Entry::getKey).filter(k -> ((AEKeyFilter) args[1]).matches(k)).findFirst().orElse(null);
                default -> throw new AssertionError(method);
            });
            snapshot = proxy(Ae2PlanningGraphSnapshot.class, (method, args) -> switch (method) {
                case "graph" -> graph;
                case "orderedPatternsFor" -> graph.patternsFor((AEKey) args[0]);
                case "registeredPatternCount" -> graph.patternsFor((AEKey) args[0]).size();
                case "isIncompletelyCompiled", "isEmittable" -> false;
                case "hasExactInputDomain" -> exactDomains.contains(args[0]);
                case "pattern" -> bindings.get(args[0]);
                default -> throw new AssertionError(method);
            });
            KeyCounter counter = new KeyCounter(); stock.forEach(counter::set);
            Set<AEKey> referenced = new LinkedHashSet<>();
            for (var p : compiled) {
                referenced.addAll(p.outputs().keySet());
                for (var slot : p.inputs()) for (var value : slot.alternatives()) referenced.add(value.key());
            }
            rules = new Ae2BranchingInputRules(snapshot,
                    Ae2PlanningInventorySnapshot.captureReferenced(counter, referenced, OUT),
                    () -> service, null, new Ae2BranchingInputRules.ServerCall() {
                        public <T> T call(Supplier<T> action) { calls.incrementAndGet(); return action.get(); }
                    }, PlanningGuard.none());
        }
    }

    static GenericStack stack(AEKey key, long n) { return new GenericStack(key, n); }
    static IPatternDetails.IInput exact(AEKey key, long n) {
        return input(List.of(stack(key, 1)), n, key::equals, k -> null);
    }
    static IPatternDetails.IInput input(List<GenericStack> possible, long multiplier,
            Predicate<AEKey> valid, Function<AEKey, AEKey> remainder) {
        return new IPatternDetails.IInput() {
            public GenericStack[] getPossibleInputs() { return possible.toArray(GenericStack[]::new); }
            public long getMultiplier() { return multiplier; }
            public boolean isValid(AEKey key, Level level) { return valid.test(key); }
            public AEKey getRemainingKey(AEKey key) { return remainder.apply(key); }
        };
    }
    static IPatternDetails pattern(String name, List<IPatternDetails.IInput> inputs, Map<AEKey, Long> outputs) {
        ItemStack definition = new ItemStack(Items.PAPER);
        var tag = new net.minecraft.nbt.CompoundTag();
        tag.putString("recipe", name);
        definition.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
        AEItemKey key = AEItemKey.of(definition);
        return new IPatternDetails() {
            public AEItemKey getDefinition() { return key; }
            public IInput[] getInputs() { return inputs.toArray(IInput[]::new); }
            public List<GenericStack> getOutputs() {
                return outputs.entrySet().stream().map(e -> stack(e.getKey(), e.getValue())).toList();
            }
        };
    }
    interface Handler { Object call(String method, Object[] args); }
    @SuppressWarnings("unchecked") static <T> T proxy(Class<T> type, Handler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (p, method, args) -> switch (method.getName()) {
            case "hashCode" -> System.identityHashCode(p);
            case "equals" -> p == args[0];
            default -> handler.call(method.getName(), args);
        });
    }
}
