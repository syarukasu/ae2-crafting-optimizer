package com.syaru.ae2craftingoptimizer.api.batch;

import net.minecraft.resources.ResourceLocation;

/**
 * 完全なProcessing Patternを一回以上、機械側の所有物として受理するAdapter契約。
 *
 * <p>AE2の{@code ICraftingProvider.pushPattern}より厳しい次の条件を持つ。</p>
 *
 * <ul>
 *     <li>0を返す場合、対象を変更せず入力も保持してはならない。</li>
 *     <li>Nを返す場合、完全なN回分を永続的に受理済みでなければならない。</li>
 *     <li>挿入Simulationや一部Slotへの挿入だけでは受理とみなさない。</li>
 *     <li>{@code maximumExecutions}を超える値を返してはならない。</li>
 * </ul>
 *
 * <p>電力、Crafting Task進捗、期待出力の会計はACO側が引き続き担当する。</p>
 */
public interface PatternBatchAdapter {
    ResourceLocation id();

    default int priority() {
        return 0;
    }

    /**
     * Provider本来の複数面Routingを完全に維持できるNative Adapterだけtrueを返す。
     * falseの場合、ACOは対象が一意に決まるContextだけを渡す。
     */
    default boolean supportsMultipleProviderTargets() {
        return false;
    }

    boolean supports(PatternBatchContext context);

    /**
     * 所有権移転前にACOが準備する実行数を狭める。
     * Adapterは安価に判定できる固定上限やQueue空き容量を返し、直後に戻す入力の抽出を避ける。
     * 戻り値は0以上{@code offeredExecutions}以下でなければならない。
     */
    default long limitExecutions(PatternBatchContext context, long offeredExecutions) {
        return offeredExecutions;
    }

    PatternBatchResult commit(PatternBatchContext context, PatternBatchBudget budget);
}
