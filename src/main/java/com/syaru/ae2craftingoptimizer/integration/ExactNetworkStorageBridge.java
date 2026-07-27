package com.syaru.ae2craftingoptimizer.integration;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.me.storage.NetworkStorage;
import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import com.syaru.ae2craftingoptimizer.api.vector.ExactStorageMutationResult;
import com.syaru.ae2craftingoptimizer.api.vector.ExactVectorStoragePolicy;
import com.syaru.ae2craftingoptimizer.mixin.DelegatingMEInventoryAccessor;
import com.syaru.ae2craftingoptimizer.mixin.ExtendedAePlusBigIntegerCellInventoryAccessor;
import com.syaru.ae2craftingoptimizer.mixin.NetworkStorageMountsAccessor;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Set;

/**
 * AE2 NetworkStorageのmount優先順を保ったまま、監査済みBigIntegerセルだけを正確に操作する。
 *
 * <p>通常MEStorageのlong APIを数量Windowとして繰り返さない。全量を正確に扱えるmountが
 * 揃わない場合は、入力所有権を移す前にfalseを返す。</p>
 */
public final class ExactNetworkStorageBridge {
    private static final String BASE_BIG_INTEGER_CELL =
            "com.extendedae_plus.api.storage.InfinityBigIntegerCellInventory";
    /** 委譲循環や異常に深いアドオンwrapperでmain threadを止めない固定上限。 */
    private static final int MAXIMUM_DELEGATE_DEPTH = 16;
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);
    /** 複数セルへまたがる直接変更を、同一JVM内では一つずつ確定する。 */
    private static final Object MUTATION_LOCK = new Object();

    private ExactNetworkStorageBridge() {
    }

    public static boolean canExtract(
            IGrid grid,
            AEKey key,
            BigInteger amount,
            IActionSource source) {
        return prepare(grid, key, amount, source, Direction.EXTRACT) != null;
    }

    public static boolean canInsert(
            IGrid grid,
            AEKey key,
            BigInteger amount,
            IActionSource source) {
        return prepare(grid, key, amount, source, Direction.INSERT) != null;
    }

    public static ExactStorageMutationResult extract(
            IGrid grid,
            AEKey key,
            BigInteger amount,
            IActionSource source) {
        return mutate(grid, key, amount, source, Direction.EXTRACT);
    }

    public static ExactStorageMutationResult insert(
            IGrid grid,
            AEKey key,
            BigInteger amount,
            IActionSource source) {
        return mutate(grid, key, amount, source, Direction.INSERT);
    }

    private static ExactStorageMutationResult mutate(
            IGrid grid,
            AEKey key,
            BigInteger amount,
            IActionSource source,
            Direction direction) {
        synchronized (MUTATION_LOCK) {
            PreparedMutation prepared =
                    prepare(grid, key, amount, source, direction);
            // simulate時点で全量を扱えない場合は、セルへ一切触れず拒否する。
            if (prepared == null) {
                return ExactStorageMutationResult.rejected(
                        "no exact BigInteger storage route can accept the full amount");
            }

            List<AppliedStep> applied = new ArrayList<>(prepared.steps().size());
            try {
                // 分割単位はmount数だけであり、要求数量によるloopやlong Windowを作らない。
                for (MutationStep step : prepared.steps()) {
                    applied.add(apply(step, direction));
                }
                grid.getStorageService().invalidateCache();
                return ExactStorageMutationResult.success(amount);
            } catch (RuntimeException | LinkageError mutationFailure) {
                boolean rollbackComplete = rollback(applied);
                grid.getStorageService().invalidateCache();
                if (!rollbackComplete) {
                    AE2CraftingOptimizer.LOGGER.error(
                            "ACO exact storage mutation became uncertain during {}",
                            direction,
                            mutationFailure);
                    return ExactStorageMutationResult.uncertain(
                            "exact storage callback failed and rollback could not be proven");
                }
                AE2CraftingOptimizer.LOGGER.warn(
                        "ACO exact storage mutation was rolled back during {}: {}",
                        direction,
                        mutationFailure.toString());
                return ExactStorageMutationResult.rejected(
                        "exact storage changed between simulation and commit");
            }
        }
    }

    private static PreparedMutation prepare(
            IGrid grid,
            AEKey key,
            BigInteger amount,
            IActionSource source,
            Direction direction) {
        Objects.requireNonNull(grid, "grid");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(direction, "direction");
        if (Objects.requireNonNull(amount, "amount").signum() <= 0) {
            throw new IllegalArgumentException("exact storage amount must be positive");
        }

        MEStorage networkInventory = grid.getStorageService().getInventory();
        if (!(networkInventory instanceof NetworkStorage)
                || !(networkInventory instanceof NetworkStorageMountsAccessor accessor)) {
            return null;
        }
        NavigableMap<Integer, List<MEStorage>> mounts =
                accessor.aco$getPriorityInventory();
        List<MEStorage> ordered = direction == Direction.EXTRACT
                ? extractionOrder(mounts)
                : insertionOrder(mounts, key, source);

        BigInteger remaining = amount;
        List<MutationStep> steps = new ArrayList<>();
        Set<Object> visitedExactCells =
                Collections.newSetFromMap(new IdentityHashMap<>());
        // 同じunderlying cellが複数wrapperから見えても、一度だけ在庫へ数える。
        for (MEStorage mount : ordered) {
            ResolvedExactStorage resolved = resolveExactStorage(mount);
            if (resolved == null || !visitedExactCells.add(resolved.accessor())) {
                continue;
            }
            BigInteger available = capacity(
                    resolved, mount, key, remaining, source, direction);
            if (available.signum() <= 0) {
                continue;
            }
            BigInteger selected = available.min(remaining);
            steps.add(new MutationStep(
                    resolved.accessor(),
                    key,
                    resolved.currentAmount(key),
                    resolved.accessor().aco$getExactStoredTotal(),
                    resolved.accessor().aco$getExactStoredTypeCount(),
                    selected));
            remaining = remaining.subtract(selected);
            // 全量を確保できた時点で残りmountを走査しない。
            if (remaining.signum() == 0) {
                return new PreparedMutation(List.copyOf(steps));
            }
        }
        return null;
    }

    private static List<MEStorage> extractionOrder(
            NavigableMap<Integer, List<MEStorage>> mounts) {
        List<MEStorage> result = new ArrayList<>();
        // AE2 NetworkStorage.extractと同じdescendingMap順をそのまま複製する。
        for (List<MEStorage> priority : mounts.descendingMap().values()) {
            result.addAll(priority);
        }
        return result;
    }

    private static List<MEStorage> insertionOrder(
            NavigableMap<Integer, List<MEStorage>> mounts,
            AEKey key,
            IActionSource source) {
        List<MEStorage> result = new ArrayList<>();
        // 各priorityでpreferred storageを先にし、その後に同priorityの通常mountを並べる。
        for (List<MEStorage> priority : mounts.values()) {
            for (MEStorage mount : priority) {
                if (mount.isPreferredStorageFor(key, source)) {
                    result.add(mount);
                }
            }
            for (MEStorage mount : priority) {
                if (!mount.isPreferredStorageFor(key, source)) {
                    result.add(mount);
                }
            }
        }
        return result;
    }

    private static BigInteger capacity(
            ResolvedExactStorage resolved,
            MEStorage mount,
            AEKey key,
            BigInteger requested,
            IActionSource source,
            Direction direction) {
        BigInteger current = resolved.currentAmount(key);
        BigInteger candidate;
        if (direction == Direction.EXTRACT) {
            candidate = current.min(requested);
        } else {
            candidate = insertionCapacity(resolved, key, current, requested);
        }
        if (candidate.signum() <= 0) {
            return BigInteger.ZERO;
        }

        long probe = candidate.min(LONG_MAX).longValueExact();
        long accepted = direction == Direction.EXTRACT
                ? mount.extract(key, probe, Actionable.SIMULATE, source)
                : mount.insert(key, probe, Actionable.SIMULATE, source);
        // wrapperのfilterやextract/insert禁止を、直接Map変更より前に必ず尊重する。
        return accepted == probe ? candidate : BigInteger.ZERO;
    }

    private static BigInteger insertionCapacity(
            ResolvedExactStorage resolved,
            AEKey key,
            BigInteger current,
            BigInteger requested) {
        Object exactCell = resolved.accessor();
        if (exactCell.getClass().getName().equals(BASE_BIG_INTEGER_CELL)) {
            return requested;
        }
        // 派生セルは独自容量やfilterを持ち得るため、明示Policyがない限り直接挿入しない。
        if (exactCell instanceof ExactVectorStoragePolicy policy) {
            BigInteger permitted = Objects.requireNonNull(
                    policy.acoMaximumExactInsert(key, current),
                    "exact storage policy result");
            if (permitted.signum() < 0) {
                throw new IllegalStateException(
                        "exact storage policy returned a negative capacity");
            }
            return permitted.min(requested);
        }
        return BigInteger.ZERO;
    }

    private static ResolvedExactStorage resolveExactStorage(MEStorage mount) {
        MEStorage current = mount;
        // AE2標準DelegatingMEInventoryの内側だけを辿り、任意Reflectionは行わない。
        for (int depth = 0; depth < MAXIMUM_DELEGATE_DEPTH; depth++) {
            if (current
                    instanceof ExtendedAePlusBigIntegerCellInventoryAccessor accessor) {
                return new ResolvedExactStorage(accessor);
            }
            if (!(current instanceof DelegatingMEInventoryAccessor delegating)) {
                return null;
            }
            MEStorage next = delegating.aco$getDelegateStorage();
            // null、自己参照、循環は未対応routeとして拒否する。
            if (next == null || next == current) {
                return null;
            }
            current = next;
        }
        return null;
    }

    private static AppliedStep apply(
            MutationStep step,
            Direction direction) {
        ExtendedAePlusBigIntegerCellInventoryAccessor accessor = step.accessor();
        /*
         * 空のInfinity Cellへ初めて直接挿入する場合だけ、ExtendedAE Plus自身の
         * UUID/SavedData生成処理を一度使う。数量Windowは作らない。
         */
        if (direction == Direction.INSERT
                && !accessor.aco$hasExactStorageUuid()) {
            accessor.aco$assignExactStorageUuid();
        }
        Object2ObjectMap<AEKey, BigInteger> amounts =
                accessor.aco$getExactStoredAmounts();
        BigInteger current = amounts.getOrDefault(step.key(), BigInteger.ZERO);
        BigInteger total = accessor.aco$getExactStoredTotal();
        // simulate後に対象キーまたはセル総量が変わった場合は、直接変更を始めない。
        if (!current.equals(step.beforeAmount())
                || !total.equals(step.beforeTotal())
                || accessor.aco$getExactStoredTypeCount() != step.beforeTypes()) {
            throw new IllegalStateException(
                    "exact cell changed between simulation and commit");
        }

        BigInteger replacement = direction == Direction.EXTRACT
                ? current.subtract(step.amount())
                : current.add(step.amount());
        BigInteger replacementTotal = direction == Direction.EXTRACT
                ? total.subtract(step.amount())
                : total.add(step.amount());
        if (replacement.signum() < 0 || replacementTotal.signum() < 0) {
            throw new IllegalStateException("exact cell amount would become negative");
        }

        // 0量キーをMapへ残さず、ExtendedAE Plus本来の型数会計と一致させる。
        if (replacement.signum() == 0) {
            amounts.remove(step.key());
        } else {
            amounts.put(step.key(), replacement);
        }
        accessor.aco$setExactStoredTypeCount(amounts.size());
        accessor.aco$setExactStoredTotal(replacementTotal);
        ExactBigIntegerCellConsistency.record(
                amounts, replacementTotal);
        accessor.aco$saveExactChanges();
        return new AppliedStep(step, replacement, replacementTotal);
    }

    private static boolean rollback(List<AppliedStep> applied) {
        boolean complete = true;
        // 後から適用したセルから逆順で、記録済み実値だけを戻す。
        for (int index = applied.size() - 1; index >= 0; index--) {
            AppliedStep change = applied.get(index);
            MutationStep step = change.step();
            try {
                Object2ObjectMap<AEKey, BigInteger> amounts =
                        step.accessor().aco$getExactStoredAmounts();
                // 他のcallbackが変更した場合は上書きせず、不確定として隔離する。
                if (!amounts.getOrDefault(step.key(), BigInteger.ZERO)
                                .equals(change.afterAmount())
                        || !step.accessor().aco$getExactStoredTotal()
                                .equals(change.afterTotal())) {
                    complete = false;
                    continue;
                }
                if (step.beforeAmount().signum() == 0) {
                    amounts.remove(step.key());
                } else {
                    amounts.put(step.key(), step.beforeAmount());
                }
                step.accessor().aco$setExactStoredTypeCount(step.beforeTypes());
                step.accessor().aco$setExactStoredTotal(step.beforeTotal());
                ExactBigIntegerCellConsistency.record(
                        amounts, step.beforeTotal());
                step.accessor().aco$saveExactChanges();
            } catch (RuntimeException | LinkageError rollbackFailure) {
                complete = false;
            }
        }
        return complete;
    }

    private enum Direction {
        INSERT,
        EXTRACT
    }

    private record PreparedMutation(List<MutationStep> steps) {
        private PreparedMutation {
            steps = List.copyOf(steps);
            if (steps.isEmpty()) {
                throw new IllegalArgumentException(
                        "exact mutation must contain at least one cell step");
            }
        }
    }

    private record MutationStep(
            ExtendedAePlusBigIntegerCellInventoryAccessor accessor,
            AEKey key,
            BigInteger beforeAmount,
            BigInteger beforeTotal,
            int beforeTypes,
            BigInteger amount) {
    }

    private record AppliedStep(
            MutationStep step,
            BigInteger afterAmount,
            BigInteger afterTotal) {
    }

    private record ResolvedExactStorage(
            ExtendedAePlusBigIntegerCellInventoryAccessor accessor) {
        private BigInteger currentAmount(AEKey key) {
            return accessor.aco$getExactStoredAmounts()
                    .getOrDefault(key, BigInteger.ZERO);
        }
    }
}
