package com.syaru.ae2craftingoptimizer.engine.vector;

import com.syaru.ae2craftingoptimizer.engine.CompiledRootProgram;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 実行されるPattern DAGを一巡し、数量と無関係な最長依存段数を求める。 */
public final class VectorCriticalPathCalculator {
    private VectorCriticalPathCalculator() {
    }

    public static <K> int calculate(
            CompiledRootProgram<K> program,
            Set<String> executedPatternIds) {
        Objects.requireNonNull(program, "program");
        Set<String> executed = Set.copyOf(
                Objects.requireNonNull(executedPatternIds, "executedPatternIds"));
        int nodes = program.nodeCount();
        List<List<Integer>> dependents = new ArrayList<>(nodes);
        int[] dependencyCounts = new int[nodes];
        int[] depths = new int[nodes];
        boolean[] active = new boolean[nodes];
        int activePatterns = 0;

        // ノードごとの逆辺Listを一度だけ確保する。
        for (int node = 0; node < nodes; node++) {
            dependents.add(new ArrayList<>());
            String patternId = program.patternIdAt(node);
            if (patternId != null && executed.contains(patternId)) {
                active[node] = true;
                activePatterns++;
            }
        }

        // 実際に実行量を持つPattern同士の依存辺だけを登録する。
        for (int consumer = 0; consumer < nodes; consumer++) {
            if (!active[consumer]) {
                continue;
            }
            Set<Integer> uniqueDependencies = new HashSet<>();
            for (int input = 0; input < program.inputCountAt(consumer); input++) {
                int producer = program.indexOf(
                        program.inputKeyAt(consumer, input));
                // 在庫終端または実行0のPatternはactive pathへ含めない。
                if (producer < 0
                        || !active[producer]
                        || !uniqueDependencies.add(producer)) {
                    continue;
                }
                dependencyCounts[consumer]++;
                dependents.get(producer).add(consumer);
            }
        }

        ArrayDeque<Integer> ready = new ArrayDeque<>();
        // 依存する実行Patternがない最初の段をdepth 1として開始する。
        for (int node = 0; node < nodes; node++) {
            if (active[node] && dependencyCounts[node] == 0) {
                depths[node] = 1;
                ready.addLast(node);
            }
        }

        int visited = 0;
        int criticalPath = 0;
        // Kahn順に親へ最大深度を伝播し、再帰stackを使わない。
        while (!ready.isEmpty()) {
            int producer = ready.removeFirst();
            visited++;
            criticalPath = Math.max(criticalPath, depths[producer]);
            for (int consumer : dependents.get(producer)) {
                depths[consumer] = Math.max(
                        depths[consumer],
                        Math.addExact(depths[producer], 1));
                int remaining = --dependencyCounts[consumer];
                // 全依存Patternの深度が揃った時だけ親を確定する。
                if (remaining == 0) {
                    ready.addLast(consumer);
                }
            }
        }
        if (visited != activePatterns || criticalPath <= 0) {
            throw new IllegalArgumentException(
                    "executed vector patterns do not form a non-empty DAG");
        }
        return criticalPath;
    }
}
