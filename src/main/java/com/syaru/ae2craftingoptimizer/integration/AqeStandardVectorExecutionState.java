package com.syaru.ae2craftingoptimizer.integration;

import appeng.api.stacks.AEKey;
import com.syaru.ae2craftingoptimizer.api.vector.ExactStack;
import com.syaru.ae2craftingoptimizer.api.vector.PreparedVectorBatch;
import com.syaru.ae2craftingoptimizer.api.vector.PreparedVectorBatchCodec;
import com.syaru.ae2craftingoptimizer.api.vector.VectorResourceMode;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * AdvancedAE標準Jobが所有するExact Vector入力Escrowと再開位置。
 */
final class AqeStandardVectorExecutionState {
    /** AQE標準Job用Receiptの初期保存形式。 */
    private static final int SCHEMA_VERSION = 1;
    /** 数量ではなく、破損NBTが持てるキー・Task件数の防御上限。 */
    private static final int MAXIMUM_LEDGER_KEYS = 65_536;
    /** 外部Executor IDによる過大NBTとログ汚染を防ぐ文字数上限。 */
    private static final int MAXIMUM_EXECUTOR_ID_LENGTH = 512;
    /** 状態説明による過大NBTとログ汚染を防ぐ文字数上限。 */
    private static final int MAXIMUM_DETAIL_LENGTH = 2_048;

    private final PreparedVectorBatch plan;
    private final String executorId;
    private final List<PatternTask> patternTasks;
    private final List<ExactStack> internalOutputs;
    private final Map<AEKey, BigInteger> extractedInputs =
            new LinkedHashMap<>();
    private Phase phase;
    private PendingOperation pendingOperation;
    private int inputCursor;
    private String detail;

    AqeStandardVectorExecutionState(
            PreparedVectorBatch plan,
            String executorId,
            List<PatternTask> patternTasks,
            List<ExactStack> internalOutputs) {
        this(
                plan,
                executorId,
                patternTasks,
                internalOutputs,
                Phase.PREPARED,
                PendingOperation.NONE,
                0,
                Map.of(),
                "prepared AQE standard Exact Vector execution");
    }

    private AqeStandardVectorExecutionState(
            PreparedVectorBatch plan,
            String executorId,
            List<PatternTask> patternTasks,
            List<ExactStack> internalOutputs,
            Phase phase,
            PendingOperation pendingOperation,
            int inputCursor,
            Map<AEKey, BigInteger> extractedInputs,
            String detail) {
        this.plan = Objects.requireNonNull(plan, "plan");
        this.executorId = checkedExecutorId(executorId);
        this.patternTasks = checkedPatternTasks(patternTasks);
        this.internalOutputs =
                checkedStacks(internalOutputs, "internalOutputs");
        this.phase = Objects.requireNonNull(phase, "phase");
        this.pendingOperation =
                Objects.requireNonNull(pendingOperation, "pendingOperation");
        this.inputCursor = inputCursor;
        this.extractedInputs.putAll(
                Objects.requireNonNull(extractedInputs, "extractedInputs"));
        this.detail = checkedDetail(detail);
        validate();
        /*
         * 保存された外部呼出し中状態や会計commit途中は、成否を推測して再実行しない。
         * INPUT/OUTPUTの二重処理より、診断可能な隔離を優先する。
         */
        if (this.pendingOperation != PendingOperation.NONE
                || this.phase == Phase.ACCOUNTING_COMMITTING) {
            quarantine(
                    "server stopped during an AQE standard Exact Vector operation");
        }
    }

    PreparedVectorBatch plan() {
        return plan;
    }

    String executorId() {
        return executorId;
    }

    List<PatternTask> patternTasks() {
        return patternTasks;
    }

    List<ExactStack> internalOutputs() {
        return internalOutputs;
    }

    Phase phase() {
        return phase;
    }

