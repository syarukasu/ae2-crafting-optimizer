package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.networking.security.IActionHost;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.ISubMenuHost;
import appeng.menu.MenuOpener;
import appeng.menu.me.crafting.CraftAmountMenu;
import appeng.menu.me.crafting.CraftConfirmMenu;
import appeng.menu.slot.AppEngSlot;
import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.craftingamount.LongCraftAmountMenuBridge;
import com.syaru.ae2craftingoptimizer.craftingamount.LongCraftAmountRules;
import com.syaru.ae2craftingoptimizer.craftingamount.LongCraftConfirmMenuBridge;
import java.util.Objects;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * CraftAmountMenuへlong専用のサーバー経路を追加する。
 * AE2のconfirm(int, boolean, boolean)は上書きせず、int注文から完全に分離する。
 */
@Mixin(value = CraftAmountMenu.class, remap = false)
public abstract class CraftAmountMenuLongAmountMixin
        implements LongCraftAmountMenuBridge {
    @Shadow
    @Final
    private AppEngSlot craftingItem;

    @Shadow
    private AEKey whatToCraft;

    @Shadow
    @Final
    private ISubMenuHost host;

    /** 戻る操作から復元されたlong量。通常の新規int画面では0のまま。 */
    @Unique
    private long aco$initialAmount;

    @Inject(method = "setWhatToCraft", at = @At("HEAD"), require = 1)
    private void aco$clearLongInitialAmountForNormalOpen(
            AEKey what,
            int amount,
            CallbackInfo ci) {
        // 本家open(AEKey, int)が使われた画面へ、前のlong初期値を持ち越さない。
        this.aco$initialAmount = 0L;
    }

    @Override
    public void aco$confirmLong(
            long amount,
            boolean subtractStoredAmount,
            boolean autoStart) {
        CraftAmountMenu menu = (CraftAmountMenu) (Object) this;
        // C2S側の検証に加えてMenu側でも設定・範囲・対象Keyを再検証する。
        if (!ACOConfig.enableLongRootCraftAmounts()
                || !LongCraftAmountRules.isValidExtendedRequest(amount)
                || this.whatToCraft == null
                || menu.isClientSide()) {
            return;
        }

        this.aco$initialAmount = amount;
        long requestedAmount = amount;
        // 「=」入力だけ、AE2本来の意味と同じく現在庫を要求量から差し引く。
        if (subtractStoredAmount) {
            Object target = menu.getTarget();
            // Action Hostと接続Gridを一度だけ取得できた場合に限り、現在庫を参照する。
            if (target instanceof IActionHost actionHost) {
                var node = actionHost.getActionableNode();
                // 未接続Nodeでは在庫量を0と推測せず、本家と同じ元要求を維持する。
                if (node != null) {
                    var grid = node.getGrid();
                    // 構造解除と同tickでGridが消えた場合も、在庫差引きを行わない。
                    if (grid != null) {
                        long available = grid
                                .getStorageService()
                                .getCachedInventory()
                                .get(this.whatToCraft);
                        requestedAmount = LongCraftAmountRules.subtractAvailable(
                                requestedAmount,
                                available);
                    }
                }
            }
        }

        var locator = menu.getLocator();
        Player player = menu.getPlayer();
        // 在庫差引き後に要求が0なら、本家と同じく確認画面を開かず元Menuへ戻る。
        if (requestedAmount <= 0L) {
            // 本家と同様、戻り先Locatorが有効な時だけHostへ復帰を依頼する。
            if (locator != null) {
                this.host.returnToMainMenu(player, menu);
            }
            return;
        }
        // Locatorが失われた古い画面からは、新しいCraftConfirmMenuを開かない。
        if (locator == null) {
            return;
        }

        MenuOpener.open(CraftConfirmMenu.TYPE, player, locator);
        // 開いたMenuが期待した型とlong Bridgeを持つ場合だけ計算を開始する。
        if (player.containerMenu instanceof CraftConfirmMenu confirmMenu
                && confirmMenu instanceof LongCraftConfirmMenuBridge bridge) {
            confirmMenu.setAutoStart(autoStart);
            boolean planned = bridge.aco$planLong(
                    this.whatToCraft,
                    requestedAmount,
                    appeng.api.networking.crafting.CalculationStrategy.REPORT_MISSING_ITEMS);
            // Grid消失などで計画開始できなければ、空の計算画面を残さず閉じる。
            if (!planned) {
                confirmMenu.setValidMenu(false);
            }
            menu.broadcastChanges();
            return;
        }

        AE2CraftingOptimizer.LOGGER.error(
                "ACO refused a long craft amount because CraftConfirmMenu did not expose the required bridge");
    }

    @Override
    public void aco$setWhatToCraftLong(AEKey what, long amount) {
        Objects.requireNonNull(what, "what");
        // int範囲をこのSidecarへ通す呼出しは実装ミスなので、値を丸めず拒否する。
        if (!LongCraftAmountRules.isValidExtendedRequest(amount)) {
            throw new IllegalArgumentException("long craft amount must exceed Integer.MAX_VALUE");
        }
        this.whatToCraft = what;
        this.aco$initialAmount = amount;
        /*
         * AE2の表示用ItemStackはint countなので、Key同期には安全な互換値だけを載せる。
         * 真のlong量は専用S2C Messageで同じcontainer IDへ送る。
         */
        this.craftingItem.set(GenericStack.wrapInItemStack(
                what,
                LongCraftAmountRules.VANILLA_AE2_MAXIMUM));
    }

    @Override
    public void aco$setClientInitialAmount(long amount) {
        // Client側も範囲を再検証し、異常値を入力欄へ反映しない。
        if (LongCraftAmountRules.isValidExtendedRequest(amount)) {
            this.aco$initialAmount = amount;
        }
    }

    @Override
    public long aco$getInitialAmount() {
        return this.aco$initialAmount;
    }
}
