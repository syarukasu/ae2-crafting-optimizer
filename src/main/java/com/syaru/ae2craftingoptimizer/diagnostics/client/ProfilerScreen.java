package com.syaru.ae2craftingoptimizer.diagnostics.client;

import com.syaru.ae2craftingoptimizer.diagnostics.ProfilerSnapshot;
import com.syaru.ae2craftingoptimizer.diagnostics.network.ProfilerNetwork;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

/** Read-only, manually refreshed calculation history. No menus, world access or craft actions. */
@OnlyIn(Dist.CLIENT)
public final class ProfilerScreen extends Screen {
    private static final int ROW_HEIGHT = 30;
    private static final int LINE_HEIGHT = 11;
    private static final String NOTE = "CPU\u6642\u9593\u30fb\u5f85\u6a5f\u30fb\u5404\u30d5\u30a7\u30fc\u30ba\u306f\u91cd\u8907\u3057\u307e\u3059\uff08\u5408\u8a08\u4e0d\u53ef\uff09\u3002\u8a08\u7b97\u306e\u307f\u3001\u7269\u7406\u30af\u30e9\u30d5\u30c8\u5b8c\u4e86\u3067\u306f\u3042\u308a\u307e\u305b\u3093\u3002";
    private ProfilerSnapshot snapshot;
    private int selected;
    private int recentOffset;
    private int detailOffset;
    private int top;
    private int bottom;
    private int divider;
    private List<FormattedCharSequence> details = List.of();
    private Button refresh;
    private long lastRefresh;
    private boolean refreshed;

    public ProfilerScreen(ProfilerSnapshot snapshot) {
        super(Component.literal("ACO \u8a08\u7b97\u30d7\u30ed\u30d5\u30a1\u30a4\u30e9\u30fc"));
        this.snapshot = snapshot;
    }