    void phase(Phase replacement, String replacementDetail) {
        phase = Objects.requireNonNull(replacement, "replacement");
        detail = checkedDetail(replacementDetail);
    }

    void quarantine(String reason) {
        pendingOperation = PendingOperation.NONE;
        phase = Phase.QUARANTINED;
        detail = checkedDetail(
                reason == null || reason.isBlank()
                        ? "AQE standard Exact Vector state became uncertain"
                        : reason);
    }

    String detail() {
        return detail;
    }

    PendingOperation pendingOperation() {
        return pendingOperation;
    }

    void pendingOperation(PendingOperation replacement) {
        pendingOperation =
                Objects.requireNonNull(replacement, "replacement");
    }

    boolean inputComplete() {
        return inputCursor >= plan.totalInputs().size();
    }

    ExactStack currentInput() {
        return plan.totalInputs().get(inputCursor);
    }

    void recordExtractedInput(ExactStack input) {
        // 現在Cursor以外の入力をReceiptへ記録すると返却対象が曖昧になるため拒否する。
        if (!input.equals(currentInput())) {
            throw new IllegalStateException(
                    "AQE standard Vector input does not match its cursor");
        }
        extractedInputs.put(input.key(), input.amount());
        inputCursor++;
    }

    Map<AEKey, BigInteger> extractedInputs() {
        return Map.copyOf(extractedInputs);
    }

    void recordRolledBackInput(AEKey key, BigInteger amount) {
        BigInteger owned = extractedInputs.get(
                Objects.requireNonNull(key, "key"));
        // 実際に所有した全量と一致する返却だけをLedgerから外す。
        if (!Objects.requireNonNull(amount, "amount").equals(owned)) {
            throw new IllegalStateException(
                    "AQE standard Vector rollback differs from its input receipt");
        }
        extractedInputs.remove(key);
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema", SCHEMA_VERSION);
        tag.put("plan", PreparedVectorBatchCodec.encode(plan));
        tag.putString("executorId", executorId);
        tag.putString("phase", phase.name());
        tag.putString("pendingOperation", pendingOperation.name());
        tag.putInt("inputCursor", inputCursor);
        tag.putString("detail", detail);

        ListTag tasks = new ListTag();
        // Pattern一件につき一つの残実行数を保存し、数量ぶんのTaskを生成しない。
        for (PatternTask task : patternTasks) {
            CompoundTag entry = new CompoundTag();
            entry.putString("id", task.patternId());
            entry.putLong("executions", task.executions());
            tasks.add(entry);
        }
        tag.put("patternTasks", tasks);
        tag.put(
                "internalOutputs",
                PreparedVectorBatchCodec.encodeStacks(internalOutputs));

        ListTag extracted = new ListTag();
        // 抽出済みAEKeyだけを保存し、未処理入力はplanとcursorから再構築する。
        for (Map.Entry<AEKey, BigInteger> entry :
                extractedInputs.entrySet()) {
            CompoundTag stack = new CompoundTag();
            stack.put("key", entry.getKey().toTagGeneric());
            PreparedVectorBatchCodec.putNonNegative(
                    stack,
                    "amount",
                    entry.getValue());
            extracted.add(stack);
        }
        tag.put("extractedInputs", extracted);
        return tag;
    }

    static AqeStandardVectorExecutionState load(CompoundTag tag) {
        Objects.requireNonNull(tag, "tag");
        // 未知schemaや計画を欠くReceiptは自動推測で復旧しない。
        if (tag.getInt("schema") != SCHEMA_VERSION
                || !tag.contains("plan", Tag.TAG_COMPOUND)) {
            throw new IllegalArgumentException(
                    "unsupported AQE standard Exact Vector state schema");
        }
        Phase phase;
        PendingOperation pending;
        try {
            phase = Phase.valueOf(tag.getString("phase"));
            pending = PendingOperation.valueOf(
                    tag.getString("pendingOperation"));
        } catch (IllegalArgumentException invalidEnum) {
            throw new IllegalArgumentException(
                    "invalid AQE standard Exact Vector state",
                    invalidEnum);
        }
        return new AqeStandardVectorExecutionState(
                PreparedVectorBatchCodec.decode(tag.getCompound("plan")),
                tag.getString("executorId"),
                readPatternTasks(tag),
                PreparedVectorBatchCodec.decodeStacks(
                        tag,
                        "internalOutputs"),
                phase,
                pending,
                tag.getInt("inputCursor"),
                readExtractedInputs(tag),
                tag.getString("detail"));
    }

