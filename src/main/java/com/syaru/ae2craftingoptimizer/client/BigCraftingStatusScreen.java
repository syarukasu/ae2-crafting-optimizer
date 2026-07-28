package com.syaru.ae2craftingoptimizer.client;

import appeng.api.stacks.AEKey;
import appeng.api.stacks.AmountFormat;
import com.syaru.ae2craftingoptimizer.api.big.BigCraftingStatusInbox;
import com.syaru.ae2craftingoptimizer.api.big.BigCraftingStatusPage;
import com.syaru.ae2craftingoptimizer.engine.vector.LongClampedProgressProjection;
import com.syaru.ae2craftingoptimizer.menu.BigCraftingStatusMenu;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * ACO BigInteger親Jobの実状態だけを表示する画面。
 *
 * <p>表示用long飽和値はServer会計へ戻さず、正確な注文量と残量は
 * {@link BigCraftingStatusPage}のBigInteger Snapshotを正とする。</p>
 */
public final class BigCraftingStatusScreen
        extends AbstractContainerScreen<BigCraftingStatusMenu> {
    /** AE2系画面へ収まりつつ、長い指数表記を一行で表示できる幅。 */
    private static final int PANEL_WIDTH = 276;
    /** タイトル、成果物、進捗、状態、操作Buttonを収める高さ。 */
    private static final int PANEL_HEIGHT = 166;
    /** 進捗比率計算の小数4桁を整数演算へ変換する尺度。 */
    private static final int PROGRESS_SCALE = 10_000;

    private BigCraftingStatusPage.JobSummary<AEKey> latestJob;
    private boolean observedJob;
    private boolean jobEnded;
    private boolean cancelRequested;
    private Button actionButton;

    public BigCraftingStatusScreen(
            BigCraftingStatusMenu menu,
            Inventory inventory,
            Component title) {
        super(menu, inventory, title);
        this.imageWidth = PANEL_WIDTH;
        this.imageHeight = PANEL_HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        actionButton = addRenderableWidget(Button.builder(
                        Component.translatable(
                                "gui.ae2_crafting_optimizer.cancel"),
                        ignored -> onAction())
                .bounds(
                        leftPos + imageWidth - 88,
                        topPos + imageHeight - 28,
                        76,
                        20)
                .build());
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        BigCraftingStatusPage<AEKey> page =
                BigCraftingStatusInbox.latest(menu.runtimeId());
        // 初回Status Packet受信前は、前回値を捏造せず待機表示を維持する。
        if (page == null) {
            return;
        }
        BigCraftingStatusPage.JobSummary<AEKey> current =
                page.jobs().stream()
                        .filter(job -> job.id().equals(menu.jobId()))
                        .findFirst()
                        .orElse(null);
        // 対象JobがSnapshot内にある間だけ、Serverが送った正確値を更新する。
        if (current != null) {
            latestJob = current;
            observedJob = true;
            jobEnded = false;
        } else if (observedJob) {
            /*
             * Status Pageから消えるのはServer台帳がJobを完了または取消して
             * 容量予約を解放した後だけ。Client側で先に完了扱いしない。
             */
            jobEnded = true;
        }
        updateActionButton();
    }

    private void onAction() {
        // Server台帳からJobが消えた後は、同じButtonを画面を閉じる操作へ切り替える。
        if (jobEnded) {
            onClose();
            return;
        }
        // 接続、操作Controller、または重複取消し防止条件が欠ける時はPacketを送らない。
        if (minecraft == null
                || minecraft.gameMode == null
                || cancelRequested) {
            return;
        }
        cancelRequested = true;
        actionButton.active = false;
        minecraft.gameMode.handleInventoryButtonClick(
                menu.containerId,
                BigCraftingStatusMenu.CANCEL_BUTTON_ID);
    }

    private void updateActionButton() {
        // init前のtickではButtonがまだ生成されていないため表示を更新しない。
        if (actionButton == null) {
            return;
        }
        // 完了・取消し確定後はCancelではなくCloseとして再利用する。
        if (jobEnded) {
            actionButton.setMessage(
                    Component.translatable(
                            "gui.ae2_crafting_optimizer.close"));
            actionButton.active = true;
            return;
        }
        actionButton.setMessage(
                Component.translatable(
                        cancelRequested
                                ? "gui.ae2_crafting_optimizer.cancelling"
                                : "gui.ae2_crafting_optimizer.cancel"));
        actionButton.active = observedJob && !cancelRequested;
    }

    @Override
    protected void renderBg(
            GuiGraphics graphics,
            float partialTick,
            int mouseX,
            int mouseY) {
        // 不透明なAE2風パネル。状態行の変化で画面寸法を変えない。
        graphics.fill(
                leftPos,
                topPos,
                leftPos + imageWidth,
                topPos + imageHeight,
                0xFFB8BCCB);
        graphics.fill(
                leftPos + 3,
                topPos + 3,
                leftPos + imageWidth - 3,
                topPos + imageHeight - 3,
                0xFFE3E5EC);
        graphics.fill(
                leftPos + 11,
                topPos + 88,
                leftPos + imageWidth - 11,
                topPos + 102,
                0xFF6A6E7D);

        float progress = progress();
        int availableWidth = imageWidth - 24;
        int filledWidth = Math.max(
                0,
                Math.min(
                        availableWidth,
                        Math.round(availableWidth * progress)));
        graphics.fill(
                leftPos + 12,
                topPos + 89,
                leftPos + 12 + filledWidth,
                topPos + 101,
                0xFF5B9E77);
    }

    @Override
    protected void renderLabels(
            GuiGraphics graphics,
            int mouseX,
            int mouseY) {
        graphics.drawString(
                font,
                title,
                12,
                9,
                0x30323A,
                false);
        // Snapshot受信前は成果物や数量を推測せず、待機行だけを描画する。
        if (latestJob == null) {
            graphics.drawString(
                    font,
                    Component.translatable(
                            "gui.ae2_crafting_optimizer.waiting_status"),
                    12,
                    42,
                    0x555A68,
                    false);
            return;
        }

        AEKey key = latestJob.requestedKey();
        ItemStack displayStack = key.wrapForDisplayOrFilter();
        graphics.renderItem(displayStack, 14, 30);
        graphics.drawString(
                font,
                key.getDisplayName(),
                38,
                29,
                0x30323A,
                false);
        graphics.drawString(
                font,
                Component.translatable(
                        "gui.ae2_crafting_optimizer.exact_requested",
                        BigAmountFormatter.format(
                                key,
                                latestJob.requestedAmount(),
                                AmountFormat.SLOT)),
                38,
                43,
                0x555A68,
                false);

        BigInteger actualRemaining = actualRemaining();
        long displayRemaining =
                LongClampedProgressProjection.clamp(
                        actualRemaining);
        graphics.drawString(
                font,
                Component.translatable(
                        "gui.ae2_crafting_optimizer.display_remaining",
                        BigAmountFormatter.formatCompact(
                                BigInteger.valueOf(displayRemaining))),
                14,
                65,
                0x30323A,
                false);
        graphics.drawString(
                font,
                statusText(),
                14,
                112,
                statusColor(),
                false);
        graphics.drawString(
                font,
                Component.translatable(
                        "gui.ae2_crafting_optimizer.reserved",
                        BigAmountFormatter.formatCompact(
                                latestJob.reservedCapacity())),
                14,
                128,
                0x555A68,
                false);
    }

    @Override
    public void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        // 成果物アイコン上だけ、AEKey本来のTooltipを表示する。
        if (latestJob != null
                && mouseX >= leftPos + 14
                && mouseX < leftPos + 30
                && mouseY >= topPos + 30
                && mouseY < topPos + 46) {
            graphics.renderTooltip(
                    font,
                    latestJob.requestedKey()
                            .wrapForDisplayOrFilter(),
                    mouseX,
                    mouseY);
        }
    }

    private BigInteger actualRemaining() {
        // Snapshot受信前の描画要求は残量0として扱い、Server状態を推測しない。
        if (latestJob == null) {
            return BigInteger.ZERO;
        }
        // 取消時は未実行量を0へ偽装せず、最後にServerが報告した残量を維持する。
        if (jobEnded && !cancelRequested) {
            return BigInteger.ZERO;
        }
        return latestJob.remainingExecutions();
    }

    private float progress() {
        // Snapshot受信前は進捗0で固定する。
        if (latestJob == null) {
            return 0.0F;
        }
        // 正常完了だけを100%とし、取消しを完了演出へ見せない。
        if (jobEnded && !cancelRequested) {
            return 1.0F;
        }
        BigInteger requested = latestJob.requestedAmount();
        BigInteger remaining = latestJob.remainingExecutions();
        BigInteger completed = requested.subtract(
                remaining.min(requested));
        // まだ正数の完了量がない時はBigDecimal除算を行わない。
        if (completed.signum() <= 0) {
            return 0.0F;
        }
        int scaled = new BigDecimal(completed)
                .multiply(BigDecimal.valueOf(PROGRESS_SCALE))
                .divide(
                        new BigDecimal(requested),
                        0,
                        RoundingMode.DOWN)
                .intValue();
        return Math.max(
                0.0F,
                Math.min(
                        1.0F,
                        scaled / (float) PROGRESS_SCALE));
    }

    private Component statusText() {
        // Server台帳から消えたJobは、取消要求の有無で終端表示を分ける。
        if (jobEnded) {
            return Component.translatable(
                    cancelRequested
                            ? "gui.ae2_crafting_optimizer.cancelled"
                            : "gui.ae2_crafting_optimizer.completed");
        }
        // Server確定待ちの間は、最後の実行状態より取消要求を優先表示する。
        if (cancelRequested) {
            return Component.translatable(
                    "gui.ae2_crafting_optimizer.cancelling");
        }
        return Component.translatable(
                "gui.ae2_crafting_optimizer.running",
                latestJob.state().name());
    }

    private int statusColor() {
        // 正常完了は成功色、取消しは待機・警告色、実行中は通常色に分ける。
        if (jobEnded && !cancelRequested) {
            return 0x2E7D4D;
        }
        // 取消し要求後と取消し確定後は同じ警告色を使う。
        if (cancelRequested) {
            return 0xA0661F;
        }
        return 0x40465A;
    }
}