    public static void accept(boolean open, byte[] json) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.getConnection() == null) {
            return;
        }
        ProfilerSnapshot snapshot;
        try {
            snapshot = ProfilerSnapshot.decodeJson(json);
        } catch (RuntimeException malformed) {
            return;
        }
        if (open) {
            client.setScreen(new ProfilerScreen(snapshot));
        } else if (client.screen instanceof ProfilerScreen screen) {
            screen.update(snapshot);
        }
    }

    @Override
    protected void init() {
        divider = Math.max(110, width / 3);
        top = 48 + font.split(Component.literal(NOTE), Math.max(1, width - 24)).size() * LINE_HEIGHT;
        bottom = height - 34;
        refresh = addRenderableWidget(Button.builder(Component.literal("\u21bb"), button -> refresh())
                .bounds(12, height - 26, 24, 20)
                .tooltip(Tooltip.create(Component.literal("\u66f4\u65b0"))).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(width - 92, height - 26, 80, 20).build());
        rebuildDetails();
    }

    private void refresh() {
        var connection = Minecraft.getInstance().getConnection();
        if (connection != null && ProfilerNetwork.isRemotePresent(connection.getConnection())) {
            ProfilerNetwork.requestRefresh();
            lastRefresh = System.nanoTime();
            refreshed = true;
            refresh.active = false;
        }
    }

    @Override
    public void tick() {
        var connection = Minecraft.getInstance().getConnection();
        refresh.active = connection != null && ProfilerNetwork.isRemotePresent(connection.getConnection())
                && (!refreshed || System.nanoTime() - lastRefresh >= 1_000_000_000L);
    }

    private void update(ProfilerSnapshot next) {
        Long order = snapshot.profiles().isEmpty() ? null : sample().order();
        snapshot = next;
        selected = 0;
        if (order != null) {
            for (int i = 0; i < next.profiles().size(); i++) {
                if (next.profiles().get(next.profiles().size() - 1 - i).order() == order) {
                    selected = i;
                    break;
                }
            }
        }
        detailOffset = 0;
        rebuildDetails();
    }

    private ProfilerSnapshot.Sample sample() {
        return snapshot.profiles().get(snapshot.profiles().size() - 1 - selected);
    }

    private void rebuildDetails() {
        List<String> lines = new ArrayList<>();
        if (!snapshot.available()) {
            lines.add("\u8a08\u6e2c\u60c5\u5831: " + ProfilerSnapshot.UNKNOWN);
        } else if (snapshot.profiles().isEmpty()) {
            lines.add("\u8a18\u9332\u306a\u3057");
        } else {
            var sample = sample();
            var largest = sample.largestMeasuredInterval();
            lines.add("\u6700\u5927\u306e\u8a08\u6e2c\u6e08\u307f\u533a\u9593: " + (largest == null
                    ? ProfilerSnapshot.UNKNOWN : metricLabel(largest.getKey()) + " "
                            + ProfilerSnapshot.nanosText(largest.getValue())));
            lines.add("\u539f\u56e0\u306e\u65ad\u5b9a\u3067\u306f\u3042\u308a\u307e\u305b\u3093\u3002VM\u533a\u9593\u306f\u5f85\u6a5f\u3092\u542b\u307f\u307e\u3059\u3002");
            lines.add("\u30ef\u30fc\u30eb\u30c9\u8aad\u53d6\u5f85\u6a5f (worldWait): "
                    + ProfilerSnapshot.nanosText(sample.nanos().get("worldWait")));
            lines.add("\u30ef\u30fc\u30ab\u30fc CPU (workerCpu): "
                    + ProfilerSnapshot.nanosText(sample.nanos().get("workerCpu")));
            lines.add("");
            lines.add("\u30ad\u30e3\u30c3\u30b7\u30e5\u30d2\u30c3\u30c8\u7387: hits / (hits + misses)");
            for (String cache : List.of("producerCache", "programCache", "emitterCache", "inputCache", "remainderCache",
                    "compile", "fuzzy")) {
                lines.add(cache + ": " + (snapshot.detailed() ? sample.cacheEfficiency(cache) : ProfilerSnapshot.UNKNOWN));
            }
            lines.add("");
            lines.add("\u6ce8\u6587: " + ProfilerSnapshot.countText(sample.order()));
            lines.add("\u51fa\u529b: " + sample.output());
            lines.add("\u8981\u6c42\u6570: " + sample.requested());
            lines.add("\u7d50\u679c: " + sample.outcome());
            lines.add("\u7406\u7531: " + sample.reason());
            lines.add("\u8a08\u7b97\u7d4c\u8def: " + sample.route());
            lines.add("\u6700\u9069\u5316\u9069\u683c\u6027: " + sample.eligibility());
            lines.add("\u30d5\u30a9\u30fc\u30eb\u30d0\u30c3\u30af: " + sample.fallback());
            lines.add("");
            lines.add("\u6642\u9593\uff08\u91cd\u8907\u3042\u308a\u30fb\u5408\u8a08\u4e0d\u53ef\uff09");
            if (sample.nanos().isEmpty()) {
                lines.add(ProfilerSnapshot.UNKNOWN);
            }
            sample.nanos().forEach((key, value) -> lines.add(metricLabel(key) + " (" + key + "): "
                    + ProfilerSnapshot.nanosText(value)));
            lines.add("");
            lines.add("\u30ab\u30a6\u30f3\u30bf\u30fc\uff08\u8a73\u7d30\u7121\u52b9\u6642\u306f\u4e0d\u660e\u3092\u542b\u3080\uff09");
            if (sample.counters().isEmpty()) {
                lines.add(ProfilerSnapshot.UNKNOWN);
            }
            sample.counters().forEach((key, value) -> lines.add(key + ": " + ProfilerSnapshot.countText(value)));
        }
        List<FormattedCharSequence> wrapped = new ArrayList<>();
        for (String line : lines) {
            if (line.isEmpty()) {
                wrapped.add(FormattedCharSequence.EMPTY);
            } else {
                wrapped.addAll(font.split(Component.literal(line), Math.max(1, width - divider - 30)));
            }
        }
        details = List.copyOf(wrapped);
        clampOffsets();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        graphics.drawString(font, title, 12, 10, 0xFFFFFF);
        String status = "\u8a08\u6e2c: " + flag(snapshot.enabled()) + "  \u8a73\u7d30: " + flag(snapshot.detailed())
                + "  \u5c65\u6b74: " + snapshot.profiles().size()
                + (!snapshot.available() ? "  " + ProfilerSnapshot.UNKNOWN : "")
                + (snapshot.truncated() ? "  \u7701\u7565\u3042\u308a" : "");
        graphics.drawString(font, font.plainSubstrByWidth(status, width - 24), 12, 24, 0xB7D6C5);
        int noteY = 37;
        for (var line : font.split(Component.literal(NOTE), Math.max(1, width - 24))) {
            graphics.drawString(font, line, 12, noteY, 0xD3C69B);
            noteY += LINE_HEIGHT;
        }
        graphics.drawString(font, "\u76f4\u8fd1\u306e\u8a08\u7b97", 12, top - 11, 0xFFFFFF);
        graphics.drawString(font, "\u8a73\u7d30", divider + 10, top - 11, 0xFFFFFF);
        graphics.fill(10, top, divider, bottom, 0xB014181B);
        graphics.fill(divider + 6, top, width - 10, bottom, 0xB014181B);
        graphics.enableScissor(10, top, divider, bottom);
        int visible = recentRows();
        for (int row = recentOffset; row < Math.min(snapshot.profiles().size(), recentOffset + visible); row++) {
            var sample = snapshot.profiles().get(snapshot.profiles().size() - 1 - row);
            int y = top + (row - recentOffset) * ROW_HEIGHT;
            if (row == selected) {
                graphics.fill(10, y, divider - 4, y + ROW_HEIGHT, 0xFF324E48);
            }
            graphics.drawString(font, fit("#" + ProfilerSnapshot.countText(sample.order()) + " " + sample.outcome()),
                    14, y + 4, 0xFFFFFF);
            graphics.drawString(font, fit(sample.output()), 14, y + 16, 0xC0C8CC);
        }
        graphics.disableScissor();
        graphics.enableScissor(divider + 6, top, width - 10, bottom);
        for (int row = detailOffset; row < Math.min(details.size(), detailOffset + detailRows()); row++) {
            graphics.drawString(font, details.get(row), divider + 10,
                    top + 4 + (row - detailOffset) * LINE_HEIGHT, 0xE0E5E8);
        }
        graphics.disableScissor();
        scrollbar(graphics, divider - 3, snapshot.profiles().size(), visible, recentOffset);
        scrollbar(graphics, width - 13, details.size(), detailRows(), detailOffset);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private String fit(String text) {
        int available = Math.max(1, divider - 25);
        return font.width(text) <= available ? text : font.plainSubstrByWidth(text, Math.max(1, available - 9)) + "...";
    }

    private static String flag(boolean enabled) {
        return enabled ? "\u6709\u52b9" : "\u7121\u52b9";
    }

    private static String metricLabel(String key) {
        return switch (key) {
            case "elapsed" -> "\u7d4c\u904e";
            case "initializationQueue" -> "\u521d\u671f\u5316\u30ad\u30e5\u30fc";
            case "workerQueue" -> "\u30ef\u30fc\u30ab\u30fc\u30ad\u30e5\u30fc";
            case "stock" -> "\u5728\u5eab\u53d6\u5f97";
            case "prepare" -> "\u6e96\u5099";
            case "vm" -> "VM\u533a\u9593\uff08\u5f85\u6a5f\u8fbc\u307f\uff09";
            case "validate" -> "\u691c\u8a3c";
            case "finalize" -> "\u7d42\u4e86\u51e6\u7406";
            case "vmActive" -> "VM\u6d3b\u52d5";
            case "workerCpu" -> "\u30ef\u30fc\u30ab\u30fcCPU";
            case "worldWait" -> "\u30ef\u30fc\u30eb\u30c9\u8aad\u53d6\u5f85\u6a5f";
            case "exclusive" -> "\u6392\u4ed6\u8aad\u53d6\u533a\u9593";
            case "serverQueue" -> "\u30b5\u30fc\u30d0\u30fc\u30ad\u30e5\u30fc";
            case "serverWork" -> "\u30b5\u30fc\u30d0\u30fc\u51e6\u7406";
            case "handoffBlocked" -> "\u5f15\u6e21\u3057\u5f85\u6a5f";
            default -> key;
        };
    }

    private void scrollbar(GuiGraphics graphics, int x, int total, int visible, int offset) {
        if (total > visible && bottom > top) {
            int height = bottom - top;
            int thumb = Math.max(8, height * visible / total);
            int y = top + (height - thumb) * offset / (total - visible);
            graphics.fill(x, top, x + 2, bottom, 0xFF343C41);
            graphics.fill(x, y, x + 2, y + thumb, 0xFF9CAEB3);
        }
    }

    private int recentRows() {
        return Math.max(1, (bottom - top) / ROW_HEIGHT);
    }

    private int detailRows() {
        return Math.max(1, (bottom - top - 4) / LINE_HEIGHT);
    }

    private void clampOffsets() {
        recentOffset = Math.max(0, Math.min(recentOffset, snapshot.profiles().size() - recentRows()));
        detailOffset = Math.max(0, Math.min(detailOffset, details.size() - detailRows()));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseY >= top && mouseY < bottom) {
            if (mouseX >= 10 && mouseX < divider) {
                recentOffset -= (int) Math.signum(delta) * 3;
            } else if (mouseX >= divider + 6 && mouseX < width - 10) {
                detailOffset -= (int) Math.signum(delta) * 3;
            }
            clampOffsets();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= 10 && mouseX < divider && mouseY >= top && mouseY < bottom) {
            int row = recentOffset + (int) (mouseY - top) / ROW_HEIGHT;
            if (row < snapshot.profiles().size() && row < recentOffset + recentRows()) {
                selected = row;
                detailOffset = 0;
                rebuildDetails();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        if (key == GLFW.GLFW_KEY_PAGE_UP || key == GLFW.GLFW_KEY_PAGE_DOWN) {
            detailOffset += (key == GLFW.GLFW_KEY_PAGE_UP ? -1 : 1) * detailRows();
            clampOffsets();
            return true;
        }
        if (!snapshot.profiles().isEmpty() && (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_DOWN)) {
            selected = Math.max(0, Math.min(snapshot.profiles().size() - 1,
                    selected + (key == GLFW.GLFW_KEY_UP ? -1 : 1)));
            recentOffset = Math.min(recentOffset, selected);
            recentOffset = Math.max(recentOffset, selected - recentRows() + 1);
            detailOffset = 0;
            rebuildDetails();
            return true;
        }
        return super.keyPressed(key, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
