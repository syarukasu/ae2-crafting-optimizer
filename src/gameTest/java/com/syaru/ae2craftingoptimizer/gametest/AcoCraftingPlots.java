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
            var request = java.math.BigInteger.TEN.pow(64);
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
