package com.syaru.ae2craftingoptimizer.api.vector;

import java.util.Objects;

/** 保存済みReceiptと再接続するための、設備種類と実体を表す安定ID。 */
public record VectorExecutorIdentity(String id, String displayName) {
    public VectorExecutorIdentity {
        id = Objects.requireNonNull(id, "id").trim();
        displayName = Objects.requireNonNull(displayName, "displayName").trim();
        // 空IDや表示名は隔離ログから設備を特定できないため受け付けない。
        if (id.isEmpty() || displayName.isEmpty()) {
            throw new IllegalArgumentException(
                    "vector executor identity must not be blank");
        }
    }
}
