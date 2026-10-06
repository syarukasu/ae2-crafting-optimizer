package com.ae2vm.addon.nativeengine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.crafting.pattern.AECraftingPattern;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NativeVmFixedCraftingTest {
    @BeforeAll static void bootstrap() throws Exception { NativeVmCaptureTest.bootstrap(); }

    @Test void fixedReturnedNbtPreservesQuantitiesWithoutLiveCandidateReads() throws Exception {
        long observedReads = 0;
        boolean needsLiveAccess = false;
        for (var order : List.of(BigInteger.ONE, BigInteger.TWO, BigInteger.valueOf(1000),
                BigInteger.valueOf(Long.MAX_VALUE), BigInteger.ONE.shiftLeft(63),
                BigInteger.TEN.pow(1024).subtract(BigInteger.ONE))) {
            for (var supplied : List.of(BigInteger.ZERO, order.divide(BigInteger.TWO), order)) {
                try (var fixture = new Fixture(false, true)) {
                    fixture.returnedVariant = 1;
                    var access = new NativeVmAccess();
                    var context = fixture.context(Map.of(key(0), supplied), access);
                    var result = fixture.planWithAccess(context, access, order);
                    assertEquals(supplied, result.used().getOrDefault(key(0), BigInteger.ZERO));
                    assertEquals(order.subtract(supplied), result.missing().getOrDefault(key(0), BigInteger.ZERO));
                    assertFalse(result.used().containsKey(key(1)));
                    assertEquals(Map.of("vm-0", order), result.crafts());
                    System.out.printf("Issue218 fixed returned NBT: bits=%d suppliedBits=%d reads=%d intervals=%d%n",
                            order.bitLength(), supplied.bitLength(), access.metrics().reads(), access.metrics().intervals());
                    observedReads += access.metrics().reads();
                    needsLiveAccess |= context.requiresLiveAccess();
                }
            }
        }
        assertEquals(0, observedReads, "Exact NBT rejection must not acquire world access");
        assertFalse(needsLiveAccess);
    }

    @Test void fixedReturnedNbtCompletesWithTheWorldBoundaryClosed() throws Exception {
        try (var fixture = new Fixture(false, true)) {
            fixture.returnedVariant = 1;
            var order = BigInteger.valueOf(1000);
            var access = new NativeVmAccess();
            var supplied = order.divide(BigInteger.TWO);
            var context = fixture.context(Map.of(key(0), supplied), access);
            access.finish();
            fixture.owner.set(null);
            var result = fixture.executor.submit(() -> context.plan(fixture.output, order, false, ignored -> { }))
                    .get(5, TimeUnit.SECONDS);
            assertEquals(Map.of(key(0), order.subtract(supplied)), result.missing());
            assertEquals(Map.of(key(0), supplied), result.used());
            assertEquals(Map.of("vm-0", order), result.crafts());
        }
    }

    @Test void substitutableReturnedNbtStillObservesAndReusesTheReturnedState() throws Exception {
        try (var fixture = new Fixture(true, true)) {
            fixture.returnedVariant = 1;
            var order = BigInteger.TEN.pow(1024).subtract(BigInteger.ONE);
            var access = new NativeVmAccess();
            var context = fixture.context(Map.of(key(0), BigInteger.ONE), access);
            assertTrue(context.requiresLiveAccess());
            var result = fixture.planWithAccess(context, access, order);
            assertEquals(Map.of(key(0), BigInteger.ONE), result.used());
            assertTrue(result.missing().isEmpty());
            assertEquals(Map.of("vm-0", order), result.crafts());
            assertEquals(1, access.metrics().reads(), "Observe the new substitutable key once, not once per craft");
        }
    }

    @Test void exactProducerDoesNotCloseASubstitutableProducerInTheSameKeyFamily() throws Exception {
        try (var fixed = new Fixture(false, true); var flexible = new Fixture(true, true)) {
            fixed.returnedVariant = 1;
            flexible.returnedVariant = 1;
            var access = new NativeVmAccess();
            when(fixed.grid.getCraftingService().getCraftingFor(fixed.output))
                    .thenReturn(List.of(fixed.pattern, flexible.pattern));
            flexible.owner.set(Thread.currentThread());
            var context = fixed.context(Map.of(key(0), BigInteger.ONE), access);
            assertTrue(context.requiresLiveAccess());
            var order = BigInteger.ONE.shiftLeft(80);
            // Both recipes share the same exclusive worker in this order.
            var original = flexible.owner;
            var future = fixed.executor.submit(() -> original.set(Thread.currentThread()));
            future.get(5, TimeUnit.SECONDS);
            var result = fixed.planWithAccess(context, access, order);
            assertEquals(Map.of(key(0), BigInteger.ONE), result.used());
            assertTrue(result.missing().isEmpty());
            assertEquals(order, result.crafts().values().stream().reduce(BigInteger.ZERO, BigInteger::add));
            assertTrue(access.metrics().reads() > 0);
        }
    }

    @Test void fixedCraftingRejectsNbtVariantsWithoutServerRoundTrips() throws Exception {
        try (var fixture = new Fixture(false, false)) {
            var stock = new LinkedHashMap<AEKey, BigInteger>();
            for (int i = 1; i <= 2048; i++) stock.put(key(i), BigInteger.ONE);
            var capture = fixture.capture(stock);
            var result = capture.plan(fixture.output, BigInteger.ONE, false);
            capture.validate();
            assertEquals(Map.of(key(0), BigInteger.ONE), result.missing());
            assertTrue(result.used().isEmpty());
            System.out.println("Issue218 fixed crafting: " + fixture.timing.summary());
            assertEquals(0, inputCalls(fixture.timing), "Fixed crafting inputs do not need fuzzy NBT callbacks");
        }
    }

    @Test void fixedCraftingRetainsExactPrimaryReturnsAndRevalidation() throws Exception {
        try (var fixture = new Fixture(false, true)) {
            var capture = fixture.capture(Map.of(key(0), BigInteger.ONE, key(1), BigInteger.TEN));
            var order = BigInteger.TEN.pow(1024).subtract(BigInteger.ONE);
            var result = capture.plan(fixture.output, order, false);
            assertEquals(Map.of(key(0), BigInteger.ONE), result.used());
            assertTrue(result.missing().isEmpty());
            assertEquals(Map.of("vm-0", order), result.crafts());
            assertEquals(0, inputCalls(fixture.timing));
            capture.validate();
            fixture.returnPrimary = false;
            assertThrows(LegacyVmState.Changed.class, capture::validate);
        }
    }

    @Test void substitutionEnabledInputsStillConsultTheRecipeOnTheServer() throws Exception {
        try (var fixture = new Fixture(true, false)) {
            var capture = fixture.capture(Map.of(key(1), BigInteger.TWO));
            var result = capture.plan(fixture.output, BigInteger.TWO, false);
            assertEquals(Map.of(key(1), BigInteger.TWO), result.used());
            assertTrue(result.missing().isEmpty());
            assertTrue(inputCalls(fixture.timing) > 0);
            capture.validate();
        }
    }

    @Test void fixedCraftingStockMatrixRemainsExactThrough3402Bits() throws Exception {
        for (var order : List.of(BigInteger.ONE, BigInteger.TWO, BigInteger.valueOf(Long.MAX_VALUE),
                BigInteger.ONE.shiftLeft(63), BigInteger.TEN.pow(1024).subtract(BigInteger.ONE))) {
            for (var available : List.of(BigInteger.ZERO, order.divide(BigInteger.TWO), order)) {
                try (var fixture = new Fixture(false, false)) {
                    var capture = fixture.capture(Map.of(key(0), available, key(1), order));
                    var result = capture.plan(fixture.output, order, false);
                    capture.validate();
                    assertEquals(available, result.used().getOrDefault(key(0), BigInteger.ZERO));
                    assertEquals(order.subtract(available), result.missing().getOrDefault(key(0), BigInteger.ZERO));
                    assertFalse(result.used().containsKey(key(1)));
                    assertEquals(Map.of("vm-0", order), result.crafts());
                    assertEquals(0, inputCalls(fixture.timing));
                }
            }
        }
    }

    @Test void graphSnapshotUsesActualAe2CraftingInputsAndRecipeReturns() throws Exception {
        for (boolean substitute : List.of(false,true)) for (boolean returns : List.of(false,true)) {
            try (var fixture = new Fixture(substitute,returns)) {
                fixture.owner.set(Thread.currentThread());
                var requester = mock(appeng.api.networking.crafting.ICraftingSimulationRequester.class);
                var node=mock(appeng.api.networking.IGridNode.class);
                when(requester.getGridNode()).thenReturn(node); when(node.getGrid()).thenReturn(fixture.grid);
                var stock=new LinkedHashMap<AEKey,BigInteger>();
                stock.put(key(0),BigInteger.ONE); stock.put(key(1),BigInteger.valueOf(99));
                var snapshot=NativeOrderSnapshot.capture(fixture.grid,fixture.level,requester,fixture.output,
                        new VmAccounting.Stock(stock,true,stock.keySet()),fixture.timing,()->false);
                // The bound recipe becomes unusable after capture. No worker callback is permitted.
                fixture.owner.set(null);
                var executor=Executors.newSingleThreadExecutor();
                try {
                    var calculated=executor.submit(()->NativeGraphCalculation.plan(snapshot,BigInteger.valueOf(100),false)).get();
                    var result=calculated.quantities();
                    if (returns || substitute) assertTrue(result.missing().isEmpty());
                    else assertEquals(Map.of(key(0),BigInteger.valueOf(99)),result.missing());
                    assertEquals(BigInteger.valueOf(100),result.crafts().get("graph-0"));
                    if (!substitute) assertFalse(result.used().containsKey(key(1)));
                } finally { executor.shutdownNow(); }
            }
        }
    }

    private static long inputCalls(NativeVmTiming timing) {
        var match = java.util.regex.Pattern.compile("INPUTS:(\\d+)").matcher(timing.summary());
        return match.find() ? Long.parseLong(match.group(1)) : 0;
    }

    private static AEItemKey key(int variant) {
        var stack = new ItemStack(Items.PAPER);
        com.ae2vm.addon.PortStacks.putInt(stack, "variant", variant);
        return AEItemKey.of(stack);
    }

    private static final class Fixture implements AutoCloseable {
        final AEKey output = AEItemKey.of(Items.DIAMOND);
        final IGrid grid = mock(IGrid.class);
        final Level level = mock(Level.class);
        final MinecraftServer server = mock(MinecraftServer.class);
        final ExecutorService executor = Executors.newSingleThreadExecutor();
        final AtomicReference<Thread> owner = new AtomicReference<>();
        final NativeVmTiming timing = new NativeVmTiming();
        final AECraftingPattern pattern;
        volatile boolean returnPrimary;
        volatile int returnedVariant;

        @SuppressWarnings("unchecked") Fixture(boolean substitute, boolean returnPrimary) throws Exception {
            this.returnPrimary = returnPrimary;
            when(level.getServer()).thenReturn(server);
            when(server.isSameThread()).thenAnswer(call -> Thread.currentThread() == owner.get());
            when(server.submit(any(java.util.function.Supplier.class))).thenAnswer(call -> {
                java.util.function.Supplier<?> action = call.getArgument(0);
                return CompletableFuture.supplyAsync(() -> {
                    owner.set(Thread.currentThread());
                    return action.get();
                }, executor);
            });
            var id = ResourceLocation.fromNamespaceAndPath("aco_test", "fixed_crafting");
            var recipe = mock(CraftingRecipe.class);
            when(recipe.matches(any(), any())).thenAnswer(call -> {
                assertSame(owner.get(), Thread.currentThread());
                return true;
            });
            when(recipe.assemble(any(), any())).thenReturn(new ItemStack(Items.DIAMOND));
            when(recipe.getIngredients()).thenReturn(NonNullList.of(Ingredient.EMPTY, Ingredient.of(Items.PAPER)));
            when(recipe.getRemainingItems(any())).thenAnswer(call -> {
                assertSame(owner.get(), Thread.currentThread());
                var result = NonNullList.withSize(9, ItemStack.EMPTY);
                if (this.returnPrimary) result.set(0, key(this.returnedVariant).toStack());
                return result;
            });
            var tag = new CompoundTag();
            tag.putString("recipe", id.toString());
            tag.putBoolean("substitute", substitute);
            var inputs = new ListTag();
            inputs.add(key(0).toStack().save(com.ae2vm.addon.PortStacks.REGISTRIES));
            for (int i = 1; i < 9; i++) inputs.add(new CompoundTag());
            tag.put("in", inputs);
            var definition = new ItemStack(Items.PAPER);
            definition.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
            // Headless JUnit has no AE2 access transformer for RecipeManager.byType.
            // Supply the constructor's fields, then use the real private Input and recipe callbacks.
            pattern = mock(AECraftingPattern.class, CALLS_REAL_METHODS);
            set(pattern, "definition", AEItemKey.of(definition));
            set(pattern, "canSubstitute", substitute);
            set(pattern, "recipe", recipe);
            set(pattern, "output", new ItemStack(Items.DIAMOND));
            var sparse = new appeng.api.stacks.GenericStack[9];
            sparse[0] = new appeng.api.stacks.GenericStack(key(0), 1);
            set(pattern, "sparseInputs", java.util.Arrays.asList(sparse));
            var stacks = new java.util.ArrayList<>(java.util.Collections.nCopies(9, ItemStack.EMPTY));
            stacks.set(0, key(0).toStack());
            set(pattern, "positionedPattern", net.minecraft.world.item.crafting.CraftingInput.ofPositioned(3, 3, stacks));
            set(pattern, "isValidCache", new Map[9]);
            var type = Class.forName("appeng.crafting.pattern.AECraftingPattern$Input");
            var constructor = type.getDeclaredConstructor(AECraftingPattern.class, int.class, appeng.api.stacks.GenericStack.class);
            constructor.setAccessible(true);
            appeng.api.crafting.IPatternDetails.IInput slot;
            // Paper has no fluid capability; Forge capability token transformation is absent here.
            try (var ignored = mockStatic(appeng.api.behaviors.ContainerItemStrategies.class)) {
                slot = (appeng.api.crafting.IPatternDetails.IInput) constructor.newInstance(pattern, 0, sparse[0]);
            }
            doReturn(new appeng.api.crafting.IPatternDetails.IInput[]{slot}).when(pattern).getInputs();
            doReturn(List.of(new appeng.api.stacks.GenericStack(output, 1))).when(pattern).getOutputs();
            var service = mock(ICraftingService.class);
            when(grid.getCraftingService()).thenReturn(service);
            when(service.getCraftingFor(output)).thenReturn(List.of(pattern));
        }

        private static void set(AECraftingPattern pattern, String name, Object value) throws Exception {
            var field = AECraftingPattern.class.getDeclaredField(name);
            field.setAccessible(true);
            field.set(pattern, value);
        }

        NativeVmPlanningContext context(Map<AEKey, BigInteger> amounts, NativeVmAccess access) {
            owner.set(Thread.currentThread());
            var requester = mock(appeng.api.networking.crafting.ICraftingSimulationRequester.class);
            var node = mock(appeng.api.networking.IGridNode.class);
            when(requester.getGridNode()).thenReturn(node);
            when(node.getGrid()).thenReturn(grid);
            var context = new NativeVmPlanningContext(grid, level, requester,
                    new VmAccounting.Stock(amounts, true, amounts.keySet()), access, timing);
            context.prepare(output);
            return context;
        }

        VmQuantities planWithAccess(NativeVmPlanningContext context, NativeVmAccess access, BigInteger order)
                throws Exception {
            var future = executor.submit(() -> {
                owner.set(Thread.currentThread());
                try { return context.plan(output, order, false, ignored -> { }); }
                finally { access.finish(); }
            });
            try {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                while (!future.isDone() && System.nanoTime() < deadline) {
                    access.simulateFor(1000);
                    Thread.sleep(1);
                }
                return future.get(1, TimeUnit.SECONDS);
            } finally { access.finish(); }
        }

        NativeVmCapture capture(Map<AEKey, BigInteger> amounts) {
            return new NativeVmCapture(grid, level, new VmAccounting() {
                public BigInteger maximumCount() { return BigInteger.ONE.shiftLeft(4096); }
                public Stock exactStock(IGrid ignored) { return new Stock(amounts, true, amounts.keySet()); }
                public ICraftingPlan wideResult(IGrid ignored, Level world, NativeVmResult result) { throw new AssertionError(); }
            }, ignored -> { }, timing);
        }

        public void close() throws Exception {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }
}
