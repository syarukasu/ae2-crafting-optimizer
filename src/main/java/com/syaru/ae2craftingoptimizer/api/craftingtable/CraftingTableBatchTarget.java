package com.syaru.ae2craftingoptimizer.api.craftingtable;

import java.util.Optional;
import java.util.UUID;

/**
 * Pattern Provider配下の物理作業台設備が実装する最小契約。
 *
 * <p>ACOはレシピ計画と数量会計だけを行い、電力・物理進捗・Worker選択はTargetへ委譲する。</p>
 */
public interface CraftingTableBatchTarget {
    /** Requestを永続Receipt付きの物理仕事として受理する。 */
    boolean aco$acceptCraftingTableBatch(
            CraftingTableBatchRequest request);

    /** 停止直後の再照合で、同一Payloadの所有権がTargetにあるかを返す。 */
    boolean aco$ownsCraftingTableBatch(
            UUID transactionId,
            String payloadDigest);

    /** BigInteger親Jobが物理進捗と完成出力を読む。 */
    Optional<CraftingTableBatchSnapshot>
            aco$craftingTableBatchSnapshot(
                    UUID transactionId,
                    String payloadDigest);

    /**
     * 親Jobが出力Receiptを永続化した後、物理Threadを解放する。
     *
     * <p>標準AE2仕事はNeoECOの通常搬出が解放するため、この操作を呼ばない。</p>
     */
    boolean aco$acknowledgeCraftingTableBatch(
            UUID transactionId,
            String payloadDigest);

    /**
     * 親JobがOUTPUT_CREDITED状態を永続化した後、Target側の終端Receiptを破棄する。
     *
     * <p>Receiptが既に破棄済みでもtrueを返せる冪等操作とする。</p>
     */
    boolean aco$forgetCraftingTableBatch(
            UUID transactionId,
            String payloadDigest);

    /** 未完了BigInteger仕事を代表スタックの返却なしで取り消す。 */
    boolean aco$cancelCraftingTableBatch(
            UUID transactionId,
            String payloadDigest);
}
