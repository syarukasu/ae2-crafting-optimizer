package com.syaru.ae2craftingoptimizer.mixin;

import appeng.client.gui.me.crafting.CraftAmountScreen;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.NumberEntryWidget;
import appeng.menu.me.crafting.CraftAmountMenu;
import com.syaru.ae2craftingoptimizer.client.LongCraftAmountClientParser;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.craftingamount.LongCraftAmountMenuBridge;
import com.syaru.ae2craftingoptimizer.craftingamount.LongCraftAmountRules;
import com.syaru.ae2craftingoptimizer.network.BigCraftingNetwork;
import java.math.BigInteger;
import com.syaru.ae2craftingoptimizer.access.ExactCraftAmountWidgetAccess;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * AE2のクラフト量画面へlong入力を追加する。
 * int範囲ではconfirm()をキャンセルせず、AE2本来のPacket経路をそのまま通す。
 */
@Mixin(value = CraftAmountScreen.class, remap = false)
public abstract class CraftAmountScreenLongAmountMixin {
    @Shadow
    @Final
    private Button next;

    @Shadow
    @Final
    private NumberEntryWidget amountToCraft;

    /** 戻る操作で受け取った初期値を、ユーザー編集中に繰り返し上書きしないための印。 */
    @Unique
    private BigInteger aco$appliedInitialAmount = BigInteger.ZERO;

    @Shadow private void confirm() { throw new AssertionError(); }

    @Inject(method = "<init>", at = @At("RETURN"), require = 1)
    private void aco$enableLongInput(
            CraftAmountMenu menu,
            Inventory inventory,
            Component title,
            ScreenStyle style,
            CallbackInfo ci) {
        // 無効時は最大値も入力欄長もAE2 15.4.10のままにする。
        if (!ACOConfig.enableLongRootCraftAmounts()) {
            return;
        }
        this.amountToCraft.setMaxValue(Long.MAX_VALUE);
        var exact = (ExactCraftAmountWidgetAccess) this.amountToCraft;
        exact.aco$enableExactCraftAmounts();
        ((NumberEntryWidgetAccessor) (Object) this.amountToCraft).aco$getTextField().setOnConfirm(() -> {
            if (exact.aco$getExactCraftAmount().isPresent()) this.confirm();
        });
    }

    @Inject(method = "updateBeforeRender", at = @At("RETURN"), require = 1)
    private void aco$validateLongInputAndRestoreInitialAmount(CallbackInfo ci) {
        // 無効時はAE2本来のgetIntValue()によるボタン状態を変更しない。
        if (!ACOConfig.enableLongRootCraftAmounts()) {
            return;
        }

        CraftAmountMenu menu = ((CraftAmountScreen) (Object) this).getMenu();
        BigInteger initialAmount = menu instanceof LongCraftAmountMenuBridge bridge
                ? bridge.aco$getInitialExactAmount()
                : BigInteger.ZERO;
        // サーバーから新しいlong初期値が届いた時だけ入力欄へ一度反映する。
        if (LongCraftAmountRules.isValidAmount(initialAmount)
                && !initialAmount.equals(this.aco$appliedInitialAmount)) {
            ((ExactCraftAmountWidgetAccess) this.amountToCraft).aco$setExactCraftAmount(initialAmount);
            this.aco$appliedInitialAmount = initialAmount;
        }

        var exactAmount = LongCraftAmountClientParser.parseBigInteger(this.amountToCraft);
        // 本家のint判定ではlong入力が無効になるため、厳密long検証結果だけでボタンを復帰する。
        this.next.active = exactAmount.isPresent();
    }

    @Inject(method = "confirm", at = @At("HEAD"), cancellable = true, require = 1)
    private void aco$sendLongAmountOnlyAboveAe2Boundary(CallbackInfo ci) {
        // 無効時はprivate confirm()全体をAE2へ委譲する。
        if (!ACOConfig.enableLongRootCraftAmounts()) {
            return;
        }

        var exactAmount = LongCraftAmountClientParser.parseBigInteger(this.amountToCraft);
        // 範囲外の数式をNumberEntryWidget.longValue()で折り返させず、操作を何も送らず終了する。
        if (exactAmount.isEmpty()) {
            ci.cancel();
            return;
        }
        BigInteger amount = exactAmount.get();
        // int以下は既存ConfirmAutoCraftPacketへ通し、追加Packetを一切使わない。
        if (amount.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) <= 0) {
            return;
        }

        CraftAmountMenu menu = ((CraftAmountScreen) (Object) this).getMenu();
        BigCraftingNetwork.sendExactCraftAmount(
                menu.containerId,
                amount,
                this.amountToCraft.startsWithEquals(),
                Screen.hasShiftDown());
        ci.cancel();
    }
}
