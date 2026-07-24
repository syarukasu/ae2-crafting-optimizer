package com.syaru.ae2craftingoptimizer.api.big;

import com.syaru.ae2craftingoptimizer.engine.BigCountMath;
import com.syaru.ae2craftingoptimizer.engine.BigCraftingJob;
import com.syaru.ae2craftingoptimizer.engine.BigCraftingKeyCodec;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * アドオンCPU一台の物理容量を一元管理する。
 *
 * <p>通常AE2 Jobは{@link #reserveExternal}からlong範囲の容量を予約し、BigInteger Jobは
 * 内部の{@link BigCraftingRuntime}を使用する。両者を同じ台帳から差し引くことで、
 * 同じ容量を二重に予約することを防ぐ。</p>
 */
public final class BigCraftingHostRuntime<K> {
    public static final int SCHEMA_VERSION = 2;
    public static final int MAX_EXTERNAL_RESERVATIONS = 65_536;
    public static final int MAX_EXTERNAL_EXECUTIONS = 16_384;
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    private final UUID hostId;
    private final BigCraftingKeyCodec<K> keyCodec;
    private final int maximumBits;
    private final int maximumExecutionsPerWindow;
    private final int maximumStatusPageEntries;
    private final long maximumRuntimeCountBytes;
    private final Map<UUID, BigInteger> externalReservations = new LinkedHashMap<>();
    /** 子AE2 CPUが所有している未確定Execution。Big予約と二重加算しないため同じHostで管理する。 */
    private final Map<UUID, ExternalExecutionBinding> externalExecutions = new LinkedHashMap<>();
    private BigInteger physicalCapacity;
    private BigInteger externalReserved = BigInteger.ZERO;
    private long externalCountBytes;
    private final BigCraftingRuntime<K> bigRuntime;

    BigCraftingHostRuntime(
            BigInteger physicalCapacity,
            BigCraftingKeyCodec<K> keyCodec,
            int maximumBits,
            int maximumExecutionsPerWindow,
            int maximumStatusPageEntries,
            long maximumRuntimeCountBytes) {
        this(
                UUID.randomUUID(),
                checkedCapacity(physicalCapacity, maximumBits),
                Map.of(),
                Map.of(),
                new BigCraftingRuntime<>(
                        physicalCapacity,
                        keyCodec,
                        maximumBits,
                        maximumExecutionsPerWindow,
                        maximumStatusPageEntries,
                        maximumRuntimeCountBytes),
                keyCodec,
                maximumBits,
                maximumExecutionsPerWindow,
                maximumStatusPageEntries,
                maximumRuntimeCountBytes);
    }

    private BigCraftingHostRuntime(
            UUID hostId,
            BigInteger physicalCapacity,
            Map<UUID, BigInteger> externalReservations,
            Map<UUID, ExternalExecutionBinding> externalExecutions,
            BigCraftingRuntime<K> bigRuntime,
            BigCraftingKeyCodec<K> keyCodec,
            int maximumBits,
            int maximumExecutionsPerWindow,
            int maximumStatusPageEntries,
            long maximumRuntimeCountBytes) {
        this.hostId = Objects.requireNonNull(hostId, "hostId");
        this.keyCodec = Objects.requireNonNull(keyCodec, "keyCodec");
        this.maximumBits = maximumBits;
        this.maximumExecutionsPerWindow = maximumExecutionsPerWindow;
        this.maximumStatusPageEntries = maximumStatusPageEntries;
        this.maximumRuntimeCountBytes = maximumRuntimeCountBytes;
        this.physicalCapacity = checkedCapacity(physicalCapacity, maximumBits);
        this.bigRuntime = Objects.requireNonNull(bigRuntime, "bigRuntime");
        replaceExternalExecutions(externalExecutions);
        replaceExternalReservations(externalReservations);
    }

    /** 通常AE2・Advanced AE Job用の容量を原子的に予約する。 */
    public synchronized boolean reserveExternal(UUID jobId, BigInteger amount) {
        Objects.requireNonNull(jobId, "jobId");
        BigInteger checked = checkedPositive(amount, "external reservation", maximumBits);
        if (externalReservations.containsKey(jobId)) {
            throw new IllegalStateException("external job already has a reservation: " + jobId);
        }
        long amountBytes = BigCountMath.encodedBytes(checked);
        if (externalReservations.size() >= MAX_EXTERNAL_RESERVATIONS
                || exceedsExternalCountBudget(amountBytes)
                || available().compareTo(checked) < 0) {
            return false;
        }
        externalReservations.put(jobId, checked);
        externalReserved = BigCountMath.add(
                externalReserved, checked, "host/externalReserved", maximumBits);
        externalCountBytes = Math.addExact(externalCountBytes, amountBytes);
        rebalanceBigRuntimeCapacity();
        return true;
    }

    public synchronized BigInteger releaseExternal(UUID jobId) {
        BigInteger released = externalReservations.remove(Objects.requireNonNull(jobId, "jobId"));
        if (released == null) {
            return BigInteger.ZERO;
        }
        externalReserved = externalReserved.subtract(released);
        externalCountBytes = Math.subtractExact(
                externalCountBytes, BigCountMath.encodedBytes(released));
        rebalanceBigRuntimeCapacity();
        return released;
    }

    /**
     * AE2がLong.MAX_VALUEとして登録した標準子Jobを、真のBigInteger容量へ原子的に昇格する。
     * Job本体の各カウントはlongのままで、容量予約だけをSidecarの正確な値へ置き換える。
     */
    public synchronized boolean promoteExternalReservation(
            UUID jobId,
            BigInteger exactAmount) {
        Objects.requireNonNull(jobId, "jobId");
        BigInteger checked = checkedPositive(exactAmount, "promoted external reservation", maximumBits);
        BigInteger previous = externalReservations.get(jobId);
        // AdvancedAEが先に作成した互換予約が存在しないJobは、別Jobとの取り違えを防ぐため拒否する。
        if (previous == null || externalExecutions.containsKey(jobId)) {
            return false;
        }
        // BigInteger昇格対象は、AE2へ見せたLong.MAX_VALUEより真の容量が大きいJobだけに限定する。
        if (!previous.equals(LONG_MAX) || checked.compareTo(LONG_MAX) <= 0) {
            return false;
        }

        BigInteger reservedWithoutPrevious = reserved().subtract(previous);
        BigInteger availableForReplacement = physicalCapacity.subtract(reservedWithoutPrevious);
        long previousBytes = BigCountMath.encodedBytes(previous);
        long replacementBytes = BigCountMath.encodedBytes(checked);
        long projectedCountBytes = Math.addExact(
                Math.subtractExact(externalCountBytes, previousBytes), replacementBytes);
        // 真の容量または保存用カウント予算を超える場合は、既存予約を一切変更せず失敗する。
        if (availableForReplacement.compareTo(checked) < 0
                || projectedCountBytes > maximumRuntimeCountBytes) {
            return false;
        }

        externalReservations.put(jobId, checked);
        externalReserved = BigCountMath.add(
                externalReserved.subtract(previous),
                checked,
                "host/externalReserved",
                maximumBits);
        externalCountBytes = projectedCountBytes;
        rebalanceBigRuntimeCapacity();
        return true;
    }

    /**
     * CPUが保持する正本Job一覧から、通常Jobの予約状態を丸ごと再構築する。
     *
     * <p>復元時だけは容量超過状態も受け入れる。既存Jobを消さず、空き容量を0として扱い、
     * 構造の容量が戻るかJobが完了するまで新規Jobを受理しない。</p>
     */
    public synchronized void replaceExternalReservations(Map<UUID, BigInteger> replacement) {
        Objects.requireNonNull(replacement, "external reservations");
        Map<UUID, BigInteger> unmanaged = new LinkedHashMap<>();
        replacement.forEach((jobId, amount) -> {
            BigInteger current = externalReservations.get(jobId);
            // CPU NBTは互換値Long.MAX_VALUEしか持たない。Sidecarに真値があれば再計算・再起動後も維持する。
            BigInteger authoritative = current != null
                            && current.compareTo(LONG_MAX) > 0
                            && LONG_MAX.equals(amount)
                    ? current
                    : amount;
            if (!externalExecutions.containsKey(jobId)) {
                unmanaged.put(jobId, authoritative);
            }
        });
        CheckedReservations checked = checkedReservations(
                unmanaged, maximumBits, maximumRuntimeCountBytes);
        BigInteger total = BigInteger.ZERO;
        for (BigInteger amount : checked.values().values()) {
            total = BigCountMath.add(total, amount, "host/externalReserved", maximumBits);
        }
        externalReservations.clear();
        externalReservations.putAll(checked.values());
        externalReserved = total;
        externalCountBytes = checked.encodedCountBytes();
        rebalanceBigRuntimeCapacity();
    }

    public synchronized void resizePhysicalCapacity(BigInteger replacement) {
        physicalCapacity = checkedCapacity(replacement, maximumBits);
        rebalanceBigRuntimeCapacity();
    }

    public synchronized boolean submit(BigCraftingJob<K> job) {
        Objects.requireNonNull(job, "job");
        if (available().compareTo(job.reservedCapacity()) < 0) {
            return false;
        }
        rebalanceBigRuntimeCapacity();
        return bigRuntime.submit(job);
    }

    public synchronized List<BigCraftingRuntime.ExecutionLease<K>> schedule(long operationBudget) {
        return bigRuntime.schedule(operationBudget);
    }

    public synchronized List<BigCraftingRuntime.ExecutionLease<K>> schedule(
            long operationBudget,
            int maximumWindows) {
        return bigRuntime.schedule(operationBudget, maximumWindows);
    }

    /**
     * 準備済みBigInteger窓を、同じQuantum Computer内の標準AE2子CPUへ紐付ける。
     * 以後は子CPUの成功/失敗通知が来るまでBig側の進捗を確定しない。
     */
    public synchronized void bindExternalExecution(
            BigCraftingRuntime.ExecutionLease<K> lease,
            UUID childCpuId,
            BigInteger childReservedCapacity) {
        Objects.requireNonNull(lease, "lease");
        Objects.requireNonNull(childCpuId, "childCpuId");
        BigInteger checkedCapacity = checkedPositive(
                childReservedCapacity, "child execution reservation", maximumBits);
        bigRuntime.validateLease(lease);
        if (externalExecutions.size() >= MAX_EXTERNAL_EXECUTIONS
                || externalExecutions.containsKey(childCpuId)
                || externalExecutions.values().stream().anyMatch(binding ->
                        binding.transactionId().equals(lease.prepared().transactionId()))) {
            throw new IllegalStateException("duplicate or excessive BigInteger child execution binding");
        }
        externalExecutions.put(childCpuId, new ExternalExecutionBinding(
                childCpuId,
                lease.jobId(),
                lease.prepared().transactionId(),
                lease.prepared().patternId(),
                lease.prepared().window().executions(),
                checkedCapacity));
        // 子CPUはBig Jobの予約容量から実行しているため、標準Job予約との二重計上を除く。
        BigInteger removed = externalReservations.remove(childCpuId);
        if (removed != null) {
            externalReserved = externalReserved.subtract(removed);
            externalCountBytes = Math.subtractExact(
                    externalCountBytes, BigCountMath.encodedBytes(removed));
        }
        rebalanceBigRuntimeCapacity();
    }

    /** 子AE2 Jobが完了した時だけ、対応するBigInteger窓を確定する。 */
    public synchronized boolean resolveExternalExecution(UUID childCpuId, boolean successful) {
        ExternalExecutionBinding binding = externalExecutions.get(
                Objects.requireNonNull(childCpuId, "childCpuId"));
        if (binding == null) {
            return false;
        }
        if (successful) {
            bigRuntime.commitRecovered(
                    binding.jobId(),
                    binding.transactionId(),
                    binding.executions(),
                    Map.of());
        } else {
            bigRuntime.rollbackRecovered(binding.jobId(), binding.transactionId());
        }
        externalExecutions.remove(childCpuId);
        rebalanceBigRuntimeCapacity();
        return true;
    }

    /**
     * 保存済みBindingに対応する子CPUが消えていた場合、成功/失敗を推測せずJobを隔離する。
     * アイテム複製より停止を選び、管理者がログと保存状態を確認できるようにする。
     */
    public synchronized boolean quarantineExternalExecution(UUID childCpuId) {
        ExternalExecutionBinding binding = externalExecutions.remove(
                Objects.requireNonNull(childCpuId, "childCpuId"));
        if (binding == null) {
            return false;
        }
        bigRuntime.cancel(binding.jobId());
        rebalanceBigRuntimeCapacity();
        return true;
    }

    /**
     * 保存時点で子CPUへ渡っていないprepared窓だけを安全に戻す。
     * 子CPUへ渡った窓はexternalExecutionsに存在するので、推測でrollbackしない。
     */
    public synchronized int rollbackUnboundPreparedExecutions() {
        Set<UUID> boundTransactions = new LinkedHashSet<>();
        externalExecutions.values().forEach(binding -> boundTransactions.add(binding.transactionId()));
        int rolledBack = 0;
        for (var recovered : List.copyOf(bigRuntime.unresolvedExecutions())) {
            if (!boundTransactions.contains(recovered.prepared().transactionId())) {
                bigRuntime.rollbackRecovered(
                        recovered.jobId(), recovered.prepared().transactionId());
                rolledBack++;
            }
        }
        return rolledBack;
    }

    public synchronized void commit(
            BigCraftingRuntime.ExecutionLease<K> lease,
            long acceptedExecutions,
            Map<K, BigInteger> expectedOutputs) {
        bigRuntime.commit(lease, acceptedExecutions, expectedOutputs);
        rebalanceBigRuntimeCapacity();
    }

    public synchronized void rollback(BigCraftingRuntime.ExecutionLease<K> lease) {
        bigRuntime.rollback(lease);
    }

    public synchronized void acceptOutput(UUID jobId, K key, BigInteger amount) {
        bigRuntime.acceptOutput(jobId, key, amount);
        rebalanceBigRuntimeCapacity();
    }

    public synchronized boolean cancel(UUID jobId) {
        boolean cancelled = bigRuntime.cancel(jobId);
        if (cancelled) {
            rebalanceBigRuntimeCapacity();
        }
        return cancelled;
    }

    public synchronized List<BigCraftingRuntime.RecoveredExecution<K>> unresolvedExecutions() {
        return bigRuntime.unresolvedExecutions();
    }

    public synchronized void commitRecovered(
            UUID jobId,
            UUID transactionId,
            long acceptedExecutions,
            Map<K, BigInteger> expectedOutputs) {
        bigRuntime.commitRecovered(jobId, transactionId, acceptedExecutions, expectedOutputs);
        rebalanceBigRuntimeCapacity();
    }

    public synchronized void rollbackRecovered(UUID jobId, UUID transactionId) {
        bigRuntime.rollbackRecovered(jobId, transactionId);
    }

    public synchronized BigCraftingStatusPage<K> statusPage(int offset, int requestedPageSize) {
        return bigRuntime.statusPage(offset, requestedPageSize);
    }

    public synchronized CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema", SCHEMA_VERSION);
        tag.putUUID("hostId", hostId);
        putNonNegative(tag, "physicalCapacity", physicalCapacity, maximumBits);
        ListTag external = new ListTag();
        for (var entry : externalReservations.entrySet()) {
            CompoundTag reservation = new CompoundTag();
            reservation.putUUID("jobId", entry.getKey());
            putNonNegative(reservation, "amount", entry.getValue(), maximumBits);
            external.add(reservation);
        }
        tag.put("externalReservations", external);
        ListTag executions = new ListTag();
        for (ExternalExecutionBinding binding : externalExecutions.values()) {
            CompoundTag execution = new CompoundTag();
            execution.putUUID("childCpuId", binding.childCpuId());
            execution.putUUID("jobId", binding.jobId());
            execution.putUUID("transactionId", binding.transactionId());
            execution.putString("taskId", binding.taskId());
            execution.putLong("executions", binding.executions());
            putNonNegative(
                    execution, "reservedCapacity", binding.reservedCapacity(), maximumBits);
            executions.add(execution);
        }
        tag.put("externalExecutions", executions);
        tag.put("bigRuntime", bigRuntime.save());
        return tag;
    }

    static <K> BigCraftingHostRuntime<K> load(
            CompoundTag tag,
            BigInteger currentPhysicalCapacity,
            BigCraftingKeyCodec<K> keyCodec,
            int maximumBits,
            int maximumExecutionsPerWindow,
            int maximumStatusPageEntries,
            long maximumRuntimeCountBytes) {
        Objects.requireNonNull(tag, "tag");
        int schema = tag.getInt("schema");
        if ((schema != 1 && schema != SCHEMA_VERSION)
                || !tag.hasUUID("hostId")
                || !tag.contains("bigRuntime", Tag.TAG_COMPOUND)
                || !tag.contains("externalReservations", Tag.TAG_LIST)) {
            throw new IllegalArgumentException("unsupported BigInteger host runtime schema");
        }
        // 現在の構造・Configを正とするが、壊れた保存値を見逃さないため永続値も検証する。
        readNonNegative(tag, "physicalCapacity", maximumBits);
        Map<UUID, BigInteger> external = readReservations(tag, maximumBits);
        BigCraftingRuntime<K> runtime = BigCraftingRuntime.load(
                tag.getCompound("bigRuntime"),
                keyCodec,
                maximumBits,
                maximumExecutionsPerWindow,
                maximumStatusPageEntries,
                maximumRuntimeCountBytes);
        Map<UUID, ExternalExecutionBinding> bindings = schema >= 2
                ? readExternalExecutions(tag, runtime, maximumBits)
                : Map.of();
        return new BigCraftingHostRuntime<>(
                tag.getUUID("hostId"),
                currentPhysicalCapacity,
                external,
                bindings,
                runtime,
                keyCodec,
                maximumBits,
                maximumExecutionsPerWindow,
                maximumStatusPageEntries,
                maximumRuntimeCountBytes);
    }

    public UUID hostId() {
        return hostId;
    }

    public synchronized BigInteger physicalCapacity() {
        return physicalCapacity;
    }

    public synchronized BigInteger externalReserved() {
        return externalReserved;
    }

    public synchronized BigInteger bigReserved() {
        return bigRuntime.reserved();
    }

    public synchronized BigInteger reserved() {
        return BigCountMath.add(
                externalReserved, bigRuntime.reserved(), "host/reserved", maximumBits);
    }

    public synchronized BigInteger available() {
        BigInteger remaining = physicalCapacity.subtract(reserved());
        return remaining.signum() < 0 ? BigInteger.ZERO : remaining;
    }

    public synchronized boolean isOvercommitted() {
        return reserved().compareTo(physicalCapacity) > 0;
    }

    public synchronized long physicalCapacityAsSaturatedLong() {
        return saturatedLong(physicalCapacity);
    }

    public synchronized long availableAsSaturatedLong() {
        return saturatedLong(available());
    }

    public synchronized Map<UUID, BigInteger> externalReservations() {
        return Map.copyOf(externalReservations);
    }

    public synchronized Map<UUID, ExternalExecutionBinding> externalExecutions() {
        return Map.copyOf(externalExecutions);
    }

    public synchronized Set<UUID> managedExternalChildIds() {
        return Set.copyOf(externalExecutions.keySet());
    }

    public synchronized long estimatedExternalCountBytes() {
        return externalCountBytes;
    }

    public synchronized List<UUID> bigJobIds() {
        return bigRuntime.jobIds();
    }

    public synchronized int bigJobCount() {
        return bigRuntime.jobIds().size();
    }

    public synchronized int managedChildJobCount() {
        return externalExecutions.size();
    }

    public int maximumBits() {
        return maximumBits;
    }

    private void rebalanceBigRuntimeCapacity() {
        BigInteger physicalForBigJobs = physicalCapacity.subtract(externalReserved);
        if (physicalForBigJobs.signum() < 0) {
            physicalForBigJobs = BigInteger.ZERO;
        }
        BigInteger replacement = physicalForBigJobs.max(bigRuntime.reserved());
        if (!bigRuntime.resizeCapacity(replacement)) {
            throw new IllegalStateException("failed to reconcile BigInteger host capacity");
        }
    }

    private void replaceExternalExecutions(Map<UUID, ExternalExecutionBinding> replacement) {
        Objects.requireNonNull(replacement, "external executions");
        if (replacement.size() > MAX_EXTERNAL_EXECUTIONS) {
            throw new IllegalArgumentException("too many external BigInteger executions");
        }
        Set<UUID> transactions = new LinkedHashSet<>();
        externalExecutions.clear();
        for (var entry : replacement.entrySet()) {
            UUID childId = Objects.requireNonNull(entry.getKey(), "child CPU id");
            ExternalExecutionBinding binding = Objects.requireNonNull(entry.getValue(), "external execution");
            if (!childId.equals(binding.childCpuId())
                    || !transactions.add(binding.transactionId())) {
                throw new IllegalArgumentException("duplicate or malformed external execution binding");
            }
            externalExecutions.put(childId, binding);
        }
    }

    private static <K> Map<UUID, ExternalExecutionBinding> readExternalExecutions(
            CompoundTag owner,
            BigCraftingRuntime<K> runtime,
            int maximumBits) {
        if (!owner.contains("externalExecutions", Tag.TAG_LIST)) {
            throw new IllegalArgumentException("missing external BigInteger execution list");
        }
        ListTag list = owner.getList("externalExecutions", Tag.TAG_COMPOUND);
        Tag raw = owner.get("externalExecutions");
        if (!(raw instanceof ListTag rawList)
                || (!rawList.isEmpty() && rawList.getElementType() != Tag.TAG_COMPOUND)
                || list.size() > MAX_EXTERNAL_EXECUTIONS) {
            throw new IllegalArgumentException("malformed or oversized external execution list");
        }
        Map<UUID, BigCraftingRuntime.RecoveredExecution<K>> recoveredByTransaction =
                new LinkedHashMap<>();
        for (var recovered : runtime.unresolvedExecutions()) {
            recoveredByTransaction.put(recovered.prepared().transactionId(), recovered);
        }
        Map<UUID, ExternalExecutionBinding> result = new LinkedHashMap<>();
        Set<UUID> transactions = new LinkedHashSet<>();
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entry = list.getCompound(index);
            if (!entry.hasUUID("childCpuId")
                    || !entry.hasUUID("jobId")
                    || !entry.hasUUID("transactionId")) {
                throw new IllegalArgumentException("external execution is missing an id");
            }
            UUID childId = entry.getUUID("childCpuId");
            UUID jobId = entry.getUUID("jobId");
            UUID transactionId = entry.getUUID("transactionId");
            String taskId = entry.getString("taskId");
            long executions = entry.getLong("executions");
            BigInteger reservation = checkedPositive(
                    readNonNegative(entry, "reservedCapacity", maximumBits),
                    "child execution reservation",
                    maximumBits);
            var recovered = recoveredByTransaction.get(transactionId);
            if (taskId.isBlank()
                    || executions <= 0L
                    || recovered == null
                    || !recovered.jobId().equals(jobId)
                    || !recovered.prepared().patternId().equals(taskId)
                    || recovered.prepared().window().executions() != executions
                    || !transactions.add(transactionId)
                    || result.putIfAbsent(childId, new ExternalExecutionBinding(
                                    childId,
                                    jobId,
                                    transactionId,
                                    taskId,
                                    executions,
                                    reservation)) != null) {
                throw new IllegalArgumentException("external execution does not match its prepared lease");
            }
        }
        return Map.copyOf(result);
    }

    private static CheckedReservations checkedReservations(
            Map<UUID, BigInteger> source,
            int maximumBits,
            long maximumCountBytes) {
        Objects.requireNonNull(source, "external reservations");
        if (source.size() > MAX_EXTERNAL_RESERVATIONS) {
            throw new IllegalArgumentException("too many external crafting reservations");
        }
        Map<UUID, BigInteger> checked = new LinkedHashMap<>();
        long countBytes = 0L;
        source.forEach((jobId, amount) -> {
            UUID id = Objects.requireNonNull(jobId, "external job id");
            BigInteger value = checkedPositive(amount, "external reservation", maximumBits);
            if (checked.putIfAbsent(id, value) != null) {
                throw new IllegalArgumentException("duplicate external crafting reservation " + id);
            }
        });
        for (BigInteger value : checked.values()) {
            countBytes = Math.addExact(countBytes, BigCountMath.encodedBytes(value));
            if (countBytes > maximumCountBytes) {
                throw new IllegalArgumentException(
                        "external crafting reservation count budget exceeded");
            }
        }
        return new CheckedReservations(Map.copyOf(checked), countBytes);
    }

    private static Map<UUID, BigInteger> readReservations(CompoundTag owner, int maximumBits) {
        Tag raw = owner.get("externalReservations");
        if (!(raw instanceof ListTag list)
                || (!list.isEmpty() && list.getElementType() != Tag.TAG_COMPOUND)
                || list.size() > MAX_EXTERNAL_RESERVATIONS) {
            throw new IllegalArgumentException("malformed or oversized external reservation list");
        }
        Map<UUID, BigInteger> result = new LinkedHashMap<>();
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entry = list.getCompound(index);
            if (!entry.hasUUID("jobId")) {
                throw new IllegalArgumentException("external reservation is missing its job id");
            }
            UUID id = entry.getUUID("jobId");
            BigInteger amount = checkedPositive(
                    readNonNegative(entry, "amount", maximumBits),
                    "external reservation",
                    maximumBits);
            if (result.putIfAbsent(id, amount) != null) {
                throw new IllegalArgumentException("duplicate external reservation " + id);
            }
        }
        return result;
    }

    private static BigInteger checkedCapacity(BigInteger value, int maximumBits) {
        return BigCountMath.requireMaximumBits(
                BigCountMath.requireNonNegative(value, "physical capacity"),
                "physical capacity",
                maximumBits);
    }

    private static BigInteger checkedPositive(BigInteger value, String name, int maximumBits) {
        BigInteger checked = BigCountMath.requireMaximumBits(
                BigCountMath.requireNonNegative(value, name), name, maximumBits);
        if (checked.signum() == 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return checked;
    }

    private static void putNonNegative(
            CompoundTag owner,
            String key,
            BigInteger value,
            int maximumBits) {
        BigInteger checked = BigCountMath.requireMaximumBits(
                BigCountMath.requireNonNegative(value, key), key, maximumBits);
        owner.putByteArray(key, checked.toByteArray());
    }

    private static BigInteger readNonNegative(CompoundTag owner, String key, int maximumBits) {
        if (!owner.contains(key, Tag.TAG_BYTE_ARRAY)) {
            throw new IllegalArgumentException("missing BigInteger byte array " + key);
        }
        byte[] encoded = owner.getByteArray(key);
        int maximumBytes = Math.addExact(maximumBits, 8) / 8;
        if (encoded.length == 0 || encoded.length > maximumBytes) {
            throw new IllegalArgumentException("invalid or oversized BigInteger byte array " + key);
        }
        BigInteger value = new BigInteger(encoded);
        BigInteger checked = BigCountMath.requireMaximumBits(
                BigCountMath.requireNonNegative(value, key), key, maximumBits);
        if (!java.util.Arrays.equals(encoded, checked.toByteArray())) {
            throw new IllegalArgumentException("non-canonical BigInteger byte array " + key);
        }
        return checked;
    }

    private static long saturatedLong(BigInteger value) {
        return value.compareTo(LONG_MAX) >= 0 ? Long.MAX_VALUE : value.longValueExact();
    }

    private boolean exceedsExternalCountBudget(long additionalBytes) {
        if (additionalBytes < 0L || externalCountBytes > maximumRuntimeCountBytes - additionalBytes) {
            return true;
        }
        return false;
    }

    private record CheckedReservations(
            Map<UUID, BigInteger> values,
            long encodedCountBytes) {
    }

    public record ExternalExecutionBinding(
            UUID childCpuId,
            UUID jobId,
            UUID transactionId,
            String taskId,
            long executions,
            BigInteger reservedCapacity) {
        public ExternalExecutionBinding {
            Objects.requireNonNull(childCpuId, "childCpuId");
            Objects.requireNonNull(jobId, "jobId");
            Objects.requireNonNull(transactionId, "transactionId");
            if (Objects.requireNonNull(taskId, "taskId").isBlank()) {
                throw new IllegalArgumentException("taskId must not be blank");
            }
            if (executions <= 0L) {
                throw new IllegalArgumentException("executions must be positive");
            }
            Objects.requireNonNull(reservedCapacity, "reservedCapacity");
        }
    }
}
