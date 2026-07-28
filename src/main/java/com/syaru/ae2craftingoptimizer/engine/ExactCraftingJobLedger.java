package com.syaru.ae2craftingoptimizer.engine;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * AE2実JobのBigIntegerカウンタを再起動後も検証する永続Journal。
 *
 * <p>実行時の正本は実Job上のTaskProgress、waitingFor、remainingAmount拡張である。
 * このJournalは物理Receiptから導出した累積絶対値を検証し、再読込時の二重計上を防ぐ。</p>
 */
public final class ExactCraftingJobLedger<T, K> {
    private static final BigInteger LONG_MAX = BigInteger.valueOf(Long.MAX_VALUE);

    private final Map<T, BigInteger> taskTotals;
    private final Map<K, BigInteger> initialWaiting;
    private Map<T, BigInteger> dispatchedTasks;
    private Map<K, BigInteger> introducedOutputs;
    private Map<K, BigInteger> creditedOutputs;
    private final BigInteger requestedAmount;
    private BigInteger remainingOutput;

    public ExactCraftingJobLedger(
            Map<T, BigInteger> taskTotals,
            Map<K, BigInteger> initialWaiting,
            Map<T, BigInteger> dispatchedTasks,
            Map<K, BigInteger> introducedOutputs,
            Map<K, BigInteger> creditedOutputs,
            BigInteger requestedAmount,
            BigInteger remainingOutput) {
        this.taskTotals = checkedCounts(taskTotals, "taskTotals", false);
        this.initialWaiting = checkedCounts(initialWaiting, "initialWaiting", true);
        this.dispatchedTasks = checkedCounts(dispatchedTasks, "dispatchedTasks", true);
        this.introducedOutputs =
                checkedCounts(introducedOutputs, "introducedOutputs", true);
        this.creditedOutputs = checkedCounts(creditedOutputs, "creditedOutputs", true);
        this.requestedAmount = positive(requestedAmount, "requestedAmount");
        this.remainingOutput = nonNegative(remainingOutput, "remainingOutput");
        validateSnapshot(
                this.dispatchedTasks,
                this.introducedOutputs,
                this.creditedOutputs,
                this.remainingOutput);
    }

    public static <T, K> ExactCraftingJobLedger<T, K> planned(
            Map<T, BigInteger> taskTotals,
            Map<K, BigInteger> initialWaiting,
            BigInteger requestedAmount) {
        return new ExactCraftingJobLedger<>(
                taskTotals,
                initialWaiting,
                Map.of(),
                Map.of(),
                Map.of(),
                requestedAmount,
                requestedAmount);
    }

