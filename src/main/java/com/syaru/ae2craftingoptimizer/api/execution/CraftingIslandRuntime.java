package com.syaru.ae2craftingoptimizer.api.execution;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import appeng.crafting.inv.ICraftingInventory;
import java.util.List;
import java.util.Map;

/**
 * ACOの島トランザクションと、CPU実装固有のJob/在庫/出力会計をつなぐ最小Port。
 */
public interface CraftingIslandRuntime {
    /** 現在Jobの同一性を比較するための参照を返す。 */
    Object acoIslandJobIdentity();

    /** 現在JobのPattern Task Mapを返す。 */
    Map<IPatternDetails, Object> acoIslandTasks();

    /** Jobが所有する抽出元Crafting Inventoryを返す。 */
    ICraftingInventory acoIslandInventory();

    /** prepare時と同じJobがまだCPUへ属しているかを返す。 */
    boolean acoIslandJobStillActive(Object expectedJob);

    /**
     * 現在の島を原子的に所有する外部設備へ、この一回の実行を束縛する。
     *
     * <p>CPU実装自身が設備を所有する既存Backendは既定値を使える。外部設備を動的選択する
     * Runtimeは全Patternを同時所有できない場合にfalseを返す。</p>
     */
    default boolean acoIslandBindBackend(List<IPatternDetails> patterns) {
        return true;
    }

    /** 現在形成済みの設備が一Waveで扱える外部sink Pattern回数。 */
    long acoIslandRootExecutionCapacity();

    /** 指定Patternを、この設備が現在実行できるProviderとして公開しているかを返す。 */
    boolean acoIslandSupportsPattern(IPatternDetails pattern);

    /** 一つの内部Pattern実行に設備が消費するAE電力。 */
    double acoIslandEnergyPerLogicalExecution();

    /** 構造、設定、接続がcommit直前にも有効かを再検証する。 */
    boolean acoIslandBackendStillAvailable();

    /** 出力を待機一覧へ加える前に、最終Requesterが受理可能かを検証する。 */
    boolean acoIslandCanAcceptOutput(AEKey key, long amount);

    /** 境界出力を通常Pattern完了と同じwaiting/in-flight会計へ登録する。 */
    void acoIslandStageOutput(AEKey key, long amount);

    /** 出力配送開始前の失敗時に、登録済みwaiting/in-flight会計を元へ戻す。 */
    void acoIslandUnstageOutput(AEKey key, long amount);

    /** 登録済み境界出力をCPU本来のinsert経路へ渡す。 */
    long acoIslandInsertOutput(AEKey key, long amount);

    /** 実体化しない内部出力ぶんだけJobの時間進捗を減らす。 */
    void acoIslandDecrementInternalOutput(AEKey key, long amount);

    /** Task Mapの一括変更をCPU監視画面へ一回だけ通知する。 */
    void acoIslandNotifyTaskChanges();

    /** CPUと所有Block Entityを次の保存対象へする。 */
    void acoIslandMarkDirty();

    /** commit後の不確定失敗時にJobを停止し、通常配送による二重実行を防ぐ。 */
    void acoIslandSuspend(String reason, Throwable failure);

    /** 最終Requesterへ直接送るキーかを返し、出力commit順を安定させる。 */
    boolean acoIslandIsFinalOutput(AEKey key);

    /** 診断ログへ出す短いバックエンド名。 */
    String acoIslandBackendName();
}
