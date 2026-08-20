package com.syaru.ae2craftingoptimizer.engine;

import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.networking.crafting.UnsuitableCpus;
import appeng.crafting.execution.CraftingSubmitResult;
import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * BigInteger台帳非対応CPUの拒否を、容量不足と誤報しないための共通境界。
 *
 * <p>回帰防止: ACO Issue #103。コンパイル済み計画や正確容量を取得できない状態を
 * {@code CPU_TOO_SMALL}として扱うと、容量を増やしても解決しない誤案内になる。</p>
 */
public final class WidePlanSubmissionGuard {
    /** 警告済み出力IDを最大1024件だけ保持し、ログ連打と常駐量増加を防ぐ。 */
    private static final int MAXIMUM_LOGGED_DECLINES = 1024;
    private static final Set<String> LOGGED_DECLINES = ConcurrentHashMap.newKeySet();

    private WidePlanSubmissionGuard() {
    }

    public static ICraftingSubmitResult declineOnUnsupportedCpu(
            ICraftingPlan plan,
            long availableStorage) {
        logDeclineOnce(plan, availableStorage);
        return unsupportedCpuResult();
    }

    /** 容量不足カウンタを増やさず、BigInteger台帳非対応CPUとして拒否する。 */
    public static ICraftingSubmitResult unsupportedCpuResult() {
        // tooSmallを0のまま返し、容量を増やせば解決するという誤案内を避ける。
        return CraftingSubmitResult.noSuitableCpu(new UnsuitableCpus(0, 0, 0, 1));
    }

    private static void logDeclineOnce(ICraftingPlan plan, long availableStorage) {
        if (!ACOConfig.logWidePlanSubmissionDeclines()) {
            return;
        }
        String outputId = plan.finalOutput().what().getId().toString();
        // 長時間稼働でも重複除去表が無制限に増えないよう、固定上限で再利用する。
        if (LOGGED_DECLINES.size() >= MAXIMUM_LOGGED_DECLINES) {
            LOGGED_DECLINES.clear();
        }
        if (!LOGGED_DECLINES.add(outputId)) {
            return;
        }
        String exactBytes = Ae2CraftingPlanSidecars.metadata(plan)
                .map(WidePlanSubmissionGuard::exactBytesText)
                .orElse("<unknown>");
        AE2CraftingOptimizer.LOGGER.warn(
                "ACO refused wide plan {} because this CPU has no BigInteger ledger;"
                        + " exactBytes={}, signedLongFree={}. This is not CPU capacity exhaustion.",
                outputId,
                exactBytes,
                availableStorage);
    }

    /** Wide計画が共通契約として保持する、正確なCPU byte数を文字列化する。 */
    private static String exactBytesText(WideCraftingPlan plan) {
        return plan.exactBytes().toString();
    }

    static void clearForTests() {
        LOGGED_DECLINES.clear();
    }
}
