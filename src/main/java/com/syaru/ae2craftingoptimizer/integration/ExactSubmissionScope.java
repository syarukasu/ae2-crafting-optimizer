package com.syaru.ae2craftingoptimizer.integration;

import appeng.api.networking.crafting.ICraftingPlan;
import java.util.Objects;

/**
 * ACOが正確なBigInteger台帳で承認済みの提出だけを指す、呼出し一回分の目印。
 *
 * <p>CPUのlong容量ゲートは{@code CraftingCpuLogic#trySubmitJob}
 * (およびAdvanced AEの複製) の中にあり、
 * {@code cpu.getAvailableStorage() < plan.bytes()}を見る。
 * wide計画のlong Facadeは常に{@code Long.MAX_VALUE}なので、
 * 残容量がlong上限ぴったりでない限り<b>必ず不成立</b>になり、
 * 理由の出ないCPU_TOO_SMALLとして返る。</p>
 *
 * <p>wide計画の容量判定は提出前に正確なBigInteger台帳へ対して済ませてあり、
 * 不足なら所有権を取る前に拒否している。ここではその「済んでいる」事実だけを
 * 同じスレッドの下流へ渡し、long Facadeで測り直させない。</p>
 */
public final class ExactSubmissionScope {
    private static final ThreadLocal<ICraftingPlan> CURRENT = new ThreadLocal<>();

    private ExactSubmissionScope() {
    }

    /** これから{@code trySubmitJob}へ渡す計画を、ACO承認済みとして記録する。 */
    public static void enter(ICraftingPlan plan) {
        CURRENT.set(Objects.requireNonNull(plan, "plan"));
    }

    /** 呼出しが終わったら必ず外す。残すと無関係な提出まで容量検査を素通りする。 */
    public static void exit() {
        CURRENT.remove();
    }

    /** この計画が、いま実行中のACO承認済み提出そのものかどうか。 */
    public static boolean owns(ICraftingPlan plan) {
        // 同一インスタンスだけを一致させる。等価な別計画は承認の対象外。
        return plan != null && CURRENT.get() == plan;
    }
}