    private void validate() {
        // 標準AQE経路はCPU Escrow専用であり、Cursorとキー数も保存境界内に制限する。
        if (plan.resourceMode() != VectorResourceMode.HOST_ESCROWED
                || inputCursor < 0
                || inputCursor > plan.totalInputs().size()
                || extractedInputs.size() > MAXIMUM_LEDGER_KEYS) {
            throw new IllegalArgumentException(
                    "malformed AQE standard Exact Vector state");
        }
        // PREPAREDはまだ一つも入力を所有していない状態に限定する。
        if (phase == Phase.PREPARED
                && inputCursor != 0) {
            throw new IllegalArgumentException(
                    "prepared AQE standard Vector state already owns input");
        }
        // 外部設備へ渡した後の状態は、全入力所有が確定済みでなければならない。
        if ((phase == Phase.INPUTS_ESCROWED
                        || phase == Phase.EXECUTOR_ACTIVE
                        || phase == Phase.ACCOUNTING
                        || phase == Phase.ACCOUNTING_COMMITTING)
                && !inputComplete()) {
            throw new IllegalArgumentException(
                    "AQE standard Vector state advanced without all inputs");
        }
        Set<String> plannedIds =
                new LinkedHashSet<>(plan.requiredPatternIds());
        Set<String> savedIds = new LinkedHashSet<>();
        // Task一覧とplan所有権一覧が一対一でなければ、再起動後の会計対象を特定できない。
        for (PatternTask task : patternTasks) {
            savedIds.add(task.patternId());
        }
        if (!plannedIds.equals(savedIds)
                || savedIds.size() != patternTasks.size()) {
            throw new IllegalArgumentException(
                    "AQE standard Vector task receipt differs from its plan");
        }
        // 隔離状態以外は、cursor以前の全入力を正確に所有している必要がある。
        if (phase != Phase.QUARANTINED) {
            // Cursorより前の各キーが計画どおりの全量で保存されているか照合する。
            for (int index = 0; index < inputCursor; index++) {
                ExactStack expected = plan.totalInputs().get(index);
                if (!expected.amount().equals(
                        extractedInputs.get(expected.key()))) {
                    throw new IllegalArgumentException(
                            "AQE standard Vector input ledger differs from its cursor");
                }
            }
            if (extractedInputs.size() != inputCursor) {
                throw new IllegalArgumentException(
                        "AQE standard Vector input ledger contains an unowned key");
            }
        }
    }

