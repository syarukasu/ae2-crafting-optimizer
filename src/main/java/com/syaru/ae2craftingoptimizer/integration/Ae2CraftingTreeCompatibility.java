package com.syaru.ae2craftingoptimizer.integration;

import appeng.api.networking.crafting.ICraftingPlan;
import appeng.crafting.CraftingPlan;
import appeng.menu.me.crafting.CraftingPlanSummary;
import java.lang.reflect.Method;
import java.util.Objects;
import net.neoforged.fml.ModList;

/**
 * AE2 Crafting TreeがCraftingPlanSummaryへ追加するRecipeHelperを任意連携で初期化する。
 *
 * <p>ACOのWide計画はAE2のunchecked long集計を避けるためfromJobを早期終了する。
 * その際、AE2CTのTAIL Injectも実行されないため、同じ初期化を公開メソッド経由で補う。</p>
 */
public final class Ae2CraftingTreeCompatibility {
    public static final String MOD_ID = "ae2ct";

    private static final String SUMMARY_INTERFACE =
            "com.neuvillette.ae2ct.api.ICraftingPlanSummary";
    private static final String RECIPE_HELPER =
            "com.neuvillette.ae2ct.api.RecipeHelper";

    private static volatile Accessors resolvedAccessors;

    private Ae2CraftingTreeCompatibility() {
    }

    /**
     * AE2CT導入時だけ、Wide計画のlong互換Facadeからクラフトツリー情報を復元する。
     */
    public static void populateWideSummary(
            CraftingPlanSummary summary,
            ICraftingPlan plan) {
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(plan, "plan");

        // AE2CT未導入環境ではクラス探索も行わず、ACO単体の従来経路を維持する。
        if (!ModList.get().isLoaded(MOD_ID)) {
            return;
        }
        // Wide計画は必ず純正CraftingPlan Facadeとして外部へ渡す設計である。
        if (!(plan instanceof CraftingPlan craftingPlan)) {
            throw new IllegalStateException(
                    "ACO cannot initialize AE2CT: wide plan is not an AE2 CraftingPlan facade");
        }

        Accessors accessors = accessors();
        // AE2CTのMixinがSummaryへ適用されていなければ、packet形式が一致しないため即時停止する。
        if (!accessors.summaryInterface().isInstance(summary)) {
            throw new IllegalStateException(
                    "ACO cannot initialize AE2CT: CraftingPlanSummary extension is missing");
        }

        try {
            Object helper = accessors.fromCraftingPlan().invoke(null, craftingPlan);
            // nullを書き込むとAE2CTのwrite Injectが再び落ちるため、生成結果を必ず検査する。
            if (helper == null) {
                throw new IllegalStateException(
                        "ACO cannot initialize AE2CT: RecipeHelper creation returned null");
            }
            accessors.setJob().invoke(summary, helper);
            Object stored = accessors.getJob().invoke(summary);
            // Setter呼び出し後もnullなら、対象AE2CT版の契約が変わったものとして送信前に止める。
            if (stored == null) {
                throw new IllegalStateException(
                        "ACO cannot initialize AE2CT: RecipeHelper was not stored");
            }
        } catch (ReflectiveOperationException | LinkageError exception) {
            throw new IllegalStateException(
                    "ACO failed to initialize AE2CT crafting-tree data for a wide plan",
                    exception);
        }
    }

    private static Accessors accessors() {
        Accessors current = resolvedAccessors;
        // 初回解決後はクラフト確認ごとのReflection探索を行わない。
        if (current != null) {
            return current;
        }

        synchronized (Ae2CraftingTreeCompatibility.class) {
            current = resolvedAccessors;
            // 同時に複数計算が到達した場合、先に解決した結果を再利用する。
            if (current != null) {
                return current;
            }
            resolvedAccessors = resolveAccessors();
            return resolvedAccessors;
        }
    }

    private static Accessors resolveAccessors() {
        try {
            ClassLoader loader = Ae2CraftingTreeCompatibility.class.getClassLoader();
            Class<?> summaryInterface = Class.forName(SUMMARY_INTERFACE, false, loader);
            Class<?> recipeHelper = Class.forName(RECIPE_HELPER, false, loader);
            Method fromCraftingPlan =
                    recipeHelper.getMethod("fromCraftingPlan", CraftingPlan.class);
            Method setJob = summaryInterface.getMethod("setJob", recipeHelper);
            Method getJob = summaryInterface.getMethod("getJob");
            return new Accessors(
                    summaryInterface,
                    fromCraftingPlan,
                    setJob,
                    getJob);
        } catch (ReflectiveOperationException | LinkageError exception) {
            throw new IllegalStateException(
                    "ACO detected AE2CT but its RecipeHelper API is incompatible",
                    exception);
        }
    }

    private record Accessors(
            Class<?> summaryInterface,
            Method fromCraftingPlan,
            Method setJob,
            Method getJob) {
    }
}
