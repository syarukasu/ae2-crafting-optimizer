package com.syaru.ae2craftingoptimizer.api.vector;

import appeng.api.stacks.AEKey;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * ACOと外部Executorが共有するExact Vector計画のNBT Codec。
 *
 * <p>旧AAC試験Receiptのschema 1は、従来どおりNETWORK_STORAGEとして読み込む。</p>
 */
public final class PreparedVectorBatchCodec {
    /**
     * 物理設備が正本となる時間・電力・冷却材の予測値を計画から除いた現在形式。
     */
    private static final int SCHEMA_VERSION = 5;
    /** Planner選択済みslot入力を実作業台Stepへ追加した旧保存形式。 */
    private static final int SELECTED_INPUTS_SCHEMA_VERSION = 4;
    /** 実作業台Step列はあるが、選択済みslot入力を持たない旧形式。 */
    private static final int CRAFTING_STEPS_SCHEMA_VERSION = 3;
    /** resourceMode追加済み、実作業台Step追加前の旧形式。 */
    private static final int RESOURCE_MODE_SCHEMA_VERSION = 2;
    /** resourceMode追加前のAAC試験Receiptを読むための旧形式。 */
    private static final int LEGACY_SCHEMA_VERSION = 1;
    /** 16,384桁のBigIntegerを含めつつ、破損NBTの巨大配列を拒否する固定上限。 */
    private static final int MAXIMUM_BIG_INTEGER_BYTES = 8_192;
    /** Config上限より広く取り、異常NBTだけを配列確保前に拒否する。 */
    private static final int MAXIMUM_LIST_ENTRIES = 65_536;
    /** Registry IDとFingerprintへ通常必要な長さを越える破損文字列を拒否する。 */
    private static final int MAXIMUM_PATTERN_ID_LENGTH = 256;

    private PreparedVectorBatchCodec() {
    }

    public static CompoundTag encode(PreparedVectorBatch plan) {
        Objects.requireNonNull(plan, "plan");
        CompoundTag tag = new CompoundTag();
        tag.putInt("schema", SCHEMA_VERSION);
        tag.putUUID("transaction", plan.transactionId());
        tag.putUUID("parentJob", plan.parentJobId());
        tag.putString("resourceMode", plan.resourceMode().name());
        tag.put("requestedOutput", plan.requestedOutput().toTagGeneric(com.syaru.ae2craftingoptimizer.lifecycle.ACORegistryAccess.require()));
        putPositive(tag, "requestedAmount", plan.requestedAmount());
        putPositive(tag, "logicalExecutions", plan.logicalExecutions());
        tag.putInt("logicalStageCount", plan.logicalStageCount());
        tag.put("totalInputs", encodeStacks(plan.totalInputs()));
        tag.put("finalOutputs", encodeStacks(plan.finalOutputs()));
        tag.put("remainingOutputs", encodeStacks(plan.remainingOutputs()));
        ListTag patterns = new ListTag();
        // Pattern IDは安定Fingerprintだけを保存し、実行時オブジェクトをNBTへ保持しない。
        for (String patternId : plan.requiredPatternIds()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("id", checkedIdentifier(patternId));
            patterns.add(entry);
        }
        tag.put("requiredPatterns", patterns);
        ListTag steps = new ListTag();
        // 一つの固有Patternを一つの係数Stepとして保存し、実行数量ぶん要素を増やさない。
        for (ExactCraftingStep step : plan.craftingSteps()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("id", checkedIdentifier(step.patternId()));
            entry.putInt("depth", step.depth());
            putPositive(entry, "executions", step.executions());
            ListTag selectedInputs = new ListTag();
            // slot順と同一キーの重複を維持し、Plannerが選んだ一回入力だけを保存する。
            for (ExactCraftingInputSlot input :
                    step.selectedInputs()) {
                CompoundTag selected =
                        new CompoundTag();
                selected.put(
                        "key",
                        input.key()
                                .toTagGeneric(com.syaru.ae2craftingoptimizer.lifecycle.ACORegistryAccess.require()));
                selected.putLong(
                        "amount",
                        input.amountPerExecution());
                selectedInputs.add(
                        selected);
            }
            entry.put(
                    "selectedInputs",
                    selectedInputs);
            steps.add(entry);
        }
        tag.put("craftingSteps", steps);
        tag.putString(
                "programFingerprint",
                checkedIdentifier(plan.programFingerprint()));
        tag.putLong("patternGeneration", plan.patternGeneration());
        tag.putLong("recipeGeneration", plan.recipeGeneration());
        return tag;
    }

