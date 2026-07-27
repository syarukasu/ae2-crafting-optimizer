package com.syaru.ae2craftingoptimizer.engine;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AECraftingPattern;
import com.syaru.ae2craftingoptimizer.access.CraftingTaskProgressAccess;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * 実行中AE2 Jobから、結果を証明できる通常作業台Patternだけを島コンパイラへ渡す。
 */
public final class Ae2CraftingIslandCompiler {
    /** AEItemKeyの追加状態を示す汎用NBTキー。 */
    private static final String ITEM_STATE_TAG = "tag";
    /** Forge Capability由来の追加状態を示す汎用NBTキー。 */
    private static final String ITEM_CAPABILITIES_TAG = "caps";

    private Ae2CraftingIslandCompiler() {
    }

    /** 保存Receiptと現在Patternを同じ方式で照合する公開Fingerprint境界。 */
    public static String patternFingerprint(IPatternDetails details) {
        return Ae2CompiledPatternFactory.fingerprint(
                Objects.requireNonNull(details, "details"));
    }

    /**
     * 実行中Taskを一回だけ読み、機械・液体・化学・可変NBTを境界として島へ分割する。
     */
    public static Optional<List<CompiledCraftingIsland<AEKey, IPatternDetails>>> tryCompile(
            Map<IPatternDetails, Object> liveTasks,
            Level level,
            int maximumPatterns,
            int maximumBits) {
        Objects.requireNonNull(liveTasks, "liveTasks");
        Objects.requireNonNull(level, "level");
        // 設定上限外のJobでは補助配列を作らずNeo ECO本来の配送へ戻す。
        if (liveTasks.size() > maximumPatterns) {
            return Optional.empty();
        }

        Map<AEKey, IPatternDetails> allOutputOwners = new LinkedHashMap<>();
        // 非対応Patternも含めた全出力を確認し、同一キーの複数生産者を先に排除する。
        for (IPatternDetails details : liveTasks.keySet()) {
            // null Patternを含む破損Jobは部分最適化しない。
            if (details == null) {
                return Optional.empty();
            }
            for (GenericStack output : details.getOutputs()) {
                // nullまたは0量出力はJob会計を証明できない。
                if (output == null || output.amount() <= 0L) {
                    return Optional.empty();
                }
                IPatternDetails previous = allOutputOwners.putIfAbsent(output.what(), details);
                // 別Patternが同じキーを生産するJobはAE2の実行順へ任せる。
                if (previous != null && previous != details) {
                    return Optional.empty();
                }
            }
        }

        List<CompiledCraftingIsland.Task<AEKey, IPatternDetails>> safeTasks =
                new ArrayList<>();
        // 各Taskの残回数と固定式を一回だけ読み、安全な作業台Patternだけを抽出する。
        for (Map.Entry<IPatternDetails, Object> entry : liveTasks.entrySet()) {
            Object rawProgress = entry.getValue();
            // TaskProgress accessorが無い環境では値を書き換えず元実装へ戻す。
            if (!(rawProgress instanceof CraftingTaskProgressAccess progress)) {
                return Optional.empty();
            }
            long executions = progress.aco$getTaskProgress();
            // 完了済みTaskはNeo ECO本体が削除するため島へ含めない。
            if (executions <= 0L) {
                continue;
            }
            CompiledCraftingIsland.Task<AEKey, IPatternDetails> task =
                    tryCompileExactCraftingTask(entry.getKey(), executions, level);
            // 非対応Patternは機械境界として残し、隣接する安全Task同士だけを接続する。
            if (task != null) {
                safeTasks.add(task);
            }
        }
        /*
         * 入出力式が一意なら一段だけでも数量非依存Batchとして成立する。
         * 段数を安全条件にせず、曖昧性と会計可能性だけを下位コンパイラで検査する。
         */
        return CompiledCraftingIsland.tryCompileIncludingSingletons(
                safeTasks, maximumBits);
    }

