package com.syaru.ae2craftingoptimizer.api.execution;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.me.service.CraftingService;
import com.syaru.ae2craftingoptimizer.AE2CraftingOptimizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ACOをAACなどの任意設備MODへ直接依存させずに接続する、登録順固定のBackendレジストリ。
 */
public final class CraftingIslandBackendRegistry {
    /** 外部Backendがコンパイル時に合わせる公開契約版。 */
    public static final int API_VERSION = 1;

    private static final Map<String, CraftingIslandBackend> BACKENDS =
            new LinkedHashMap<>();
    private static final Set<String> LOGGED_FAILURES =
            ConcurrentHashMap.newKeySet();

    private CraftingIslandBackendRegistry() {
    }

    /**
     * 外部設備Backendを一度だけ登録する。
     *
     * @throws IllegalArgumentException IDが空、または同じIDが既に別実装へ使われている場合
     */
    public static synchronized void register(CraftingIslandBackend backend) {
        Objects.requireNonNull(backend, "backend");
        String id = Objects.requireNonNull(
                        backend.acoBackendId(),
                        "backend id")
                .trim();
        // 空IDはログ上で設備を識別できないため登録しない。
        if (id.isEmpty()) {
            throw new IllegalArgumentException(
                    "Crafting Island backend id must not be empty");
        }
        CraftingIslandBackend previous = BACKENDS.get(id);
        // 同じインスタンスの再登録だけはMOD lifecycleの冪等呼出しとして許可する。
        if (previous == backend) {
            return;
        }
        if (previous != null) {
            throw new IllegalArgumentException(
                    "Crafting Island backend id is already registered: " + id);
        }
        BACKENDS.put(id, backend);
        AE2CraftingOptimizer.LOGGER.info(
                "Registered Compiled Crafting Island backend: {} (API v{})",
                id,
                API_VERSION);
    }

    /**
     * 全Patternを同時所有できる最初のBackendを、登録順に選択する。
     */
    public static Optional<CraftingIslandBackendSession> openFirst(
            IGrid grid,
            CraftingService craftingService,
            List<IPatternDetails> patterns) {
        Objects.requireNonNull(grid, "grid");
        Objects.requireNonNull(craftingService, "craftingService");
        Objects.requireNonNull(patterns, "patterns");
        // 呼出側の可変Task Mapから切り離し、Backendが途中変更された一覧を見ないようにする。
        List<IPatternDetails> immutablePatterns = List.copyOf(patterns);
        // Patternなしでは設備所有権を証明する対象がないため、Backendを呼び出さない。
        if (immutablePatterns.isEmpty()) {
            return Optional.empty();
        }

        List<Map.Entry<String, CraftingIslandBackend>> snapshot;
        synchronized (CraftingIslandBackendRegistry.class) {
            snapshot = new ArrayList<>(BACKENDS.entrySet());
        }
        // 登録順に試し、同じ構成では常に同じBackendが選ばれるようにする。
        for (Map.Entry<String, CraftingIslandBackend> entry : snapshot) {
            try {
                Optional<CraftingIslandBackendSession> opened =
                        entry.getValue().acoOpenSession(
                                grid,
                                craftingService,
                                immutablePatterns);
                // このGridを所有しないBackendは、次の登録実装へ判定を譲る。
                if (opened.isEmpty()) {
                    continue;
                }
                CraftingIslandBackendSession session = opened.orElseThrow();
                // Backend実装の取りこぼしを防ぎ、ACO側でも全Pattern所有権を確認する。
                boolean supportsAll = true;
                for (IPatternDetails pattern : immutablePatterns) {
                    // 一件でも対象外なら複数設備を混ぜず、セッション全体を拒否する。
                    if (!session.acoSupportsPattern(pattern)) {
                        supportsAll = false;
                        break;
                    }
                }
                // 正の容量とcommit直前再検証が揃ったSessionだけをCPUへ渡す。
                if (supportsAll
                        && session.acoRootExecutionCapacity() > 0L
                        && session.acoStillAvailable()) {
                    return Optional.of(session);
                }
            } catch (RuntimeException failure) {
                // 外部Backend一つの破損で通常AE2配送まで止めず、同じ例外は一度だけ報告する。
                String failureKey =
                        entry.getKey() + ':' + failure.getClass().getName();
                // 同じBackendと例外型を毎tick出さず、最初の一件だけ原因を残す。
                if (LOGGED_FAILURES.add(failureKey)) {
                    AE2CraftingOptimizer.LOGGER.error(
                            "Compiled Crafting Island backend {} failed while opening a session",
                            entry.getKey(),
                            failure);
                }
            }
        }
        return Optional.empty();
    }
}
