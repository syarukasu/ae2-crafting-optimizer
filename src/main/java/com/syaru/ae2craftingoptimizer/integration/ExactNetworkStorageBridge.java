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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

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

    public static boolean canExtractAll(
            IGrid grid,
            Map<AEKey, BigInteger> amounts,
            IActionSource source) {
        return canMutateAll(
                grid,
                amounts,
                source,
                Direction.EXTRACT);
    }

    public static boolean canInsertAll(
            IGrid grid,
            Map<AEKey, BigInteger> amounts,
            IActionSource source) {
        return canMutateAll(
                grid,
                amounts,
                source,
                Direction.INSERT);
    }

    public static ExactStorageMutationResult extractAll(
            IGrid grid,
            Map<AEKey, BigInteger> amounts,
            IActionSource source) {
        return mutateAll(
                grid,
                amounts,
                source,
                Direction.EXTRACT);
    }

    public static ExactStorageMutationResult insertAll(
            IGrid grid,
            Map<AEKey, BigInteger> amounts,
            IActionSource source) {
        return mutateAll(
                grid,
                amounts,
                source,
                Direction.INSERT);
    }

    /**
     * 監査済みBigIntegerセル群が現在保持する、指定キーの正確な合計量を返す。
     *
     * <p>クラフトTransactionはこの値を事前保存し、停止後に境界操作が適用済みかを
     * before/afterで再照合する。通常セルのlong値を混ぜて推測しない。</p>
     */
    public static Optional<BigInteger> exactStoredAmount(
            IGrid grid,
            AEKey key) {
        Objects.requireNonNull(
                grid,
                "grid");
        Objects.requireNonNull(
                key,
                "key");
        MEStorage networkInventory =
                grid.getStorageService()
                        .getInventory();
        // NetworkStorageのmount一覧を取得できない実装では、正確な再照合を行わない。
        if (!(networkInventory
                        instanceof NetworkStorage)
                || !(networkInventory
                        instanceof NetworkStorageMountsAccessor accessor)) {
            return Optional.empty();
        }

        BigInteger total =
                BigInteger.ZERO;
        boolean found =
                false;
        Set<ExactStorageIdentity> visitedExactCells =
                new LinkedHashSet<>();
        /*
         * 優先度は合計値へ影響しないが、AE2のmount順を維持して決定的に走査する。
         * 同じunderlying cellを複数wrapperが公開しても一度だけ数える。
         */
        for (List<MEStorage> priority :
                accessor.aco$getPriorityInventory()
                        .values()) {
            // 同一priority内の各mountを一度だけ正確なセルへ解決する。
            for (MEStorage mount :
                    priority) {
                ResolvedExactStorage resolved =
                        resolveExactStorage(
                                mount);
                // 非対応mountまたは既に数えた同一セルは合計へ加えない。
                if (resolved == null
                        || !visitedExactCells.add(
                                resolved.storageIdentity())) {
                    continue;
                }
                found =
                        true;
                total =
                        total.add(
                                resolved.currentAmount(
                                        key));
            }
        }
        return found
                ? Optional.of(
                        total)
                : Optional.empty();
    }

    /**
     * 一つのmount走査で、指定された全AEKeyの正確な在庫量を返す。
     */
    public static Optional<Map<AEKey, BigInteger>> exactStoredAmounts(
            IGrid grid,
            Set<AEKey> keys) {
        Objects.requireNonNull(
                grid,
                "grid");
        Set<AEKey> checkedKeys =
                Collections.unmodifiableSet(
                        new LinkedHashSet<>(
                                Objects.requireNonNull(
                                        keys,
                                        "keys")));
        // 空の境界操作は呼出側の状態不整合なので受理しない。
        if (checkedKeys.isEmpty()) {
            throw new IllegalArgumentException(
                    "exact storage key set must not be empty");
        }
        MEStorage networkInventory =
                grid.getStorageService()
                        .getInventory();
        // NetworkStorageのmount一覧を取得できない実装では、正確な再照合を行わない。
        if (!(networkInventory
                        instanceof NetworkStorage)
                || !(networkInventory
                        instanceof NetworkStorageMountsAccessor accessor)) {
            return Optional.empty();
        }
        Map<AEKey, BigInteger> totals =
                new java.util.LinkedHashMap<>();
        // 要求キーの順序を維持し、未保有キーも0として結果へ残す。
        for (AEKey key :
                checkedKeys) {
            totals.put(
                    Objects.requireNonNull(
                            key,
                            "exact storage key"),
                    BigInteger.ZERO);
        }
        boolean found =
                false;
        Set<ExactStorageIdentity> visitedExactCells =
                new LinkedHashSet<>();
        // 同じ正確セルを一度だけ解決し、その中の要求キーだけを合算する。
        for (List<MEStorage> priority :
                accessor.aco$getPriorityInventory()
                        .values()) {
            // 同一priority内の各mountを一度だけ正確なセルへ解決する。
            for (MEStorage mount :
                    priority) {
                ResolvedExactStorage resolved =
                        resolveExactStorage(
                                mount);
                // 非対応mountまたは既に数えた同一セルは合計へ加えない。
                if (resolved == null
                        || !visitedExactCells.add(
                                resolved.storageIdentity())) {
                    continue;
                }
                found =
                        true;
                // 要求キー数だけを走査し、セル内の全登録キーは列挙しない。
                for (AEKey key :
                        checkedKeys) {
                    totals.merge(
                            key,
                            resolved.currentAmount(
                                    key),
                            BigInteger::add);
                }
            }
        }
        return found
                ? Optional.of(
                        Collections.unmodifiableMap(
                                new LinkedHashMap<>(
                                        totals)))
                : Optional.empty();
    }

    private static boolean canMutateAll(
            IGrid grid,
            Map<AEKey, BigInteger> amounts,
            IActionSource source,
            Direction direction) {
        Map<AEKey, BigInteger> checked =
                checkedBatchAmounts(
                        amounts);
        synchronized (MUTATION_LOCK) {
            // 全キーが現在のfilter・優先度・容量で処理できる場合だけtrueを返す。
            for (Map.Entry<AEKey, BigInteger> entry :
                    checked.entrySet()) {
                // 一キーでも全量routeを作れない場合は、境界Batch全体を拒否する。
                if (prepare(
                                grid,
                                entry.getKey(),
                                entry.getValue(),
                                source,
                                direction)
                        == null) {
                    return false;
                }
            }
            return true;
        }
    }

    private static ExactStorageMutationResult mutateAll(
            IGrid grid,
            Map<AEKey, BigInteger> amounts,
            IActionSource source,
            Direction direction) {
        Map<AEKey, BigInteger> checked =
                checkedBatchAmounts(
                        amounts);
        synchronized (MUTATION_LOCK) {
            /*
             * 一キーも変更する前に全routeを検証する。
             * 注文数量ではなく境界AEKey数だけを一巡する。
             */
            for (Map.Entry<AEKey, BigInteger> entry :
                    checked.entrySet()) {
                // 一キーでも扱えなければ、セルを一切変更せずBatchを拒否する。
                if (prepare(
                                grid,
                                entry.getKey(),
                                entry.getValue(),
                                source,
                                direction)
                        == null) {
                    return ExactStorageMutationResult.rejected(
                            "no exact BigInteger storage route can accept the complete key batch");
                }
            }

            List<Map.Entry<AEKey, BigInteger>> applied =
                    new ArrayList<>();
            // 同じ排他区間内で各キーを確定し、途中失敗時は逆順に全量を戻す。
            for (Map.Entry<AEKey, BigInteger> entry :
                    checked.entrySet()) {
                ExactStorageMutationResult result =
                        mutate(
                                grid,
                                entry.getKey(),
                                entry.getValue(),
                                source,
                                direction);
                // 成功したキーだけをRollback台帳へ積む。
                if (result.successful()) {
                    applied.add(
                            entry);
                    continue;
                }
                boolean rolledBack =
                        rollbackBatch(
                                grid,
                                source,
                                direction,
                                applied);
                // 元の失敗またはRollbackのどちらかが不確定なら、親Jobへ隔離を要求する。
                if (result.stateUncertain()
                        || !rolledBack) {
                    return ExactStorageMutationResult.uncertain(
                            "exact storage key batch failed and rollback could not be proven");
                }
                return ExactStorageMutationResult.rejected(
                        result.detail());
            }
            return ExactStorageMutationResult.success(
                    sumAmounts(
                            checked));
        }
    }

    private static boolean rollbackBatch(
            IGrid grid,
            IActionSource source,
            Direction appliedDirection,
            List<Map.Entry<AEKey, BigInteger>> applied) {
        Direction rollbackDirection =
                appliedDirection == Direction.EXTRACT
                        ? Direction.INSERT
                        : Direction.EXTRACT;
        // 最後に適用したキーから逆順で、正確な同量を戻す。
        for (int index = applied.size() - 1;
                index >= 0;
                index--) {
            Map.Entry<AEKey, BigInteger> entry =
                    applied.get(
                            index);
            ExactStorageMutationResult rollback =
                    mutate(
                            grid,
                            entry.getKey(),
                            entry.getValue(),
                            source,
                            rollbackDirection);
            // 一件でも戻せなければ、それ以前を推測で成功扱いしない。
            if (!rollback.successful()) {
                return false;
            }
        }
        return true;
    }

    private static Map<AEKey, BigInteger> checkedBatchAmounts(
            Map<AEKey, BigInteger> amounts) {
        Map<AEKey, BigInteger> checked =
                new LinkedHashMap<>();
        Objects.requireNonNull(
                        amounts,
                        "amounts")
                .forEach(
                        (key, amount) -> {
                            Objects.requireNonNull(
                                    key,
                                    "exact storage batch key");
                            // 一キー一正数だけを受理し、0量や重複を境界操作へ流さない。
                            if (amount == null
                                    || amount.signum()
                                            <= 0
                                    || checked.putIfAbsent(
                                                    key,
                                                    amount)
                                            != null) {
                                throw new IllegalArgumentException(
                                        "invalid exact storage batch amount");
                            }
                        });
        // 空Batchは成功量0になり、Receipt状態を曖昧にするため拒否する。
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(
                    "exact storage batch must not be empty");
        }
        return Collections.unmodifiableMap(
                new LinkedHashMap<>(
                        checked));
    }

    private static BigInteger sumAmounts(
            Map<AEKey, BigInteger> amounts) {
        BigInteger total =
                BigInteger.ZERO;
        // 結果用合計もBigIntegerで計算し、複数キー合計のlong overflowを起こさない。
        for (BigInteger amount :
                amounts.values()) {
            total =
                    total.add(
                            amount);
        }
        return total;
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
        Set<ExactStorageIdentity> visitedExactCells =
                new LinkedHashSet<>();
        // 同じunderlying cellが複数wrapperから見えても、一度だけ在庫へ数える。
        for (MEStorage mount : ordered) {
            ResolvedExactStorage resolved = resolveExactStorage(mount);
            if (resolved == null
                    || !visitedExactCells.add(
                            resolved.storageIdentity())) {
                continue;
            }
            BigInteger available = capacity(
                    resolved, mount, key, remaining, source, direction);
            if (available.signum() <= 0) {
                continue;
            }
            BigInteger selected = available.min(remaining);
            Object2ObjectMap<AEKey, BigInteger> amounts =
                    resolved.currentMap();
            steps.add(new MutationStep(
                    resolved.accessor(),
                    amounts,
                    key,
                    amounts.getOrDefault(
                            key,
                            BigInteger.ZERO),
                    authoritativeTotal(
                            resolved.accessor(),
                            amounts),
                    amounts.size(),
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
        // simulation後に保存Map自体が交換された場合は、別セルへ変更を適用しない。
        if (amounts
                != step.amounts()) {
            throw new IllegalStateException(
                    "exact cell storage map changed between simulation and commit");
        }
        BigInteger current = amounts.getOrDefault(step.key(), BigInteger.ZERO);
        BigInteger total =
                authoritativeTotal(
                        accessor,
                        amounts);
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
                // 保存Mapが交換済みなら、別セルへ古い値を上書きしない。
                if (amounts
                        != step.amounts()) {
                    complete = false;
                    continue;
                }
                BigInteger currentTotal =
                        authoritativeTotal(
                                step.accessor(),
                                amounts);
                // 他のcallbackが変更した場合は上書きせず、不確定として隔離する。
                if (!amounts.getOrDefault(step.key(), BigInteger.ZERO)
                                .equals(change.afterAmount())
                        || !currentTotal.equals(
                                change.afterTotal())) {
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

    private static BigInteger authoritativeTotal(
            ExtendedAePlusBigIntegerCellInventoryAccessor accessor,
            Object2ObjectMap<AEKey, BigInteger> amounts) {
        BigInteger total =
                ExactBigIntegerCellConsistency.authoritativeTotal(
                        amounts);
        int typeCount =
                amounts.size();
        /*
         * Inventory wrapper固有cacheだけが古い場合は、共有Mapの正本へ同期する。
         * この修復は在庫Mapを変更しないため、simulate中にも安全に行える。
         */
        if (!total.equals(
                        accessor.aco$getExactStoredTotal())
                || typeCount
                        != accessor.aco$getExactStoredTypeCount()) {
            accessor.aco$setExactStoredTotal(
                    total);
            accessor.aco$setExactStoredTypeCount(
                    typeCount);
        }
        return total;
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
            Object2ObjectMap<AEKey, BigInteger> amounts,
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
        private Object2ObjectMap<AEKey, BigInteger> currentMap() {
            return accessor.aco$getExactStoredAmounts();
        }

        private ExactStorageIdentity storageIdentity() {
            UUID storageUuid =
                    accessor.aco$getExactStorageUuid();
            // 保存UUIDがあれば、別wrapperや空Mapでも同じunderlying cellとして扱う。
            if (storageUuid != null) {
                return ExactStorageIdentity.saved(
                        storageUuid);
            }
            Object2ObjectMap<AEKey, BigInteger> amounts =
                    currentMap();
            /*
             * UUID未割当セルは共有Mapのidentityで重複排除する。
             * 空セルは共通empty mapを返し得るためwrapper自身を一時identityに使う。
             */
            return ExactStorageIdentity.unsaved(
                    amounts.isEmpty()
                            ? accessor
                            : amounts);
        }

        private BigInteger currentAmount(AEKey key) {
            return currentMap()
                    .getOrDefault(key, BigInteger.ZERO);
        }
    }

    /**
     * 保存済みセルはUUIDの値、未保存セルはMapまたはwrapperのidentityで比較する。
     */
    private static final class ExactStorageIdentity {
        private final UUID storageUuid;
        private final Object transientIdentity;

        private ExactStorageIdentity(
                UUID storageUuid,
                Object transientIdentity) {
            this.storageUuid =
                    storageUuid;
            this.transientIdentity =
                    transientIdentity;
        }

        private static ExactStorageIdentity saved(
                UUID storageUuid) {
            return new ExactStorageIdentity(
                    Objects.requireNonNull(
                            storageUuid,
                            "storageUuid"),
                    null);
        }

        private static ExactStorageIdentity unsaved(
                Object transientIdentity) {
            return new ExactStorageIdentity(
                    null,
                    Objects.requireNonNull(
                            transientIdentity,
                            "transientIdentity"));
        }

        @Override
        public int hashCode() {
            return storageUuid != null
                    ? storageUuid.hashCode()
                    : System.identityHashCode(
                            transientIdentity);
        }

        @Override
        public boolean equals(Object other) {
            // 同じ識別子インスタンスは追加比較なしで一致する。
            if (this == other) {
                return true;
            }
            // 保存/未保存セル以外の任意オブジェクトを同一セルとして扱わない。
            if (!(other
                    instanceof ExactStorageIdentity identity)) {
                return false;
            }
            // 片方でも保存済みなら、両方のUUID値が一致する場合だけ同じセルとする。
            if (storageUuid != null
                    || identity.storageUuid != null) {
                return storageUuid != null
                        && storageUuid.equals(
                                identity.storageUuid);
            }
            return transientIdentity
                    == identity.transientIdentity;
        }
    }
}
