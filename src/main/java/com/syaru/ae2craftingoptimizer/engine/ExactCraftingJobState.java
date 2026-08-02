package com.syaru.ae2craftingoptimizer.engine;

import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * Advanced AEの実ExecutingCraftingJobへ付随するBigInteger Sidecar。
 *
 * <p>CPU容量だけでなく、Pattern task、waitingFor、remainingOutput、物理Receiptを同じJob NBTへ
 * 保存する。別の親Job台帳をクラフト完了の正本にはしない。</p>
 */
public final class ExactCraftingJobState {
    public static final int SCHEMA_VERSION = 2;
    private static final int MAXIMUM_ENTRIES = 65_536;
    private static final int MAXIMUM_METADATA_LENGTH = 256;

    private final AEKey requestedKey;
    private final BigInteger reservedBytes;
    private final Map<AEKey, BigInteger> plannedInventory;
    private final long patternGeneration;
    private final long recipeGeneration;
    private final String planningEpoch;
    private final String programFingerprint;
    private final ExactCraftingJobLedger<AEItemKey, AEKey> ledger;
    private CompoundTag physicalExecution;
    private boolean cancellationRequested;
    private boolean quarantined;

    private ExactCraftingJobState(
            AEKey requestedKey,
            BigInteger reservedBytes,
            Map<AEKey, BigInteger> plannedInventory,
            long patternGeneration,
            long recipeGeneration,
            String planningEpoch,
            String programFingerprint,
            ExactCraftingJobLedger<AEItemKey, AEKey> ledger,
            CompoundTag physicalExecution,
            boolean cancellationRequested,
            boolean quarantined) {
        this.requestedKey = Objects.requireNonNull(requestedKey, "requestedKey");
        this.reservedBytes = nonNegative(reservedBytes, "reservedBytes");
        this.plannedInventory = checkedCounts(
                plannedInventory,
                "plannedInventory");
        // 世代値の負数は未初期化または破損状態なので、実グラフへ接続しない。
        if (patternGeneration < 0L || recipeGeneration < 0L) {
            throw new IllegalArgumentException(
                    "exact job generations must not be negative");
        }
        this.patternGeneration = patternGeneration;
        this.recipeGeneration = recipeGeneration;
        this.planningEpoch = checkedMetadata(planningEpoch, "planningEpoch");
        this.programFingerprint =
                checkedMetadata(programFingerprint, "programFingerprint");
        this.ledger = Objects.requireNonNull(ledger, "ledger");
        this.physicalExecution =
                Objects.requireNonNull(physicalExecution, "physicalExecution").copy();
        this.cancellationRequested = cancellationRequested;
        this.quarantined = quarantined;
    }

    public static ExactCraftingJobState fromPlan(BigIntegerCraftingPlan plan) {
        Objects.requireNonNull(plan, "plan");
        Map<AEItemKey, BigInteger> tasks = new LinkedHashMap<>();
        plan.exactPatternTimes().forEach((pattern, amount) -> {
            AEItemKey definition = Objects.requireNonNull(
                    pattern.getDefinition(),
                    "pattern definition");
            /*
             * 実JobのTaskProgressと永続NBTを一対一で結ぶため、同じEncoded Pattern定義を
             * 複数の別Patternとして扱う計画は曖昧なまま合算しない。
             */
            if (tasks.putIfAbsent(definition, amount) != null) {
                throw new IllegalArgumentException(
                        "exact crafting plan contains duplicate pattern definitions");
            }
        });
        var prepared = plan.preparedRoot();
        var parentJob = prepared.job();
        return new ExactCraftingJobState(
                plan.finalOutput().what(),
                plan.exactBytes(),
                plan.exactPlan().usedInventory(),
                prepared.patternGeneration(),
                prepared.recipeGeneration(),
                parentJob.planningEpoch(),
                parentJob.programFingerprint(),
                ExactCraftingJobLedger.planned(
                        tasks,
                        plan.exactPlan().emitted(),
                        plan.exactPlan().requestedAmount()),
                new CompoundTag(),
                false,
                false);
    }

