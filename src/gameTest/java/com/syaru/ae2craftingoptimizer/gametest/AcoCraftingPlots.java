package com.syaru.ae2craftingoptimizer.gametest;

import appeng.api.config.Settings;
import appeng.api.config.Actionable;
import appeng.api.config.YesNo;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.core.definitions.AEBlocks;
import appeng.core.definitions.AEItems;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.me.helpers.BaseActionSource;
import appeng.me.helpers.MachineSource;
import appeng.server.testplots.TestPlot;
import appeng.server.testworld.PlotBuilder;
import appeng.server.testworld.PlotTestHelper;
import appeng.server.testworld.TestCraftingJob;
import com.syaru.ae2craftingoptimizer.access.CraftingOwnerTransactionAccess;
import java.util.concurrent.Future;
import java.util.Map;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;

/** Issue #190: real AE2 machinery and finite ME inventory, no simulated output. */
public final class AcoCraftingPlots {
    private static final BlockPos ORIGIN = BlockPos.ZERO;
    private static final AEItemKey INPUT = AEItemKey.of(AEItems.CERTUS_QUARTZ_CRYSTAL);
    private static final AEItemKey OUTPUT = AEItemKey.of(AEItems.CERTUS_QUARTZ_DUST);

    private AcoCraftingPlots() {}

    @TestPlot("aco_vm_substitution_queries")
    public static void vmSubstitutionQueries(PlotBuilder plot) {
        network(plot, 32, false);
        plot.test(helper -> {
            var probe = new java.util.concurrent.atomic.AtomicReference<NativeVmContinuousProbe>();
            helper.startSequence().thenWaitUntil(() -> checkRuntime(helper)).thenExecute(() -> {
                var recipe = (CraftingRecipe) helper.getLevel().getRecipeManager()
                        .byKey(new ResourceLocation("aco_gametest", "template_alternatives")).orElseThrow();
                var inputs = new ItemStack[9]; Arrays.fill(inputs, ItemStack.EMPTY);
                inputs[0] = new ItemStack(Items.IRON_INGOT);
                var encoded = PatternDetailsHelper.encodeCraftingPattern(recipe, inputs, new ItemStack(Items.DIAMOND), true, false);
                var decoded = new appeng.crafting.pattern.AECraftingPattern(AEItemKey.of(encoded), helper.getLevel());
                helper.check(decoded instanceof com.ae2vm.addon.nativeengine.NativeVmRecipeView,
                        "Native bound-recipe accessor was not mixed in");
                var provider = (PatternProviderBlockEntity) helper.getBlockEntity(new BlockPos(0, 0, -3));
                provider.getLogic().getPatternInv().setItemDirect(0, encoded);
            }).thenIdle(2).thenExecute(() -> probe.set(new NativeVmContinuousProbe(
                    helper.getGrid(ORIGIN), helper.getLevel(), AEItemKey.of(Items.DIAMOND), true)))
                    .thenWaitUntil(() -> helper.check(probe.get().ready(), "Substitution input not prepared"))
                    .thenExecute(() -> {
                        try (var current = probe.get()) { current.verifyWithoutFurtherTicks(); checkStock(helper, 32, 0); }
                        catch (Exception failure) { helper.fail("Substitution queries still wait for ticks: " + failure); }
                    }).thenSucceed();
        }).maxTicks(800);
    }

    @TestPlot("aco_vm_declared_inputs")
    public static void vmDeclaredInputs(PlotBuilder plot) {
        network(plot, 32, false);
        plot.test(helper -> {
            var probe = new java.util.concurrent.atomic.AtomicReference<NativeVmContinuousProbe>();
            helper.startSequence().thenWaitUntil(() -> checkRuntime(helper)).thenExecute(() ->
                    probe.set(new NativeVmContinuousProbe(
                            helper.getGrid(ORIGIN), helper.getLevel(), OUTPUT, true)))
                    .thenWaitUntil(() -> helper.check(probe.get().ready(), "No declared processing input was prepared"))
                    .thenExecute(() -> {
                        try (var current = probe.get()) {
                            current.verifyWithoutFurtherTicks();
                            checkStock(helper, 32, 0);
                        } catch (Exception failure) { helper.fail("Declared input queries still require ticks: " + failure); }
                    }).thenSucceed();
        }).maxTicks(800);
    }

