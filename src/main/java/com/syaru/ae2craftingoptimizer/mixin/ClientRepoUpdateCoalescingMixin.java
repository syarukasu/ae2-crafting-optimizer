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

    @Inject(method = "updateView", at = @At("HEAD"), cancellable = true)
    private void aco$coalesceTerminalViewUpdates(CallbackInfo ci) {
        aco$viewGeneration = aco$viewGeneration == Long.MAX_VALUE ? 1L : aco$viewGeneration + 1L;
        if (aco$asyncViewTask != null) {
            aco$asyncViewTask.cancel(true);
            aco$asyncViewTask = null;
        }
        if (ClientRepoUpdateScheduler.shouldDefer((Repo) (Object) this)) {
            ci.cancel();
            return;
        }
        if (aco$scheduleAsyncSearchAndSort()) {
            ci.cancel();
        }
    }

    @Unique
    private boolean aco$scheduleAsyncSearchAndSort() {
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
        for (GridInventoryEntry entry : entries.values()) {
            if (viewMode == ViewItems.CRAFTABLE && !entry.isCraftable()) {
                continue;
            }
            if (viewMode == ViewItems.STORED && entry.getStoredAmount() == 0) {
                continue;
            }
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
                .thenAccept(result -> Minecraft.getInstance().execute(() -> {
                    if (aco$viewGeneration != generation) {
                        return;
                    }
                    view.clear();
                    view.addAll(result);
                    pinnedRow.clear();
                    if (updateViewListener != null) {
                        updateViewListener.run();
                    }
                    aco$asyncViewTask = null;
                }));
        return true;
    }

}