    public static PreparedVectorBatch decode(CompoundTag tag) {
        Objects.requireNonNull(tag, "tag");
        int schema = tag.getInt("schema");
        // 対応schemaと二つの所有権UUIDが揃わないNBTは復旧対象にしない。
        if ((schema != SCHEMA_VERSION
                        && schema != SELECTED_INPUTS_SCHEMA_VERSION
                        && schema != CRAFTING_STEPS_SCHEMA_VERSION
                        && schema != RESOURCE_MODE_SCHEMA_VERSION
                        && schema != LEGACY_SCHEMA_VERSION)
                || !tag.hasUUID("transaction")
                || !tag.hasUUID("parentJob")) {
            throw new IllegalArgumentException(
                    "unsupported ACO Exact Vector plan schema");
        }
        AEKey requestedOutput = Objects.requireNonNull(
                AEKey.fromTagGeneric(com.syaru.ae2craftingoptimizer.lifecycle.ACORegistryAccess.require(), tag.getCompound("requestedOutput")),
                "requestedOutput");
        VectorResourceMode resourceMode =
                readResourceMode(schema, tag);
        return new PreparedVectorBatch(
                tag.getUUID("transaction"),
                tag.getUUID("parentJob"),
                resourceMode,
                requestedOutput,
                readPositive(tag, "requestedAmount"),
                readPositive(tag, "logicalExecutions"),
                tag.getInt("logicalStageCount"),
                decodeStacks(tag, "totalInputs"),
                decodeStacks(tag, "finalOutputs"),
                decodeStacks(tag, "remainingOutputs"),
                decodePatterns(tag),
                decodeCraftingSteps(schema, tag),
                checkedIdentifier(tag.getString("programFingerprint")),
                tag.getLong("patternGeneration"),
                tag.getLong("recipeGeneration"));
    }

    public static ListTag encodeStacks(List<ExactStack> stacks) {
        List<ExactStack> checked =
                List.copyOf(Objects.requireNonNull(stacks, "stacks"));
        // この上限は数量ではなく、破損NBTによる巨大なキーList確保を防ぐ。
        if (checked.size() > MAXIMUM_LIST_ENTRIES) {
            throw new IllegalArgumentException(
                    "Exact Vector stack list is oversized");
        }
        ListTag result = new ListTag();
        // 一つのAEKeyを一つのNBT entryへ保存し、数量ぶんの要素は作らない。
        for (ExactStack stack : checked) {
            CompoundTag entry = new CompoundTag();
            entry.put("key", stack.key().toTagGeneric(com.syaru.ae2craftingoptimizer.lifecycle.ACORegistryAccess.require()));
            putPositive(entry, "amount", stack.amount());
            result.add(entry);
        }
        return result;
    }

