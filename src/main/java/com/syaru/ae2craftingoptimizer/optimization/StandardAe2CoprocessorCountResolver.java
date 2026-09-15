package com.syaru.ae2craftingoptimizer.optimization;

import appeng.api.networking.crafting.ICraftingCPU;
import appeng.blockentity.crafting.CraftingBlockEntity;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * 標準AE2クラスタのコプロセッサ数を、AE2の{@code int}集計が桁あふれする前の値へ戻す。
 *
 * <p>AE2 15.4.x/19.2.xのクラスタは各ユニットのスレッド数を{@code int}へ加算する。
 * 2^31以上では符号反転し、さらに実行側の{@code + 1}でも桁あふれするため、例外なしで
 * クラフト実行が停止する。クラスタ構成は形成後に不変で、変更時は別クラスタへ再構築されるため、
 * 構成ブロックからのlong再集計をクラスタ単位で一度だけ行う。</p>
 */
final class StandardAe2CoprocessorCountResolver {
    private static final Map<CraftingCPUCluster, Long> RESOLVED_COUNTS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private StandardAe2CoprocessorCountResolver() {
    }

    static long resolve(ICraftingCPU cpu, int reportedCount) {
        // 標準クラスタ以外は構成ブロックをACOが所有していないため、公開された値だけを検証する。
        if (!(cpu instanceof CraftingCPUCluster cluster)) {
            return requireNonNegativeReportedCount(reportedCount, cpu.getClass().getName());
        }

        long scannedCount;
        synchronized (RESOLVED_COUNTS) {
            scannedCount = RESOLVED_COUNTS.computeIfAbsent(
                    cluster,
                    StandardAe2CoprocessorCountResolver::scanCluster);
        }
        return reconcile(reportedCount, scannedCount);
    }

    static void clear() {
        synchronized (RESOLVED_COUNTS) {
            RESOLVED_COUNTS.clear();
        }
    }

    /** 単体試験用に、AE2の報告値とlong再集計値の採用規則を分離する。 */
    static long reconcile(int reportedCount, long scannedCount) {
        // 再集計値がint範囲を越えていれば、符号反転または正値への再ラップより再集計値を優先する。
        if (scannedCount > Integer.MAX_VALUE) {
            return scannedCount;
        }
        // int範囲内のクラスタが負数を返す場合は、別MODによる不正な状態を推測で補正しない。
        if (reportedCount < 0) {
            throw new IllegalStateException(
                    "AE2 crafting CPU reported a negative co-processor count without a wide cluster total: "
                            + reportedCount + " (scanned " + scannedCount + ")");
        }
        // intフィールドへ直接加算する互換MODを保持するため、健全な報告値の方が大きければ尊重する。
        return Math.max(scannedCount, (long) reportedCount);
    }

    private static long scanCluster(CraftingCPUCluster cluster) {
        long total = 0L;
        Iterator<CraftingBlockEntity> blocks = cluster.getBlockEntities();
        // AE2クラスタを構成する全ユニットを一度だけ走査し、int加算前の値をlongで復元する。
        while (blocks.hasNext()) {
            CraftingBlockEntity block = blocks.next();
            int threads = block.getAcceleratorThreads();
            // ICraftingUnitTypeの契約に反する負数は、桁あふれとして推測せず明示的に失敗させる。
            if (threads < 0) {
                throw new IllegalStateException(
                        "AE2 crafting unit reported a negative accelerator thread count: "
                                + block.getClass().getName() + " = " + threads);
            }
            // ストレージやモニターなど0スレッドの構成ブロックは合計へ影響しない。
            if (threads == 0) {
                continue;
            }
            total = Math.addExact(total, (long) threads);
        }
        // intを越えたクラスタだけを一度記録し、/aco statsで停止修正経路の使用を確認可能にする。
        if (total > Integer.MAX_VALUE) {
            OptimizationMetrics.recordWideCoprocessorReconstruction();
        }
        return total;
    }

    private static long requireNonNegativeReportedCount(int reportedCount, String cpuClassName) {
        // 構成を再集計できないCPUの負数は、安全な実数を証明できないため受け入れない。
        if (reportedCount < 0) {
            throw new IllegalStateException(
                    "Crafting CPU reported a negative co-processor count: "
                            + cpuClassName + " = " + reportedCount);
        }
        return reportedCount;
    }
}