    /**
     * 物理Receiptから導出した絶対値Snapshotを一括反映する。
     *
     * <p>差分加算ではなく絶対値を使うため、同じReceiptを再起動後に再照合しても二重計上しない。</p>
     */
    public synchronized void reconcile(
            Map<T, BigInteger> absoluteDispatchedTasks,
            Map<K, BigInteger> absoluteIntroducedOutputs,
            Map<K, BigInteger> absoluteCreditedOutputs,
            BigInteger absoluteRemainingOutput) {
        Map<T, BigInteger> checkedDispatched =
                checkedCounts(absoluteDispatchedTasks, "dispatchedTasks", true);
        Map<K, BigInteger> checkedIntroduced =
                checkedCounts(absoluteIntroducedOutputs, "introducedOutputs", true);
        Map<K, BigInteger> checkedCredited =
                checkedCounts(absoluteCreditedOutputs, "creditedOutputs", true);
        BigInteger checkedRemaining =
                nonNegative(absoluteRemainingOutput, "remainingOutput");
        validateSnapshot(
                checkedDispatched,
                checkedIntroduced,
                checkedCredited,
                checkedRemaining);

        // 正常実行中のPattern配送、待機出力追加、出力受領、最終出力確定は巻き戻らない。
        for (var entry : dispatchedTasks.entrySet()) {
            // 前回保存済みの配送数より小さい絶対値は、古いReceiptの巻き戻しとして拒否する。
            if (checkedDispatched
                            .getOrDefault(entry.getKey(), BigInteger.ZERO)
                            .compareTo(entry.getValue())
                    < 0) {
                throw new IllegalStateException(
                        "exact crafting task dispatch progress moved backwards");
            }
        }
        // 一度waitingForへ導入した期待出力が、後のSnapshotで消えていないか全キー確認する。
        for (var entry : introducedOutputs.entrySet()) {
            // 導入済み量の減少は、Pattern投入会計を巻き戻すため拒否する。
            if (checkedIntroduced
                            .getOrDefault(entry.getKey(), BigInteger.ZERO)
                            .compareTo(entry.getValue())
                    < 0) {
                throw new IllegalStateException(
                        "exact crafting waiting output moved backwards");
            }
        }
        // 一度受領した物理出力が、後のSnapshotで未受領へ戻っていないか全キー確認する。
        for (var entry : creditedOutputs.entrySet()) {
            // 受領済み量の減少は、同じ出力を再度受け取る余地を作るため拒否する。
            if (checkedCredited
                            .getOrDefault(entry.getKey(), BigInteger.ZERO)
                            .compareTo(entry.getValue())
                    < 0) {
                throw new IllegalStateException(
                        "exact crafting output progress moved backwards");
            }
        }
        // 最終出力残数は減少方向だけを許し、増加する古いSnapshotを拒否する。
        if (checkedRemaining.compareTo(remainingOutput) > 0) {
            throw new IllegalStateException(
                    "exact crafting final-output progress moved backwards");
        }

        dispatchedTasks = checkedDispatched;
        introducedOutputs = checkedIntroduced;
        creditedOutputs = checkedCredited;
        remainingOutput = checkedRemaining;
    }

    public Map<T, BigInteger> taskTotals() {
        return taskTotals;
    }

    public Map<K, BigInteger> initialWaiting() {
        return initialWaiting;
    }

    public synchronized Map<T, BigInteger> dispatchedTasks() {
        return dispatchedTasks;
    }

    public synchronized Map<T, BigInteger> remainingTasks() {
        Map<T, BigInteger> remaining = new LinkedHashMap<>();
        // 固有Patternごとの残数だけを作り、注文数量ぶんの要素は生成しない。
        for (var entry : taskTotals.entrySet()) {
            BigInteger amount = entry.getValue().subtract(
                    dispatchedTasks.getOrDefault(entry.getKey(), BigInteger.ZERO));
            // 完了済み0件は実行待ちMapから除き、未完了タスクだけを返す。
            if (amount.signum() > 0) {
                remaining.put(entry.getKey(), amount);
            }
        }
        return Map.copyOf(remaining);
    }

    public synchronized Map<K, BigInteger> creditedOutputs() {
        return creditedOutputs;
    }

    public synchronized Map<K, BigInteger> introducedOutputs() {
        return introducedOutputs;
    }

    public synchronized Map<K, BigInteger> waitingFor() {
        Map<K, BigInteger> available =
                addCounts(initialWaiting, introducedOutputs);
        return subtractCounts(
                available,
                creditedOutputs,
                "creditedOutputs");
    }

    public BigInteger requestedAmount() {
        return requestedAmount;
    }

    public synchronized BigInteger remainingOutput() {
        return remainingOutput;
    }

    public synchronized boolean completeAndBalanced() {
        return remainingOutput.signum() == 0
                && waitingFor().isEmpty()
                && dispatchedTasks.equals(taskTotals);
    }

    public static long saturatedLong(BigInteger value) {
        BigInteger checked = nonNegative(value, "long projection");
        return checked.compareTo(LONG_MAX) >= 0
                ? Long.MAX_VALUE
                : checked.longValueExact();
    }