    public static ExactCraftingJobState load(
            CompoundTag owner,
            int maximumBits) {
        Objects.requireNonNull(owner, "owner");
        // 未対応schemaを推測変換せず、重複実行を防ぐため復元を拒否する。
        if (owner.getInt("schema") != SCHEMA_VERSION) {
            throw new IllegalArgumentException(
                    "unsupported exact crafting-job schema");
        }
        AEKey requestedKey = AEKey.fromTagGeneric(com.syaru.ae2craftingoptimizer.lifecycle.ACORegistryAccess.require(),
                owner.getCompound("requestedKey"));
        // 登録解除された出力キーを別アイテムへ置換せず、Job復元を止める。
        if (requestedKey == null) {
            throw new IllegalArgumentException(
                    "exact crafting job contains an unknown requested key");
        }
        Map<AEItemKey, BigInteger> totals =
                readPatternCounts(owner, "taskTotals", maximumBits);
        Map<AEItemKey, BigInteger> dispatched =
                readPatternCounts(owner, "dispatchedTasks", maximumBits);
        CompoundTag physicalExecution = owner.contains(
                        "physicalExecution",
                        Tag.TAG_COMPOUND)
                ? owner.getCompound("physicalExecution")
                : new CompoundTag();
        return new ExactCraftingJobState(
                requestedKey,
                BigIntegerNbtCodec.getNonNegative(
                        owner,
                        "reservedBytes",
                        maximumBits),
                readKeyCounts(
                        owner,
                        "plannedInventory",
                        maximumBits),
                owner.getLong("patternGeneration"),
                owner.getLong("recipeGeneration"),
                owner.getString("planningEpoch"),
                owner.getString("programFingerprint"),
                new ExactCraftingJobLedger<>(
                        totals,
                        readKeyCounts(
                                owner,
                                "initialWaiting",
                                maximumBits),
                        dispatched,
                        readKeyCounts(
                                owner,
                                "introducedOutputs",
                                maximumBits),
                        readKeyCounts(
                                owner,
                                "creditedOutputs",
                                maximumBits),
                        BigIntegerNbtCodec.getNonNegative(
                                owner,
                                "requestedAmount",
                                maximumBits),
                        BigIntegerNbtCodec.getNonNegative(
                                owner,
                                "remainingOutput",
                                maximumBits)),
                physicalExecution,
                owner.getBoolean("cancellationRequested"),
                owner.getBoolean("quarantined"));
    }

    public synchronized CompoundTag save(int maximumBits) {
        CompoundTag owner = new CompoundTag();
        owner.putInt("schema", SCHEMA_VERSION);
        owner.put("requestedKey", requestedKey.toTagGeneric(com.syaru.ae2craftingoptimizer.lifecycle.ACORegistryAccess.require()));
        BigIntegerNbtCodec.putNonNegative(
                owner,
                "reservedBytes",
                reservedBytes,
                maximumBits);
        BigIntegerNbtCodec.putNonNegative(
                owner,
                "requestedAmount",
                ledger.requestedAmount(),
                maximumBits);
        BigIntegerNbtCodec.putNonNegative(
                owner,
                "remainingOutput",
                ledger.remainingOutput(),
                maximumBits);
        owner.put(
                "plannedInventory",
                writeKeyCounts(plannedInventory, maximumBits));
        owner.putLong("patternGeneration", patternGeneration);
        owner.putLong("recipeGeneration", recipeGeneration);
        owner.putString("planningEpoch", planningEpoch);
        owner.putString("programFingerprint", programFingerprint);
        owner.put(
                "taskTotals",
                writePatternCounts(ledger.taskTotals(), maximumBits));
        owner.put(
                "dispatchedTasks",
                writePatternCounts(ledger.dispatchedTasks(), maximumBits));
        owner.put(
                "initialWaiting",
                writeKeyCounts(ledger.initialWaiting(), maximumBits));
        owner.put(
                "introducedOutputs",
                writeKeyCounts(ledger.introducedOutputs(), maximumBits));
        owner.put(
                "creditedOutputs",
                writeKeyCounts(ledger.creditedOutputs(), maximumBits));
        // 空Compoundは未開始を表す。開始後だけ保存して旧Jobとの区別を明確にする。
        if (!physicalExecution.isEmpty()) {
            owner.put("physicalExecution", physicalExecution.copy());
        }
        // 取消要求済みの場合だけフラグを保存し、既定falseのNBTを増やさない。
        if (cancellationRequested) {
            owner.putBoolean("cancellationRequested", true);
        }
        // 隔離済みの場合だけフラグを保存し、自動再実行を確実に止める。
        if (quarantined) {
            owner.putBoolean("quarantined", true);
        }
        return owner;
    }

    public synchronized void reconcile(
            Map<AEItemKey, BigInteger> dispatchedTasks,
            Map<AEKey, BigInteger> introducedOutputs,
            Map<AEKey, BigInteger> creditedOutputs,
            BigInteger remainingOutput) {
        ledger.reconcile(
                dispatchedTasks,
                introducedOutputs,
                creditedOutputs,
                remainingOutput);
    }

