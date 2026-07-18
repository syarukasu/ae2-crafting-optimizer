package com.syaru.ae2craftingoptimizer.engine;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * BigInteger注文の永続状態。
 * 保存上の残量はBigIntegerのまま維持し、機械へ渡す時だけ上限付きlong実行Windowとして貸し出す。
 */
public final class BigCraftingJob<K> {
    public static final int SCHEMA_VERSION = 3;
    public static final long MAX_EXECUTIONS_PER_WINDOW = 1_048_576L;
    /**
     * BigInteger注文を標準AE2 Jobへ分割する時だけ使う予約済みTask ID。
     * 通常のPattern fingerprintと衝突しないACO所有の名前空間に固定する。
     */
    public static final String ROOT_WINDOW_TASK_ID = "aco:root-window-v1";
    private static final int MAX_ENTRIES = 1_048_576;

    private final UUID id;
    private final K requestedKey;
    private final BigInteger requestedAmount;
    private final BigInteger reservedCapacity;
    private final long patternGeneration;
    private final long recipeGeneration;
    private final Map<String, BigCraftingTaskProgress> tasks;
    private final BigCraftingInventory<K> waitingFor;
    private BigInteger remainingExecutionTotal;
    private int remainingTaskTypes;
    private long fixedAndTaskCountBytes;
    private State state;
    private PreparedExecution preparedExecution;

    public BigCraftingJob(
            UUID id,
            K requestedKey,
            BigInteger requestedAmount,
            BigInteger reservedCapacity,
            Map<String, BigInteger> patternExecutions,
            Map<K, BigInteger> initialWaitingFor) {
        this(
                id,
                requestedKey,
                requestedAmount,
                reservedCapacity,
                newTasks(patternExecutions),
                new BigCraftingInventory<>(initialWaitingFor),
                State.PLANNED,
                null,
                -1L,
                -1L);
    }

    /**
     * 一つの巨大な完成品要求を、同じ完成品のlong範囲Jobへ順番に分割する。
     * 実行窓の成功確認までは進捗を増やさないため、再起動時も未確定分を再送しない。
     */
    public static <K> BigCraftingJob<K> rootWindowed(
            UUID id,
            K requestedKey,
            BigInteger requestedAmount,
            BigInteger reservedCapacity) {
        return rootWindowed(
                id, requestedKey, requestedAmount, reservedCapacity, -1L, -1L);
    }

    public static <K> BigCraftingJob<K> rootWindowed(
            UUID id,
            K requestedKey,
            BigInteger requestedAmount,
            BigInteger reservedCapacity,
            long patternGeneration,
            long recipeGeneration) {
        if (patternGeneration < -1L || recipeGeneration < -1L) {
            throw new IllegalArgumentException("planning generations must be -1 or non-negative");
        }
        return new BigCraftingJob<>(
                id,
                requestedKey,
                requestedAmount,
                reservedCapacity,
                newTasks(Map.of(ROOT_WINDOW_TASK_ID, requestedAmount)),
                new BigCraftingInventory<>(Map.of()),
                State.PLANNED,
                null,
                patternGeneration,
                recipeGeneration);
    }

    /** Lossless migration entry point for an add-on's existing signed-long job state. */
    public static <K> BigCraftingJob<K> fromLong(
            UUID id,
            K requestedKey,
            long requestedAmount,
            long reservedCapacity,
            Map<String, Long> patternExecutions,
            Map<K, Long> initialWaitingFor) {
        if (requestedAmount <= 0L || reservedCapacity < 0L) {
            throw new IllegalArgumentException("long crafting job counts are invalid");
        }
        Map<String, BigInteger> tasks = new LinkedHashMap<>();
        Objects.requireNonNull(patternExecutions, "patternExecutions").forEach((pattern, amount) -> {
            if (amount == null || amount <= 0L) {
                throw new IllegalArgumentException("long pattern execution counts must be positive");
            }
            tasks.put(pattern, BigInteger.valueOf(amount));
        });
        Map<K, BigInteger> waiting = new LinkedHashMap<>();
        Objects.requireNonNull(initialWaitingFor, "initialWaitingFor").forEach((key, amount) -> {
            if (amount == null || amount <= 0L) {
                throw new IllegalArgumentException("long waiting-output counts must be positive");
            }
            waiting.put(key, BigInteger.valueOf(amount));
        });
        return new BigCraftingJob<>(
                id,
                requestedKey,
                BigInteger.valueOf(requestedAmount),
                BigInteger.valueOf(reservedCapacity),
                newTasks(tasks),
                new BigCraftingInventory<>(waiting),
                State.PLANNED,
                null,
                -1L,
                -1L);
    }