    @TestPlot("aco_vm_continuous_arithmetic")
    public static void vmContinuousArithmetic(PlotBuilder plot) {
        network(plot, 32, false);
        plot.test(helper -> {
            var probe = new java.util.concurrent.atomic.AtomicReference<NativeVmContinuousProbe>();
            helper.startSequence().thenWaitUntil(() -> checkRuntime(helper)).thenExecute(() ->
                    probe.set(new NativeVmContinuousProbe(
                            helper.getGrid(ORIGIN), helper.getLevel(), OUTPUT)))
                    .thenWaitUntil(() -> helper.check(probe.get().ready(), "No exclusive live interval was granted"))
                    .thenExecute(() -> {
                        try (var current = probe.get()) {
                            current.verifyWithoutFurtherTicks();
                            checkStock(helper, 32, 0);
                        } catch (Exception failure) { helper.fail("Pure VM arithmetic still requires ticks: " + failure); }
                    }).thenSucceed();
        }).maxTicks(800);
    }

    @TestPlot("aco_vm_ae2_lifecycle")
    public static void vmAe2Lifecycle(PlotBuilder plot) {
        network(plot, 32, false);
        plot.test(helper -> {
            var futures = new java.util.ArrayList<Future<ICraftingPlan>>();
            var callbacks = new java.util.concurrent.atomic.AtomicInteger();
            helper.startSequence().thenWaitUntil(() -> checkRuntime(helper)).thenExecute(() -> {
                var grid = helper.getGrid(ORIGIN);
                var serverThread = Thread.currentThread();
                var source = new MachineSource(grid::getPivot);
                var requester = new appeng.api.networking.crafting.ICraftingSimulationRequester() {
                    public appeng.api.networking.security.IActionSource getActionSource() {
                        helper.check(Thread.currentThread() == serverThread, "Stock snapshot left the server thread");
                        return source;
                    }
                    public appeng.api.networking.IGridNode getGridNode() {
                        helper.check(Thread.currentThread().getName().equals("AE Crafting Calculator"),
                                "VM did not use AE2's calculation pool");
                        helper.check(serverThread.getState() == Thread.State.WAITING,
                                "Pattern callbacks ran outside AE2's exclusive calculation slice");
                        callbacks.incrementAndGet();
                        return grid.getPivot();
                    }
                };
                var cancelled = com.ae2vm.addon.nativeengine.NativeVm.calculate(grid, helper.getLevel(),
                        requester, OUTPUT, java.math.BigInteger.TEN.pow(1024).subtract(java.math.BigInteger.ONE),
                        CalculationStrategy.REPORT_MISSING_ITEMS);
                helper.check(cancelled.cancel(true), "Pre-slice cancellation failed");
                for (int i = 1; i <= 2; i++) futures.add(grid.getCraftingService().beginCraftingCalculation(
                        helper.getLevel(), requester, OUTPUT, i, CalculationStrategy.REPORT_MISSING_ITEMS));
            }).thenWaitUntil(() -> helper.check(futures.stream().allMatch(Future::isDone),
                    "AE2 scheduling did not finish concurrent VM calculations"))
                    .thenExecute(() -> {
                        try {
                            for (int i = 0; i < futures.size(); i++) {
                                var plan = futures.get(i).get();
                                helper.check(!plan.simulation() && plan.usedItems().get(INPUT) == i + 1,
                                        "AE2 scheduled VM returned the wrong quantity");
                            }
                            helper.check(callbacks.get() >= 2, "No VM pattern callbacks ran");
                            var samples = com.ae2vm.addon.nativeengine.NativeVm.recentCalculations().stream()
                                    .filter(s -> s.output().equals(OUTPUT.getId().toString())
                                            && s.status().equals("ready")).toList();
                            helper.check(samples.stream().allMatch(s -> s.timing().contains("scheduler=ae2")
                                            && s.timing().contains("handoffs=0")), "Detached VM callbacks survived");
                            checkStock(helper, 32, 0);
                        } catch (Exception failure) { helper.fail("AE2 VM lifecycle failed: " + failure); }
                    }).thenSucceed();
        }).maxTicks(800);
    }

