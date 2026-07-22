package com.syaru.ae2craftingoptimizer.api.big;

import com.syaru.ae2craftingoptimizer.engine.BigCountMath;
import com.syaru.ae2craftingoptimizer.engine.BigCraftingCpuLedger;
import com.syaru.ae2craftingoptimizer.engine.BigCraftingJob;
import com.syaru.ae2craftingoptimizer.engine.BigCraftingKeyCodec;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;

/**
 * BigInteger対応CPU一台分のVersion付き実行状態。
 * 予約量と残量はBigIntegerで保持し、既存のlong/int機械処理へは上限付きLeaseだけを渡す。
 */
public final class BigCraftingRuntime<K> {
    public static final int SCHEMA_VERSION = 2;
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    private final BigCraftingKeyCodec<K> keyCodec;
    private final UUID runtimeId;
    private final int maximumBits;
    private final int maximumExecutionsPerWindow;
    private final int maximumStatusPageEntries;
    private final long maximumRuntimeCountBytes;
    private final BigCraftingCpuLedger<K> ledger;
    private long runtimeCountBytes;

    public BigCraftingRuntime(
            BigInteger capacity,
            BigCraftingKeyCodec<K> keyCodec,
            int maximumBits,
            int maximumExecutionsPerWindow) {
        this(capacity, keyCodec, maximumBits, maximumExecutionsPerWindow, 1024, 256L * 1024L * 1024L);
    }

    public BigCraftingRuntime(
            BigInteger capacity,
            BigCraftingKeyCodec<K> keyCodec,
            int maximumBits,
            int maximumExecutionsPerWindow,
            int maximumStatusPageEntries) {
        this(
                capacity,
                keyCodec,
                maximumBits,
                maximumExecutionsPerWindow,
                maximumStatusPageEntries,
                256L * 1024L * 1024L);
    }

    public BigCraftingRuntime(
            BigInteger capacity,
            BigCraftingKeyCodec<K> keyCodec,
            int maximumBits,
            int maximumExecutionsPerWindow,
            int maximumStatusPageEntries,
            long maximumRuntimeCountBytes) {
        this(
                UUID.randomUUID(),
                new BigCraftingCpuLedger<>(validateMagnitude(capacity, "capacity", maximumBits)),
                keyCodec,
                maximumBits,
                maximumExecutionsPerWindow,
                maximumStatusPageEntries,
                maximumRuntimeCountBytes);
    }

    private BigCraftingRuntime(
            UUID runtimeId,
            BigCraftingCpuLedger<K> ledger,
            BigCraftingKeyCodec<K> keyCodec,
            int maximumBits,
            int maximumExecutionsPerWindow,
            int maximumStatusPageEntries,
            long maximumRuntimeCountBytes) {
        validateLimits(
                maximumBits,
                maximumExecutionsPerWindow,
                maximumStatusPageEntries,
                maximumRuntimeCountBytes);
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.runtimeId = Objects.requireNonNull(runtimeId, "runtimeId");
        this.keyCodec = Objects.requireNonNull(keyCodec, "keyCodec");
        this.maximumBits = maximumBits;
        this.maximumExecutionsPerWindow = maximumExecutionsPerWindow;
        this.maximumStatusPageEntries = maximumStatusPageEntries;
        this.maximumRuntimeCountBytes = maximumRuntimeCountBytes;
        for (UUID jobId : ledger.jobIds()) {
            BigCraftingJob<K> job = ledger.get(jobId);
            if (job != null) {
                validateJob(job);
            }
        }
        this.runtimeCountBytes = calculateRuntimeCountBytes();
        ensureReplacementCountBudget(0L, 0L);
    }

