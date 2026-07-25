package com.syaru.ae2craftingoptimizer.engine;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.crafting.pattern.AECraftingPattern;
import com.syaru.ae2craftingoptimizer.access.CraftingTaskProgressAccess;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * 実行中AE2 Jobから、結果を証明できる通常作業台Patternだけを島コンパイラへ渡す。
 */
public final class Ae2CraftingIslandCompiler {
    /** AE2 15.4.xがPattern定義NBTへ保存する元CraftingRecipe IDキー。 */
    private static final String PATTERN_RECIPE_ID_TAG = "recipe";
    /** AEItemKeyの追加状態を示す汎用NBTキー。 */
    private static final String ITEM_STATE_TAG = "tag";
    /** Forge Capability由来の追加状態を示す汎用NBTキー。 */
    private static final String ITEM_CAPABILITIES_TAG = "caps";
    /** KubeJSが通常の整形作業台レシピへ使う実装クラス名。 */
    private static final String KUBEJS_SHAPED_RECIPE =
            "dev.latvian.mods.kubejs.recipe.special.ShapedKubeJSRecipe";
    /** KubeJSが通常の不定形作業台レシピへ使う実装クラス名。 */
    private static final String KUBEJS_SHAPELESS_RECIPE =
            "dev.latvian.mods.kubejs.recipe.special.ShapelessKubeJSRecipe";
    /** KubeJSの動的入力処理一覧を読む公開アクセサ名。 */
    private static final String KUBEJS_INGREDIENT_ACTIONS_METHOD =
            "kjs$getIngredientActions";
    /** KubeJSの動的出力コールバックを読む公開アクセサ名。 */
    private static final String KUBEJS_MODIFY_RESULT_METHOD =
            "kjs$getModifyResult";
    /** KubeJSのプレイヤーステージ条件を読む公開アクセサ名。 */
    private static final String KUBEJS_STAGE_METHOD = "kjs$getStage";

    private Ae2CraftingIslandCompiler() {
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
        return CompiledCraftingIsland.tryCompile(safeTasks, maximumBits);
    }

    @Nullable
    private static CompiledCraftingIsland.Task<AEKey, IPatternDetails>
            tryCompileExactCraftingTask(
                    IPatternDetails details,
                    long executions,
                    Level level) {
        // AECraftingPatternそのもの以外は独自機械またはアドオンPatternとして境界にする。
        if (details.getClass() != AECraftingPattern.class) {
            return null;
        }
        AECraftingPattern crafting = (AECraftingPattern) details;
        // 代替素材・液体代替は在庫状態で選択結果が変わるため島へ入れない。
        if (crafting.canSubstitute || crafting.canSubstituteFluids) {
            return null;
        }
        // 特殊レシピはNBTやワールド状態から出力が変わり得るため固定式にしない。
        if (!isOrdinaryFixedRecipe(crafting, level)) {
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
        // 各圧縮入力slotを単一の確定Itemへ変換する。
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

    private static boolean isOrdinaryFixedRecipe(
            AECraftingPattern pattern,
            Level level) {
        CompoundTag definition = pattern.getDefinition().getTag();
        // Recipe IDが欠けたPatternはデータパック再読込後の固定性を証明できない。
        if (definition == null
                || !definition.contains(PATTERN_RECIPE_ID_TAG)) {
            return false;
        }
        ResourceLocation recipeId = ResourceLocation.tryParse(
                definition.getString(PATTERN_RECIPE_ID_TAG));
        // 壊れたIDはRecipeManagerへ渡さず標準経路へ戻す。
        if (recipeId == null) {
            return false;
        }
        Recipe<?> recipe = level.getRecipeManager().byKey(recipeId).orElse(null);
        // 特殊作業台レシピは入力欄以外の状態を読むため、固定式へ変換しない。
        if (!(recipe instanceof CraftingRecipe craftingRecipe)
                || craftingRecipe.isSpecial()) {
            return false;
        }
        // 素のMinecraft実装は入力、出力、返却物の固定性を上位の検査だけで証明できる。
        if (recipe.getClass() == ShapedRecipe.class
                || recipe.getClass() == ShapelessRecipe.class) {
            return true;
        }
        // KubeJSの通常作業台レシピはShapedRecipeを継承するが、厳密なclass一致では除外される。
        // 動的アクション、結果コールバック、ステージ条件が無い場合だけ同じ固定式として扱う。
        return isStaticKubeJsCraftingRecipe(recipe);
    }

    private static boolean isStaticKubeJsCraftingRecipe(Recipe<?> recipe) {
        String className = recipe.getClass().getName();
        // KubeJS以外の派生Recipeは独自状態を持つ可能性があるため許可しない。
        if (!KUBEJS_SHAPED_RECIPE.equals(className)
                && !KUBEJS_SHAPELESS_RECIPE.equals(className)) {
            return false;
        }

        try {
            Method ingredientActionsMethod = recipe.getClass().getMethod(
                    KUBEJS_INGREDIENT_ACTIONS_METHOD);
            Object rawActions = ingredientActionsMethod.invoke(recipe);
            // 空のList以外は消費量・残存物を動的に変えるため、AE2本来の実行へ戻す。
            if (!(rawActions instanceof Collection<?> actions) || !actions.isEmpty()) {
                return false;
            }

            Method modifyResultMethod = recipe.getClass().getMethod(
                    KUBEJS_MODIFY_RESULT_METHOD);
            // 結果コールバックはクラフトグリッドの内容で出力を変更できるため許可しない。
            if (modifyResultMethod.invoke(recipe) != null) {
                return false;
            }

            Method stageMethod = recipe.getClass().getMethod(KUBEJS_STAGE_METHOD);
            Object rawStage = stageMethod.invoke(recipe);
            // 空文字以外のステージはプレイヤーごとに結果が変わるため固定式へ畳み込まない。
            return rawStage instanceof String stage && stage.isEmpty();
        } catch (IllegalAccessException
                | InvocationTargetException
                | NoSuchMethodException
                | RuntimeException unsupportedKubeJsVersion) {
            // KubeJS側APIの変更や反射失敗時は、壊れた高速経路を使わず通常AE2へ戻す。
            return false;
        }
    }

    private static boolean isPlainItem(AEItemKey key) {
        CompoundTag serialized = key.toTag();
        // NBT、Capability、耐久値を持つItemは同じRegistry IDでも状態が異なるため除外する。
        return !serialized.contains(ITEM_STATE_TAG)
                && !serialized.contains(ITEM_CAPABILITIES_TAG)
                && key.getFuzzySearchMaxValue() <= 0;
    }
}
