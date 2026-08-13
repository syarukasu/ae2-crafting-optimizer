package com.syaru.ae2craftingoptimizer.optimization;

import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import org.jetbrains.annotations.Nullable;

/** BigInteger経路の採用・辞退理由を、計算結果と分離して記録する。 */
public final class BigIntegerPlanDiagnostics {
    private static final Map<BigIntegerPlanDeclineReason, LongAdder> COUNTERS =
            new ConcurrentHashMap<>();

    private BigIntegerPlanDiagnostics() {
    }

    public static void record(
            BigIntegerPlanDeclineReason reason,
            @Nullable String outputId,
            String detail) {
        record(reason, outputId, null, -1L, -1L, detail);
    }

    public static void record(
            BigIntegerPlanDeclineReason reason,
            @Nullable String outputId,
            @Nullable BigInteger requestedAmount,
            long patternGeneration,
            long recipeGeneration,
            String detail) {
        COUNTERS.computeIfAbsent(reason, ignored -> new LongAdder()).increment();
        // 詳細ログは設定時だけ出し、通常時のログ汚染を避ける。
        // ユニット試験や早期初期化ではConfig未ロードでも統計記録を継続する。
        if (!detailedLoggingEnabled()) {
            return;
        }
        AE2CraftingOptimizer.LOGGER.debug(
                "ACO BigInteger plan declined: reason={}, output={}, requested={}, "
                        + "patternGeneration={}, recipeGeneration={}, thread={}, detail={}",
                reason,
                outputId == null ? "<unknown>" : outputId,
                requestedAmount == null ? "<unknown>" : requestedAmount,
                patternGeneration < 0L ? "<unknown>" : patternGeneration,
                recipeGeneration < 0L ? "<unknown>" : recipeGeneration,
                Thread.currentThread().getName(),
                detail);
    }

    private static boolean detailedLoggingEnabled() {
        try {
            return ACOConfig.logBigIntegerPlanDeclines();
        } catch (IllegalStateException configNotLoaded) {
            return false;
        }
    }

    public static List<String> summaryLines() {
        List<String> lines = new ArrayList<>();
        // enum順で出力し、同じ環境の/aco statsを比較可能にする。
        for (BigIntegerPlanDeclineReason reason : BigIntegerPlanDeclineReason.values()) {
            LongAdder counter = COUNTERS.get(reason);
            // 未発生の理由は統計表示を短くするため省略する。
            if (counter == null || counter.sum() == 0L) {
                continue;
            }
            lines.add("BigInteger plan " + reason + ": " + counter.sum());
        }
        return List.copyOf(lines);
    }

    public static void reset() {
        // 現在の理由カウンタだけをリセットし、参照中のMapを差し替えない。
        COUNTERS.values().forEach(LongAdder::reset);
    }
}
