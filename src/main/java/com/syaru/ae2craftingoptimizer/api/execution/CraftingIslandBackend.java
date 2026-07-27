package com.syaru.ae2craftingoptimizer.api.execution;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.me.service.CraftingService;
import java.util.List;
import java.util.Optional;

/**
 * ACOのCompiled Crafting Islandへ、外部MODが原子的な実行設備を提供するための入口。
 *
 * <p>実装はPatternを実際に一件ずつ投入せず、島全体の入力・出力を一つの取引として
 * 所有できる設備だけを公開する。単なる高速Providerを登録してはいけない。</p>
 */
public interface CraftingIslandBackend {
    /** ログと重複登録判定に使う、名前空間付きの安定IDを返す。 */
    String acoBackendId();

    /**
     * 指定GridとPattern集合を同時に所有できる設備セッションを開く。
     *
     * <p>構造未形成、Pattern非対応、設定OFFの場合は空を返し、ACOは元のCPU経路へ戻る。</p>
     */
    Optional<CraftingIslandBackendSession> acoOpenSession(
            IGrid grid,
            CraftingService craftingService,
            List<IPatternDetails> patterns);
}