    private BigCraftingJob(
            UUID id,
            K requestedKey,
            BigInteger requestedAmount,
            BigInteger reservedCapacity,
            Map<String, BigCraftingTaskProgress> tasks,
            BigCraftingInventory<K> waitingFor,
            State state,
            PreparedExecution preparedExecution,
            long patternGeneration,
            long recipeGeneration) {
        this.id = Objects.requireNonNull(id, "id");
        this.requestedKey = Objects.requireNonNull(requestedKey, "requestedKey");
        this.requestedAmount = positive(requestedAmount, "requestedAmount");
        this.reservedCapacity = BigCountMath.requireNonNegative(reservedCapacity, "reservedCapacity");
        if (patternGeneration < -1L || recipeGeneration < -1L) {
            throw new IllegalArgumentException("planning generations must be -1 or non-negative");
        }
        this.patternGeneration = patternGeneration;
        this.recipeGeneration = recipeGeneration;
        this.tasks = new LinkedHashMap<>(Objects.requireNonNull(tasks, "tasks"));
        if (this.tasks.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("too many BigInteger crafting tasks");
        }
        BigInteger remaining = BigInteger.ZERO;
        int remainingTypes = 0;
        long taskBytes = Math.addExact(
                BigCountMath.encodedBytes(this.requestedAmount),
                BigCountMath.encodedBytes(this.reservedCapacity));
        for (BigCraftingTaskProgress task : this.tasks.values()) {
            taskBytes = Math.addExact(taskBytes, BigCountMath.encodedBytes(task.total()));
            taskBytes = Math.addExact(taskBytes, BigCountMath.encodedBytes(task.completed()));
            if (!task.isComplete()) {
                remaining = remaining.add(task.remaining());
                remainingTypes++;
            }
        }
        this.remainingExecutionTotal = remaining;
        this.remainingTaskTypes = remainingTypes;
        this.fixedAndTaskCountBytes = taskBytes;
        this.waitingFor = Objects.requireNonNull(waitingFor, "waitingFor");
        this.state = Objects.requireNonNull(state, "state");
        this.preparedExecution = preparedExecution;
    }

    public synchronized PreparedExecution prepareWindow(String patternId, long maximumExecutions) {
        ensureRunnable();
        if (maximumExecutions <= 0L || maximumExecutions > MAX_EXECUTIONS_PER_WINDOW) {
            throw new IllegalArgumentException(
                    "maximumExecutions must be between 1 and " + MAX_EXECUTIONS_PER_WINDOW);
        }
        if (preparedExecution != null) {
            throw new IllegalStateException("BigInteger job already has a prepared execution window");
        }
        BigCraftingTaskProgress task = requireTask(patternId);
        if (task.isComplete()) {
            throw new IllegalStateException("pattern task is complete: " + patternId);
        }
        state = State.RUNNING;
        preparedExecution = new PreparedExecution(UUID.randomUUID(), patternId, task.nextWindow(maximumExecutions));
        return preparedExecution;
    }