    /**
     * AdvancedAE標準Job全体が一つの決定的作業台DAGである場合だけ返す。
     *
     * <p>一部だけを別Receiptへ所有すると通常Pattern Pushとの順序が変わるため、
     * Exact Vector標準経路では全active Taskの完全包含を必須にする。</p>
     */
    public static Optional<CompiledCraftingIsland<AEKey, IPatternDetails>>
            tryCompileWholeDeterministicJob(
                    Map<IPatternDetails, Object> liveTasks,
                    Level level,
                    int maximumPatterns,
                    int maximumBits) {
        Objects.requireNonNull(liveTasks, "liveTasks");
        Objects.requireNonNull(level, "level");
        if (liveTasks.isEmpty()
                || liveTasks.size() > maximumPatterns) {
            return Optional.empty();
        }

        List<CompiledCraftingIsland.Task<AEKey, IPatternDetails>> tasks =
                new ArrayList<>(liveTasks.size());
        // 全Taskを固定作業台Patternへ変換できないJobは、部分採用せずAdvancedAEへ戻す。
        for (Map.Entry<IPatternDetails, Object> entry : liveTasks.entrySet()) {
            if (!(entry.getValue() instanceof CraftingTaskProgressAccess progress)) {
                return Optional.empty();
            }
            long executions = progress.aco$getTaskProgress();
            if (executions <= 0L) {
                return Optional.empty();
            }
            CompiledCraftingIsland.Task<AEKey, IPatternDetails> task =
                    tryCompileExactCraftingTask(
                            entry.getKey(),
                            executions,
                            level);
            if (task == null) {
                return Optional.empty();
            }
            tasks.add(task);
        }

        Optional<List<CompiledCraftingIsland<AEKey, IPatternDetails>>> compiled =
                CompiledCraftingIsland.tryCompileIncludingSingletons(
                        tasks,
                        maximumBits);
        if (compiled.isEmpty()
                || compiled.orElseThrow().size() != 1) {
            return Optional.empty();
        }
        CompiledCraftingIsland<AEKey, IPatternDetails> island =
                compiled.orElseThrow().get(0);
        // 複数の独立成分や欠落Taskを一つの親Transactionへ誤結合しない。
        if (island.tasks().size() != tasks.size()) {
            return Optional.empty();
        }
        return Optional.of(island);
    }

    /**
     * AdvancedAE標準Jobが既に選択した作業台Patternだけを、現在世代の固定式へ投影する。
     *
     * <p>同じ出力を作る未選択レシピは参照しない。各active TaskのPattern ID、固定係数、
     * 残実行回数だけを結合するため、注文数に比例する展開を行わない。</p>
     */
    public static Optional<CompiledCraftingIsland<AEKey, IPatternDetails>>
            tryCompileGenerationBackedWholeDeterministicJob(
                    Map<IPatternDetails, Object> liveTasks,
                    Level level,
                    IGrid grid,
                    int maximumPatterns,
                    int maximumBits) {
        Objects.requireNonNull(liveTasks, "liveTasks");
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(grid, "grid");
        // 空Jobまたは設定上限を超えたJobでは、Graph参照前にAdvancedAEへ戻す。
        if (liveTasks.isEmpty()
                || liveTasks.size() > maximumPatterns) {
            return Optional.empty();
        }

        Ae2CompiledCraftingGraphCache.Snapshot snapshot;
        try {
            snapshot =
                    Ae2CompiledCraftingGraphCache.getOrCompile(grid, level);
        } catch (StalePlanningSnapshotException staleGeneration) {
            // Provider更新が連続したtickは古い式を採用せず、次tickの標準経路へ委譲する。
            return Optional.empty();
        }

        List<CompiledCraftingIsland.Task<AEKey, IPatternDetails>>
                selectedTasks = new ArrayList<>(liveTasks.size());
        // 現在Jobが選択済みのTaskだけを、同じProvider世代の固定式へ一対一に接続する。
        for (Map.Entry<IPatternDetails, Object> entry :
                liveTasks.entrySet()) {
            IPatternDetails details = entry.getKey();
            // null PatternやAccessor未適用Taskは、会計を書き換えずAdvancedAEへ戻す。
            if (details == null
                    || !(entry.getValue()
                            instanceof CraftingTaskProgressAccess progress)) {
                return Optional.empty();
            }
            long executions = progress.aco$getTaskProgress();
            // 標準Jobのactive Taskは必ず正数であり、0以下は同期途中として採用しない。
            if (executions <= 0L) {
                return Optional.empty();
            }
            String patternId = resolveCurrentPatternId(
                    snapshot,
                    details,
                    level);
            // 登録時またはNBT復元時の式と完全一致しないPatternは、数式Programへ接続しない。
            if (patternId == null) {
                return Optional.empty();
            }
            CompiledCraftingIsland.Task<AEKey, IPatternDetails> selected =
                    tryCompileSelectedCraftingTask(
                            snapshot,
                            details,
                            patternId,
                            executions);
            // 選択済みPattern自体が固定作業台式でなければ、Job全体をAdvancedAEへ戻す。
            if (selected == null) {
                return Optional.empty();
            }
            selectedTasks.add(selected);
        }

        Optional<List<CompiledCraftingIsland<AEKey, IPatternDetails>>> compiled =
                CompiledCraftingIsland.tryCompileIncludingSingletons(
                        selectedTasks,
                        maximumBits);
        // 一つのJobを複数Transactionへ分裂させず、全active Taskが連結する場合だけ採用する。
        if (compiled.isEmpty()
                || compiled.orElseThrow().size() != 1) {
            return Optional.empty();
        }
        CompiledCraftingIsland<AEKey, IPatternDetails> island =
                compiled.orElseThrow().get(0);
        // 選択Taskが一件でも欠落した場合は部分実行せず、元のAdvancedAE会計を維持する。
        if (island.tasks().size() != selectedTasks.size()) {
            return Optional.empty();
        }
        return Optional.of(island);
    }