    @TestPlot("aco_native_wide_api_bridge")
    public static void nativeWideApiBridge(PlotBuilder plot) {
        // A planning-only large-output pattern. No machine is installed and no
        // output is generated; this verifies the bridge, not wide machine execution.
        network(plot, 32, false);
        plot.test(helper -> {
            var request = java.math.BigInteger.valueOf(Long.MAX_VALUE).add(java.math.BigInteger.ONE);
            var future = new java.util.concurrent.atomic.AtomicReference<Future<ICraftingPlan>>();
            helper.startSequence().thenWaitUntil(() -> checkRuntime(helper))
                    .thenExecute(() -> {
                        var provider = (PatternProviderBlockEntity) helper.getBlockEntity(new BlockPos(0, 0, -3));
                        provider.getLogic().getPatternInv().setItemDirect(0, PatternDetailsHelper.encodeProcessingPattern(
                                new GenericStack[] {new GenericStack(INPUT, 1)},
                                new GenericStack[] {new GenericStack(OUTPUT, Long.MAX_VALUE)}));
                    }).thenIdle(2).thenExecute(() -> {
                        var grid = helper.getGrid(ORIGIN);
                        future.set(com.ae2vm.addon.nativeengine.NativeVm.calculate(grid, helper.getLevel(),
                                () -> new MachineSource(grid::getPivot), OUTPUT, request,
                                CalculationStrategy.REPORT_MISSING_ITEMS));
                    }).thenWaitUntil(() -> helper.check(future.get().isDone(), "Wide stocked VM calculation pending"))
                    .thenExecute(() -> {
                        try {
                            var plan = future.get().get();
                            var api = com.syaru.ae2craftingoptimizer.api.big.BigCraftingEngineApi
                                    .inspectBigIntegerPlan(plan).orElseThrow();
                            helper.check(!api.simulation(), "Stocked processing plan became a missing simulation");
                            helper.check(api.exactRequestedAmount().equals(request), "Bridge narrowed the request");
                            helper.check(api.usedItems().equals(Map.of(INPUT, java.math.BigInteger.TWO)), "Wrong VM inputs");
                            helper.check(api.patternTimes().values().stream().reduce(java.math.BigInteger.ZERO,
                                    java.math.BigInteger::add).equals(java.math.BigInteger.TWO), "Wrong VM executions");
                            var metadata = com.syaru.ae2craftingoptimizer.engine.Ae2CraftingPlanSidecars.metadata(plan).orElseThrow();
                            var summary = com.syaru.ae2craftingoptimizer.engine.BigCraftingPlanSummary.from(metadata);
                            helper.check(summary.entries().get(OUTPUT).craft().equals(
                                    java.math.BigInteger.valueOf(Long.MAX_VALUE).multiply(java.math.BigInteger.TWO)),
                                    "Bridge narrowed the planned outputs");
                            var grid = helper.getGrid(ORIGIN);
                            var cpu = (appeng.me.cluster.implementations.CraftingCPUCluster)
                                    grid.getCraftingService().getCpus().iterator().next();
                            var rejected = cpu.craftingLogic.trySubmitJob(grid, plan, new BaseActionSource(), null);
                            helper.check(!rejected.successful(), "Unprepared wide projection reached the long executor");
                            checkStock(helper, 32, 0);
                        } catch (Exception failure) { helper.fail("Wide API bridge failed: " + failure); }
                    }).thenSucceed();
        }).maxTicks(800);
    }

    @TestPlot("aco_native_wide_missing")
    public static void nativeWideMissing(PlotBuilder plot) {
        network(plot, 32, true);
        plot.test(helper -> {
            var request = java.math.BigInteger.TEN.pow(1024).subtract(java.math.BigInteger.ONE);
            var future = new java.util.concurrent.atomic.AtomicReference<Future<ICraftingPlan>>();
            helper.startSequence().thenWaitUntil(() -> checkRuntime(helper))
                    .thenExecute(() -> {
                        var grid = helper.getGrid(ORIGIN);
                        future.set(com.ae2vm.addon.nativeengine.NativeVm.calculate(grid, helper.getLevel(),
                                () -> new MachineSource(grid::getPivot), OUTPUT, request,
                                CalculationStrategy.REPORT_MISSING_ITEMS));
                    }).thenWaitUntil(() -> helper.check(future.get().isDone(), "Wide VM calculation pending"))
                    .thenExecute(() -> {
                        try {
                            var plan = future.get().get();
                            var exact = com.syaru.ae2craftingoptimizer.engine.Ae2CraftingPlanSidecars
                                    .bigIntegerSimulation(plan).orElseThrow().exactPlan();
                            helper.check(exact.requestedAmount().equals(request), "Requested amount narrowed");
                            helper.check(exact.usedInventory().get(INPUT).equals(java.math.BigInteger.valueOf(32)),
                                    "Exact used quantity incorrect");
                            helper.check(exact.missing().get(INPUT).equals(request.subtract(java.math.BigInteger.valueOf(32))),
                                    "Exact missing quantity incorrect");
                            helper.check(exact.patternExecutions().values().stream().reduce(java.math.BigInteger.ZERO,
                                    java.math.BigInteger::add).equals(request), "VM execution count narrowed");
                        } catch (Exception failure) { helper.fail("Wide native calculation failed: " + failure); }
                    }).thenSucceed();
        }).maxTicks(800);
    }

