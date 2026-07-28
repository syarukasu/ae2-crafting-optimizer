package com.syaru.ae2craftingoptimizer.api.craftingtable;

import appeng.api.stacks.AEKey;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** 物理Workerの小さな進捗Receipt。注文全文や入力一覧は同期しない。 */
public record CraftingTableBatchSnapshot(
        UUID transactionId,
        String payloadDigest,
        State state,
        int progress,
        int maximumProgress,
        Map<AEKey, BigInteger> exactOutputs,
        String detail) {
    /** 状態文字列でNBTまたはログを過剰に膨らませない固定上限。 */
    private static final int MAXIMUM_DETAIL_LENGTH = 2_048;

    public CraftingTableBatchSnapshot {
        Objects.requireNonNull(transactionId, "transactionId");
        payloadDigest = Objects.requireNonNull(
                payloadDigest,
                "payloadDigest").trim();
        Objects.requireNonNull(state, "state");
        detail = Objects.requireNonNull(
                detail,
                "detail");
        Map<AEKey, BigInteger> copy =
                new LinkedHashMap<>();
        Objects.requireNonNull(
                        exactOutputs,
                        "exactOutputs")
                .forEach((key, amount) -> {
                    Objects.requireNonNull(
                            key,
                            "exact output key");
                    // Snapshotへは正数かつAPI固定bit上限内の数量だけを保存する。
                    if (amount == null
                            || amount.signum() <= 0
                            || amount.bitLength()
                                    > CraftingTableBatchRequest
                                            .MAXIMUM_COUNT_BITS) {
                        throw new IllegalArgumentException(
                                "invalid exact output amount");
                    }
                    copy.put(key, amount);
                });
        exactOutputs = Map.copyOf(copy);
        // 不正な進捗や識別文字列は親Jobの状態判断を曖昧にするため拒否する。
        if (payloadDigest.isEmpty()
                || maximumProgress <= 0
                || progress < 0
                || progress > maximumProgress
                || detail.length()
                        > MAXIMUM_DETAIL_LENGTH) {
            throw new IllegalArgumentException(
                    "invalid crafting-table batch snapshot");
        }
        // 出力提示中と永続完了Receiptは、どちらも同じ実出力一覧を必須にする。
        if ((state == State.OUTPUT_READY
                        || state == State.ACKNOWLEDGED)
                && exactOutputs.isEmpty()) {
            throw new IllegalArgumentException(
                    "completed batch snapshot has no outputs");
        }
    }

    public enum State {
        RUNNING,
        OUTPUT_READY,
        ACKNOWLEDGED,
        CANCELLED,
        QUARANTINED
    }
}