    @Nullable
    private static CompiledCraftingIsland.Task<AEKey, IPatternDetails>
            tryCompileSelectedCraftingTask(
                    Ae2CompiledCraftingGraphCache.Snapshot snapshot,
                    IPatternDetails details,
                    String patternId,
                    long executions) {
        CompiledPattern<AEKey> selected =
                snapshot.compiledPattern(patternId);
        // 外部機械Patternと複数出力Patternは、作業台の原子的な一出力式ではない。
        if (selected == null
                || selected.externalPush()
                || selected.outputs().size() != 1) {
            return null;
        }

        List<CompiledCraftingIsland.Input<AEKey>> inputs =
                new ArrayList<>(selected.inputs().size());
        // エンコード済みPatternの各slotから、実際に固定された一つの入力だけを取得する。
        for (CompiledPattern.InputSlot<AEKey> slot :
                selected.inputs()) {
            // 代替許可が残るslotだけは、実際に消費されたキーをTask Mapから証明できない。
            if (slot.alternatives().size() != 1) {
                return null;
            }
            CompiledPattern.Stack<AEKey> input =
                    slot.alternatives().get(0);
            inputs.add(new CompiledCraftingIsland.Input<>(
                    input.key(),
                    input.amount()));
        }

        Map.Entry<AEKey, Long> output =
                selected.outputs().entrySet().iterator().next();
        return new CompiledCraftingIsland.Task<>(
                details,
                patternId,
                output.getKey(),
                output.getValue(),
                inputs,
                BigInteger.valueOf(executions));
    }

    @Nullable
    private static String resolveCurrentPatternId(
            Ae2CompiledCraftingGraphCache.Snapshot snapshot,
            IPatternDetails details,
            Level level) {
        String identityId = snapshot.id(details);
        // 新規Jobは登録済みPattern参照を保持するため、通常はIdentity索引だけで確定する。
        if (identityId != null
                && snapshot.pattern(identityId) == details) {
            return identityId;
        }

        /*
         * AdvancedAEは再起動時にPattern itemのNBTからIPatternDetailsを再デコードする。
         * 別オブジェクトでも定義NBT、入出力、候補、倍率、外部push属性が全て一致する時だけ
         * 現世代の登録Patternへ戻し、保存済み標準Jobを不要にFallbackさせない。
         */
        try {
            String decodedId = patternFingerprint(details);
            IPatternDetails registered = snapshot.pattern(decodedId);
            // 現世代に同じ定義が登録されていなければ、削除・変更済みPatternとして拒否する。
            if (registered == null) {
                return null;
            }
            CompiledPattern<AEKey> decoded =
                    Ae2CompiledPatternFactory.compile(
                            details,
                            decodedId,
                            level);
            CompiledPattern<AEKey> current =
                    Ae2CompiledPatternFactory.compile(
                            registered,
                            decodedId,
                            level);
            // コンパイル不能または一箇所でも式が異なる復元Patternは、AE2標準経路へ戻す。
            if (decoded == null
                    || current == null
                    || !sameCompiledFormula(decoded, current)) {
                return null;
            }
            return decodedId;
        } catch (RuntimeException invalidDecodedPattern) {
            // 動的Pattern APIが検証中に失敗した場合も、起動を落とさず該当JobだけFallbackする。
            return null;
        }
    }