    public synchronized void commitPreparedWindow(
            UUID transactionId,
            long acceptedExecutions,
            Map<K, BigInteger> expectedOutputs) {
        ensureRunnable();
        PreparedExecution prepared = requirePrepared(transactionId);
        BigExecutionWindow window = prepared.window();
        if (acceptedExecutions <= 0L || acceptedExecutions > window.executions()) {
            throw new IllegalArgumentException("accepted executions are outside the prepared window");
        }
        BigCraftingTaskProgress task = requireTask(prepared.patternId());
        if (!task.completed().equals(window.offset())) {
            throw new IllegalStateException("stale or replayed BigInteger execution window");
        }
        Map<K, BigInteger> checkedOutputs = validateOutputs(expectedOutputs);
        try (BigCraftingInventory.Transaction<K> transaction = waitingFor.beginTransaction()) {
            checkedOutputs.forEach(transaction::insert);
            BigInteger completedBefore = task.completed();
            task.complete(acceptedExecutions);
            fixedAndTaskCountBytes = replaceEncodedBytes(
                    fixedAndTaskCountBytes, completedBefore, task.completed());
            remainingExecutionTotal = remainingExecutionTotal.subtract(
                    BigInteger.valueOf(acceptedExecutions));
            if (task.isComplete()) {
                remainingTaskTypes--;
            }
            transaction.commit();
        }
        preparedExecution = null;
        if (remainingTaskTypes == 0) {
            state = waitingFor.distinctKeys() == 0 ? State.COMPLETE : State.WAITING_FOR_OUTPUTS;
        }
    }

    public synchronized void rollbackPreparedWindow(UUID transactionId) {
        ensureRunnable();
        requirePrepared(transactionId);
        preparedExecution = null;
    }

    public synchronized void acceptOutput(K key, BigInteger amount) {
        if (state == State.CANCELLED || state == State.QUARANTINED || state == State.COMPLETE) {
            throw new IllegalStateException("job cannot accept output in state " + state);
        }
        positive(amount, "accepted output amount");
        waitingFor.extractExact(key, amount);
        if (remainingTaskTypes == 0 && waitingFor.distinctKeys() == 0) {
            state = State.COMPLETE;
        }
    }

    public synchronized void cancel() {
        if (state != State.COMPLETE && state != State.QUARANTINED) {
            state = preparedExecution == null && waitingFor.distinctKeys() == 0
                    ? State.CANCELLED
                    : State.QUARANTINED;
        }
    }

    public synchronized void quarantine() {
        if (state != State.COMPLETE) {
            state = State.QUARANTINED;
        }
    }

    public synchronized CompoundTag save(BigCraftingKeyCodec<K> codec, int maximumBits) {
        Objects.requireNonNull(codec, "codec");
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema", SCHEMA_VERSION);
        tag.putUUID("id", id);
        tag.put("requestedKey", codec.encode(requestedKey));
        BigIntegerNbtCodec.putNonNegative(tag, "requestedAmount", requestedAmount, maximumBits);
        BigIntegerNbtCodec.putNonNegative(tag, "reservedCapacity", reservedCapacity, maximumBits);
        tag.putString("state", state.name());
        tag.putLong("patternGeneration", patternGeneration);
        tag.putLong("recipeGeneration", recipeGeneration);
        if (preparedExecution != null) {
            CompoundTag prepared = new CompoundTag();
            prepared.putUUID("transaction", preparedExecution.transactionId());
            prepared.putString("pattern", preparedExecution.patternId());
            BigIntegerNbtCodec.putNonNegative(
                    prepared, "offset", preparedExecution.window().offset(), maximumBits);
            prepared.putLong("executions", preparedExecution.window().executions());
            BigIntegerNbtCodec.putNonNegative(
                    prepared, "remainingAfter", preparedExecution.window().remainingAfter(), maximumBits);
            tag.put("prepared", prepared);
        }

        ListTag taskList = new ListTag();
        for (var entry : tasks.entrySet()) {
            CompoundTag task = new CompoundTag();
            task.putString("pattern", entry.getKey());
            BigIntegerNbtCodec.putNonNegative(task, "total", entry.getValue().total(), maximumBits);
            BigIntegerNbtCodec.putNonNegative(task, "completed", entry.getValue().completed(), maximumBits);
            taskList.add(task);
        }
        tag.put("tasks", taskList);
        tag.put("waitingFor", writeCounts(waitingFor.snapshot(), codec, maximumBits));
        return tag;
    }

