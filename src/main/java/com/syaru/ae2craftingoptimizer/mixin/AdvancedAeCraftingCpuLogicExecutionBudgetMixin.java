package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.config.Actionable;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.AEKey;
import appeng.crafting.inv.ICraftingInventory;
import appeng.crafting.inv.ListCraftingInventory;
import appeng.me.service.CraftingService;
import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import com.syaru.ae2craftingoptimizer.access.CraftingIslandJobAccess;
import com.syaru.ae2craftingoptimizer.api.execution.CraftingIslandBackendRegistry;
import com.syaru.ae2craftingoptimizer.api.execution.CraftingIslandBackendSession;
import com.syaru.ae2craftingoptimizer.api.execution.CraftingIslandExecutionOwner;
import com.syaru.ae2craftingoptimizer.api.execution.CraftingIslandRuntime;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.engine.Ae2CraftingIslandExecutor;
import com.syaru.ae2craftingoptimizer.optimization.CraftingExecutionBudget;
import com.syaru.ae2craftingoptimizer.optimization.SequentialInstantDispatcher;
import com.syaru.ae2craftingoptimizer.optimization.ServerTickClock;
import java.util.List;
import java.util.Map;
import net.minecraft.world.level.Level;
import net.pedroksl.advanced_ae.common.cluster.AdvCraftingCPU;
import net.pedroksl.advanced_ae.common.logic.ExecutingCraftingJob;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Advanced AE CPUをACOの操作数・時間予算へ参加させる。
 * Quantum Computer固有の並列数は変更せず、そのtickで実際に消費できる上限だけを狭める。
 */
@Pseudo
@Mixin(targets = "net.pedroksl.advanced_ae.common.logic.AdvCraftingCPULogic", remap = false)
public abstract class AdvancedAeCraftingCpuLogicExecutionBudgetMixin {
    @Shadow
    @Final
    private AdvCraftingCPU cpu;

    @Shadow
    private ExecutingCraftingJob job;

    @Shadow
    @Final
    private ListCraftingInventory inventory;

    @Shadow(remap = false)
    public abstract int executeCrafting(
            int maxPatterns,
            CraftingService craftingService,
            IEnergyService energyService,
            Level level);

    @Shadow(remap = false)
    public abstract long insert(
            AEKey key,
            long amount,
            Actionable actionable);

    @Redirect(
            method = "tickCraftingLogic",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/pedroksl/advanced_ae/common/cluster/AdvCraftingCPU;getCoProcessors()I"),
            remap = false,
            require = 0)
    private int aco$limitAdvancedAeCraftingExecution(@Coerce Object cpu) {
        ICraftingCPU craftingCpu = (ICraftingCPU) cpu;
        return CraftingExecutionBudget.limitCoProcessors(this, craftingCpu, craftingCpu.getCoProcessors());
    }

    @Redirect(
            method = "tickCraftingLogic",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/pedroksl/advanced_ae/common/logic/AdvCraftingCPULogic;executeCrafting(ILappeng/me/service/CraftingService;Lappeng/api/networking/energy/IEnergyService;Lnet/minecraft/world/level/Level;)I"),
            remap = false,
            require = 0)
    private int aco$dispatchAdvancedAeInstantWave(
            @Coerce Object logic,
            int maxPatterns,
            CraftingService craftingService,
            IEnergyService energyService,
            Level level) {
        // AQE/AdvancedAE CPUでも、AACなどが島全体を原子的に所有できる時だけ一括実行する。
        if (ACOConfig.enableCompiledCraftingIslands()) {
            long compiledStartedAt = System.nanoTime();
            int compiledResult = aco$tryAdvancedAeCraftingIsland(
                    maxPatterns,
                    craftingService,
                    energyService,
                    level);
            // NOT_HANDLEDは非対応・入力待ちを表し、元のSequential経路を同じtickで継続する。
            if (compiledResult != CraftingIslandExecutionOwner.NOT_HANDLED) {
                long elapsedNanos =
                        System.nanoTime() - compiledStartedAt;
                // 論理個数ではなく一つの原子Waveとして学習し、巨大島で次CPUの時間予算も守る。
                CraftingExecutionBudget.recordExecution(
                        this,
                        1,
                        compiledResult > 0 ? 1 : 0,
                        elapsedNanos);
                CraftingExecutionBudget.recordSharedExecution(
                        craftingService,
                        this,
                        ServerTickClock.currentTick(),
                        elapsedNanos);
                return compiledResult;
            }
        }

        // AdvancedAEもAE2と同じ元会計を使用し、Quantum Computerの巨大な並列数を
        // 固定回数で切らず、計測波と時間予算で次tickへ分割する。
        return SequentialInstantDispatcher.executeWave(
                this,
                maxPatterns,
                craftingService,
                ServerTickClock.currentTick(),
                limitedOperations -> executeCrafting(
                        limitedOperations, craftingService, energyService, level));
    }

