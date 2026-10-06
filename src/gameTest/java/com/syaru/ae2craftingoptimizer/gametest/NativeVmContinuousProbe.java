package com.syaru.ae2craftingoptimizer.gametest;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEKey;
import com.ae2vm.addon.nativeengine.NativeVmCalculation;
import com.ae2vm.addon.nativeengine.NativeVmHook;
import java.math.BigInteger;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.world.level.Level;

/** Test-only probe of the actual mixed-in AE2 pool and TickHandler job. */
public final class NativeVmContinuousProbe implements AutoCloseable {
    private final AtomicBoolean ready = new AtomicBoolean();
    private final AtomicBoolean proceed = new AtomicBoolean();
    private final FutureTask<ICraftingPlan> task;

    public NativeVmContinuousProbe(IGrid grid, Level level, AEKey output) {
        this(grid, level, output, false);
    }

    public NativeVmContinuousProbe(IGrid grid, Level level, AEKey output, boolean declaredInputs) {
        var serverThread = Thread.currentThread();
        Function<Object, ICraftingPlan> calculation = rawAccess -> {
            var access = new Access(rawAccess);
            access.read(() -> {
                if (!Thread.currentThread().getName().equals("AE Crafting Calculator")
                        || serverThread.getState() != Thread.State.WAITING)
                    throw new AssertionError("live access escaped AE2 coordination");
                return null;
            });
            var input = declaredInputs ? declaredInput(grid, level, output, rawAccess) : null;
            var primary = input == null ? null : input.getPossibleInputs()[0].what();
            var variants = new java.util.ArrayList<AEKey>();
            var verdicts = new java.util.ArrayList<Boolean>();
            // Prime one native observation; repeated queries must need no further tick.
            if (input != null) { input.isValid(primary, level); input.getRemainingKey(primary); }
            if (input != null && input.getPossibleInputs().length > 1) {
                var tagged = ((appeng.api.stacks.AEItemKey) primary).toStack();
                tagged.getOrCreateTag().putInt("aco_probe", 1);
                variants.add(appeng.api.stacks.AEItemKey.of(tagged));
                variants.add(appeng.api.stacks.AEItemKey.of(net.minecraft.world.item.Items.STICK));
                for (var variant : variants) {
                    verdicts.add(input.isValid(variant, level));
                    if (input.getRemainingKey(variant) != null) throw new AssertionError("unexpected probe remainder");
                }
                if (!verdicts.equals(java.util.List.of(true, false))) throw new AssertionError("invalid substitution fixture");
            }
            ready.set(true);
            while (!proceed.get()) {
                access.checkpoint();
                LockSupport.parkNanos(100_000);
            }
            BigInteger unit = BigInteger.TEN.pow(1024).subtract(BigInteger.ONE), total = BigInteger.ZERO;
            long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(30), operations = 0;
            do {
                access.checkpoint();
                if (input != null && (!input.isValid(primary, null) || input.getRemainingKey(primary) != null))
                    throw new AssertionError("declared input semantics changed");
                for (int i = 0; i < variants.size(); i++)
                    if (input.isValid(variants.get(i), null) != verdicts.get(i) || input.getRemainingKey(variants.get(i)) != null)
                        throw new AssertionError("memoized substitution semantics changed");
                total = total.add(unit);
                operations++;
            } while (System.nanoTime() - end < 0);
            if (!total.equals(unit.multiply(BigInteger.valueOf(operations))))
                throw new AssertionError("continuous arithmetic lost precision");
            return null;
        };
        NativeVmCalculation job;
        try {
            // Keep this probe out of the VM's named-module package and public API.
            var constructor = NativeVmCalculation.class.getDeclaredConstructor(Level.class, IGrid.class,
                    AEKey.class, CalculationStrategy.class, Function.class);
            constructor.setAccessible(true);
            job = constructor.newInstance(level, grid, output, CalculationStrategy.REPORT_MISSING_ITEMS, calculation);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        task = new FutureTask<>(job::run);
        ((NativeVmHook) grid.getCraftingService()).vm$execute(task);
    }

    public boolean ready() { return ready.get() || task.isDone(); }

    public void verifyWithoutFurtherTicks() throws Exception {
        proceed.set(true);
        // The test server is intentionally not ticking while this bounded get runs.
        task.get(2, TimeUnit.SECONDS);
    }

    @Override public void close() { task.cancel(true); }

    private static appeng.api.crafting.IPatternDetails.IInput declaredInput(
            IGrid grid, Level level, AEKey output, Object access) {
        try {
            var contextType = Class.forName("com.ae2vm.addon.nativeengine.NativeVmPlanningContext");
            var timingType = Class.forName("com.ae2vm.addon.nativeengine.NativeVmTiming");
            var timingConstructor = timingType.getDeclaredConstructor();
            timingConstructor.setAccessible(true);
            var requester = new appeng.api.networking.crafting.ICraftingSimulationRequester() {
                public appeng.api.networking.security.IActionSource getActionSource() { return null; }
                public appeng.api.networking.IGridNode getGridNode() { return grid.getPivot(); }
            };
            var stock = new com.ae2vm.addon.nativeengine.VmAccounting.Stock(java.util.Map.of(), true, java.util.Set.of());
            var constructor = contextType.getDeclaredConstructor(IGrid.class, Level.class,
                    appeng.api.networking.crafting.ICraftingSimulationRequester.class, stock.getClass(),
                    access.getClass(), timingType);
            constructor.setAccessible(true);
            var context = constructor.newInstance(grid, level, requester, stock, access, timingConstructor.newInstance());
            var candidates = contextType.getDeclaredMethod("candidates", AEKey.class);
            candidates.setAccessible(true);
            var patterns = (java.util.List<?>) candidates.invoke(context, output);
            var input = ((appeng.api.crafting.IPatternDetails) patterns.get(0)).getInputs()[0];
            return input;
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private static final class Access {
        private final Object target;
        private final Method checkpoint, read;
        Access(Object target) {
            this.target = target;
            try {
                checkpoint = target.getClass().getDeclaredMethod("checkpoint");
                read = target.getClass().getDeclaredMethod("read", Supplier.class);
                checkpoint.setAccessible(true);
                read.setAccessible(true);
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        }
        void checkpoint() { invoke(checkpoint); }
        void read(Supplier<?> callback) { invoke(read, callback); }
        private void invoke(Method method, Object... arguments) {
            try { method.invoke(target, arguments); }
            catch (InvocationTargetException failure) {
                if (failure.getCause() instanceof RuntimeException runtime) throw runtime;
                if (failure.getCause() instanceof Error error) throw error;
                throw new AssertionError(failure.getCause());
            } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        }
    }
}