    private static List<PatternTask> readPatternTasks(
            CompoundTag owner) {
        ListTag list = checkedCompoundList(owner, "patternTasks");
        List<PatternTask> result = new ArrayList<>(list.size());
        // 保存Taskを一件ずつ復号し、要求数量とは無関係な件数に保つ。
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entry = list.getCompound(index);
            result.add(new PatternTask(
                    entry.getString("id"),
                    entry.getLong("executions")));
        }
        return List.copyOf(result);
    }

    private static Map<AEKey, BigInteger> readExtractedInputs(
            CompoundTag owner) {
        ListTag list = checkedCompoundList(owner, "extractedInputs");
        Map<AEKey, BigInteger> result = new LinkedHashMap<>();
        // Receipt一件につき一つのAEKeyを読み、同じキーの重複保存を拒否する。
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entry = list.getCompound(index);
            AEKey key = Objects.requireNonNull(
                    AEKey.fromTagGeneric(entry.getCompound("key")),
                    "decoded AQE standard Vector input");
            BigInteger amount =
                    PreparedVectorBatchCodec.readNonNegative(
                            entry,
                            "amount");
            if (amount.signum() == 0
                    || result.putIfAbsent(key, amount) != null) {
                throw new IllegalArgumentException(
                        "duplicate or empty AQE standard Vector input");
            }
        }
        return Map.copyOf(result);
    }

    private static ListTag checkedCompoundList(
            CompoundTag owner,
            String name) {
        Tag raw = owner.get(name);
        // Compound以外の要素と過大な件数は、個別復号前に拒否する。
        if (!(raw instanceof ListTag list)
                || (!list.isEmpty()
                        && list.getElementType() != Tag.TAG_COMPOUND)
                || list.size() > MAXIMUM_LEDGER_KEYS) {
            throw new IllegalArgumentException(
                    "invalid AQE standard Vector list " + name);
        }
        return owner.getList(name, Tag.TAG_COMPOUND);
    }

    private static List<PatternTask> checkedPatternTasks(
            List<PatternTask> source) {
        List<PatternTask> copy =
                List.copyOf(Objects.requireNonNull(source, "patternTasks"));
        Set<String> ids = new LinkedHashSet<>();
        // 一つのPattern IDに複数の残数を割り当てる曖昧なReceiptを拒否する。
        for (PatternTask task : copy) {
            if (!ids.add(
                    Objects.requireNonNull(task, "patternTask").patternId())) {
                throw new IllegalArgumentException(
                        "duplicate AQE standard Vector pattern task");
            }
        }
        return copy;
    }

    private static List<ExactStack> checkedStacks(
            List<ExactStack> source,
            String name) {
        List<ExactStack> copy =
                List.copyOf(Objects.requireNonNull(source, name));
        Set<AEKey> keys = new LinkedHashSet<>();
        // 内部出力もAEKey一件へ集約済みであることを保存前に確認する。
        for (ExactStack stack : copy) {
            if (!keys.add(Objects.requireNonNull(stack, name + " entry").key())) {
                throw new IllegalArgumentException(
                        name + " contains a duplicate key");
            }
        }
        return copy;
    }

    private static String checkedExecutorId(String value) {
        String checked = Objects.requireNonNull(value, "executorId").trim();
        // Executorを一意に再発見できない空IDと過大IDを保存しない。
        if (checked.isEmpty()
                || checked.length() > MAXIMUM_EXECUTOR_ID_LENGTH) {
            throw new IllegalArgumentException(
                    "AQE standard Vector executor ID is invalid");
        }
        return checked;
    }

    private static String checkedDetail(String value) {
        String checked = Objects.requireNonNull(value, "detail");
        // 状態説明は診断用途だけなので固定文字数を越えさせない。
        if (checked.length() > MAXIMUM_DETAIL_LENGTH) {
            throw new IllegalArgumentException(
                    "AQE standard Vector detail is oversized");
        }
        return checked;
    }

    record PatternTask(String patternId, long executions) {
        PatternTask {
            patternId = Objects.requireNonNull(patternId, "patternId").trim();
            // 空IDや実行0のTaskは完了会計の対象を特定できないため拒否する。
            if (patternId.isEmpty() || executions <= 0L) {
                throw new IllegalArgumentException(
                        "invalid AQE standard Vector task receipt");
            }
        }
    }

    enum Phase {
        PREPARED,
        INPUTS_EXTRACTING,
        INPUTS_ESCROWED,
        EXECUTOR_ACTIVE,
        ACCOUNTING,
        ACCOUNTING_COMMITTING,
        QUARANTINED
    }

    enum PendingOperation {
        NONE,
        INPUT_EXTRACT,
        INPUT_ROLLBACK,
        OUTPUT_ACCOUNTING
    }
}