    public synchronized boolean submit(BigCraftingJob<K> job) {
        validateJob(job);
        if (job.state() != BigCraftingJob.State.PLANNED || job.hasPreparedExecution()) {
            throw new IllegalArgumentException("only a fresh planned BigInteger job can be submitted");
        }
        if (ledger.get(job.id()) != null) {
            throw new IllegalStateException("duplicate BigInteger crafting job " + job.id());
        }
        BigCraftingJob<K> owned = BigCraftingJob.load(
                job.save(keyCodec, maximumBits), keyCodec, maximumBits);
        validateJob(owned);
        if (owned.state() != BigCraftingJob.State.PLANNED || owned.hasPreparedExecution()) {
            throw new IllegalArgumentException(
                    "job changed while its owned submission snapshot was being created");
        }
        long ownedBytes = owned.estimatedCountBytes();
        ensureReplacementCountBudget(0L, ownedBytes);
        if (!ledger.submit(owned)) {
            return false;
        }
        runtimeCountBytes = Math.addExact(runtimeCountBytes, ownedBytes);
        return true;
    }

    public synchronized List<ExecutionLease<K>> schedule(long operationBudget) {
        return schedule(operationBudget, Integer.MAX_VALUE);
    }

    public synchronized List<ExecutionLease<K>> schedule(
            long operationBudget,
            int maximumWindows) {
        return ledger.schedule(
                        operationBudget, maximumExecutionsPerWindow, maximumWindows)
                .stream()
                .map(scheduled -> new ExecutionLease<>(
                        runtimeId,
                        scheduled.job().id(),
                        scheduled.job().requestedKey(),
                        scheduled.job().reservedCapacity(),
                        scheduled.job().patternGeneration(),
                        scheduled.job().recipeGeneration(),
                        scheduled.prepared()))
                .toList();
    }

    /** Optional host integrations call this before transferring ownership to an external child job. */
    public synchronized void validateLease(ExecutionLease<K> lease) {
        requireOwned(lease);
    }

    public synchronized void commit(
            ExecutionLease<K> lease,
            long acceptedExecutions,
            Map<K, BigInteger> expectedOutputs) {
        BigCraftingJob<K> job = requireOwned(lease);
        Map<K, BigInteger> checkedOutputs = validatedOutputs(expectedOutputs);
        job.validateProjectedWaiting(checkedOutputs, maximumBits);
        long previousEstimate = job.estimatedCountBytes();
        long projectedEstimate = job.estimatedCountBytesAfterCommit(
                acceptedExecutions, checkedOutputs);
        ensureReplacementCountBudget(previousEstimate, projectedEstimate);
        job.commitPreparedWindow(
                lease.prepared().transactionId(), acceptedExecutions, checkedOutputs);
        ledger.removeTerminal(job.id());
        replaceTrackedJobBytes(
                previousEstimate,
                ledger.get(job.id()) == null ? 0L : job.estimatedCountBytes());
    }

    public synchronized void rollback(ExecutionLease<K> lease) {
        BigCraftingJob<K> job = requireOwned(lease);
        job.rollbackPreparedWindow(lease.prepared().transactionId());
    }

    /**
     * 保存をまたいで残った実行Leaseを列挙する。
     * 連携CPUは、自身の機械側永続取引を照合してからLeaseをcommitまたはrollbackする必要がある。
     */
    public synchronized List<RecoveredExecution<K>> unresolvedExecutions() {
        return ledger.jobIds().stream()
                .map(ledger::get)
                .filter(Objects::nonNull)
                .filter(BigCraftingJob::hasPreparedExecution)
                .map(job -> new RecoveredExecution<>(
                        job.id(), job.requestedKey(), job.preparedExecution()))
                .toList();
    }

    /** Resolves a persisted lease after the host proves the target accepted it. */
    public synchronized void commitRecovered(
            UUID jobId,
            UUID transactionId,
            long acceptedExecutions,
            Map<K, BigInteger> expectedOutputs) {
        BigCraftingJob<K> job = requireJob(jobId);
        requirePrepared(job, transactionId);
        Map<K, BigInteger> checkedOutputs = validatedOutputs(expectedOutputs);
        job.validateProjectedWaiting(checkedOutputs, maximumBits);
        long previousEstimate = job.estimatedCountBytes();
        long projectedEstimate = job.estimatedCountBytesAfterCommit(
                acceptedExecutions, checkedOutputs);
        ensureReplacementCountBudget(previousEstimate, projectedEstimate);
        job.commitPreparedWindow(transactionId, acceptedExecutions, checkedOutputs);
        ledger.removeTerminal(job.id());
        replaceTrackedJobBytes(
                previousEstimate,
                ledger.get(job.id()) == null ? 0L : job.estimatedCountBytes());
    }