    private static boolean sameCompiledFormula(
            CompiledPattern<AEKey> first,
            CompiledPattern<AEKey> second) {
        // 出力、外部機械push属性、入力slot数が違うPatternは同じ数式ではない。
        if (first.externalPush() != second.externalPush()
                || !first.outputs().equals(second.outputs())
                || first.inputs().size() != second.inputs().size()) {
            return false;
        }
        // slot順と全候補Stackを比較し、タグ候補等の並び替えも別式として扱う。
        for (int slot = 0; slot < first.inputs().size(); slot++) {
            List<CompiledPattern.Stack<AEKey>> firstAlternatives =
                    first.inputs().get(slot).alternatives();
            List<CompiledPattern.Stack<AEKey>> secondAlternatives =
                    second.inputs().get(slot).alternatives();
            // 候補キーまたは一回入力量が一つでも異なれば、同じProgramへ接続しない。
            if (!firstAlternatives.equals(secondAlternatives)) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    private static CompiledCraftingIsland.Task<AEKey, IPatternDetails>
            tryCompileExactCraftingTask(
                    IPatternDetails details,
                    long executions,
                    Level level) {
        /*
         * AE2の作業台Pattern APIを実装する派生型も、公開された固定入出力を同じ規則で検査する。
         * class完全一致はラッパーや互換実装を不要にFallbackさせるため使用しない。
         */
        if (!(details instanceof AECraftingPattern crafting)) {
            return null;
        }
        // 代替素材・液体代替は在庫状態で選択結果が変わるため島へ入れない。
        if (crafting.canSubstitute || crafting.canSubstituteFluids) {
            return null;
        }

        GenericStack[] outputs = details.getOutputs();
        // 副産物または複数出力を持つPatternは既存AE2会計へ戻す。
        if (outputs.length != 1
                || outputs[0] == null
                || outputs[0].amount() <= 0L
                || !(outputs[0].what() instanceof AEItemKey outputKey)
                || !isPlainItem(outputKey)) {
            return null;
        }

        List<CompiledCraftingIsland.Input<AEKey>> inputs = new ArrayList<>();
        // 各作業台入力slotを単一の確定Itemへ変換する。
        for (IPatternDetails.IInput input : details.getInputs()) {
            // 0以下の倍率はAE2のPattern会計と一致しないため拒否する。
            if (input == null || input.getMultiplier() <= 0L) {
                return null;
            }
            GenericStack[] candidates = input.getPossibleInputs();
            // タグ、代替候補、空候補は数量式へ固定しない。
            if (candidates.length != 1 || candidates[0] == null) {
                return null;
            }
            GenericStack candidate = candidates[0];
            // Item以外、NBT付き、耐久Itemは液体・容器・状態依存境界として扱う。
            if (candidate.amount() <= 0L
                    || !(candidate.what() instanceof AEItemKey inputKey)
                    || !isPlainItem(inputKey)) {
                return null;
            }
            try {
                // 実Patternが候補を受理し、使用後の返却物が無いことを実APIで確認する。
                if (!input.isValid(inputKey, level)
                        || input.getRemainingKey(inputKey) != null) {
                    return null;
                }
                long amount = Math.multiplyExact(
                        candidate.amount(),
                        input.getMultiplier());
                inputs.add(new CompiledCraftingIsland.Input<>(inputKey, amount));
            } catch (RuntimeException unsupportedInput) {
                return null;
            }
        }

        return new CompiledCraftingIsland.Task<>(
                details,
                Ae2CompiledPatternFactory.fingerprint(details),
                outputKey,
                outputs[0].amount(),
                inputs,
                BigInteger.valueOf(executions));
    }

    private static boolean isPlainItem(AEItemKey key) {
        CompoundTag serialized = key.toTag();
        // NBT、Capability、耐久値を持つItemは同じRegistry IDでも状態が異なるため除外する。
        return !serialized.contains(ITEM_STATE_TAG)
                && !serialized.contains(ITEM_CAPABILITIES_TAG)
                && key.getFuzzySearchMaxValue() <= 0;
    }
}
