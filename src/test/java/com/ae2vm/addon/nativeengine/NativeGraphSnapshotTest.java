package com.ae2vm.addon.nativeengine;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NativeGraphSnapshotTest {
    @BeforeAll static void bootstrap() throws Exception { NativeVmCaptureTest.bootstrap(); }

    @Test void changingWorldAndPatternAfterCaptureCannotChangeWorkerArithmetic() throws Exception {
        var fixture = new Fixture();
        var snapshot = fixture.capture(BigInteger.valueOf(200));
        verify(fixture.input,times(1)).isValid(fixture.raw,fixture.level);
        verify(fixture.input,times(1)).getRemainingKey(fixture.raw);
        verify(fixture.input,times(1)).getMultiplier();
        clearInvocations(fixture.input,fixture.pattern,fixture.service,fixture.grid,fixture.level,fixture.requester);
        when(fixture.input.isValid(any(),any())).thenThrow(new AssertionError("live worker read"));
        when(fixture.input.getRemainingKey(any())).thenThrow(new AssertionError("live worker remainder"));
        when(fixture.pattern.getOutputs()).thenThrow(new AssertionError("live pattern read"));
        clearInvocations(fixture.input,fixture.pattern);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var plan = executor.submit(() -> NativeGraphCalculation.plan(snapshot,BigInteger.valueOf(100),false)).get();
            assertEquals(Map.of(fixture.raw,BigInteger.valueOf(200)),plan.quantities().used());
            assertTrue(plan.quantities().missing().isEmpty());
            assertEquals(BigInteger.valueOf(100),plan.quantities().crafts().get("graph-0"));
        } finally { executor.shutdownNow(); }
        verifyNoInteractions(fixture.input,fixture.pattern,fixture.service,fixture.grid,fixture.level,fixture.requester);
    }

    @Test void wideInputsMissingAndReturnsRemainExactOnTheConnectedCalculator() {
        var f = new Fixture();
        var emptyBucket = AEItemKey.of(Items.BUCKET);
        when(f.input.getRemainingKey(f.raw)).thenReturn(emptyBucket);
        BigInteger request=BigInteger.ONE.shiftLeft(3402).add(BigInteger.ONE);
        var snapshot = f.capture(BigInteger.valueOf(7));
        var plan=NativeGraphCalculation.plan(snapshot,request,false);
        assertEquals(request,plan.quantities().requested());
        assertEquals(request.multiply(BigInteger.TWO).subtract(BigInteger.valueOf(7)),plan.quantities().missing().get(f.raw));
        assertEquals(request,plan.graphPlan().remaining().get(snapshot.keys().indexOf(emptyBucket)));
        assertTrue(plan.quantities().integerCharges().bitLength()>63);
    }

    @Test void independentOrdersNeverReuseInventoryOrValidityAnswers() {
        var f=new Fixture();
        var first=f.capture(BigInteger.TWO);
        var second=f.capture(BigInteger.ZERO);
        assertFalse(NativeGraphCalculation.plan(first,BigInteger.ONE,false).graphPlan().simulation());
        assertEquals(Map.of(f.raw,BigInteger.TWO),NativeGraphCalculation.plan(second,BigInteger.ONE,false).quantities().missing());
        verify(f.input,times(2)).getRemainingKey(f.raw);
    }

    @Test void actualEntryCompletesOnOneAe2PoolTaskWithoutWorkerCallbacks() throws Exception {
        var f = new Fixture();
        var server = mock(net.minecraft.server.MinecraftServer.class);
        when(f.level.getServer()).thenReturn(server);
        when(server.isSameThread()).thenReturn(true);
        var owner = Thread.currentThread();
        when(f.input.isValid(f.raw,f.level)).thenAnswer(call -> {
            assertSame(owner,Thread.currentThread()); return true;
        });
        var executor = Executors.newSingleThreadExecutor();
        try {
            doAnswer(call -> {
                assertSame(owner,Thread.currentThread());
                when(f.input.isValid(any(),any())).thenThrow(new AssertionError("input access after capture"));
                when(f.pattern.getOutputs()).thenThrow(new AssertionError("pattern access after capture"));
                executor.execute(call.getArgument(0));
                return null;
            }).when((NativeVmHook)f.service).vm$execute(any());
            var plan = NativeVm.calculate(f.grid,f.level,f.requester,f.output,BigInteger.valueOf(100),
                    appeng.api.networking.crafting.CalculationStrategy.REPORT_MISSING_ITEMS)
                    .get(10,java.util.concurrent.TimeUnit.SECONDS);
            assertTrue(plan.simulation());
            assertEquals(200,plan.missingItems().get(f.raw));
            assertEquals(100,plan.patternTimes().get(f.pattern));
            verify((NativeVmHook)f.service,times(1)).vm$execute(any());
            verify(server,never()).execute(any(Runnable.class));
        } finally { executor.shutdownNow(); }
    }

    @Test void actualNativeProcessingPatternRetainsNbtAndWideQuantities() {
        var f=new Fixture();
        var tagged=new net.minecraft.world.item.ItemStack(Items.IRON_INGOT);
        tagged.getOrCreateTag().putInt("variant",1);
        var exact=AEItemKey.of(tagged);
        var encoded=new net.minecraft.world.item.ItemStack(Items.PAPER);
        var inputs=new net.minecraft.nbt.ListTag(); inputs.add(GenericStack.writeTag(new GenericStack(exact,3)));
        var outputs=new net.minecraft.nbt.ListTag(); outputs.add(GenericStack.writeTag(new GenericStack(f.output,2)));
        encoded.getOrCreateTag().put("in",inputs); encoded.getOrCreateTag().put("out",outputs);
        var pattern=new appeng.crafting.pattern.AEProcessingPattern(AEItemKey.of(encoded));
        when(f.service.getCraftingFor(f.output)).thenReturn(List.of(pattern));
        var quantity=BigInteger.ONE.shiftLeft(63).add(BigInteger.ONE);
        var needed=quantity.add(BigInteger.ONE).divide(BigInteger.TWO).multiply(BigInteger.valueOf(3));
        var snapshot=NativeOrderSnapshot.capture(f.grid,f.level,f.requester,f.output,
                new VmAccounting.Stock(Map.of(exact,needed.subtract(BigInteger.ONE),f.raw,needed),true,Set.of()),new NativeVmTiming(),()->false);
        var result=NativeGraphCalculation.plan(snapshot,quantity,false).quantities();
        assertEquals(Map.of(exact,BigInteger.ONE),result.missing());
        assertEquals(Map.of(exact,needed.subtract(BigInteger.ONE)),result.used());
        verify(f.service,never()).getCraftables(any());
        verify(f.service,never()).getFuzzyCraftable(any(),any());
    }

    @Test void unboundedRemainderDomainIsExplicitlyUnsupportedInsteadOfHangingTheServer() {
        var f=new Fixture();
        when(f.input.isValid(any(),any())).thenReturn(true);
        when(f.input.getRemainingKey(any())).thenAnswer(call->{
            var stack=((AEItemKey)call.getArgument(0)).toStack();
            stack.getOrCreateTag().putInt("unbounded",stack.getOrCreateTag().getInt("unbounded")+1);
            return AEItemKey.of(stack);
        });
        assertThrows(UnsupportedOperationException.class,()->f.capture(BigInteger.ONE));
    }

    @Test void installedScaledPatternIsExpandedBeforeItsLongGettersOverflow() throws Exception {
        var jar = System.getProperty("aco.eaepPatternJar");
        org.junit.jupiter.api.Assumptions.assumeTrue(jar != null, "Supply the ExtendedAE Plus contract JAR");
        try (var loader = new java.net.URLClassLoader(new java.net.URL[] {java.nio.file.Path.of(jar).toUri().toURL()},
                getClass().getClassLoader())) {
            long base = Long.MAX_VALUE / 2 + 1;
            var exact = BigInteger.valueOf(base).multiply(BigInteger.valueOf(5));
            for (boolean wideInput : new boolean[] {true, false}) {
                var f = new Fixture();
                var encoded = new net.minecraft.world.item.ItemStack(Items.PAPER);
                var inputs = new net.minecraft.nbt.ListTag();
                inputs.add(GenericStack.writeTag(new GenericStack(f.raw, wideInput ? base : 1)));
                var outputs = new net.minecraft.nbt.ListTag();
                outputs.add(GenericStack.writeTag(new GenericStack(f.output, wideInput ? 1 : base)));
                encoded.getOrCreateTag().put("in", inputs); encoded.getOrCreateTag().put("out", outputs);
                var original = new appeng.crafting.pattern.AEProcessingPattern(AEItemKey.of(encoded));
                var wrapper = (IPatternDetails) loader.loadClass("com.extendedae_plus.api.crafting.ScaledProcessingPattern")
                        .getConstructor(appeng.crafting.pattern.AEProcessingPattern.class, long.class).newInstance(original, 5L);
                when(f.service.getCraftingFor(f.output)).thenReturn(List.of(wrapper));
                var snapshot = f.capture(BigInteger.ZERO);
                var plan = NativeGraphCalculation.plan(snapshot, BigInteger.ONE, false);
                assertEquals(Map.of(f.raw, wideInput ? exact : BigInteger.valueOf(5)), plan.quantities().missing());
                assertSame(wrapper, snapshot.bindings().get("graph-0"));
                var produced = snapshot.graph().recipes().get(0).outputs().get(snapshot.root());
                assertEquals(wideInput ? BigInteger.valueOf(5) : exact, produced);
                assertEquals(!wideInput, plan.wideOutputs());
                var context = new NativeVmPlanningContext(f.grid, f.level, f.requester,
                        new VmAccounting.Stock(Map.of(), true, Set.of()), new NativeVmAccess(), new NativeVmTiming());
                context.prepare(f.output);
                var vm = context.plan(f.output, BigInteger.ONE, false, ignored -> { });
                assertEquals(plan.quantities().missing(), vm.missing());
                assertEquals(Map.of("vm-0", BigInteger.ONE), vm.crafts());
                assertSame(wrapper, context.bindings().get("vm-0"));
                assertEquals(!wideInput, context.widePendingOutputs(vm.crafts()));
            }
        }
    }

    private static final class Fixture {
        final AEKey raw=AEItemKey.of(Items.IRON_INGOT), output=AEItemKey.of(Items.DIAMOND);
        final Level level=mock(Level.class);
        final IGrid grid=mock(IGrid.class);
        final ICraftingService service=mock(ICraftingService.class,withSettings().extraInterfaces(
                NativeVmHook.class, NativeOrderSnapshot.CatalogSource.class));
        final ICraftingSimulationRequester requester=mock(ICraftingSimulationRequester.class);
        final IPatternDetails.IInput input=mock(IPatternDetails.IInput.class);
        final IPatternDetails pattern=mock(IPatternDetails.class);
        Fixture() {
            when(((NativeOrderSnapshot.CatalogSource)service).catalog()).thenReturn(new NativePatternCatalog());
            var node=mock(IGridNode.class);
            when(requester.getGridNode()).thenReturn(node); when(node.getGrid()).thenReturn(grid);
            when(grid.getCraftingService()).thenReturn(service);
            when(input.getPossibleInputs()).thenReturn(new GenericStack[]{new GenericStack(raw,2)});
            when(input.getMultiplier()).thenReturn(1L); when(input.isValid(raw,level)).thenReturn(true);
            when(pattern.getInputs()).thenReturn(new IPatternDetails.IInput[]{input});
            when(pattern.getOutputs()).thenReturn(new GenericStack[]{new GenericStack(output,1)});
            when(service.getCraftingFor(output)).thenReturn(List.of(pattern));
        }
        NativeOrderSnapshot capture(BigInteger count) {
            return NativeOrderSnapshot.capture(grid,level,requester,output,
                    new VmAccounting.Stock(Map.of(raw,count),true,Set.of(raw)),new NativeVmTiming(),()->false);
        }
    }
}
