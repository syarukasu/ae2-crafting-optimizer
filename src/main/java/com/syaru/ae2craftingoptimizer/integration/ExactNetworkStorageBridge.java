package com.syaru.ae2craftingoptimizer.integration;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.me.storage.NetworkStorage;
import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import com.syaru.ae2craftingoptimizer.access.DelegatingMEInventoryAccess;
import com.syaru.ae2craftingoptimizer.access.ExtendedAePlusBigIntegerCellInventoryAccess;
import com.syaru.ae2craftingoptimizer.access.NetworkStorageMountsAccess;
import com.syaru.ae2craftingoptimizer.api.vector.ExactStorageMutationResult;
import com.syaru.ae2craftingoptimizer.api.vector.ExactVectorStoragePolicy;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.lifecycle.ACORegistryAccess;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

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
    /** Network単位だけを排他し、別gridの正確な在庫操作は並行して進める。 */
    private static final Map<IGrid, Object> GRID_LOCKS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private ExactNetworkStorageBridge() {
    }

    public static boolean canExtract(
            IGrid grid,
            AEKey key,
            BigInteger amount,
            IActionSource source) {
        return canMutate(grid, key, amount, source, Direction.EXTRACT);
    }

    public static boolean canInsert(
            IGrid grid,
            AEKey key,
            BigInteger amount,
            IActionSource source) {
        return canMutate(grid, key, amount, source, Direction.INSERT);
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
                        instanceof NetworkStorageMountsAccess accessor)) {
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
                        instanceof NetworkStorageMountsAccess accessor)) {
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

    private static boolean canMutate(
            IGrid grid,
            AEKey key,
            BigInteger amount,
            IActionSource source,
            Direction direction) {
        Objects.requireNonNull(grid, "grid");
        synchronized (lockFor(grid)) {
            return prepareBatch(
                    grid,
                    Map.of(
                            Objects.requireNonNull(key, "key"),
                            Objects.requireNonNull(amount, "amount")),
                    source,
                    direction)
                    != null;
        }
    }

    private static boolean canMutateAll(
            IGrid grid,
            Map<AEKey, BigInteger> amounts,
            IActionSource source,
            Direction direction) {
        Map<AEKey, BigInteger> checked = checkedBatchAmounts(amounts);
        synchronized (lockFor(grid)) {
            // バッチ境界で一度だけrouteを準備し、同じ準備結果を確定側へ渡す。
            return prepareBatch(grid, checked, source, direction) != null;
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
        synchronized (lockFor(grid)) {
            ExactStorageMutationResult recovery = recoverPending(grid);
            if (!recovery.successful()) {
                return recovery;
            }
            PreparedMutation prepared =
                    prepareBatch(grid, checked, source, direction);
            if (prepared == null) {
                return ExactStorageMutationResult.rejected(
                        "no exact BigInteger storage route can accept the complete key batch");
            }
            return executePrepared(grid, prepared, direction);
        }
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
        synchronized (lockFor(grid)) {
            ExactStorageMutationResult recovery = recoverPending(grid);
            if (!recovery.successful()) {
                return recovery;
            }
            PreparedMutation prepared =
                    prepareBatch(
                            grid,
                            Map.of(
                                    Objects.requireNonNull(key, "key"),
                                    Objects.requireNonNull(amount, "amount")),
                            source,
                            direction);
            // simulate時点で全量を扱えない場合は、セルへ一切触れず拒否する。
            if (prepared == null) {
                return ExactStorageMutationResult.rejected(
                        "no exact BigInteger storage route can accept the full amount");
            }
            return executePrepared(grid, prepared, direction);
        }
    }

    /**
     * Builds one immutable route for the complete boundary batch.
     *
     * <p>Mounts are resolved once and a per-cell shadow is advanced while each key is
     * planned. This preserves exact before/after state even when multiple keys share
     * one cell, so commit never has to prepare a route again.</p>
     */
    private static PreparedMutation prepareBatch(
            IGrid grid,
            Map<AEKey, BigInteger> requested,
            IActionSource source,
            Direction direction) {
        Objects.requireNonNull(grid, "grid");
        Map<AEKey, BigInteger> checked = checkedBatchAmounts(requested);
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(direction, "direction");

        MEStorage networkInventory = grid.getStorageService().getInventory();
        if (!(networkInventory instanceof NetworkStorage)
                || !(networkInventory instanceof NetworkStorageMountsAccess accessor)) {
            return null;
        }
        NavigableMap<Integer, List<MEStorage>> mounts =
                accessor.aco$getPriorityInventory();
        List<ResolvedMount> resolvedMounts = resolveMounts(mounts);
        if (resolvedMounts.isEmpty()) {
            return null;
        }

        Map<ExactStorageIdentity, CellShadow> shadows = new LinkedHashMap<>();
        for (ResolvedMount resolvedMount : resolvedMounts) {
            ResolvedExactStorage resolved = resolvedMount.resolved();
            Object2ObjectMap<AEKey, BigInteger> amounts = resolved.currentMap();
            shadows.putIfAbsent(
                    resolved.storageIdentity(),
                    new CellShadow(
                            resolved.accessor(),
                            amounts,
                            new LinkedHashMap<>(amounts),
                            authoritativeTotal(resolved.accessor(), amounts)));
        }

        List<MutationStep> steps = new ArrayList<>();
        for (Map.Entry<AEKey, BigInteger> entry : checked.entrySet()) {
            AEKey key = entry.getKey();
            BigInteger remaining = entry.getValue();
            for (ResolvedMount resolvedMount :
                    orderedResolvedMounts(
                            mounts,
                            resolvedMounts,
                            key,
                            source,
                            direction)) {
                ResolvedExactStorage resolved = resolvedMount.resolved();
                CellShadow shadow = shadows.get(resolved.storageIdentity());
                BigInteger current = shadow.amounts().getOrDefault(key, BigInteger.ZERO);
                BigInteger available = direction == Direction.EXTRACT
                        ? current.min(remaining)
                        : insertionCapacity(
                                resolved,
                                key,
                                current,
                                remaining);
                if (available.signum() <= 0) {
                    continue;
                }
                long probe = available.min(LONG_MAX).longValueExact();
                long accepted = direction == Direction.EXTRACT
                        ? resolvedMount.mount().extract(
                                key,
                                probe,
                                Actionable.SIMULATE,
                                source)
                        : resolvedMount.mount().insert(
                                key,
                                probe,
                                Actionable.SIMULATE,
                                source);
                if (accepted != probe) {
                    continue;
                }
                BigInteger selected = available.min(remaining);
                BigInteger replacement = direction == Direction.EXTRACT
                        ? current.subtract(selected)
                        : current.add(selected);
                BigInteger replacementTotal = direction == Direction.EXTRACT
                        ? shadow.total().subtract(selected)
                        : shadow.total().add(selected);
                int replacementTypes = replacement.signum() == 0
                        ? shadow.amounts().size() - 1
                        : shadow.amounts().containsKey(key)
                                ? shadow.amounts().size()
                                : shadow.amounts().size() + 1;
                steps.add(new MutationStep(
                        shadow.accessor(),
                        shadow.actualAmounts(),
                        key,
                        current,
                        shadow.total(),
                        shadow.amounts().size(),
                        selected,
                        replacement,
                        replacementTotal,
                        replacementTypes));
                if (replacement.signum() == 0) {
                    shadow.amounts().remove(key);
                } else {
                    shadow.amounts().put(key, replacement);
                }
                shadow.total = replacementTotal;
                remaining = remaining.subtract(selected);
                if (remaining.signum() == 0) {
                    break;
                }
            }
            if (remaining.signum() != 0) {
                return null;
            }
        }
        return new PreparedMutation(
                UUID.randomUUID(),
                checked,
                List.copyOf(steps));
    }

    private static ExactStorageMutationResult executePrepared(
            IGrid grid,
            PreparedMutation prepared,
            Direction direction) {
        ExactStorageMutationJournal journal =
                ExactStorageMutationJournal.forGrid(grid);
        if (journal == null || !journal.isHealthy()) {
            return ExactStorageMutationResult.rejected(
                    "exact storage journal is unavailable or malformed");
        }
        try {
            // UUIDを付けるのはjournal作成直前だけ。can*のsimulationでは保存状態を触らない。
            for (MutationStep step : prepared.steps()) {
                if (!step.accessor().aco$hasExactStorageUuid()) {
                    step.accessor().aco$assignExactStorageUuid();
                }
            }
            List<ExactStorageMutationJournal.Step> journalSteps = new ArrayList<>();
            for (MutationStep step : prepared.steps()) {
                UUID storageId = step.accessor().aco$getExactStorageUuid();
                if (storageId == null) {
                    throw new IllegalStateException("exact cell has no persistent storage UUID");
                }
                journalSteps.add(new ExactStorageMutationJournal.Step(
                        storageId,
                        step.key().toTagGeneric(
                                grid.getPivot().getLevel().registryAccess()),
                        step.beforeAmount(),
                        step.afterAmount(),
                        step.beforeTotal(),
                        step.afterTotal(),
                        step.beforeTypes(),
                        step.afterTypes(),
                        step.amount()));
            }
            if (!journal.begin(
                    prepared.operationId(),
                    ExactNetworkStorageSnapshotCache.currentGeneration(),
                    direction.name(),
                    journalSteps,
                    ACOConfig.getBatchTransactionJournalMaximumEntries())) {
                return ExactStorageMutationResult.rejected(
                        "exact storage journal cannot accept the prepared operation");
            }

            List<AppliedStep> applied = new ArrayList<>(prepared.steps().size());
            try {
                // このリストだけがcommitのroute。再prepareやquantity windowは行わない。
                for (int index = 0; index < prepared.steps().size(); index++) {
                    applied.add(apply(prepared.steps().get(index), direction));
                    if (!journal.markApplied(prepared.operationId(), index)) {
                        throw new IllegalStateException(
                                "exact storage journal could not acknowledge an applied step");
                    }
                }
                journal.acknowledge(prepared.operationId());
                grid.getStorageService().invalidateCache();
                return ExactStorageMutationResult.success(
                        sumAmounts(prepared.requested()));
            } catch (RuntimeException | LinkageError mutationFailure) {
                boolean rollbackComplete = rollback(applied);
                grid.getStorageService().invalidateCache();
                if (rollbackComplete) {
                    journal.acknowledge(prepared.operationId());
                    AE2CraftingOptimizer.LOGGER.warn(
                            "ACO exact storage mutation was rolled back during {}: {}",
                            direction,
                            mutationFailure.toString());
                    return ExactStorageMutationResult.rejected(
                            "exact storage changed between simulation and commit");
                }
                journal.quarantine(
                        prepared.operationId(),
                        "callback failure or rollback mismatch: "
                                + mutationFailure);
                AE2CraftingOptimizer.LOGGER.error(
                        "ACO exact storage mutation became uncertain during {}",
                        direction,
                        mutationFailure);
                return ExactStorageMutationResult.uncertain(
                        "exact storage callback failed and rollback could not be proven");
            }
        } catch (RuntimeException | LinkageError preparationFailure) {
            return ExactStorageMutationResult.rejected(
                    "exact storage journal preparation failed: "
                            + preparationFailure.getMessage());
        }
    }

    /**
     * Replays only journal steps whose exact before-state is still present. A step
     * matching neither before nor after is quarantined; unrelated network totals are
     * never used as proof.
     */
    private static ExactStorageMutationResult recoverPending(IGrid grid) {
        ExactStorageMutationJournal journal =
                ExactStorageMutationJournal.forGrid(grid);
        if (journal == null || !journal.isHealthy()) {
            return ExactStorageMutationResult.rejected(
                    "exact storage journal is unavailable or malformed");
        }
        MEStorage networkInventory = grid.getStorageService().getInventory();
        if (!(networkInventory instanceof NetworkStorageMountsAccess mountsAccess)) {
            return ExactStorageMutationResult.rejected(
                    "exact storage mounts are unavailable during recovery");
        }
        List<ResolvedMount> mounts = resolveMounts(
                mountsAccess.aco$getPriorityInventory());
        Map<UUID, ExtendedAePlusBigIntegerCellInventoryAccess> cells =
                new LinkedHashMap<>();
        for (ResolvedMount mount : mounts) {
            UUID storageId = mount.resolved().accessor().aco$getExactStorageUuid();
            if (storageId != null) {
                cells.putIfAbsent(storageId, mount.resolved().accessor());
            }
        }
        for (ExactStorageMutationJournal.Entry entry : journal.pending()) {
            boolean belongsToThisGrid = false;
            for (ExactStorageMutationJournal.Step step : entry.steps()) {
                if (cells.containsKey(step.storageId())) {
                    belongsToThisGrid = true;
                    break;
                }
            }
            // Journalはworld単位なので、別gridの未完了操作を他のgridから隔離しない。
            if (!belongsToThisGrid) {
                continue;
            }
            try {
                List<RecoveryStep> recoverySteps = new ArrayList<>();
                for (ExactStorageMutationJournal.Step step : entry.steps()) {
                    ExtendedAePlusBigIntegerCellInventoryAccess accessor =
                            cells.get(step.storageId());
                    if (accessor == null) {
                        throw new IllegalStateException(
                                "journal cell is no longer mounted: " + step.storageId());
                    }
                    AEKey key = AEKey.fromTagGeneric(
                            grid.getPivot().getLevel().registryAccess(),
                            step.key());
                    if (key == null) {
                        throw new IllegalStateException("journal key could not be decoded");
                    }
                    Object2ObjectMap<AEKey, BigInteger> amounts =
                            accessor.aco$getExactStoredAmounts();
                    BigInteger currentAmount = amounts.getOrDefault(key, BigInteger.ZERO);
                    BigInteger currentTotal = authoritativeTotal(accessor, amounts);
                    boolean before = currentAmount.equals(step.beforeAmount())
                            && currentTotal.equals(step.beforeTotal())
                            && accessor.aco$getExactStoredTypeCount() == step.beforeTypes();
                    boolean after = currentAmount.equals(step.afterAmount())
                            && currentTotal.equals(step.afterTotal())
                            && accessor.aco$getExactStoredTypeCount() == step.afterTypes();
                    if (!before && !after) {
                        throw new IllegalStateException(
                                "journal cell state matches neither before nor after");
                    }
                    recoverySteps.add(new RecoveryStep(
                            step,
                            accessor,
                            amounts,
                            key,
                            before));
                }
                for (int index = 0; index < recoverySteps.size(); index++) {
                    RecoveryStep recovery = recoverySteps.get(index);
                    if (recovery.before()) {
                        apply(
                                new MutationStep(
                                        recovery.accessor(),
                                        recovery.amounts(),
                                        recovery.key(),
                                        recovery.step().beforeAmount(),
                                        recovery.step().beforeTotal(),
                                        recovery.step().beforeTypes(),
                                        recovery.step().amount(),
                                        recovery.step().afterAmount(),
                                        recovery.step().afterTotal(),
                                        recovery.step().afterTypes()),
                                Direction.valueOf(entry.direction()));
                    }
                    journal.markApplied(entry.operationId(), index);
                }
                journal.acknowledge(entry.operationId());
                grid.getStorageService().invalidateCache();
            } catch (RuntimeException | LinkageError failure) {
                journal.quarantine(
                        entry.operationId(),
                        "recovery proof failed: " + failure);
                AE2CraftingOptimizer.LOGGER.error(
                        "ACO quarantined exact storage operation {} during recovery",
                        entry.operationId(),
                        failure);
                return ExactStorageMutationResult.uncertain(
                        "exact storage recovery could not be proven");
            }
        }
        return ExactStorageMutationResult.success(BigInteger.ZERO);
    }

    private static Object lockFor(IGrid grid) {
        Objects.requireNonNull(grid, "grid");
        synchronized (GRID_LOCKS) {
            return GRID_LOCKS.computeIfAbsent(grid, ignored -> new Object());
        }
    }

    private record RecoveryStep(
            ExactStorageMutationJournal.Step step,
            ExtendedAePlusBigIntegerCellInventoryAccess accessor,
            Object2ObjectMap<AEKey, BigInteger> amounts,
            AEKey key,
            boolean before) {
    }

    private static List<ResolvedMount> resolveMounts(
            NavigableMap<Integer, List<MEStorage>> mounts) {
        List<ResolvedMount> result = new ArrayList<>();
        for (List<MEStorage> priority : mounts.values()) {
            for (MEStorage mount : priority) {
                ResolvedExactStorage resolved = resolveExactStorage(mount);
                if (resolved != null) {
                    result.add(new ResolvedMount(mount, resolved));
                }
            }
        }
        return result;
    }

    private static List<ResolvedMount> orderedResolvedMounts(
            NavigableMap<Integer, List<MEStorage>> mounts,
            List<ResolvedMount> resolvedMounts,
            AEKey key,
            IActionSource source,
            Direction direction) {
        IdentityHashMap<MEStorage, ResolvedMount> byMount = new IdentityHashMap<>();
        for (ResolvedMount resolvedMount : resolvedMounts) {
            byMount.put(resolvedMount.mount(), resolvedMount);
        }
        List<ResolvedMount> result = new ArrayList<>();
        Set<ExactStorageIdentity> visited = new LinkedHashSet<>();
        if (direction == Direction.EXTRACT) {
            for (List<MEStorage> priority : mounts.descendingMap().values()) {
                for (MEStorage mount : priority) {
                    ResolvedMount resolved = byMount.get(mount);
                    if (resolved != null
                            && visited.add(resolved.resolved().storageIdentity())) {
                        result.add(resolved);
                    }
                }
            }
            return result;
        }
        for (List<MEStorage> priority : mounts.values()) {
            for (MEStorage mount : priority) {
                ResolvedMount resolved = byMount.get(mount);
                if (resolved != null
                        && mount.isPreferredStorageFor(key, source)
                        && visited.add(resolved.resolved().storageIdentity())) {
                    result.add(resolved);
                }
            }
            for (MEStorage mount : priority) {
                ResolvedMount resolved = byMount.get(mount);
                if (resolved != null
                        && !mount.isPreferredStorageFor(key, source)
                        && visited.add(resolved.resolved().storageIdentity())) {
                    result.add(resolved);
                }
            }
        }
        return result;
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
                    instanceof ExtendedAePlusBigIntegerCellInventoryAccess accessor) {
                return new ResolvedExactStorage(accessor);
            }
            if (!(current instanceof DelegatingMEInventoryAccess delegating)) {
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
        ExtendedAePlusBigIntegerCellInventoryAccess accessor = step.accessor();
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
        int replacementTypes = replacement.signum() == 0
                ? amounts.size() - 1
                : amounts.containsKey(step.key())
                        ? amounts.size()
                        : amounts.size() + 1;
        if (!replacement.equals(step.afterAmount())
                || !replacementTotal.equals(step.afterTotal())
                || replacementTypes != step.afterTypes()) {
            throw new IllegalStateException(
                    "prepared exact cell transition does not match the live state");
        }

        // 0量キーをMapへ残さず、ExtendedAE Plus本来の型数会計と一致させる。
        if (replacement.signum() == 0) {
            amounts.remove(step.key());
        } else {
            amounts.put(step.key(), replacement);
        }
        accessor.aco$setExactStoredTypeCount(replacementTypes);
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
                                change.afterTotal())
                        || step.accessor().aco$getExactStoredTypeCount()
                                != step.afterTypes()) {
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
            ExtendedAePlusBigIntegerCellInventoryAccess accessor,
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

    private record PreparedMutation(
            UUID operationId,
            Map<AEKey, BigInteger> requested,
            List<MutationStep> steps) {
        private PreparedMutation {
            Objects.requireNonNull(operationId, "operationId");
            requested = Collections.unmodifiableMap(
                    new LinkedHashMap<>(requested));
            steps = List.copyOf(steps);
            if (steps.isEmpty()) {
                throw new IllegalArgumentException(
                        "exact mutation must contain at least one cell step");
            }
        }
    }

    private record MutationStep(
            ExtendedAePlusBigIntegerCellInventoryAccess accessor,
            Object2ObjectMap<AEKey, BigInteger> amounts,
            AEKey key,
            BigInteger beforeAmount,
            BigInteger beforeTotal,
            int beforeTypes,
            BigInteger amount,
            BigInteger afterAmount,
            BigInteger afterTotal,
            int afterTypes) {
    }

    private static final class CellShadow {
        private final ExtendedAePlusBigIntegerCellInventoryAccess accessor;
        private final Object2ObjectMap<AEKey, BigInteger> actualAmounts;
        private final Map<AEKey, BigInteger> amounts;
        private BigInteger total;

        private CellShadow(
                ExtendedAePlusBigIntegerCellInventoryAccess accessor,
                Object2ObjectMap<AEKey, BigInteger> actualAmounts,
                Map<AEKey, BigInteger> amounts,
                BigInteger total) {
            this.accessor = accessor;
            this.actualAmounts = actualAmounts;
            this.amounts = amounts;
            this.total = total;
        }

        private ExtendedAePlusBigIntegerCellInventoryAccess accessor() {
            return accessor;
        }

        private Object2ObjectMap<AEKey, BigInteger> actualAmounts() {
            return actualAmounts;
        }

        private Map<AEKey, BigInteger> amounts() {
            return amounts;
        }

        private BigInteger total() {
            return total;
        }
    }

    private record ResolvedMount(
            MEStorage mount,
            ResolvedExactStorage resolved) {
    }

    private record AppliedStep(
            MutationStep step,
            BigInteger afterAmount,
            BigInteger afterTotal) {
    }

    private record ResolvedExactStorage(
            ExtendedAePlusBigIntegerCellInventoryAccess accessor) {
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