    @TestPlot("aco_snapshot_stock_churn")
    public static void snapshotStockChurn(PlotBuilder plot) {
        snapshotStockChange(plot, false);
    }

    @TestPlot("aco_snapshot_stock_consumed")
    public static void snapshotStockConsumed(PlotBuilder plot) {
        snapshotStockChange(plot, true);
    }

    @TestPlot("aco_shared_pattern_refresh")
    public static void sharedPatternRefresh(PlotBuilder plot) {
        network(plot, 32, false);
        plot.test(helper -> {
            class PatternOrders {
                Future<ICraftingPlan> future;

                void begin() {
                    var grid = helper.getGrid(ORIGIN);
                    future = grid.getCraftingService().beginCraftingCalculation(helper.getLevel(),
                            () -> new MachineSource(grid::getPivot), OUTPUT, 1,
                            CalculationStrategy.REPORT_MISSING_ITEMS);
                }

                void replace(int amount) {
                    var provider = (PatternProviderBlockEntity) helper.getBlockEntity(new BlockPos(0, 0, -3));
                    provider.getLogic().getPatternInv().setItemDirect(0, amount == 0 ? ItemStack.EMPTY
                            : PatternDetailsHelper.encodeProcessingPattern(
                                    new GenericStack[] {new GenericStack(INPUT, amount)},
                                    new GenericStack[] {new GenericStack(OUTPUT, 1)}));
                }

                void check(int used, int missing) {
                    helper.check(future.isDone(), "Pattern refresh calculation pending");
                    try {
                        var plan = future.get();
                        helper.check(plan.usedItems().get(INPUT) == used, "Cached input quantity is stale");
                        helper.check(plan.missingItems().get(OUTPUT) == missing, "Cached producer list is stale");
                        helper.check(plan.simulation() == (missing > 0), "Wrong refreshed plan type");
                        checkStock(helper, 32, 0);
                    } catch (InterruptedException failure) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(failure);
                    } catch (java.util.concurrent.ExecutionException failure) {
                        throw new IllegalStateException(failure.getCause());
                    }
                }
            }
            var orders = new PatternOrders();
            helper.startSequence().thenWaitUntil(() -> checkRuntime(helper)).thenIdle(5)
                    .thenExecute(orders::begin).thenWaitUntil(() -> orders.check(1, 0))
                    .thenExecute(() -> orders.replace(2)).thenIdle(2)
                    .thenExecute(orders::begin).thenWaitUntil(() -> orders.check(2, 0))
                    .thenExecute(() -> orders.replace(0)).thenIdle(2)
                    .thenExecute(orders::begin).thenWaitUntil(() -> orders.check(0, 1))
                    .thenExecute(() -> orders.replace(3)).thenIdle(2)
                    .thenExecute(orders::begin).thenWaitUntil(() -> orders.check(3, 0))
                    .thenSucceed();
        }).maxTicks(800);
    }

    private static void snapshotStockChange(PlotBuilder plot, boolean removeRequiredStock) {
        network(plot, 32, true);
        plot.test(helper -> {
            var future = new java.util.concurrent.atomic.AtomicReference<Future<ICraftingPlan>>();
            helper.startSequence().thenWaitUntil(() -> checkRuntime(helper)).thenIdle(5)
                    .thenExecute(() -> {
                        var grid = helper.getGrid(ORIGIN);
                        future.set(grid.getCraftingService().beginCraftingCalculation(helper.getLevel(),
                                () -> new MachineSource(grid::getPivot), OUTPUT, 1,
                                CalculationStrategy.REPORT_MISSING_ITEMS));
                    }).thenWaitUntil(() -> {
                        var grid = helper.getGrid(ORIGIN);
                        var source = new MachineSource(grid::getPivot);
                        helper.check(grid.getStorageService().getInventory().insert(AEItemKey.of(Items.REDSTONE),
                                1, Actionable.MODULATE, source) == 1, "Stock churn insert failed");
                        helper.check(future.get().isDone(), "Native VM is still calculating during stock churn");
                    }).thenExecute(() -> {
                        var grid = helper.getGrid(ORIGIN);
                        var source = new MachineSource(grid::getPivot);
                        var storage = grid.getStorageService().getInventory();
                        if (removeRequiredStock) {
                            helper.check(storage.extract(INPUT, 32, Actionable.MODULATE, source) == 32,
                                    "Failed to remove fixture stock");
                        }
                        try {
                            var plan = future.get().get();
                            helper.check(plan != null && !plan.simulation(), "Captured stock should produce a plan");
                            helper.check(plan.usedItems().get(INPUT) == 1, "Snapshot stock accounting changed");
                            var submitted = grid.getCraftingService().submitJob(plan, null, null, true, source);
                            helper.check(submitted.successful() != removeRequiredStock,
                                    "Live reservation decision incorrect: " + submitted.errorCode());
                        } catch (Exception failure) {
                            helper.fail("Snapshot stock-change order failed: " + failure);
                        }
                    }).thenWaitUntil(() -> checkStock(helper, removeRequiredStock ? 0 : 31,
                            removeRequiredStock ? 0 : 1)).thenSucceed();
        }).maxTicks(800);
    }

    private static void network(PlotBuilder plot, int stock, boolean machine) {
        plot.block("0 0 0", AEBlocks.CRAFTING_STORAGE_64K);
        plot.cable("1 0 0");
        plot.block("2 0 0", AEBlocks.CRAFTING_STORAGE_64K);
        plot.cable("0 0 [-2,-1]");
        plot.blockEntity("0 0 -3", AEBlocks.PATTERN_PROVIDER, provider ->
                provider.getLogic().getPatternInv().addItems(PatternDetailsHelper.encodeProcessingPattern(
                        new GenericStack[] {new GenericStack(INPUT, 1)},
                        new GenericStack[] {new GenericStack(OUTPUT, 1)})));
        if (machine) {
            plot.blockEntity("1 0 -3", AEBlocks.INSCRIBER, inscriber ->
                    inscriber.getConfigManager().putSetting(Settings.AUTO_EXPORT, YesNo.YES));
        }
        plot.cable("0 0 -4");
        plot.drive(new BlockPos(0, 0, -5)).addItemCell64k().add(AEItems.CERTUS_QUARTZ_CRYSTAL, stock);
        plot.creativeEnergyCell("0 -1 -5");
    }

    private static void checkRuntime(PlotTestHelper helper) {
        var cpus = helper.getGrid(ORIGIN).getCraftingService().getCpus();
        helper.check(cpus.size() == 2, "Expected two separate real CPUs");
        helper.check(cpus.stream().allMatch(c -> c instanceof CraftingOwnerTransactionAccess),
                "ACO CPU Mixins were not applied");
        helper.check(helper.getGrid(ORIGIN).getCraftingService()
                instanceof com.ae2vm.addon.nativeengine.NativeVmHook, "Native VM entry point was not applied");
    }

    private static void checkStock(PlotTestHelper helper, long input, long output) {
        var grid = helper.getGrid(ORIGIN);
        helper.check(grid.getCraftingService().getCpus().stream().noneMatch(c -> c.isBusy()),
                "Crafting CPU has not finished returning its inventory");
        var stock = grid.getStorageService().getInventory().getAvailableStacks();
        helper.check(stock.get(INPUT) == input, "Incorrect remaining raw material: " + stock.get(INPUT));
        helper.check(stock.get(OUTPUT) == output, "Incorrect actual machine output: " + stock.get(OUTPUT));
    }

    @TestPlot("aco_processing_completion")
    public static void processingCompletion(PlotBuilder plot) {
        network(plot, 32, true);
        plot.test(helper -> {
            var job = new TestCraftingJob(helper, ORIGIN, OUTPUT, 10);
            helper.startSequence().thenWaitUntil(() -> checkRuntime(helper))
                    .thenWaitUntil(job::tickUntilStarted)
                    .thenWaitUntil(() -> checkStock(helper, 22, 10))
                    .thenSucceed();
        }).maxTicks(1200);
    }

    @TestPlot("aco_cancel_before_delivery")
    public static void cancelBeforeDelivery(PlotBuilder plot) {
        network(plot, 32, false);
        plot.test(helper -> {
            var job = new TestCraftingJob(helper, ORIGIN, OUTPUT, 10);
            helper.startSequence().thenWaitUntil(() -> checkRuntime(helper))
                    .thenWaitUntil(job::tickUntilStarted)
                    .thenExecute(() -> helper.getGrid(ORIGIN).getCraftingService().getCpus()
                            .forEach(c -> c.cancelJob()))
                    .thenWaitUntil(() -> checkStock(helper, 32, 0))
                    .thenSucceed();
        }).maxTicks(400);
    }

    @TestPlot("aco_competing_reservations")
    public static void competingReservations(PlotBuilder plot) {
        network(plot, 5, true);
        plot.test(helper -> {
            class CompetingOrders {
                Future<ICraftingPlan> first;
                Future<ICraftingPlan> second;

                void calculate() {
                    var grid = helper.getGrid(ORIGIN);
                    var service = grid.getCraftingService();
                    if (first == null) {
                        var source = new MachineSource(grid::getPivot);
                        first = service.beginCraftingCalculation(helper.getLevel(), () -> source,
                                OUTPUT, 4, CalculationStrategy.REPORT_MISSING_ITEMS);
                        second = service.beginCraftingCalculation(helper.getLevel(), () -> source,
                                OUTPUT, 4, CalculationStrategy.REPORT_MISSING_ITEMS);
                    }
                    if (!first.isDone() || !second.isDone()) throw new GameTestAssertException("Plans pending");
                }

                void submit() {
                    try {
                        var service = helper.getGrid(ORIGIN).getCraftingService();
                        var cpus = service.getCpus().stream().toList();
                        var a = service.submitJob(first.get(), null, cpus.get(0), true, new BaseActionSource());
                        var b = service.submitJob(second.get(), null, cpus.get(1), true, new BaseActionSource());
                        helper.check(a.successful(), "First order should reserve four crystals: " + a.errorCode());
                        helper.check(!b.successful(), "Second order reused already reserved crystals");
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                    } catch (java.util.concurrent.ExecutionException e) {
                        throw new IllegalStateException(e.getCause());
                    }
                }
            }
            var orders = new CompetingOrders();
            helper.startSequence().thenWaitUntil(() -> checkRuntime(helper))
                    .thenWaitUntil(orders::calculate).thenExecute(orders::submit)
                    .thenWaitUntil(() -> checkStock(helper, 1, 4)).thenSucceed();
        }).maxTicks(1200);
    }

    @TestPlot("aco_fixed_crafting_wide_nbt")
    public static void fixedCraftingWideNbt(PlotBuilder plot) {
        network(plot, 1, false);
        plot.test(helper -> {
            var output = AEItemKey.of(Items.IRON_BLOCK);
            var raw = AEItemKey.of(Items.IRON_INGOT);
            var requested = java.math.BigInteger.ONE.shiftLeft(63);
            var future = new java.util.concurrent.atomic.AtomicReference<Future<ICraftingPlan>>();
            helper.startSequence().thenWaitUntil(() -> checkRuntime(helper))
                    .thenExecute(() -> {
                        var recipe = (CraftingRecipe) helper.getLevel().getRecipeManager()
                                .byKey(new ResourceLocation("minecraft", "iron_block")).orElseThrow();
                        var inputs = new ItemStack[9];
                        Arrays.setAll(inputs, ignored -> new ItemStack(Items.IRON_INGOT));
                        var encoded = PatternDetailsHelper.encodeCraftingPattern(recipe, inputs,
                                new ItemStack(Items.IRON_BLOCK), false, false);
                        var provider = (PatternProviderBlockEntity) helper.getBlockEntity(new BlockPos(0, 0, -3));
                        provider.getLogic().getPatternInv().setItemDirect(0, encoded);
                        var storage = helper.getGrid(ORIGIN).getStorageService().getInventory();
                        helper.check(storage.insert(raw, 18, Actionable.MODULATE, new BaseActionSource()) == 18,
                                "Failed to seed exact primary stock");
                        for (int i = 1; i <= 48; i++) {
                            var stack = new ItemStack(Items.IRON_INGOT);
                            stack.getOrCreateTag().putInt("variant", i);
                            helper.check(storage.insert(AEItemKey.of(stack), 1, Actionable.MODULATE,
                                    new BaseActionSource()) == 1, "Failed to seed wrong NBT variant");
                        }
                    }).thenIdle(2).thenExecute(() -> {
                        var grid = helper.getGrid(ORIGIN);
                        future.set(com.ae2vm.addon.nativeengine.NativeVm.calculate(grid, helper.getLevel(),
                                () -> new MachineSource(grid::getPivot), output, requested,
                                CalculationStrategy.REPORT_MISSING_ITEMS));
                    }).thenWaitUntil(() -> helper.check(future.get().isDone(), "Fixed crafting plan pending"))
                    .thenExecute(() -> {
                        try {
                            var plan = future.get().get();
                            var exact = com.syaru.ae2craftingoptimizer.api.big.BigCraftingEngineApi
                                    .inspectBigIntegerPlan(plan).orElseThrow();
                            helper.check(exact.exactRequestedAmount().equals(requested), "Narrowed fixed-crafting request");
                            helper.check(exact.usedItems().equals(Map.of(raw, java.math.BigInteger.valueOf(18))),
                                    "Fixed pattern consumed a wrong NBT variant");
                            helper.check(exact.missingItems().equals(Map.of(raw,
                                    requested.multiply(java.math.BigInteger.valueOf(9)).subtract(java.math.BigInteger.valueOf(18)))),
                                    "Fixed pattern narrowed intermediate shortage");
                            var sample = com.ae2vm.addon.nativeengine.NativeVm.recentCalculations().stream()
                                    .filter(value -> value.output().equals("minecraft:iron_block") && value.requested().equals(requested))
                                    .reduce((a, b) -> b).orElseThrow();
                            helper.check(!sample.timing().matches(".*INPUTS:[1-9][0-9]*.*"),
                                    "Fixed crafting still performs live fuzzy checks: " + sample.timing());
                            var stock = helper.getGrid(ORIGIN).getStorageService().getInventory().getAvailableStacks();
                            helper.check(stock.get(raw) == 18 && stock.get(output) == 0, "Planning changed real inventory");
                        } catch (Exception failure) { helper.fail("Fixed crafting fixture failed: " + failure); }
                    }).thenSucceed();
        }).maxTicks(800);
    }

    @TestPlot("aco_crafting_substitution_completion")
    public static void craftingSubstitutionCompletion(PlotBuilder plot) {
        network(plot, 1, false);
        plot.block("1 0 -3", AEBlocks.MOLECULAR_ASSEMBLER);
        plot.test(helper -> {
            var ingredients = new net.minecraft.world.item.Item[] {
                    Items.IRON_INGOT, Items.GOLD_INGOT, Items.COPPER_INGOT, Items.EMERALD
            };
            var job = new TestCraftingJob(helper, ORIGIN, AEItemKey.of(Items.DIAMOND), ingredients.length);
            helper.startSequence().thenWaitUntil(() -> checkRuntime(helper))
                    .thenExecute(() -> {
                        var recipe = (CraftingRecipe) helper.getLevel().getRecipeManager()
                                .byKey(new ResourceLocation("aco_gametest", "template_alternatives")).orElseThrow();
                        var inputs = new ItemStack[9];
                        Arrays.fill(inputs, ItemStack.EMPTY);
                        inputs[0] = new ItemStack(ingredients[0]);
                        var encoded = PatternDetailsHelper.encodeCraftingPattern(recipe, inputs,
                                new ItemStack(Items.DIAMOND), true, false);
                        var decoded = new appeng.crafting.pattern.AECraftingPattern(AEItemKey.of(encoded), helper.getLevel());
                        helper.check(decoded.getInputs()[0].getPossibleInputs().length >= ingredients.length,
                                "Fixture did not expose actual AE2 substitution templates");
                        var provider = (PatternProviderBlockEntity) helper.getBlockEntity(new BlockPos(0, 0, -3));
                        provider.getLogic().getPatternInv().setItemDirect(0, encoded);
                        var storage = helper.getGrid(ORIGIN).getStorageService().getInventory();
                        for (var item : ingredients) helper.check(storage.insert(AEItemKey.of(item), 1,
                                Actionable.MODULATE, new BaseActionSource()) == 1, "Failed to seed substitute");
                    }).thenIdle(2).thenWaitUntil(job::tickUntilStarted)
                    .thenWaitUntil(() -> {
                        checkStock(helper, 1, 0);
                        var stock = helper.getGrid(ORIGIN).getStorageService().getInventory().getAvailableStacks();
                        for (var item : ingredients) helper.check(stock.get(AEItemKey.of(item)) == 0,
                                "Substitution consumed the wrong quantity: " + item);
                        helper.check(stock.get(AEItemKey.of(Items.DIAMOND)) == ingredients.length,
                                "Molecular assembler did not finish all substitutions");
                    }).thenSucceed();
        }).maxTicks(1200);
    }

    @TestPlot("aco_returned_containers")
    public static void returnedContainers(PlotBuilder plot) {
        network(plot, 1, false);
        plot.block("1 0 -3", AEBlocks.MOLECULAR_ASSEMBLER);
        plot.test(helper -> {
            var job = new TestCraftingJob(helper, ORIGIN, AEItemKey.of(Items.CAKE), 2);
            helper.startSequence().thenWaitUntil(() -> checkRuntime(helper))
                    .thenExecute(() -> {
                        var recipe = (CraftingRecipe) helper.getLevel().getRecipeManager()
                                .byKey(new ResourceLocation("minecraft", "cake")).orElseThrow();
                        var inputs = Arrays.stream(new net.minecraft.world.item.Item[] {
                                Items.MILK_BUCKET, Items.MILK_BUCKET, Items.MILK_BUCKET,
                                Items.SUGAR, Items.EGG, Items.SUGAR, Items.WHEAT, Items.WHEAT, Items.WHEAT
                        }).map(ItemStack::new).toArray(ItemStack[]::new);
                        var provider = (PatternProviderBlockEntity) helper.getBlockEntity(new BlockPos(0, 0, -3));
                        provider.getLogic().getPatternInv().setItemDirect(0,
                                PatternDetailsHelper.encodeCraftingPattern(recipe, inputs, new ItemStack(Items.CAKE), false, false));
                        var storage = helper.getGrid(ORIGIN).getStorageService().getInventory();
                        Map.of(Items.MILK_BUCKET, 7, Items.SUGAR, 5, Items.EGG, 3, Items.WHEAT, 7)
                                .forEach((item, count) -> {
                                    var key = AEItemKey.of(item);
                                    helper.check(storage.getAvailableStacks().get(key) == 0, "Fixture seeded twice");
                                    long inserted = storage.insert(key, count, Actionable.MODULATE, new BaseActionSource());
                                    helper.check(inserted == count, "Failed to seed finite ingredients");
                                });
                    }).thenIdle(2)
                    .thenWaitUntil(job::tickUntilStarted)
                    .thenWaitUntil(() -> {
                        checkStock(helper, 1, 0);
                        var stock = helper.getGrid(ORIGIN).getStorageService().getInventory().getAvailableStacks();
                        for (var item : new net.minecraft.world.item.Item[] {
                                Items.MILK_BUCKET, Items.SUGAR, Items.EGG, Items.WHEAT}) {
                            helper.check(stock.get(AEItemKey.of(item)) == 1, "Incorrect ingredient remainder: " + item
                                    + "=" + stock.get(AEItemKey.of(item)) + ", cakes=" + stock.get(AEItemKey.of(Items.CAKE))
                                    + ", buckets=" + stock.get(AEItemKey.of(Items.BUCKET)));
                        }
                        helper.check(stock.get(AEItemKey.of(Items.CAKE)) == 2, "Expected two real cakes");
                        helper.check(stock.get(AEItemKey.of(Items.BUCKET)) == 6, "Expected six returned buckets");
                    }).thenSucceed();
        }).maxTicks(1200);
    }
}
