package com.syaru.ae2craftingoptimizer.engine;

import java.util.UUID;

/**
 * 現在のサーバープロセスを識別する一時ID。
 *
 * <p>世代番号はJVM内だけで意味を持つため、保存Jobの世代を再起動後に直接比較しない。
 * Epochが変わった場合は、保存済みの構造Fingerprintで同じ数式Programかを再検証する。</p>
 */
public final class PlanningRuntimeEpoch {
    private static final String CURRENT = UUID.randomUUID().toString();

    private PlanningRuntimeEpoch() {
    }

    public static String current() {
        return CURRENT;
    }
}