    public static List<ExactStack> decodeStacks(
            CompoundTag owner,
            String name) {
        ListTag list = requireCompoundList(owner, name);
        List<ExactStack> result = new ArrayList<>(list.size());
        // 各AEKeyを一回だけ復号し、BigInteger数量に比例するループは行わない。
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entry = list.getCompound(index);
            AEKey key = Objects.requireNonNull(
                    AEKey.fromTagGeneric(com.syaru.ae2craftingoptimizer.lifecycle.ACORegistryAccess.require(), entry.getCompound("key")),
                    "decoded Exact Vector key");
            result.add(new ExactStack(
                    key,
                    readPositive(entry, "amount")));
        }
        return List.copyOf(result);
    }

    public static void putNonNegative(
            CompoundTag owner,
            String name,
            BigInteger value) {
        BigInteger checked = Objects.requireNonNull(value, name);
        byte[] encoded = checked.toByteArray();
        // 負数と16,384桁を越える配列は、保存前に同じ境界で拒否する。
        if (checked.signum() < 0
                || encoded.length > MAXIMUM_BIG_INTEGER_BYTES) {
            throw new IllegalArgumentException(
                    name + " is negative or oversized");
        }
        owner.putByteArray(name, encoded);
    }

    public static BigInteger readNonNegative(
            CompoundTag owner,
            String name) {
        // 数量はlongタグへ縮退させず、正規BigInteger byte arrayだけを受け入れる。
        if (!owner.contains(name, Tag.TAG_BYTE_ARRAY)) {
            throw new IllegalArgumentException(
                    "missing Exact Vector count " + name);
        }
        byte[] encoded = owner.getByteArray(name);
        // 空配列と上限超過はBigIntegerを確保する前に拒否する。
        if (encoded.length == 0
                || encoded.length > MAXIMUM_BIG_INTEGER_BYTES) {
            throw new IllegalArgumentException(
                    "invalid Exact Vector count " + name);
        }
        BigInteger value = new BigInteger(encoded);
        // 非正規表現を拒否し、同じ値に複数の永続表現を持たせない。
        if (value.signum() < 0
                || !java.util.Arrays.equals(encoded, value.toByteArray())) {
            throw new IllegalArgumentException(
                    "non-canonical Exact Vector count " + name);
        }
        return value;
    }

    static VectorResourceMode readResourceMode(
            int schema,
            CompoundTag owner) {
        // schema 1はAACがME在庫を直接所有する方式しか存在しない。
        if (schema == LEGACY_SCHEMA_VERSION) {
            return VectorResourceMode.NETWORK_STORAGE;
        }
        try {
            return VectorResourceMode.valueOf(
                    owner.getString("resourceMode"));
        } catch (IllegalArgumentException invalidMode) {
            throw new IllegalArgumentException(
                    "invalid Exact Vector resource mode",
                    invalidMode);
        }
    }

    private static List<String> decodePatterns(CompoundTag owner) {
        ListTag list = requireCompoundList(owner, "requiredPatterns");
        List<String> result = new ArrayList<>(list.size());
        // 保存順を維持し、Executor側の所有権照合も決定的にする。
        for (int index = 0; index < list.size(); index++) {
            result.add(checkedIdentifier(
                    list.getCompound(index).getString("id")));
        }
        return List.copyOf(result);
    }

    private static List<ExactCraftingStep> decodeCraftingSteps(
            int schema,
            CompoundTag owner) {
        /*
         * schema 1/2には係数、schema 3にはPlanner選択slotがない。
         * どれも推測移行せず、実行不能の空Listとして隔離する。
         */
        if (schema < SELECTED_INPUTS_SCHEMA_VERSION) {
            return List.of();
        }
        ListTag list = requireCompoundList(owner, "craftingSteps");
        List<ExactCraftingStep> result = new ArrayList<>(list.size());
        for (int index = 0; index < list.size(); index++) {
            CompoundTag entry = list.getCompound(index);
            result.add(new ExactCraftingStep(
                    checkedIdentifier(entry.getString("id")),
                    entry.getInt("depth"),
                    readPositive(entry, "executions"),
                    decodeSelectedInputs(entry)));
        }
        return List.copyOf(result);
    }

    private static List<ExactCraftingInputSlot> decodeSelectedInputs(
            CompoundTag owner) {
        ListTag list =
                requireCompoundList(
                        owner,
                        "selectedInputs");
        List<ExactCraftingInputSlot> result =
                new ArrayList<>(
                        list.size());
        // 保存されたslot順で具体キーと一回入力量を復元する。
        for (int index = 0;
                index < list.size();
                index++) {
            CompoundTag entry =
                    list.getCompound(
                            index);
            AEKey key =
                    Objects.requireNonNull(
                            AEKey.fromTagGeneric(com.syaru.ae2craftingoptimizer.lifecycle.ACORegistryAccess.require(),
                                    entry.getCompound(
                                            "key")),
                            "selected crafting input key");
            // long以外の型や非正数は破損Receiptとして拒否する。
            if (!entry.contains(
                            "amount",
                            Tag.TAG_LONG)
                    || entry.getLong(
                                    "amount")
                            <= 0L) {
                throw new IllegalArgumentException(
                        "invalid selected crafting input amount");
            }
            result.add(
                    new ExactCraftingInputSlot(
                            key,
                            entry.getLong(
                                    "amount")));
        }
        return List.copyOf(
                result);
    }

    private static void putPositive(
            CompoundTag owner,
            String name,
            BigInteger value) {
        putNonNegative(owner, name, value);
        // 正数専用フィールドへ0を書き込ませない。
        if (value.signum() == 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    private static BigInteger readPositive(
            CompoundTag owner,
            String name) {
        BigInteger value = readNonNegative(owner, name);
        // 正数専用フィールドの0は破損Receiptとして扱う。
        if (value.signum() == 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private static ListTag requireCompoundList(
            CompoundTag owner,
            String name) {
        Tag raw = owner.get(name);
        // Compound以外の要素と過大なキー件数を、List走査前に拒否する。
        if (!(raw instanceof ListTag list)
                || (!list.isEmpty()
                        && list.getElementType() != Tag.TAG_COMPOUND)
                || list.size() > MAXIMUM_LIST_ENTRIES) {
            throw new IllegalArgumentException(
                    "invalid Exact Vector list " + name);
        }
        return owner.getList(name, Tag.TAG_COMPOUND);
    }

    private static String checkedIdentifier(String value) {
        String checked = Objects.requireNonNull(value, "identifier").trim();
        // 空IDと異常に長いIDはFingerprint・所有権照合へ使わない。
        if (checked.isEmpty()
                || checked.length() > MAXIMUM_PATTERN_ID_LENGTH) {
            throw new IllegalArgumentException(
                    "Exact Vector identifier is blank or oversized");
        }
        return checked;
    }
}
