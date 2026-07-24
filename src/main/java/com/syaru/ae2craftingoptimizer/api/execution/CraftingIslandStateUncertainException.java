package com.syaru.ae2craftingoptimizer.api.execution;

/**
 * 可逆区間の復旧後も、外部listenerを含む会計状態を完全には証明できないことを示す。
 *
 * <p>この例外を受けたACOは元のPattern配送へ戻さずJobを停止し、同じ入力の二重実行を防ぐ。</p>
 */
public final class CraftingIslandStateUncertainException
        extends RuntimeException {
    public CraftingIslandStateUncertainException(
            String message,
            Throwable cause) {
        super(message, cause);
    }
}
