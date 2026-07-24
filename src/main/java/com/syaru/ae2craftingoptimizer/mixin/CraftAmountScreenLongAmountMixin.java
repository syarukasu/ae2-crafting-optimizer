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
import java.util.OptionalLong;
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
    /**
     * Long最大値19桁、先頭の「=」、Fluid/Chemicalの小数単位、短い数式を収める。
     * 数値範囲は文字数ではなくBigDecimalの厳密変換で別途検証する。
     */
    @Unique
    private static final int ACO_LONG_INPUT_MAXIMUM_CHARACTERS = 32;

    @Shadow
    @Final
    private Button next;

    @Shadow
    @Final
    private NumberEntryWidget amountToCraft;

    /** 戻る操作で受け取った初期値を、ユーザー編集中に繰り返し上書きしないための印。 */
    @Unique
    private long aco$appliedInitialAmount;

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
        ((NumberEntryWidgetAccessor) (Object) this.amountToCraft)
                .aco$getTextField()
                .setMaxLength(ACO_LONG_INPUT_MAXIMUM_CHARACTERS);
    }

    @Inject(method = "updateBeforeRender", at = @At("RETURN"), require = 1)
    private void aco$validateLongInputAndRestoreInitialAmount(CallbackInfo ci) {
        // 無効時はAE2本来のgetIntValue()によるボタン状態を変更しない。
        if (!ACOConfig.enableLongRootCraftAmounts()) {
            return;
        }

        CraftAmountMenu menu = ((CraftAmountScreen) (Object) this).getMenu();
        long initialAmount = menu instanceof LongCraftAmountMenuBridge bridge
                ? bridge.aco$getInitialAmount()
                : 0L;
        // サーバーから新しいlong初期値が届いた時だけ入力欄へ一度反映する。
        if (LongCraftAmountRules.isValidExtendedRequest(initialAmount)
                && initialAmount != this.aco$appliedInitialAmount) {
            this.amountToCraft.setLongValue(initialAmount);
            this.aco$appliedInitialAmount = initialAmount;
        }

        OptionalLong exactAmount = LongCraftAmountClientParser.parseExact(this.amountToCraft);
        // 本家のint判定ではlong入力が無効になるため、厳密long検証結果だけでボタンを復帰する。
        this.next.active = exactAmount.isPresent();
    }

    @Inject(method = "confirm", at = @At("HEAD"), cancellable = true, require = 1)
    private void aco$sendLongAmountOnlyAboveAe2Boundary(CallbackInfo ci) {
        // 無効時はprivate confirm()全体をAE2へ委譲する。
        if (!ACOConfig.enableLongRootCraftAmounts()) {
            return;
        }

        OptionalLong exactAmount = LongCraftAmountClientParser.parseExact(this.amountToCraft);
        // 範囲外の数式をNumberEntryWidget.longValue()で折り返させず、操作を何も送らず終了する。
        if (exactAmount.isEmpty()) {
            ci.cancel();
            return;
        }
        long amount = exactAmount.getAsLong();
        // int以下は既存ConfirmAutoCraftPacketへ通し、追加Packetを一切使わない。
        if (!LongCraftAmountRules.usesExtendedPath(amount)) {
            return;
        }

        CraftAmountMenu menu = ((CraftAmountScreen) (Object) this).getMenu();
        BigCraftingNetwork.sendLongCraftAmount(
                menu.containerId,
                amount,
                this.amountToCraft.startsWithEquals(),
                Screen.hasShiftDown());
        ci.cancel();
    }
}