    /** Resolves a persisted lease after the host proves the target did not accept it. */
    public synchronized void rollbackRecovered(UUID jobId, UUID transactionId) {
        BigCraftingJob<K> job = requireJob(jobId);
        requirePrepared(job, transactionId);
        job.rollbackPreparedWindow(transactionId);
    }

    public synchronized void acceptOutput(UUID jobId, K key, BigInteger amount) {
        BigCraftingJob<K> job = ledger.get(Objects.requireNonNull(jobId, "jobId"));
        if (job == null) {
            throw new IllegalArgumentException("unknown BigInteger crafting job " + jobId);
        }
        Objects.requireNonNull(key, "key");
        validateMagnitude(amount, "accepted output", maximumBits);
        if (amount.signum() == 0) {
            throw new IllegalArgumentException("accepted output must be positive");
        }
        long previousEstimate = job.estimatedCountBytes();
        job.acceptOutput(key, amount);
        ledger.removeTerminal(jobId);
        replaceTrackedJobBytes(
                previousEstimate,
                ledger.get(jobId) == null ? 0L : job.estimatedCountBytes());
    }

    public synchronized boolean cancel(UUID jobId) {
        BigCraftingJob<K> job = ledger.get(Objects.requireNonNull(jobId, "jobId"));
        if (job == null) {
            return false;
        }
        long previousEstimate = job.estimatedCountBytes();
        boolean cancelled = ledger.cancel(jobId);
        if (cancelled && ledger.get(jobId) == null) {
            replaceTrackedJobBytes(previousEstimate, 0L);
        }
        return cancelled;
    }

    public synchronized boolean discardQuarantined(UUID jobId) {
        BigCraftingJob<K> job = ledger.get(Objects.requireNonNull(jobId, "jobId"));
        if (job == null) {
            return false;
        }
        long previousEstimate = job.estimatedCountBytes();
        boolean discarded = ledger.discardQuarantined(jobId);
        if (discarded) {
            replaceTrackedJobBytes(previousEstimate, 0L);
        }
        return discarded;
    }