    public static <K> BigCraftingJob<K> load(
            CompoundTag tag,
            BigCraftingKeyCodec<K> codec,
            int maximumBits) {
        Objects.requireNonNull(tag, "tag");
        Objects.requireNonNull(codec, "codec");
        int schema = tag.getInt("schema");
        if ((schema < 1 || schema > SCHEMA_VERSION) || !tag.hasUUID("id")) {
            throw new IllegalArgumentException("unsupported BigInteger crafting job schema " + schema);
        }
        Map<String, BigCraftingTaskProgress> tasks = new LinkedHashMap<>();
        ListTag taskList = requireCompoundList(tag, "tasks");
        if (taskList.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("saved BigInteger crafting job has too many tasks");
        }
        for (int index = 0; index < taskList.size(); index++) {
            CompoundTag task = taskList.getCompound(index);
            String pattern = task.getString("pattern");
            if (pattern.isBlank() || tasks.putIfAbsent(
                    pattern,
                    new BigCraftingTaskProgress(
                            BigIntegerNbtCodec.getNonNegative(task, "total", maximumBits),
                            BigIntegerNbtCodec.getNonNegative(task, "completed", maximumBits))) != null) {
                throw new IllegalArgumentException("invalid or duplicate BigInteger pattern task");
            }
        }
        K requestedKey = Objects.requireNonNull(codec.decode(tag.getCompound("requestedKey")), "requestedKey");
        State state = parseState(tag.getString("state"));
        PreparedExecution prepared = schema >= 2 ? readPrepared(tag, maximumBits) : null;
        validatePrepared(tasks, prepared);
        Map<K, BigInteger> waitingFor = readCounts(tag, "waitingFor", codec, maximumBits);
        validateLoadedState(tasks, waitingFor, state, prepared);
        return new BigCraftingJob<>(
                tag.getUUID("id"),
                requestedKey,
                BigIntegerNbtCodec.getNonNegative(tag, "requestedAmount", maximumBits),
                BigIntegerNbtCodec.getNonNegative(tag, "reservedCapacity", maximumBits),
                tasks,
                new BigCraftingInventory<>(waitingFor),
                state,
                prepared,
                schema >= 3 ? tag.getLong("patternGeneration") : -1L,
                schema >= 3 ? tag.getLong("recipeGeneration") : -1L);
    }

    public UUID id() {
        return id;
    }

    public K requestedKey() {
        return requestedKey;
    }

    public BigInteger requestedAmount() {
        return requestedAmount;
    }

    public BigInteger reservedCapacity() {
        return reservedCapacity;
    }

    public long patternGeneration() {
        return patternGeneration;
    }

    public long recipeGeneration() {
        return recipeGeneration;
    }

    public synchronized State state() {
        return state;
    }

    public synchronized Map<String, BigInteger> remainingTasks() {
        Map<String, BigInteger> result = new LinkedHashMap<>();
        tasks.forEach((pattern, progress) -> {
            if (!progress.isComplete()) {
                result.put(pattern, progress.remaining());
            }
        });
        return Map.copyOf(result);
    }

    public synchronized boolean hasRemainingTasks() {
        return remainingTaskTypes > 0;
    }

    public synchronized boolean isRootWindowed() {
        return tasks.size() == 1 && tasks.containsKey(ROOT_WINDOW_TASK_ID);
    }

    public synchronized BigInteger remainingExecutionTotal() {
        return remainingExecutionTotal;
    }

    public synchronized String nextRunnableTaskId() {
        for (var entry : tasks.entrySet()) {
            if (!entry.getValue().isComplete()) {
                return entry.getKey();
            }
        }
        return null;
    }

    public synchronized PreparedExecution preparedExecution() {
        return preparedExecution;
    }

    public synchronized boolean hasPreparedExecution() {
        return preparedExecution != null;
    }

    public Map<K, BigInteger> waitingFor() {
        return waitingFor.snapshot();
    }

    public BigInteger waitingTotal() {
        return waitingFor.totalAmount();
    }

    public synchronized boolean terminal() {
        return state == State.COMPLETE || state == State.CANCELLED || state == State.QUARANTINED;
    }

    public synchronized boolean releasable() {
        return state == State.COMPLETE || state == State.CANCELLED;
    }

