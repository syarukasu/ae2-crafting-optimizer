package com.syaru.ae2craftingoptimizer.mixin;

import appeng.client.gui.me.common.Repo;
import appeng.api.config.SortOrder;
import appeng.client.gui.widgets.ISortSource;
import appeng.menu.me.common.GridInventoryEntry;
import com.syaru.ae2craftingoptimizer.client.ClientRepoUpdateScheduler;
import com.syaru.ae2craftingoptimizer.client.AsyncTerminalView;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import appeng.api.config.ViewItems;
import appeng.client.gui.me.common.PinnedKeys;
import appeng.client.gui.me.search.RepoSearch;
import appeng.util.prioritylist.IPartitionList;
import com.google.common.collect.BiMap;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Repo.class, remap = false)
public abstract class ClientRepoUpdateCoalescingMixin {
    @Shadow
    @Final
    private ArrayList<GridInventoryEntry> view;

    @Shadow
    @Final
    private ISortSource sortSrc;

    @Shadow
    private Runnable updateViewListener;

    @Shadow
    @Final
    private BiMap<Long, GridInventoryEntry> entries;

    @Shadow
    @Final
    private ArrayList<GridInventoryEntry> pinnedRow;

    @Shadow
    @Final
    private RepoSearch search;

    @Shadow
    private IPartitionList partitionList;

    @Shadow
    private boolean paused;

    @Unique
    private volatile long aco$viewGeneration;

    @Unique
    private CompletableFuture<?> aco$asyncViewTask;

    @Unique
    private boolean aco$forceSynchronousUpdate;

    @Inject(method = "updateView", at = @At("HEAD"), cancellable = true)
    private void aco$coalesceTerminalViewUpdates(CallbackInfo ci) {
        // 非同期処理が失敗した直後の一回だけは、AE2本来の同期更新へ戻す。
        if (aco$forceSynchronousUpdate) {
            aco$forceSynchronousUpdate = false;
            return;
        }

        aco$viewGeneration = aco$viewGeneration == Long.MAX_VALUE ? 1L : aco$viewGeneration + 1L;
        // 古い世代の結果が新しい検索条件を上書きしないように破棄する。
        if (aco$asyncViewTask != null) {
            aco$asyncViewTask.cancel(true);
            aco$asyncViewTask = null;
        }
        // 短時間の連続更新は既存Schedulerへ委譲する。
        if (ClientRepoUpdateScheduler.shouldDefer((Repo) (Object) this)) {
            ci.cancel();
            return;
        }
        // 安全条件を満たした検索だけを非同期経路へ送る。
        if (aco$scheduleAsyncSearchAndSort()) {
            ci.cancel();
        }
    }

    @Unique
    private boolean aco$scheduleAsyncSearchAndSort() {
        // AE2固有状態をworkerへ持ち出す条件では同期経路へ戻す。
        if (!ACOConfig.asyncTerminalSearchSort()
                || paused
                || partitionList != null
                || !PinnedKeys.isEmpty()
                || entries.size() < ACOConfig.getAsyncTerminalMinimumEntries()) {
            return false;
        }

        var viewMode = sortSrc.getSortDisplay();
        var visibleKeyTypes = sortSrc.getSortKeyTypes();
        List<GridInventoryEntry> candidates = new ArrayList<>(entries.size());
        // AE2が画面へ出せる候補だけをクライアントスレッド上で固定する。
        for (GridInventoryEntry entry : entries.values()) {
            // クラフト可能項目だけを表示する設定を維持する。
            if (viewMode == ViewItems.CRAFTABLE && !entry.isCraftable()) {
                continue;
            }
            // 在庫項目だけを表示する設定を維持する。
            if (viewMode == ViewItems.STORED && entry.getStoredAmount() == 0) {
                continue;
            }
            // AE2のキー種別フィルターに一致しない候補は除外する。
            if (!visibleKeyTypes.contains(entry.getWhat().getType())) {
                continue;
            }
            candidates.add(entry);
        }

        String query = search.getSearchString();
        SortOrder order = sortSrc.getSortBy();
        var direction = sortSrc.getSortDir();
        var projections = AsyncTerminalView.project(candidates, query);
        long generation = aco$viewGeneration;
        aco$asyncViewTask = CompletableFuture
                .supplyAsync(() -> AsyncTerminalView.filterAndSort(projections, query, order, direction))
                .whenComplete((result, failure) -> Minecraft.getInstance().execute(() -> {
                    // 完了前に検索条件が変わった結果は、成功・失敗とも採用しない。
                    if (aco$viewGeneration != generation) {
                        return;
                    }

                    aco$asyncViewTask = null;
                    // 独自処理の失敗時は表示を止めず、AE2本来の更新を一度だけ実行する。
                    if (failure != null) {
                        aco$forceSynchronousUpdate = true;
                        ((Repo) (Object) this).updateView();
                        return;
                    }

                    view.clear();
                    view.addAll(result);
                    pinnedRow.clear();
                    // AE2がlistenerを登録している場合だけ画面更新を通知する。
                    if (updateViewListener != null) {
                        updateViewListener.run();
                    }
                }));
        return true;
    }

}
