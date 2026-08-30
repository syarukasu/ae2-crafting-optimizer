package com.syaru.ae2craftingoptimizer.optimization;

import appeng.api.networking.security.IActionSource;
import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;
import org.jetbrains.annotations.Nullable;

/**
 * CraftingServiceの同期的なjob生成区間だけ、実際にsnapshotへ使った三つのrevisionを運ぶ。
 * workerへはThreadLocalを渡さず、submit完了前に必ず破棄する。
 */
public final class CraftingCalculationSnapshotContext {
    private static final ThreadLocal<Deque<Frame>> FRAMES =
            ThreadLocal.withInitial(ArrayDeque::new);

    private CraftingCalculationSnapshotContext() {
    }

    public static void begin() {
        begin(null, null);
    }

    /** dedup lookupとAE2本体へ同じActionSource参照を渡すrequest frameを開始する。 */
    public static void begin(
            @Nullable Object requester,
            @Nullable IActionSource actionSource) {
        Deque<Frame> frames = FRAMES.get();
        // Issue #167: 前回constructor失敗のframeを次要求へ再利用せず、次要求まで失敗させない。
        if (!frames.isEmpty()) {
            frames.clear();
            FRAMES.remove();
            AE2CraftingOptimizer.LOGGER.warn(
                    "Discarded an unfinished CraftingCalculation snapshot context before a new request");
            frames = FRAMES.get();
        }
        frames.push(new Frame(requester, actionSource));
    }

    /** 現在threadがCraftingServiceのdedup対象requestを構築中かを返す。 */
    public static boolean hasActiveFrame() {
        return !FRAMES.get().isEmpty();
    }

    /** constructor引数がframeを開始したrequesterと同一参照かを検査する。 */
    public static boolean matches(@Nullable Object requester) {
        Deque<Frame> frames = FRAMES.get();
        return !frames.isEmpty() && frames.peek().requester == requester;
    }

    /** AE2 constructorへ渡す、dedup lookup時に一度だけ取得したActionSource。 */
    @Nullable
    public static IActionSource actionSource(@Nullable Object requester) {
        Deque<Frame> frames = FRAMES.get();
        return matches(requester) ? frames.peek().actionSource : null;
    }

    /** 直接newされたCraftingCalculationではframeが無いため、正常な対象外として何もしない。 */
    public static void capture(CalculationRevision revision) {
        Objects.requireNonNull(revision, "revision");
        Deque<Frame> frames = FRAMES.get();
        if (frames.isEmpty()) {
            return;
        }
        Frame frame = frames.peek();
        // Issue #167: 一つのbeginCraftingCalculationが複数snapshotを生成する構造変化は明示失敗する。
        if (frame.revision != null) {
            throw new IllegalStateException("CraftingCalculation revisions were captured twice");
        }
        frame.revision = revision;
    }

    /** submit境界でframeを一度だけ消費し、同じrevisionでFutureを登録する。 */
    @Nullable
    public static CalculationRevision finish() {
        Deque<Frame> frames = FRAMES.get();
        if (frames.isEmpty()) {
            return null;
        }
        Frame frame = frames.pop();
        if (frames.isEmpty()) {
            FRAMES.remove();
        }
        // Issue #167: revision不明のFutureを現在世代へ付け替えない。
        if (frame.revision == null) {
            throw new IllegalStateException("CraftingCalculation revisions were not captured");
        }
        return frame.revision;
    }

    static int depth() {
        return FRAMES.get().size();
    }

    /**
     * Issue #167: Futureのdedupe keyを、constructorが読んだ一組の世代へ固定する。
     * 後から現在世代を読み直して旧Futureを新世代へ付け替えない。
     */
    public record CalculationRevision(
            StorageRevisionTracker.RevisionToken storage,
            long patternGeneration,
            long recipeGeneration,
            long configurationRevision,
            IActionSource actionSource) {
        public CalculationRevision {
            Objects.requireNonNull(storage, "storage");
            // Config revision 0以下は、実際の設定Snapshotを表さないため拒否する。
            if (configurationRevision <= 0L) {
                throw new IllegalArgumentException("configuration revision must be positive");
            }
        }
    }

    private static final class Frame {
        private final Object requester;
        private final IActionSource actionSource;
        private CalculationRevision revision;

        private Frame(
                @Nullable Object requester,
                @Nullable IActionSource actionSource) {
            this.requester = requester;
            this.actionSource = actionSource;
        }
    }
}