    /**
     * Advanced AE実Jobの現在値と永続Receipt Journalが完全一致するか確認する。
     *
     * <p>TaskProgressとwaitingForが実行時会計の正本であり、Journal側の値で補正しない。</p>
     */
    public synchronized void verifyRuntimeCounters(
            Map<AEItemKey, BigInteger> remainingTasks,
            Map<AEKey, BigInteger> waitingFor,
            BigInteger remainingOutput) {
        Map<AEItemKey, BigInteger> checkedTasks =
                checkedCounts(remainingTasks, "remainingTasks");
        Map<AEKey, BigInteger> checkedWaiting =
                checkedCounts(waitingFor, "waitingFor");
        BigInteger checkedRemaining =
                nonNegative(remainingOutput, "remainingOutput");
        // 保存するReceipt Journalと実Jobカウンタのいずれか一つでも違えば、推測で保存しない。
        if (!checkedTasks.equals(ledger.remainingTasks())
                || !checkedWaiting.equals(ledger.waitingFor())
                || !checkedRemaining.equals(ledger.remainingOutput())) {
            throw new IllegalStateException(
                    "Advanced AE exact runtime counters diverged from their receipt journal");
        }
    }

    public synchronized void beginPhysicalExecution(CompoundTag state) {
        Objects.requireNonNull(state, "state");
        // 二つ目の物理Transactionを同じ実Jobへ所有させず、二重入力を防ぐ。
        if (!physicalExecution.isEmpty()) {
            throw new IllegalStateException(
                    "exact crafting job already owns a physical execution");
        }
        physicalExecution = state.copy();
    }

    public synchronized void updatePhysicalExecution(CompoundTag state) {
        Objects.requireNonNull(state, "state");
        // 開始Receiptなしの更新は別Jobの状態混入として拒否する。
        if (physicalExecution.isEmpty()) {
            throw new IllegalStateException(
                    "exact crafting job has no physical execution");
        }
        physicalExecution = state.copy();
    }

    public synchronized boolean hasPhysicalExecution() {
        return !physicalExecution.isEmpty();
    }

    public synchronized CompoundTag physicalExecution() {
        return physicalExecution.copy();
    }

    public synchronized void requestCancellation() {
        cancellationRequested = true;
    }

    public synchronized boolean cancellationRequested() {
        return cancellationRequested;
    }

    public synchronized void quarantine() {
        quarantined = true;
    }

    public synchronized boolean quarantined() {
        return quarantined;
    }

    public AEKey requestedKey() {
        return requestedKey;
    }

    public BigInteger requestedAmount() {
        return ledger.requestedAmount();
    }

    public BigInteger reservedBytes() {
        return reservedBytes;
    }

    public Map<AEKey, BigInteger> plannedInventory() {
        return plannedInventory;
    }

    public long patternGeneration() {
        return patternGeneration;
    }

    public long recipeGeneration() {
        return recipeGeneration;
    }

    public String planningEpoch() {
        return planningEpoch;
    }

    public String programFingerprint() {
        return programFingerprint;
    }

    public Map<AEItemKey, BigInteger> remainingTasks() {
        return ledger.remainingTasks();
    }

    public Map<AEItemKey, BigInteger> taskTotals() {
        return ledger.taskTotals();
    }

    public Map<AEItemKey, BigInteger> dispatchedTasks() {
        return ledger.dispatchedTasks();
    }

    public Map<AEKey, BigInteger> initialWaiting() {
        return ledger.initialWaiting();
    }

    public Map<AEKey, BigInteger> introducedOutputs() {
        return ledger.introducedOutputs();
    }

    public Map<AEKey, BigInteger> creditedOutputs() {
        return ledger.creditedOutputs();
    }

    public Map<AEKey, BigInteger> waitingFor() {
        return ledger.waitingFor();
    }

    public BigInteger remainingOutput() {
        return ledger.remainingOutput();
    }

    public boolean completeAndBalanced() {
        return ledger.completeAndBalanced();
    }

    private static ListTag writePatternCounts(
            Map<AEItemKey, BigInteger> counts,
            int maximumBits) {
        checkEntryCount(counts.size());
        ListTag list = new ListTag();
        counts.forEach((key, amount) -> {
            CompoundTag entry = new CompoundTag();
            entry.put("key", key.toTag(com.syaru.ae2craftingoptimizer.lifecycle.ACORegistryAccess.require()));
            BigIntegerNbtCodec.putNonNegative(
                    entry,
                    "amount",
                    amount,
                    maximumBits);
            list.add(entry);
        });
        return list;
    }

