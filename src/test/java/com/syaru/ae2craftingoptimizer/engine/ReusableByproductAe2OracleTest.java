package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.*;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.*;
import appeng.crafting.CraftingCalculation;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.util.*;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ReusableByproductAe2OracleTest {
    @BeforeAll
    static void bootstrap() throws Exception {
        com.syaru.ae2craftingoptimizer.TestRegistries.initialize();
    }

    @Test
    void comparesActualAe2TreeAndInventoryForByproductOrders() throws Exception {
        AEKey out = AEItemKey.of(Items.DIAMOND), a = AEItemKey.of(Items.IRON_INGOT),
                b = AEItemKey.of(Items.GOLD_INGOT), raw = AEItemKey.of(Items.COBBLESTONE);
        for (boolean reverse : new boolean[] {false, true}) {
            for (long stock : new long[] {0, 1, 5, 100}) {
                var aSlot = slot(a, 2, 1);
                var bSlot = slot(b, 5, 1);
                var root = new CompiledPattern<>("root", reverse ? List.of(bSlot, aSlot) : List.of(aSlot, bSlot),
                        Map.of(out, 1L), true);
                var split = new CompiledPattern<>("split", List.of(slot(raw, 3, 1)),
                        Map.of(a, 3L, b, 7L), true);
                compare(List.of(root, split), out, 7, Map.of(raw, 300L, b, stock));
                compare(List.of(root, split), out, 7, Map.of(raw, 2L, b, stock));
            }
        }
    }

    @Test
    void comparesFluidQuantumAndPartialTemplates() throws Exception {
        AEKey out = AEItemKey.of(Items.DIAMOND), a = AEItemKey.of(Items.IRON_INGOT),
                fluid = AEFluidKey.of(net.minecraft.world.level.material.Fluids.WATER),
                raw = AEItemKey.of(Items.COBBLESTONE);
        var root = new CompiledPattern<>("root", List.of(slot(a, 1, 1), slot(fluid, 3000, 1000)),
                Map.of(out, 1L), true);
        var split = new CompiledPattern<>("split", List.of(slot(raw, 2, 1)),
                Map.of(a, 2L, fluid, 1500L), true);
        for (long stock : new long[] {0, 250, 750, 2000, 12500}) {
            compare(List.of(root, split), out, 3, Map.of(raw, 50L, fluid, stock));
        }
    }

    @Test
    void comparesFiveHundredSeededSharedDagPlansWithActualAe2() throws Exception {
        var random = new Random(185);
        AEKey out = AEItemKey.of(Items.DIAMOND), raw = AEItemKey.of(Items.COBBLESTONE);
        AEKey[] left = {AEItemKey.of(Items.IRON_INGOT), AEItemKey.of(Items.GOLD_INGOT), AEItemKey.of(Items.EMERALD)};
        AEKey[] right = {AEItemKey.of(Items.COPPER_INGOT), AEItemKey.of(Items.REDSTONE), AEItemKey.of(Items.QUARTZ)};
        for (int trial = 0; trial < 500; trial++) {
            List<CompiledPattern<AEKey>> patterns = new ArrayList<>();
            Map<AEKey, Long> stock = new HashMap<>();
            stock.put(raw, (long) random.nextInt(100));
            for (int stage = 0; stage < 3; stage++) {
                var inputs = stage == 0 ? List.of(slot(raw, 1 + random.nextInt(4), 1))
                        : List.of(slot(left[stage - 1], 1 + random.nextInt(4), 1),
                                slot(right[stage - 1], 1 + random.nextInt(4), 1));
                patterns.add(new CompiledPattern<>("stage" + stage, inputs,
                        Map.of(left[stage], (long) (1 + random.nextInt(4)),
                                right[stage], (long) (1 + random.nextInt(4))), true));
                stock.put(left[stage], (long) random.nextInt(10));
                stock.put(right[stage], (long) random.nextInt(10));
            }
            var inputs = new ArrayList<>(List.of(slot(left[2], 1 + random.nextInt(4), 1),
                    slot(right[2], 1 + random.nextInt(4), 1), slot(right[0], 1 + random.nextInt(4), 1)));
            Collections.shuffle(inputs, random);
            patterns.add(new CompiledPattern<>("root", inputs, Map.of(out, 1L), true));
            compare(patterns, out, 1 + random.nextInt(20), stock);
        }
    }

    @Test
    void comparesDistinctNbtKeysWithoutSubstitutingByproducts() throws Exception {
        var first = new net.minecraft.world.item.ItemStack(Items.PAPER);
        var firstTag = new net.minecraft.nbt.CompoundTag();
        firstTag.putString("variant", "first");
        first.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.of(firstTag));
        var second = new net.minecraft.world.item.ItemStack(Items.PAPER);
        var secondTag = new net.minecraft.nbt.CompoundTag();
        secondTag.putString("variant", "second");
        second.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,
                net.minecraft.world.item.component.CustomData.of(secondTag));
        AEKey a = AEItemKey.of(first), b = AEItemKey.of(second);
        assertNotEquals(a, b);
        AEKey out = AEItemKey.of(Items.DIAMOND), raw = AEItemKey.of(Items.COBBLESTONE);
        var root = new CompiledPattern<>("root", List.of(slot(a, 1, 1), slot(b, 3, 1)), Map.of(out, 1L), true);
        var split = new CompiledPattern<>("split", List.of(slot(raw, 1, 1)), Map.of(a, 2L, b, 1L), true);
        compare(List.of(root, split), out, 8, Map.of(a, 100L, b, 1L, raw, 100L));
        compare(List.of(root, split), out, 8, Map.of(a, 1L, b, 100L, raw, 100L));
    }

    @Test
    void comparesSharedSingleOutputDagsAgainstActualAe2() throws Exception {
        var random = new Random(190);
        AEKey out = AEItemKey.of(Items.DIAMOND), raw = AEItemKey.of(Items.COBBLESTONE),
                shared = AEItemKey.of(Items.IRON_INGOT), left = AEItemKey.of(Items.GOLD_INGOT),
                right = AEItemKey.of(Items.REDSTONE);
        for (int trial = 0; trial < 250; trial++) {
            var inputs = new ArrayList<>(List.of(slot(left, 1 + random.nextInt(4), 1),
                    slot(right, 1 + random.nextInt(4), 1), slot(shared, 1 + random.nextInt(3), 1)));
            Collections.shuffle(inputs, random);
            var patterns = List.of(new CompiledPattern<>("root", inputs, Map.of(out, 1L), true),
                    new CompiledPattern<>("left", List.of(slot(shared, 1 + random.nextInt(4), 1)),
                            Map.of(left, (long) (1 + random.nextInt(4))), true),
                    new CompiledPattern<>("right", List.of(slot(shared, 1 + random.nextInt(4), 1)),
                            Map.of(right, (long) (1 + random.nextInt(4))), true),
                    new CompiledPattern<>("shared", List.of(slot(raw, 1 + random.nextInt(4), 1)),
                            Map.of(shared, (long) (1 + random.nextInt(4))), true));
            compare(patterns, out, 1 + random.nextInt(20), Map.of(raw, (long) random.nextInt(200),
                    left, (long) random.nextInt(20), right, (long) random.nextInt(20),
                    shared, (long) random.nextInt(20)));
        }
    }

    @Test
    void comparesRepeatedFluidSlotsAndWholeTemplatesAgainstActualAe2() throws Exception {
        AEKey out = AEItemKey.of(Items.DIAMOND), fluid = AEFluidKey.of(net.minecraft.world.level.material.Fluids.WATER),
                raw = AEItemKey.of(Items.COBBLESTONE);
        var root = new CompiledPattern<>("root", List.of(slot(fluid, 2000, 1000), slot(fluid, 3000, 1000)),
                Map.of(out, 1L), true);
        var fluidRecipe = new CompiledPattern<>("fluid", List.of(slot(raw, 1, 1)), Map.of(fluid, 1500L), true);
        for (long amount : new long[] {1, 5, 17}) {
            for (long fluidStock : new long[] {0, 750, 2250, 12500}) {
                compare(List.of(root, fluidRecipe), out, amount, Map.of(fluid, fluidStock, raw, 100L));
                compare(List.of(root, fluidRecipe), out, amount, Map.of(fluid, fluidStock, raw, 0L));
            }
        }
    }

    private static CompiledPattern.InputSlot<AEKey> slot(AEKey key, long amount, long quantum) {
        return new CompiledPattern.InputSlot<>(List.of(new CompiledPattern.Stack<>(key, amount)), quantum);
    }

    @Test
    void comparesEmitterPrunedRecyclingAgainstActualAe2() throws Exception {
        AEKey out = AEItemKey.of(Items.DIAMOND), a = AEItemKey.of(Items.IRON_INGOT),
                b = AEItemKey.of(Items.GOLD_INGOT), raw = AEItemKey.of(Items.COBBLESTONE);
        for (boolean reverse : new boolean[] {false, true}) {
            var inputs = reverse ? List.of(slot(b, 5, 1), slot(a, 2, 1))
                    : List.of(slot(a, 2, 1), slot(b, 5, 1));
            var root = new CompiledPattern<>("root", inputs, Map.of(out, 1L), true);
            var split = new CompiledPattern<>("split", List.of(slot(raw, 3, 1)), Map.of(a, 3L, b, 7L), true);
            var recycle = new CompiledPattern<>("unused-recycle", List.of(slot(out, 1, 1)), Map.of(b, 2L), true);
            for (long stock : new long[] {0, 2, 200}) {
                compare(List.of(root, split, recycle), out, 7, Map.of(raw, 30L, b, stock), Set.of(b));
            }
        }
    }

    private static void compare(List<CompiledPattern<AEKey>> patterns, AEKey out, long amount,
            Map<AEKey, Long> stock) throws Exception {
        compare(patterns, out, amount, stock, Set.of());
    }

    private static void compare(List<CompiledPattern<AEKey>> patterns, AEKey out, long amount,
            Map<AEKey, Long> stock, Set<AEKey> emitters) throws Exception {
        var expectedResult = oracle(patterns, out, amount, stock, emitters, CalculationStrategy.REPORT_MISSING_ITEMS);
        var expected = expectedResult.plan();
        var ids = expectedResult.ids();
        var program = CompiledRootProgram.tryCompile(CompiledCraftingGraph.compile(1, patterns),
                out, emitters::contains).orElseThrow();
        var actual = program.planLong(amount, program.captureLongInventory(k -> stock.getOrDefault(k, 0L)),
                PlanningGuard.none());
        Map<String, Long> crafts = new HashMap<>();
        expected.patternTimes().forEach((detail, times) -> crafts.put(ids.get(detail), times));
        assertEquals(crafts, actual.patternExecutions());
        assertEquals(counts(expected.usedItems()), actual.usedInventory());
        assertEquals(counts(expected.missingItems()), actual.missing());
        assertEquals(counts(expected.emittedItems()), actual.emitted());
        assertEquals(expected.simulation(), !actual.craftable());
        assertNotNull(actual.trace(), "shared/co-product plans need ordered CPU byte accounting");
        assertEquals(expected.bytes(), ExactCraftingByteCounter.calculate(actual.trace(),
                key -> key.getType().getAmountPerByte()));
        assertEquals(BigInteger.valueOf(expected.bytes()), BigExactCraftingByteCounter.calculate(actual.trace(),
                key -> key.getType().getAmountPerByte(), 4096));
    }

    record Oracle(ICraftingPlan plan, Map<IPatternDetails, String> ids) { }

    static Oracle oracle(List<CompiledPattern<AEKey>> patterns, AEKey out, long amount,
            Map<AEKey, Long> stock, Set<AEKey> emitters, CalculationStrategy strategy) throws Exception {
        var byKey = new HashMap<AEKey, List<IPatternDetails>>();
        var ids = new IdentityHashMap<IPatternDetails, String>();
        for (var pattern : patterns) {
            var inputs = pattern.inputs().stream().map(slot -> proxy(IPatternDetails.IInput.class, (method, args) -> {
                var input = slot.alternatives().get(0);
                return switch (method) {
                    case "getPossibleInputs" -> new GenericStack[] {new GenericStack(input.key(), slot.templateAmount())};
                    case "getMultiplier" -> input.amount() / slot.templateAmount();
                    case "isValid" -> input.key().equals(args[0]);
                    case "getRemainingKey" -> null;
                    default -> throw new AssertionError(method);
                };
            })).toArray(IPatternDetails.IInput[]::new);
            var outputs = pattern.outputs().entrySet().stream().map(e -> new GenericStack(e.getKey(), e.getValue()))
                    .toList();
            IPatternDetails detail = proxy(IPatternDetails.class, (method, args) -> switch (method) {
                case "getInputs" -> inputs;
                case "getOutputs" -> outputs;
                case "getDefinition" -> AEItemKey.of(Items.PAPER);
                case "supportsPushInputsToExternalInventory" -> true;
                default -> throw new AssertionError(method);
            });
            ids.put(detail, pattern.id());
            pattern.outputs().keySet().forEach(key -> byKey.computeIfAbsent(key, k -> new ArrayList<>()).add(detail));
        }
        ICraftingService service = proxy(ICraftingService.class, (method, args) -> switch (method) {
            case "canEmitFor" -> emitters.contains(args[0]);
            case "getCraftingFor" -> byKey.getOrDefault(args[0], List.of());
            case "getFuzzyCraftable" -> null;
            default -> throw new AssertionError(method);
        });
        return new Oracle(oracleService(service, out, amount, stock, strategy), ids);
    }

    static ICraftingPlan oracleService(ICraftingService service, AEKey out, long amount,
            Map<AEKey, Long> stock, CalculationStrategy strategy) throws Exception {
        var cachedInventory = new KeyCounter();
        stock.forEach(cachedInventory::set);
        var storage = proxy(appeng.api.networking.storage.IStorageService.class, (method, args) -> {
            assertEquals("getCachedInventory", method);
            return cachedInventory;
        });
        IGrid grid = proxy(IGrid.class, (method, args) -> switch (method) {
            case "getCraftingService" -> service;
            case "getStorageService" -> storage;
            default -> throw new AssertionError(method);
        });
        IGridNode node = proxy(IGridNode.class, (method, args) -> {
            assertEquals("getGrid", method);
            return grid;
        });
        ICraftingSimulationRequester requester = proxy(ICraftingSimulationRequester.class, (method, args) ->
                switch (method) {
                    case "getGridNode" -> node;
                    case "getActionSource" -> null;
                    default -> throw new AssertionError(method);
                });
        var job = new CraftingCalculation(null, grid, requester, new GenericStack(out, amount),
                strategy);
        for (String name : List.of("running")) {
            var field = CraftingCalculation.class.getDeclaredField(name);
            field.setAccessible(true);
            field.setBoolean(job, true);
        }
        var time = CraftingCalculation.class.getDeclaredField("time");
        time.setAccessible(true);
        time.setInt(job, Integer.MAX_VALUE);
        // Run AE2's complete success-then-missing calculation, not a copied reference algorithm.
        var compute = CraftingCalculation.class.getDeclaredMethod("computePlan");
        compute.setAccessible(true);
        var expected = (ICraftingPlan) compute.invoke(job);

        return expected;
    }

    private static Map<AEKey, Long> counts(KeyCounter counter) {
        Map<AEKey, Long> result = new HashMap<>();
        for (var e : counter) {
            if (e.getLongValue() > 0) result.put(e.getKey(), e.getLongValue());
        }
        return result;
    }

    private interface Handler { Object call(String method, Object[] args); }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Handler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) -> {
            return switch (method.getName()) {
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == args[0];
                case "toString" -> type.getSimpleName();
                default -> handler.call(method.getName(), args);
            };
        });
    }
}
