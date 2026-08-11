package com.syaru.ae2craftingoptimizer.api.big;

import java.util.UUID;

/** 一つのCPU所有者とBigCraftingHostRuntimeを結ぶ世代付き登録ハンドル。 */
public interface BigCraftingHostRegistration extends AutoCloseable {
    Object ownerIdentity();

    UUID runtimeIdentity();

    long generation();

    boolean isClosed();

    BigCraftingHostSnapshot snapshot(long revision, BigCraftingHostBackendState backendState);

    /** その世代の終了時に一度だけ実行する後始末を登録する。 */
    void onClose(Runnable cleanup);

    /** 古い世代から新しい世代を閉じない、冪等な終了操作。 */
    @Override
    void close();
}
