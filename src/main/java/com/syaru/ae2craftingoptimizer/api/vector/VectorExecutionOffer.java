package com.syaru.ae2craftingoptimizer.api.vector;

import java.util.Objects;

/** Executorが入力所有権移転前に返す、変更を伴わない適格性判定。 */
public record VectorExecutionOffer(
        boolean accepted,
        String rejectionReason,
        int physicalThreadSlots,
        int durationTicks) {
    public VectorExecutionOffer {
        rejectionReason = Objects.requireNonNull(
                rejectionReason, "rejectionReason");
        // 受理時だけ正の設備枠と実行時間を要求し、拒否時は0へ統一する。
        if (accepted) {
            if (physicalThreadSlots <= 0 || durationTicks <= 0) {
                throw new IllegalArgumentException(
                        "accepted vector offer needs positive slots and duration");
            }
        } else if (physicalThreadSlots != 0 || durationTicks != 0) {
            throw new IllegalArgumentException(
                    "rejected vector offer must not reserve resources");
        }
    }

    public static VectorExecutionOffer accepted(
            int physicalThreadSlots,
            int durationTicks) {
        return new VectorExecutionOffer(
                true, "", physicalThreadSlots, durationTicks);
    }

    public static VectorExecutionOffer rejected(String reason) {
        String checked = Objects.requireNonNull(reason, "reason").trim();
        return new VectorExecutionOffer(
                false,
                checked.isEmpty() ? "unsupported vector plan" : checked,
                0,
                0);
    }
}