    public synchronized StatusSnapshot<K> statusSnapshot() {
        return new StatusSnapshot<>(
                id,
                requestedKey,
                requestedAmount,
                reservedCapacity,
                state,
                remainingTasks(),
                waitingFor.snapshot(),
                preparedExecution != null);
    }

    public synchronized CompactStatusSnapshot<K> compactStatusSnapshot() {
        return new CompactStatusSnapshot<>(
                id,
                requestedKey,
                requestedAmount,
                reservedCapacity,
                state,
                remainingExecutionTotal,
                waitingFor.totalAmount(),
                remainingTaskTypes,
                waitingFor.distinctKeys(),
                preparedExecution != null);
    }

    public synchronized long estimatedCountBytes() {
        return Math.addExact(fixedAndTaskCountBytes, waitingFor.encodedCountBytes());
    }

    public synchronized long estimatedCountBytesAfterCommit(
            long acceptedExecutions,
            Map<K, BigInteger> outputs) {
        if (acceptedExecutions <= 0L) {
            throw new IllegalArgumentException("accepted executions must be positive");
        }
        PreparedExecution prepared = preparedExecution;
        if (prepared == null || acceptedExecutions > prepared.window().executions()) {
            throw new IllegalStateException("accepted executions are outside the prepared window");
        }
        BigCraftingTaskProgress task = requireTask(prepared.patternId());
        BigInteger completed = task.completed();
        BigInteger projectedCompleted = completed.add(BigInteger.valueOf(acceptedExecutions));
        if (projectedCompleted.compareTo(task.total()) > 0) {
            throw new IllegalStateException("projected task progress exceeds its total");
        }
        long projectedTaskBytes = replaceEncodedBytes(
                fixedAndTaskCountBytes, completed, projectedCompleted);
        long projectedWaitingBytes = waitingFor.projectedEncodedCountBytesAfterInserts(
                validateOutputs(outputs));
        return Math.addExact(projectedTaskBytes, projectedWaitingBytes);
    }

    public synchronized void validateProjectedWaiting(
            Map<K, BigInteger> outputs,
            int maximumBits) {
        Map<K, BigInteger> checked = validateOutputs(outputs);
        for (var output : checked.entrySet()) {
            BigCountMath.requireMaximumBits(
                    waitingFor.projectedAmountAfterInsert(output.getKey(), output.getValue()),
                    "projected waiting output",
                    maximumBits);
        }
        BigCountMath.requireMaximumBits(
                waitingFor.projectedTotalAmountAfterInserts(checked),
                "projected waiting output total",
                maximumBits);
    }

    private synchronized void ensureRunnable() {
        if (state != State.PLANNED && state != State.RUNNING) {
            throw new IllegalStateException("BigInteger job is not runnable in state " + state);
        }
    }

    private BigCraftingTaskProgress requireTask(String patternId) {
        BigCraftingTaskProgress task = tasks.get(Objects.requireNonNull(patternId, "patternId"));
        if (task == null) {
            throw new IllegalArgumentException("unknown BigInteger pattern task " + patternId);
        }
        return task;
    }

    private PreparedExecution requirePrepared(UUID transactionId) {
        Objects.requireNonNull(transactionId, "transactionId");
        if (preparedExecution == null || !preparedExecution.transactionId().equals(transactionId)) {
            throw new IllegalStateException("unknown, stale, or replayed BigInteger execution transaction");
        }
        return preparedExecution;
    }

    private static <K> Map<K, BigInteger> validateOutputs(Map<K, BigInteger> outputs) {
        Objects.requireNonNull(outputs, "expectedOutputs");
        Map<K, BigInteger> result = new LinkedHashMap<>();
        outputs.forEach((key, amount) -> {
            Objects.requireNonNull(key, "expected output key");
            BigCountMath.requireNonNegative(amount, "expected output amount");
            if (amount.signum() != 0) {
                result.put(key, amount);
            }
        });
        return Map.copyOf(result);
    }

