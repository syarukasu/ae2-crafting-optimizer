package com.syaru.ae2craftingoptimizer.api.batch.v2;

import java.util.Objects;
import java.util.UUID;

/**
 * Batch入力の所有権が送信先へ移ったことを示す証明。
 * 単なるpushPatternのtrueではなく、取引IDとPayloadを永続状態へ結び付けたAdapterだけが生成する。
 */
public record BatchOwnershipProof(
        UUID transactionId,
        long executions,
        String payloadDigest,
        String durableOwner) {
    public BatchOwnershipProof {
        Objects.requireNonNull(transactionId, "transactionId");
        if (executions <= 0L) {
            throw new IllegalArgumentException("ownership proof executions must be positive");
        }
        payloadDigest = requireText(payloadDigest, "payloadDigest", 128);
        durableOwner = requireText(durableOwner, "durableOwner", 256);
    }

    public boolean matches(PreparedPatternBatch prepared) {
        return transactionId.equals(prepared.transactionId())
                && executions == prepared.offeredExecutions()
                && payloadDigest.equals(BatchPayloadFingerprint.of(prepared));
    }

    private static String requireText(String value, String name, int maximumLength) {
        String checked = Objects.requireNonNull(value, name).trim();
        if (checked.isEmpty() || checked.length() > maximumLength) {
            throw new IllegalArgumentException(name + " length is invalid");
        }
        return checked;
    }
}