    private int aco$tryAdvancedAeCraftingIsland(
            int executionBudgetHint,
            CraftingService craftingService,
            IEnergyService energyService,
            Level level) {
        ExecutingCraftingJob expectedJob = job;
        // Jobなし、サービス欠落、通常配送予算なしではAdvancedAE標準経路を維持する。
        if (expectedJob == null
                || craftingService == null
                || energyService == null
                || level == null
                || executionBudgetHint <= 0) {
            return CraftingIslandExecutionOwner.NOT_HANDLED;
        }
        return Ae2CraftingIslandExecutor.tryExecute(
                new AdvancedAeIslandRuntime(
                        expectedJob,
                        craftingService),
                energyService,
                level,
                ACOConfig.getMaximumCompiledCraftingIslandPatterns(),
                ACOConfig.getBigIntegerMaximumBits());
    }

    /**
     * AdvancedAE固有のJob会計と、任意登録された原子設備Sessionを一呼出しだけ接続する。
     */
    private final class AdvancedAeIslandRuntime
            implements CraftingIslandRuntime {
        private final ExecutingCraftingJob expectedJob;
        private final CraftingIslandJobAccess jobAccess;
        private final CraftingService craftingService;
        private CraftingIslandBackendSession backendSession;

        private AdvancedAeIslandRuntime(
                ExecutingCraftingJob expectedJob,
                CraftingService craftingService) {
            this.expectedJob = expectedJob;
            this.craftingService = craftingService;
            // 必須Accessor欠落は対象AdvancedAE版との不一致なので、通常経路へ黙って流さない。
            if (!(expectedJob instanceof CraftingIslandJobAccess access)) {
                throw new IllegalStateException(
                        "AdvancedAE crafting-island job accessor was not applied");
            }
            this.jobAccess = access;
        }

        @Override
        public Object acoIslandJobIdentity() {
            return expectedJob;
        }

        @Override
        public Map<IPatternDetails, Object> acoIslandTasks() {
            return jobAccess.aco$getIslandTasks();
        }

        @Override
        public ICraftingInventory acoIslandInventory() {
            return inventory;
        }

        @Override
        public boolean acoIslandJobStillActive(Object expectedIdentity) {
            return job == expectedIdentity;
        }

        @Override
        public boolean acoIslandBindBackend(
                List<IPatternDetails> patterns) {
            IGrid grid = cpu.getGrid();
            // CPUがGridから外れている間は設備を選ばず、AdvancedAEの停止判定へ委ねる。
            if (grid == null) {
                backendSession = null;
                return false;
            }
            backendSession = CraftingIslandBackendRegistry.openFirst(
                            grid,
                            craftingService,
                            patterns)
                    .orElse(null);
            return backendSession != null;
        }

        @Override
        public long acoIslandRootExecutionCapacity() {
            return backendSession == null
                    ? 0L
                    : backendSession.acoRootExecutionCapacity();
        }

        @Override
        public boolean acoIslandSupportsPattern(
                IPatternDetails pattern) {
            return backendSession != null
                    && backendSession.acoSupportsPattern(pattern);
        }

        @Override
        public double acoIslandEnergyPerLogicalExecution() {
            return backendSession == null
                    ? Double.NaN
                    : backendSession.acoEnergyPerPatternNode();
        }

        @Override
        public boolean acoIslandBackendStillAvailable() {
            return backendSession != null
                    && backendSession.acoStillAvailable();
        }

        @Override
        public boolean acoIslandCanAcceptOutput(
                AEKey key,
                long amount) {
            return jobAccess.aco$canAcceptIslandOutput(key, amount);
        }

        @Override
        public void acoIslandStageOutput(
                AEKey key,
                long amount) {
            jobAccess.aco$stageIslandOutput(key, amount);
        }

        @Override
        public void acoIslandUnstageOutput(
                AEKey key,
                long amount) {
            jobAccess.aco$unstageIslandOutput(key, amount);
        }

        @Override
        public long acoIslandInsertOutput(
                AEKey key,
                long amount) {
            return AdvancedAeCraftingCpuLogicExecutionBudgetMixin.this.insert(
                    key,
                    amount,
                    Actionable.MODULATE);
        }

        @Override
        public void acoIslandDecrementInternalOutput(
                AEKey key,
                long amount) {
            jobAccess.aco$decrementIslandInternalOutput(key, amount);
        }

        @Override
        public void acoIslandNotifyTaskChanges() {
            // nullは全体差分通知として一回だけlistenerを起こし、Pattern数ぶん連打しない。
            ((AdvancedAeCraftingCpuLogicIslandAccessor)
                            (Object) AdvancedAeCraftingCpuLogicExecutionBudgetMixin.this)
                    .aco$invokePostChange(null);
        }

        @Override
        public void acoIslandMarkDirty() {
            cpu.markDirty();
        }

        @Override
        public void acoIslandSuspend(
                String reason,
                Throwable failure) {
            jobAccess.aco$setIslandSuspended(true);
            AE2CraftingOptimizer.LOGGER.error(
                    "ACO cancelled an AdvancedAE job after uncertain crafting-island accounting: {}",
                    reason,
                    failure);
        }

        @Override
        public boolean acoIslandIsFinalOutput(AEKey key) {
            return jobAccess.aco$getIslandFinalOutput() != null
                    && key.matches(jobAccess.aco$getIslandFinalOutput());
        }

        @Override
        public String acoIslandBackendName() {
            return backendSession == null
                    ? "AdvancedAE/unbound"
                    : backendSession.acoBackendName();
        }
    }
}