    private static long replaceEncodedBytes(
            long current,
            BigInteger previous,
            BigInteger replacement) {
        return Math.addExact(
                Math.subtractExact(current, BigCountMath.encodedBytes(previous)),
                BigCountMath.encodedBytes(replacement));
    }

    private static State parseState(String name) {
        try {
            return State.valueOf(name);
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("invalid BigInteger crafting job state " + name, failure);
        }
    }

    private static PreparedExecution readPrepared(CompoundTag owner, int maximumBits) {
        if (!owner.contains("prepared")) {
            return null;
        }
        if (!owner.contains("prepared", Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("invalid BigInteger prepared execution");
        }
        CompoundTag tag = owner.getCompound("prepared");
        if (!tag.hasUUID("transaction")) {
            throw new IllegalArgumentException("prepared execution is missing its transaction id");
        }
        String pattern = tag.getString("pattern");
        long executions = tag.getLong("executions");
        if (pattern.isBlank()
                || executions <= 0L
                || executions > MAX_EXECUTIONS_PER_WINDOW) {
            throw new IllegalArgumentException("invalid BigInteger prepared execution window");
        }
        return new PreparedExecution(
                tag.getUUID("transaction"),
                pattern,
                new BigExecutionWindow(
                        BigIntegerNbtCodec.getNonNegative(tag, "offset", maximumBits),
                        executions,
                        BigIntegerNbtCodec.getNonNegative(tag, "remainingAfter", maximumBits)));
    }

    private static void validatePrepared(
            Map<String, BigCraftingTaskProgress> tasks,
            PreparedExecution prepared) {
        if (prepared == null) {
            return;
        }
        BigCraftingTaskProgress task = tasks.get(prepared.patternId());
        if (task == null || !task.completed().equals(prepared.window().offset())) {
            throw new IllegalArgumentException("prepared execution does not match its task progress");
        }
        BigInteger executions = BigInteger.valueOf(prepared.window().executions());
        BigInteger expectedRemaining = task.total()
                .subtract(prepared.window().offset())
                .subtract(executions);
        if (expectedRemaining.signum() < 0
                || !expectedRemaining.equals(prepared.window().remainingAfter())) {
            throw new IllegalArgumentException("prepared execution has inconsistent bounds");
        }
    }

    private static <K> void validateLoadedState(
            Map<String, BigCraftingTaskProgress> tasks,
            Map<K, BigInteger> waitingFor,
            State state,
            PreparedExecution prepared) {
        boolean tasksComplete = tasks.values().stream().allMatch(BigCraftingTaskProgress::isComplete);
        if (prepared != null && state != State.RUNNING && state != State.QUARANTINED) {
            throw new IllegalArgumentException("prepared execution exists in incompatible state " + state);
        }
        if (state == State.PLANNED && (prepared != null
                || tasks.values().stream().anyMatch(task -> task.completed().signum() != 0))) {
            throw new IllegalArgumentException("planned job already contains execution progress");
        }
        if (state == State.WAITING_FOR_OUTPUTS && (!tasksComplete || waitingFor.isEmpty())) {
            throw new IllegalArgumentException("waiting job has inconsistent task or output state");
        }
        if (state == State.COMPLETE && (!tasksComplete || !waitingFor.isEmpty() || prepared != null)) {
            throw new IllegalArgumentException("complete job has unfinished state");
        }
    }

    private static Map<String, BigCraftingTaskProgress> newTasks(Map<String, BigInteger> source) {
        Objects.requireNonNull(source, "patternExecutions");
        Map<String, BigCraftingTaskProgress> result = new LinkedHashMap<>();
        source.forEach((id, amount) -> {
            if (id == null || id.isBlank() || result.putIfAbsent(id, new BigCraftingTaskProgress(positive(amount, id))) != null) {
                throw new IllegalArgumentException("invalid or duplicate BigInteger pattern task");
            }
        });
        return result;
    }

    private static BigInteger positive(BigInteger value, String name) {
        BigCountMath.requireNonNegative(value, name);
        if (value.signum() == 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static <K> ListTag writeCounts(
            Map<K, BigInteger> counts,
            BigCraftingKeyCodec<K> codec,
            int maximumBits) {
        if (counts.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("too many BigInteger count entries");
        }
        ListTag result = new ListTag();
        counts.forEach((key, amount) -> {
            CompoundTag entry = new CompoundTag();
            entry.put("key", codec.encode(key));
            BigIntegerNbtCodec.putNonNegative(entry, "amount", amount, maximumBits);
            result.add(entry);
        });
        return result;
    }

    private static <K> Map<K, BigInteger> readCounts(
            CompoundTag owner,
            String name,
            BigCraftingKeyCodec<K> codec,
            int maximumBits) {
        ListTag list = requireCompoundList(owner, name);
        if (list.size() > MAX_ENTRIES) {
            throw new IllegalArgumentException("saved BigInteger count list is oversized");
        }
        Map<K, BigInteger> result = new LinkedHashMap<>();
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entry = list.getCompound(index);
            K key = Objects.requireNonNull(codec.decode(entry.getCompound("key")), "decoded key");
            BigInteger amount = BigIntegerNbtCodec.getNonNegative(entry, "amount", maximumBits);
            if (amount.signum() == 0 || result.putIfAbsent(key, amount) != null) {
                throw new IllegalArgumentException("invalid or duplicate BigInteger count entry");
            }
        }
        return Map.copyOf(result);
    }

    private static ListTag requireCompoundList(CompoundTag owner, String name) {
        if (!owner.contains(name, Tag.TAG_LIST)) {
            throw new IllegalArgumentException("missing BigInteger list " + name);
        }
        ListTag list = owner.getList(name, Tag.TAG_COMPOUND);
        Tag raw = owner.get(name);
        if (!(raw instanceof ListTag rawList)
                || (!rawList.isEmpty() && rawList.getElementType() != Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException("invalid BigInteger list " + name);
        }
        return list;
    }

    public enum State {
        PLANNED,
        RUNNING,
        WAITING_FOR_OUTPUTS,
        COMPLETE,
        CANCELLED,
        QUARANTINED
    }

    public record PreparedExecution(
            UUID transactionId,
            String patternId,
            BigExecutionWindow window) {
        public PreparedExecution {
            Objects.requireNonNull(transactionId, "transactionId");
            if (Objects.requireNonNull(patternId, "patternId").isBlank()) {
                throw new IllegalArgumentException("patternId must not be blank");
            }
            Objects.requireNonNull(window, "window");
        }
    }

    public record StatusSnapshot<K>(
            UUID id,
            K requestedKey,
            BigInteger requestedAmount,
            BigInteger reservedCapacity,
            State state,
            Map<String, BigInteger> remainingTasks,
            Map<K, BigInteger> waitingFor,
            boolean executionPrepared) {
        public StatusSnapshot {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(requestedKey, "requestedKey");
            Objects.requireNonNull(requestedAmount, "requestedAmount");
            Objects.requireNonNull(reservedCapacity, "reservedCapacity");
            Objects.requireNonNull(state, "state");
            remainingTasks = Map.copyOf(Objects.requireNonNull(remainingTasks, "remainingTasks"));
            waitingFor = Map.copyOf(Objects.requireNonNull(waitingFor, "waitingFor"));
        }
    }

    public record CompactStatusSnapshot<K>(
            UUID id,
            K requestedKey,
            BigInteger requestedAmount,
            BigInteger reservedCapacity,
            State state,
            BigInteger remainingExecutions,
            BigInteger waitingAmount,
            int remainingTaskTypes,
            int waitingTypes,
            boolean executionPrepared) {
        public CompactStatusSnapshot {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(requestedKey, "requestedKey");
            Objects.requireNonNull(requestedAmount, "requestedAmount");
            Objects.requireNonNull(reservedCapacity, "reservedCapacity");
            Objects.requireNonNull(state, "state");
            Objects.requireNonNull(remainingExecutions, "remainingExecutions");
            Objects.requireNonNull(waitingAmount, "waitingAmount");
            if (remainingTaskTypes < 0 || waitingTypes < 0) {
                throw new IllegalArgumentException("status type counts must not be negative");
            }
        }
    }
}
