package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.security.IActionHost;
import appeng.api.stacks.AEKey;
import appeng.menu.MenuOpener;
import appeng.menu.me.crafting.CraftAmountMenu;
import appeng.menu.me.crafting.CraftConfirmMenu;
import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.craftingamount.LongCraftAmountMenuBridge;
import com.syaru.ae2craftingoptimizer.craftingamount.LongCraftAmountRules;
import com.syaru.ae2craftingoptimizer.craftingamount.LongCraftConfirmMenuBridge;
import com.syaru.ae2craftingoptimizer.network.BigCraftingNetwork;
import java.util.Objects;
import java.util.concurrent.Future;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * CraftConfirmMenuにlongルート量のSidecarを追加し、
 * AE2本来のbeginCraftingCalculation(..., long, ...)へ無損失で渡す。
 */
@Mixin(value = CraftConfirmMenu.class, remap = false)
public abstract class CraftConfirmMenuLongAmountMixin
        implements LongCraftConfirmMenuBridge {
    @Shadow
    private AEKey whatToCraft;

    @Shadow
    private int amount;

    @Shadow
    private Future<ICraftingPlan> job;

    @Shadow
    private ICraftingPlan result;

    @Shadow
    public abstract void clearError();

    /** int互換フィールドとは別に保持する、実際のルート注文量。 */
    @Unique
    private long aco$longRootAmount;

    @Inject(method = "planJob", at = @At("HEAD"), require = 1)
    private void aco$clearLongSidecarForNormalPlan(
            AEKey what,
            int requestedAmount,
            CalculationStrategy strategy,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        // 本家int計画が始まる時は、以前のlong値をreplan/backへ持ち越さない。
        this.aco$longRootAmount = 0L;
    }

    @Override
    public boolean aco$planLong(
            AEKey what,
            long requestedAmount,
            CalculationStrategy strategy) {
        Objects.requireNonNull(what, "what");
        Objects.requireNonNull(strategy, "strategy");
        CraftConfirmMenu menu = (CraftConfirmMenu) (Object) this;
        // long専用境界、設定、サーバー側の三条件を満たさない呼出しは計画を作らない。
        if (!ACOConfig.enableLongRootCraftAmounts()
                || !LongCraftAmountRules.isValidExtendedRequest(requestedAmount)
                || menu.isClientSide()) {
            return false;
        }

        // 直前の未完了計算がある場合は、本家planJobと同じく割込み付きで取り消す。
        if (this.job != null) {
            this.job.cancel(true);
        }
        this.result = null;
        this.clearError();
        this.whatToCraft = what;
        /*
         * AE2内部で残るintフィールドは表示・互換用sentinelに限定する。
         * 計算、再計算、戻る操作は必ずaco$longRootAmountを参照する。
         */
        this.amount = Integer.MAX_VALUE;
        this.aco$longRootAmount = requestedAmount;

        IGrid grid = aco$findGrid(menu);
        // 構造解除などでGridが消えていればFutureを作らず呼出側へ失敗を返す。
        if (grid == null) {
            return false;
        }

        ICraftingSimulationRequester requester = menu::getActionSource;
        this.job = grid.getCraftingService().beginCraftingCalculation(
                menu.getPlayer().level(),
                requester,
                what,
                requestedAmount,
                strategy);
        return true;
    }

    @Inject(method = "replan", at = @At("HEAD"), cancellable = true, require = 1)
    private void aco$replanWithLongRootAmount(CallbackInfo ci) {
        // 通常int計画とクライアントのaction送信はAE2本来のreplan()へ委譲する。
        if (!ACOConfig.enableLongRootCraftAmounts()
                || !LongCraftAmountRules.isValidExtendedRequest(this.aco$longRootAmount)) {
            return;
        }
        CraftConfirmMenu menu = (CraftConfirmMenu) (Object) this;
        // Client側は本家のclient actionを送信し、long再計算そのものはServerだけが行う。
        if (menu.isClientSide()) {
            return;
        }

        this.clearError();
        boolean planned = this.whatToCraft != null
                && this.aco$planLong(
                        this.whatToCraft,
                        this.aco$longRootAmount,
                        CalculationStrategy.CRAFT_LESS);
        // 再計算を開始できない時は、AE2本来の失敗時と同じく前画面へ戻る。
        if (!planned) {
            menu.goBack();
        }
        ci.cancel();
    }

    @Inject(method = "goBack", at = @At("HEAD"), cancellable = true, require = 1)
    private void aco$returnToAmountScreenWithLongValue(CallbackInfo ci) {
        // 通常int計画とクライアントのaction送信はAE2本来のgoBack()へ委譲する。
        if (!ACOConfig.enableLongRootCraftAmounts()
                || !LongCraftAmountRules.isValidExtendedRequest(this.aco$longRootAmount)) {
            return;
        }
        CraftConfirmMenu menu = (CraftConfirmMenu) (Object) this;
        // Client側は本家のback actionを送信し、量Menuの再作成はServerだけが行う。
        if (menu.isClientSide()) {
            return;
        }

        this.clearError();
        var locator = menu.getLocator();
        // KeyまたはLocatorが失われた場合は不正な量画面を作らず、HostのMain Menuへ戻る。
        if (this.whatToCraft == null || locator == null) {
            menu.getHost().returnToMainMenu(menu.getPlayer(), menu);
            ci.cancel();
            return;
        }

        MenuOpener.open(CraftAmountMenu.TYPE, menu.getPlayer(), locator);
        // 同じServerPlayerへ作成された量MenuだけへKeyとlong量を設定する。
        if (menu.getPlayer() instanceof ServerPlayer serverPlayer
                && serverPlayer.containerMenu instanceof CraftAmountMenu amountMenu
                && amountMenu instanceof LongCraftAmountMenuBridge bridge) {
            bridge.aco$setWhatToCraftLong(this.whatToCraft, this.aco$longRootAmount);
            amountMenu.broadcastChanges();
            BigCraftingNetwork.sendLongCraftAmountState(
                    serverPlayer,
                    amountMenu.containerId,
                    this.aco$longRootAmount);
            ci.cancel();
            return;
        }

        AE2CraftingOptimizer.LOGGER.error(
                "ACO could not restore a long craft amount because CraftAmountMenu did not expose the required bridge");
        menu.getHost().returnToMainMenu(menu.getPlayer(), menu);
        ci.cancel();
    }

    @Override
    public long aco$getLongRootAmount() {
        return this.aco$longRootAmount;
    }

    @Unique
    private static IGrid aco$findGrid(CraftConfirmMenu menu) {
        Object target = menu.getTarget();
        // CraftConfirmMenuの対象がAction Hostでなければ、Gridを推測せず失敗させる。
        if (!(target instanceof IActionHost actionHost)) {
            return null;
        }
        var node = actionHost.getActionableNode();
        // 未接続または構造解除済みNodeにはクラフト計算を発注しない。
        if (node == null) {
            return null;
        }
        return node.getGrid();
    }
}
