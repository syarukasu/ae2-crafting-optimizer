package com.syaru.ae2craftingoptimizer.mixin;

import appeng.client.gui.widgets.EventRepeater;
import appeng.client.gui.widgets.Scrollbar;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * AE2のスクロールバーはトラック部分を押している間、Page Up/Downを一定間隔で反復する。
 * 他MODの画面処理がmouse-upを消費すると反復だけが残るため、物理ボタン状態を安全弁にする。
 */
@Mixin(value = Scrollbar.class, remap = false)
public abstract class Ae2ScrollbarReleaseSafetyMixin {
    @Shadow
    @Final
    private EventRepeater eventRepeater;

    @Shadow
    private boolean dragging;

    @Inject(method = "tick", at = @At("HEAD"), require = 1)
    private void aco$stopRepeatAfterPhysicalRelease(CallbackInfo ci) {
        if (!ACOConfig.fixStuckAe2ScrollbarRepeat()) {
            return;
        }

        long window = Minecraft.getInstance().getWindow().getWindow();
        if (GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_LEFT) != GLFW.GLFW_PRESS) {
            // 通常のonMouseUpと同じ状態へ戻す。長押し中の反復とドラッグは変更しない。
            this.dragging = false;
            this.eventRepeater.stop();
        }
    }
}