    private static Map<AEItemKey, BigInteger> readPatternCounts(
            CompoundTag owner,
            String name,
            int maximumBits) {
        ListTag list = requireCompoundList(owner, name);
        Map<AEItemKey, BigInteger> result = new LinkedHashMap<>();
        // NBT件数は固有Pattern数に比例し、注文数量ぶんのentryは作らない。
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entry = list.getCompound(index);
            AEItemKey key = AEItemKey.fromTag(com.syaru.ae2craftingoptimizer.lifecycle.ACORegistryAccess.require(), entry.getCompound("key"));
            BigInteger amount = BigIntegerNbtCodec.getNonNegative(
                    entry,
                    "amount",
                    maximumBits);
            // 未知キー、0件、重複定義のいずれも一意なTask会計を作れないため拒否する。
            if (key == null
                    || amount.signum() <= 0
                    || result.putIfAbsent(key, amount) != null) {
                throw new IllegalArgumentException(
                        "invalid or duplicate exact crafting task");
            }
        }
        return Map.copyOf(result);
    }

    private static ListTag writeKeyCounts(
            Map<AEKey, BigInteger> counts,
            int maximumBits) {
        checkEntryCount(counts.size());
        ListTag list = new ListTag();
        counts.forEach((key, amount) -> {
            CompoundTag entry = new CompoundTag();
            entry.put("key", key.toTagGeneric(com.syaru.ae2craftingoptimizer.lifecycle.ACORegistryAccess.require()));
            BigIntegerNbtCodec.putNonNegative(
                    entry,
                    "amount",
                    amount,
                    maximumBits);
            list.add(entry);
        });
        return list;
    }

    private static Map<AEKey, BigInteger> readKeyCounts(
            CompoundTag owner,
            String name,
            int maximumBits) {
        ListTag list = requireCompoundList(owner, name);
        Map<AEKey, BigInteger> result = new LinkedHashMap<>();
        // Item、Fluid、Chemicalを同じAEKey汎用タグから損失なく復元する。
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entry = list.getCompound(index);
            AEKey key = AEKey.fromTagGeneric(com.syaru.ae2craftingoptimizer.lifecycle.ACORegistryAccess.require(), entry.getCompound("key"));
            BigInteger amount = BigIntegerNbtCodec.getNonNegative(
                    entry,
                    "amount",
                    maximumBits);
            // 未知キー、0件、重複キーはwaitingFor正本として曖昧なので拒否する。
            if (key == null
                    || amount.signum() <= 0
                    || result.putIfAbsent(key, amount) != null) {
                throw new IllegalArgumentException(
                        "invalid or duplicate exact waiting output");
            }
        }
        return Map.copyOf(result);
    }

    private static ListTag requireCompoundList(
            CompoundTag owner,
            String name) {
        // 必須一覧が欠落した旧・破損schemaを空一覧として扱わない。
        if (!owner.contains(name, Tag.TAG_LIST)) {
            throw new IllegalArgumentException(
                    "missing exact crafting list " + name);
        }
        ListTag list = owner.getList(name, Tag.TAG_COMPOUND);
        Tag raw = owner.get(name);
        // 空でないListは全要素Compoundだけを許し、異種NBTを読み飛ばさない。
        if (!(raw instanceof ListTag rawList)
                || (!rawList.isEmpty()
                        && rawList.getElementType() != Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException(
                    "invalid exact crafting list " + name);
        }
        checkEntryCount(list.size());
        return list;
    }

    private static void checkEntryCount(int size) {
        // 65,536件は固有キー上限であり、数量に比例する巨大NBTを拒否する。
        if (size < 0 || size > MAXIMUM_ENTRIES) {
            throw new IllegalArgumentException(
                    "exact crafting entry limit exceeded");
        }
    }

    private static String checkedMetadata(String value, String name) {
        String checked = Objects.requireNonNull(value, name).trim();
        // Fingerprintとepochは空文字および256文字超を許さず、保存量を固定する。
        if (checked.isEmpty() || checked.length() > MAXIMUM_METADATA_LENGTH) {
            throw new IllegalArgumentException(
                    name + " is blank or exceeds its length limit");
        }
        return checked;
    }

    private static BigInteger nonNegative(
            BigInteger value,
            String name) {
        BigInteger checked = Objects.requireNonNull(value, name);
        // 容量・在庫・進捗は所有量なので負数を許さない。
        if (checked.signum() < 0) {
            throw new IllegalArgumentException(
                    name + " must not be negative");
        }
        return checked;
    }

    private static <K> Map<K, BigInteger> checkedCounts(
            Map<K, BigInteger> source,
            String name) {
        Objects.requireNonNull(source, name);
        checkEntryCount(source.size());
        Map<K, BigInteger> checked = new LinkedHashMap<>();
        source.forEach((key, amount) -> {
            K checkedKey = Objects.requireNonNull(key, name + " key");
            BigInteger checkedAmount = nonNegative(amount, name + " amount");
            // 0量は保存形式から除き、同じ在庫Snapshotに複数表現を作らない。
            if (checkedAmount.signum() > 0) {
                checked.put(checkedKey, checkedAmount);
            }
        });
        return Map.copyOf(checked);
    }

}