    private void validateSnapshot(
            Map<T, BigInteger> dispatched,
            Map<K, BigInteger> introduced,
            Map<K, BigInteger> credited,
            BigInteger remaining) {
        // 保存値に未知Patternが混ざる場合、別JobのSidecarを読み込んだ可能性があるため拒否する。
        for (var entry : dispatched.entrySet()) {
            BigInteger total = taskTotals.get(entry.getKey());
            // 未知Patternまたは計画総数超過は、別Job・重複Receiptとして拒否する。
            if (total == null || entry.getValue().compareTo(total) > 0) {
                throw new IllegalArgumentException(
                        "dispatched exact task is unknown or exceeds its total");
            }
        }
        Map<K, BigInteger> available =
                addCounts(initialWaiting, introduced);
        // 未投入Patternの出力や、現在までにwaitingForへ追加した量を超える受領を会計しない。
        subtractCounts(
                available,
                credited,
                "creditedOutputs");
        // 最終出力残数は注文総数以下だけを許し、NBTまたはReceiptの破損を拒否する。
        if (remaining.compareTo(requestedAmount) > 0) {
            throw new IllegalArgumentException(
                    "remaining exact output exceeds the requested amount");
        }
    }

    private static <K> Map<K, BigInteger> checkedCounts(
            Map<K, BigInteger> source,
            String name,
            boolean allowEmpty) {
        Objects.requireNonNull(source, name);
        // 初期タスク総数だけは空を禁止し、進める対象がない偽Jobを作らない。
        if (!allowEmpty && source.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        Map<K, BigInteger> checked = new LinkedHashMap<>();
        source.forEach((key, amount) -> {
            K checkedKey = Objects.requireNonNull(key, name + " key");
            BigInteger checkedAmount = nonNegative(amount, name + " amount");
            // 0件は保存・同期対象から外し、同じ意味の複数表現を作らない。
            if (checkedAmount.signum() > 0) {
                checked.put(checkedKey, checkedAmount);
            }
        });
        return Map.copyOf(checked);
    }

    private static <K> Map<K, BigInteger> addCounts(
            Map<K, BigInteger> left,
            Map<K, BigInteger> right) {
        Map<K, BigInteger> result =
                new LinkedHashMap<>(left);
        // Pattern投入時に追加された出力を、初期Emitter待機量へ正確に加算する。
        right.forEach((key, amount) ->
                result.merge(
                        key,
                        amount,
                        BigInteger::add));
        return result;
    }

    private static <K> Map<K, BigInteger> subtractCounts(
            Map<K, BigInteger> available,
            Map<K, BigInteger> consumed,
            String name) {
        Map<K, BigInteger> remaining =
                new LinkedHashMap<>(available);
        // 受領量は、その時点までにwaitingForへ登録済みの量を一件も超えてはならない。
        for (var entry : consumed.entrySet()) {
            BigInteger total = available.get(entry.getKey());
            // 登録前のキーまたは利用可能量超過の受領は、複製会計になるため拒否する。
            if (total == null
                    || entry.getValue().compareTo(total) > 0) {
                throw new IllegalArgumentException(
                        name + " contains an unknown or excessive count");
            }
            BigInteger rest =
                    total.subtract(entry.getValue());
            // 全量受領したキーは待機Mapから削除し、残量があるキーだけ保持する。
            if (rest.signum() == 0) {
                remaining.remove(entry.getKey());
            } else {
                remaining.put(
                        entry.getKey(),
                        rest);
            }
        }
        return Map.copyOf(remaining);
    }

    private static BigInteger positive(BigInteger value, String name) {
        BigInteger checked = nonNegative(value, name);
        // 注文総数0は実Jobを構成しないため、正数専用入口で拒否する。
        if (checked.signum() == 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return checked;
    }

    private static BigInteger nonNegative(BigInteger value, String name) {
        BigInteger checked = Objects.requireNonNull(value, name);
        // すべての会計値は所有量なので、負数を保存・投影しない。
        if (checked.signum() < 0) {
            throw new IllegalArgumentException(name + " must not be negative");
        }
        return checked;
    }
}