    public synchronized CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema", SCHEMA_VERSION);
        tag.putUUID("runtimeId", runtimeId);
        tag.putInt("maximumBits", maximumBits);
        tag.putInt("maximumExecutionsPerWindow", maximumExecutionsPerWindow);
        tag.put("ledger", ledger.save(keyCodec, maximumBits));
        return tag;
    }

    public static <K> BigCraftingRuntime<K> load(
            CompoundTag tag,
            BigCraftingKeyCodec<K> keyCodec,
            int configuredMaximumBits,
            int configuredMaximumExecutionsPerWindow) {
        return load(
                tag,
                keyCodec,
                configuredMaximumBits,
                configuredMaximumExecutionsPerWindow,
                1024,
                256L * 1024L * 1024L);
    }

    public static <K> BigCraftingRuntime<K> load(
            CompoundTag tag,
            BigCraftingKeyCodec<K> keyCodec,
            int configuredMaximumBits,
            int configuredMaximumExecutionsPerWindow,
            int configuredMaximumStatusPageEntries) {
        return load(
                tag,
                keyCodec,
                configuredMaximumBits,
                configuredMaximumExecutionsPerWindow,
                configuredMaximumStatusPageEntries,
                256L * 1024L * 1024L);
    }

    public static <K> BigCraftingRuntime<K> load(
            CompoundTag tag,
            BigCraftingKeyCodec<K> keyCodec,
            int configuredMaximumBits,
            int configuredMaximumExecutionsPerWindow,
            int configuredMaximumStatusPageEntries,
            long configuredMaximumRuntimeCountBytes) {
        Objects.requireNonNull(tag, "tag");
        int schema = tag.getInt("schema");
        if ((schema != 1 && schema != SCHEMA_VERSION) || !tag.contains("ledger")) {
            throw new IllegalArgumentException("unsupported BigInteger crafting runtime schema");
        }
        validateLimits(
                configuredMaximumBits,
                configuredMaximumExecutionsPerWindow,
                configuredMaximumStatusPageEntries,
                configuredMaximumRuntimeCountBytes);
        int savedMaximumBits = tag.getInt("maximumBits");
        if (savedMaximumBits < 1 || savedMaximumBits > configuredMaximumBits) {
            throw new IllegalArgumentException(
                    "saved BigInteger runtime exceeds the configured magnitude limit");
        }
        int savedMaximumExecutions = tag.getInt("maximumExecutionsPerWindow");
        if (savedMaximumExecutions < 1
                || savedMaximumExecutions > BigCraftingJob.MAX_EXECUTIONS_PER_WINDOW) {
            throw new IllegalArgumentException(
                    "saved BigInteger runtime has an invalid execution-window limit");
        }
        UUID runtimeId = schema == 1
                ? UUID.randomUUID()
                : tag.hasUUID("runtimeId")
                        ? tag.getUUID("runtimeId")
                        : null;
        if (runtimeId == null) {
            throw new IllegalArgumentException("BigInteger crafting runtime is missing its persistent id");
        }
        BigCraftingCpuLedger<K> ledger = BigCraftingCpuLedger.load(
                tag.getCompound("ledger"),
                keyCodec,
                configuredMaximumBits,
                configuredMaximumRuntimeCountBytes);
        for (UUID jobId : ledger.jobIds()) {
            BigCraftingJob<K> job = ledger.get(jobId);
            if (job != null
                    && job.preparedExecution() != null
                    && job.preparedExecution().window().executions() > savedMaximumExecutions) {
                throw new IllegalArgumentException(
                        "saved BigInteger execution lease exceeds its persisted window limit");
            }
        }
        return new BigCraftingRuntime<>(
                runtimeId,
                ledger,
                keyCodec,
                configuredMaximumBits,
                configuredMaximumExecutionsPerWindow,
                configuredMaximumStatusPageEntries,
                configuredMaximumRuntimeCountBytes);
    }

    public BigInteger capacity() {
        return ledger.capacity();
    }

    public UUID runtimeId() {
        return runtimeId;
    }

    public BigInteger reserved() {
        return ledger.reserved();
    }

    public BigInteger available() {
        return ledger.available();
    }

    /** Reconciles a re-formed CPU's current capacity without invalidating active reservations. */
    public synchronized boolean resizeCapacity(BigInteger replacement) {
        validateMagnitude(replacement, "replacement capacity", maximumBits);
        long previousBytes = BigCountMath.encodedBytes(ledger.capacity());
        long replacementBytes = BigCountMath.encodedBytes(replacement);
        ensureReplacementCountBudget(previousBytes, replacementBytes);
        if (!ledger.resizeCapacity(replacement)) {
            return false;
        }
        replaceTrackedJobBytes(previousBytes, replacementBytes);
        return true;
    }

    /** Compatibility facade for an API that can only display a signed long. */
    public long capacityAsSaturatedLong() {
        return saturatedLong(capacity());
    }

    /** Compatibility facade for an API that can only display a signed long. */
    public long reservedAsSaturatedLong() {
        return saturatedLong(reserved());
    }

    public List<UUID> jobIds() {
        return ledger.jobIds();
    }

    public int maximumBits() {
        return maximumBits;
    }

    public int maximumExecutionsPerWindow() {
        return maximumExecutionsPerWindow;
    }

    public int maximumStatusPageEntries() {
        return maximumStatusPageEntries;
    }

    public long maximumRuntimeCountBytes() {
        return maximumRuntimeCountBytes;
    }

    public synchronized long estimatedRuntimeCountBytes() {
        return runtimeCountBytes;
    }

    public synchronized BigCraftingStatusPage<K> statusPage(int offset, int requestedPageSize) {
        List<UUID> ids = ledger.jobIds();
        if (offset < 0 || offset > ids.size()) {
            throw new IllegalArgumentException("status page offset is outside the job list");
        }
        if (requestedPageSize < 1) {
            throw new IllegalArgumentException("status page size must be positive");
        }
        int pageSize = Math.min(requestedPageSize, maximumStatusPageEntries);
        int end = Math.min(ids.size(), Math.addExact(offset, pageSize));
        List<BigCraftingStatusPage.JobSummary<K>> summaries = ids.subList(offset, end).stream()
                .map(ledger::get)
                .filter(Objects::nonNull)
                .map(BigCraftingJob::compactStatusSnapshot)
                .map(BigCraftingRuntime::summarize)
                .toList();
        return new BigCraftingStatusPage<>(
                runtimeId, capacity(), reserved(), available(), ids.size(), offset, summaries);
    }

    private BigCraftingJob<K> requireOwned(ExecutionLease<K> lease) {
        Objects.requireNonNull(lease, "lease");
        if (!runtimeId.equals(lease.runtimeId())) {
            throw new IllegalStateException("execution lease belongs to another BigInteger CPU runtime");
        }
        BigCraftingJob<K> owned = ledger.get(lease.jobId());
        if (owned == null
                || owned.preparedExecution() == null
                || !owned.preparedExecution().transactionId()
                        .equals(lease.prepared().transactionId())) {
            throw new IllegalStateException("execution lease is stale or belongs to another CPU runtime");
        }
        return owned;
    }

    private BigCraftingJob<K> requireJob(UUID jobId) {
        BigCraftingJob<K> job = ledger.get(Objects.requireNonNull(jobId, "jobId"));
        if (job == null) {
            throw new IllegalArgumentException("unknown BigInteger crafting job " + jobId);
        }
        return job;
    }

    private static <K> void requirePrepared(BigCraftingJob<K> job, UUID transactionId) {
        BigCraftingJob.PreparedExecution prepared = job.preparedExecution();
        if (prepared == null
                || !prepared.transactionId().equals(Objects.requireNonNull(transactionId, "transactionId"))) {
            throw new IllegalStateException("unknown, stale, or replayed BigInteger execution transaction");
        }
    }

    private void validateJob(BigCraftingJob<K> job) {
        Objects.requireNonNull(job, "job");
        validateMagnitude(job.requestedAmount(), "job requested amount", maximumBits);
        validateMagnitude(job.reservedCapacity(), "job reserved capacity", maximumBits);
        job.remainingTasks().values().forEach(
                amount -> validateMagnitude(amount, "job task amount", maximumBits));
        job.waitingFor().values().forEach(
                amount -> validateMagnitude(amount, "job waiting amount", maximumBits));
        validateMagnitude(job.remainingExecutionTotal(), "job remaining execution total", maximumBits);
        validateMagnitude(job.waitingTotal(), "job waiting total", maximumBits);
    }

    private Map<K, BigInteger> validatedOutputs(Map<K, BigInteger> outputs) {
        Map<K, BigInteger> checked = new LinkedHashMap<>();
        Objects.requireNonNull(outputs, "expectedOutputs").forEach((key, amount) -> {
            Objects.requireNonNull(key, "expected output key");
            validateMagnitude(amount, "expected output", maximumBits);
            if (amount.signum() != 0) {
                checked.put(key, amount);
            }
        });
        return Map.copyOf(checked);
    }

    private static BigInteger validateMagnitude(BigInteger value, String name, int maximumBits) {
        validateLimits(maximumBits, 1, 1, 1);
        return BigCountMath.requireMaximumBits(value, name, maximumBits);
    }

    private static void validateLimits(
            int maximumBits,
            int maximumExecutionsPerWindow,
            int maximumStatusPageEntries,
            long maximumRuntimeCountBytes) {
        BigCountMath.requireMaximumBits(BigInteger.ZERO, "runtime maximum", maximumBits);
        // Runtimeは通常AE2のsigned long境界も包含するため、64bit未満の設定を拒否する。
        if (maximumBits < 64) {
            throw new IllegalArgumentException("maximumBits must be at least 64");
        }
        if (maximumExecutionsPerWindow < 1 || maximumExecutionsPerWindow > 1_048_576) {
            throw new IllegalArgumentException(
                    "maximumExecutionsPerWindow must be between 1 and 1048576");
        }
        if (maximumStatusPageEntries < 1
                || maximumStatusPageEntries > BigCraftingStatusPageCodec.HARD_MAXIMUM_PAGE_ENTRIES) {
            throw new IllegalArgumentException("maximumStatusPageEntries is outside the packet safety bound");
        }
        if (maximumRuntimeCountBytes < 1L) {
            throw new IllegalArgumentException("maximumRuntimeCountBytes must be positive");
        }
    }

    private long calculateRuntimeCountBytes() {
        long used = BigCountMath.encodedBytes(ledger.capacity());
        for (UUID jobId : ledger.jobIds()) {
            BigCraftingJob<K> job = ledger.get(jobId);
            if (job != null) {
                used = Math.addExact(used, job.estimatedCountBytes());
            }
        }
        return used;
    }

    private void ensureReplacementCountBudget(long previousBytes, long replacementBytes) {
        if (previousBytes < 0L || replacementBytes < 0L) {
            throw new IllegalArgumentException("BigInteger count-byte estimates must not be negative");
        }
        long projected = Math.addExact(
                Math.subtractExact(runtimeCountBytes, previousBytes),
                replacementBytes);
        if (projected > maximumRuntimeCountBytes) {
            throw new IllegalStateException(
                    "BigInteger crafting runtime count budget exceeded: "
                            + projected + " > " + maximumRuntimeCountBytes + " bytes");
        }
    }

    private void replaceTrackedJobBytes(long previousBytes, long replacementBytes) {
        runtimeCountBytes = Math.addExact(
                Math.subtractExact(runtimeCountBytes, previousBytes),
                replacementBytes);
    }

    private static <K> BigCraftingStatusPage.JobSummary<K> summarize(
            BigCraftingJob.CompactStatusSnapshot<K> snapshot) {
        return new BigCraftingStatusPage.JobSummary<>(
                snapshot.id(),
                snapshot.requestedKey(),
                snapshot.requestedAmount(),
                snapshot.reservedCapacity(),
                snapshot.remainingExecutions(),
                snapshot.waitingAmount(),
                snapshot.remainingTaskTypes(),
                snapshot.waitingTypes(),
                snapshot.state(),
                snapshot.executionPrepared());
    }

    private static long saturatedLong(BigInteger value) {
        return value.compareTo(LONG_MAX) >= 0 ? Long.MAX_VALUE : value.longValueExact();
    }

    public record RecoveredExecution<K>(
            UUID jobId,
            K requestedKey,
            BigCraftingJob.PreparedExecution prepared) {
        public RecoveredExecution {
            Objects.requireNonNull(jobId, "jobId");
            Objects.requireNonNull(requestedKey, "requestedKey");
            Objects.requireNonNull(prepared, "prepared");
        }
    }

    public record ExecutionLease<K>(
            UUID runtimeId,
            UUID jobId,
            K requestedKey,
            BigInteger jobReservedCapacity,
            long patternGeneration,
            long recipeGeneration,
            BigCraftingJob.PreparedExecution prepared) {
        public ExecutionLease {
            Objects.requireNonNull(runtimeId, "runtimeId");
            Objects.requireNonNull(jobId, "jobId");
            Objects.requireNonNull(requestedKey, "requestedKey");
            BigCountMath.requireNonNegative(jobReservedCapacity, "jobReservedCapacity");
            if (patternGeneration < -1L || recipeGeneration < -1L) {
                throw new IllegalArgumentException("planning generations must be -1 or non-negative");
            }
            Objects.requireNonNull(prepared, "prepared");
        }
    }
}
