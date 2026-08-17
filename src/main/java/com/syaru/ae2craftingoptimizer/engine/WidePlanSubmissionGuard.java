package com.syaru.ae2craftingoptimizer.engine;

import appeng.api.networking.crafting.CraftingSubmitErrorCode;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.crafting.UnsuitableCpus;
import appeng.crafting.execution.CraftingSubmitResult;
import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BigInteger容量台帳を持たないCPUがwide計画を断るときの、共通の返答と記録。
 *
 * <p>断ること自体はfail-closedとして正しいが、AE2の{@link CraftingSubmitErrorCode#CPU_TOO_SMALL}
 * は「容量が足りない」を意味するコードなので、そのまま返すとプレイヤーはストレージを
 * 増やしに行ってしまう。実際には容量をいくら足しても受理されない。ここでは容量とは
 * 無関係であることが伝わる理由コードを返し、原因をログへ一度だけ残す。</p>
 */
public final class WidePlanSubmissionGuard {
    /** 重複除去表の固定上限。超えたら丸ごと捨てて、常駐量を一定に保つ。 */
    private static final int MAXIMUM_LOGGED_DECLINES = 1024;
    private static final Set<String> LOGGED_DECLINES = ConcurrentHashMap.newKeySet();

    private WidePlanSubmissionGuard() {
    }

    /**
     * 標準AE2 CPUがwide計画を断るときの返答を作り、その理由を記録する。
     *
     * @param plan             断る対象の計画
     * @param availableStorage 断ったCPUがAE2へ見せている空きbyte数
     */
    public static ICraftingSubmitResult declineOnUnsupportedCpu(
            ICraftingPlan plan,
            long availableStorage) {
        logDeclineOnce(plan, availableStorage);
        return unsupportedCpuResult();
    }

    /**
     * 容量不足と読み違えられない拒否理由。
     *
     * <p>AE2の理由コードは公開enumで拡張できないため、除外されたCPUが一台という
     * 形で返す。{@code tooSmall}を0のまま返すことが、この修正の要点。</p>
     */
    public static ICraftingSubmitResult unsupportedCpuResult() {
        return CraftingSubmitResult.noSuitableCpu(new UnsuitableCpus(0, 0, 0, 1));
    }

    private static void logDeclineOnce(ICraftingPlan plan, long availableStorage) {
        if (!ACOConfig.logWidePlanSubmissionDeclines()) {
            return;
        }
        String outputId = plan.finalOutput().what().getId().toString();
        // 同じ出力の連続クリックで同じ警告を積み上げない。
        if (LOGGED_DECLINES.size() >= MAXIMUM_LOGGED_DECLINES) {
            LOGGED_DECLINES.clear();
        }
        if (!LOGGED_DECLINES.add(outputId)) {
            return;
        }
        String exactBytes = Ae2CraftingPlanSidecars.metadata(plan)
                .map(metadata -> metadata.exactBytes().toString())
                .orElse("<unknown>");
        AE2CraftingOptimizer.LOGGER.warn(
                "ACO refused to submit a plan for {} to a standard AE2 crafting CPU: the plan needs {}"
                        + " exact bytes, which no signed-long CPU ledger can hold (the CPU reports {} free)."
                        + " Adding crafting storage cannot fix this; the job needs a crafting CPU with a"
                        + " BigInteger ledger.",
                outputId,
                exactBytes,
                availableStorage);
    }

    /** 単体テスト間で重複除去表を共有しないためのpackage-private reset。 */
    static void clearForTests() {
        LOGGED_DECLINES.clear();
    }
}
