package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.config.Actionable;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.crafting.inv.ListCraftingInventory;
import com.syaru.ae2craftingoptimizer.access.ExactCraftingInventoryAccess;
import com.syaru.ae2craftingoptimizer.engine.ExactCraftingJobLedger;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * AE2の既存waitingForカウンタを、Exact Jobの時だけBigInteger対応へ拡張する。
 */
@Mixin(value = ListCraftingInventory.class, remap = false)
public abstract class ListCraftingInventoryExactCountsMixin
        implements ExactCraftingInventoryAccess {
    @Shadow
    @Final
    public KeyCounter list;

    @Shadow
    @Final
    private ListCraftingInventory.ChangeListener listener;

    /** nullは通常のAE2 long在庫、非nullは同じ在庫オブジェクトの正確な数量。 */
    @Unique
    private Map<AEKey, BigInteger> aco$exactCounts;

    @Override
    public boolean aco$hasExactCounts() {
        return aco$exactCounts != null;
    }

    @Override
    public void aco$replaceExactCounts(
            Map<AEKey, BigInteger> counts) {
        Map<AEKey, BigInteger> checked =
                aco$checkedCounts(counts);
        // 同じ絶対Snapshotの再照合ではKeyCounter再構築も差分通知も行わない。
        if (aco$exactCounts != null
                && aco$exactCounts.equals(checked)) {
            return;
        }
        Set<AEKey> changed = new LinkedHashSet<>();
        // 初回有効化では既存long投影とExact正本の全キーを再通知対象にする。
        if (aco$exactCounts == null) {
            changed.addAll(list.keySet());
        } else {
            // 前後で正確な値が変わったキーだけを既存差分同期へ流す。
            for (var entry : aco$exactCounts.entrySet()) {
                if (!entry.getValue().equals(
                        checked.get(entry.getKey()))) {
                    changed.add(entry.getKey());
                }
            }
        }
        for (var entry : checked.entrySet()) {
            if (aco$exactCounts == null
                    || !entry.getValue().equals(
                            aco$exactCounts.get(entry.getKey()))) {
                changed.add(entry.getKey());
            }
        }
        aco$exactCounts = new LinkedHashMap<>(checked);
        aco$rebuildLongProjection();
        // waitingForの変更通知を既存Advanced AEの差分同期経路へ渡す。
        for (AEKey key : changed) {
            listener.onChange(key);
        }
    }

    @Override
    public Map<AEKey, BigInteger> aco$getExactCounts() {
        if (aco$exactCounts == null) {
            throw new IllegalStateException(
                    "exact crafting inventory is not enabled");
        }
        return Map.copyOf(aco$exactCounts);
    }

    @Override
    public BigInteger aco$getExactCount(AEKey key) {
        Objects.requireNonNull(key, "key");
        if (aco$exactCounts == null) {
            return BigInteger.valueOf(list.get(key));
        }
        return aco$exactCounts.getOrDefault(
                key,
                BigInteger.ZERO);
    }

    @Inject(
            method = "insert",
            at = @At("HEAD"),
            cancellable = true,
            require = 1)
    private void aco$insertExact(
            AEKey key,
            long amount,
            Actionable mode,
            CallbackInfo ci) {
        // 通常在庫はAE2本来のlong処理へそのまま渡す。
        if (aco$exactCounts == null) {
            return;
        }
        Objects.requireNonNull(key, "key");
        if (amount < 0L) {
            throw new IllegalArgumentException(
                    "exact crafting insertion must not be negative");
        }
        // SIMULATEは在庫を変更せず、ListCraftingInventoryのvoid契約だけを満たす。
        if (mode == Actionable.MODULATE
                && amount > 0L) {
            aco$exactCounts.merge(
                    key,
                    BigInteger.valueOf(amount),
                    BigInteger::add);
            aco$projectKey(key);
            listener.onChange(key);
        }
        ci.cancel();
    }

    @Inject(
            method = "extract",
            at = @At("HEAD"),
            cancellable = true,
            require = 1)
    private void aco$extractExact(
            AEKey key,
            long amount,
            Actionable mode,
            CallbackInfoReturnable<Long> cir) {
        // 通常在庫はAE2本来のlong処理へそのまま渡す。
        if (aco$exactCounts == null) {
            return;
        }
        Objects.requireNonNull(key, "key");
        if (amount < 0L) {
            throw new IllegalArgumentException(
                    "exact crafting extraction must not be negative");
        }
        BigInteger stored = aco$exactCounts.getOrDefault(
                key,
                BigInteger.ZERO);
        BigInteger requested = BigInteger.valueOf(amount);
        BigInteger extracted = stored.min(requested);
        // MODULATEだけが正確な待機量と互換long投影を同時に減らす。
        if (mode == Actionable.MODULATE
                && extracted.signum() > 0) {
            BigInteger remaining =
                    stored.subtract(extracted);
            if (remaining.signum() == 0) {
                aco$exactCounts.remove(key);
            } else {
                aco$exactCounts.put(
                        key,
                        remaining);
            }
            aco$projectKey(key);
            listener.onChange(key);
        }
        cir.setReturnValue(extracted.longValueExact());
    }

    @Inject(
            method = "clear",
            at = @At("HEAD"),
            cancellable = true,
            require = 1)
    private void aco$clearExact(CallbackInfo ci) {
        // 通常在庫はAE2本来のclearへそのまま渡す。
        if (aco$exactCounts == null) {
            return;
        }
        Set<AEKey> changed =
                Set.copyOf(aco$exactCounts.keySet());
        aco$exactCounts.clear();
        list.clear();
        // clear前に存在したキーだけを既存差分Listenerへ通知する。
        for (AEKey key : changed) {
            listener.onChange(key);
        }
        ci.cancel();
    }

    @Unique
    private void aco$rebuildLongProjection() {
        list.clear();
        // AE2互換欄には各Exact数量の飽和longだけを一キー一件で置く。
        for (var entry : aco$exactCounts.entrySet()) {
            list.set(
                    entry.getKey(),
                    ExactCraftingJobLedger.saturatedLong(
                            entry.getValue()));
        }
    }

    @Unique
    private void aco$projectKey(AEKey key) {
        BigInteger exact = aco$exactCounts.getOrDefault(
                key,
                BigInteger.ZERO);
        list.set(
                key,
                ExactCraftingJobLedger.saturatedLong(exact));
        list.removeZeros();
    }

    @Unique
    private static Map<AEKey, BigInteger> aco$checkedCounts(
            Map<AEKey, BigInteger> source) {
        Objects.requireNonNull(source, "counts");
        Map<AEKey, BigInteger> checked = new LinkedHashMap<>();
        // Exact waitingForは負数と0要素を持たず、一キー一数量へ正規化する。
        for (var entry : source.entrySet()) {
            AEKey key = Objects.requireNonNull(
                    entry.getKey(),
                    "count key");
            BigInteger amount = Objects.requireNonNull(
                    entry.getValue(),
                    "count amount");
            if (amount.signum() < 0) {
                throw new IllegalArgumentException(
                        "exact crafting count must not be negative");
            }
            if (amount.signum() > 0) {
                checked.put(
                        key,
                        amount);
            }
        }
        return Map.copyOf(checked);
    }
}
