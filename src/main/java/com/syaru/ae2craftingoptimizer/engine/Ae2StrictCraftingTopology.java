package com.syaru.ae2craftingoptimizer.engine;

import appeng.api.stacks.AEKey;
import com.syaru.ae2craftingoptimizer.optimization.ProviderPatternGenerationTracker;
import java.math.BigInteger;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.jetbrains.annotations.Nullable;

/**
 * Compiled Root ProgramがAE2標準結果と一致するための不変Topology証明。
 * Issue #167以降、このクラスはlive Grid、Level、Pattern APIを読まず、server threadで
 * capture済みの候補件数、Emitter状態、入力domainだけを検査する。
 */
final class Ae2StrictCraftingTopology {
    private final CompiledRootProgram<AEKey> program;
    private final long recipeGeneration;
    private volatile WideArithmeticPreflight.LongSafetyCertificate<AEKey> longSafetyCertificate;

    private Ae2StrictCraftingTopology(
            CompiledRootProgram<AEKey> program,
            long recipeGeneration) {
        this.program = program;
        this.recipeGeneration = recipeGeneration;
    }

    @Nullable
    static Ae2StrictCraftingTopology compile(
            Ae2PlanningGraphSnapshot snapshot,
            CompiledRootProgram<AEKey> program) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(program, "program");
        // 異なるPattern世代のProgramをSnapshotへ接続しない。
        if (program.generation() != snapshot.graph().generation()) {
            return null;
        }
        /*
         * Issue #167: 共有中間素材を持つDAGでは数量計画は正しくても、AE2の展開木に対する
         * CPU bytesを固有ノード実行数から再現できない。過大なCPU容量判定を返さないため、
         * 各入力キーが一度だけ現れる木構造を証明できる場合に限ってexact結果を採用する。
         */
        if (!program.hasUniqueInputOccurrencePerKey()) {
            return null;
        }

        // 配列Programの全ノードを一巡し、live AE2状態をworkerから再読込しない。
        for (int node = 0; node < program.nodeCount(); node++) {
            // cancellationをAE2 fallbackへ読み替えず、呼出元へ明示的に伝える。
            if (Thread.currentThread().isInterrupted()) {
                throw new PlanningCancelledException(node);
            }
            AEKey key = program.keyAt(node);
            // Capture時のEmitter判定とProgram終端が一致しなければ証明を拒否する。
            if (program.isEmittableAt(node) != snapshot.isEmittable(key)) {
                return null;
            }
            if (program.isEmittableAt(node)) {
                continue;
            }

            CompiledPattern<AEKey> pattern = program.patternAt(node);
            // Patternなし終端はAE2側にも候補が存在しない場合だけ許可する。
            if (pattern == null) {
                if (!snapshot.graph().patternsFor(key).isEmpty()
                        || snapshot.isIncompletelyCompiled(key)
                        || snapshot.registeredPatternCount(key) != 0) {
                    return null;
                }
                continue;
            }

            // AE2登録側とCompiled Graph側の双方で単一Patternでなければ選択結果を証明できない。
            if (!snapshot.hasExactlyOneFullyCompiledPattern(key)) {
                return null;
            }
            CompiledPattern<AEKey> graphPattern = snapshot.graph().patternsFor(key).get(0);
            if (graphPattern != pattern) {
                return null;
            }
            // 副産物・返却物を含む複数出力は一巡会計へ入れない。
            if (pattern.outputs().size() != 1 || pattern.outputAmount(key) <= 0L) {
                return null;
            }
            // Level依存の代替候補を持つPatternは、server threadのcapture時点で除外済みである必要がある。
            if (!snapshot.hasExactInputDomain(pattern.id())) {
                return null;
            }
            // exact domainでは各slotが一候補であることも配列上で再確認する。
            for (int slot = 0; slot < program.inputCountAt(node); slot++) {
                if (program.inputAlternativeCountAt(node, slot) != 1) {
                    return null;
                }
            }
        }
        return new Ae2StrictCraftingTopology(program, snapshot.recipeGeneration());
    }

    /** exact input domainは在庫内の別NBT・タグ候補を選択しないため、追加のlive走査を要しない。 */
    boolean acceptsInventory() {
        return true;
    }

    /** Capture後にPatternまたはrecipe世代が変わっていないことだけをatomic値で検証する。 */
    boolean remainsCurrent() {
        return program.generation() == ProviderPatternGenerationTracker.generation()
                && recipeGeneration == RecipeGenerationTracker.generation();
    }

    Map<AEKey, CompiledPattern<AEKey>> patternByOutput() {
        return program.patternsByOutput();
    }

    BigInteger calculateBigExactBytes(
            AEKey root,
            BigInteger requestedAmount,
            Map<String, BigInteger> executions,
            int maximumBits) {
        return BigExactCraftingByteCounter.calculate(
                root,
                requestedAmount,
                program.patternsByOutput(),
                executions,
                key -> key.getType().getAmountPerByte(),
                maximumBits);
    }

    /** 通常long計画ではAE2 15.4.10と同じdouble加算順と飽和castを維持する。 */
    long calculateAe2LongBytes(
            AEKey root,
            long requestedAmount,
            Map<String, Long> executions) {
        return ExactCraftingByteCounter.calculate(
                root,
                requestedAmount,
                program.patternsByOutput(),
                executions,
                key -> key.getType().getAmountPerByte());
    }

    boolean mightRequireWideArithmetic(
            AEKey root,
            BigInteger requestedAmount,
            int maximumBits) {
        WideArithmeticPreflight.LongSafetyCertificate<AEKey> certificate =
                longSafetyCertificate(maximumBits);
        // 上界でlong安全を証明できた注文は、正確なBigInteger計画を重ねずに終了する。
        if (certificate.certify(requestedAmount)) {
            return false;
        }
        // 保守的上界だけでwideを確定せず、正確なBigInteger計画で最終判定する。
        boolean exactWide = WideArithmeticPreflight.requiresWideArithmetic(
                root,
                requestedAmount,
                program,
                key -> key.getType().getAmountPerByte(),
                maximumBits);
        if (!exactWide) {
            certificate.recordExactSafe(requestedAmount);
        }
        return exactWide;
    }

    Optional<Boolean> cachedLongSafetyCertificate(
            BigInteger requestedAmount,
            int maximumBits) {
        WideArithmeticPreflight.LongSafetyCertificate<AEKey> cached = longSafetyCertificate;
        // cold pathでは証明器を作らず、planning worker側へ仕事を残す。
        if (cached == null || cached.maximumBits() != maximumBits) {
            return Optional.empty();
        }
        return cached.certifiesCached(requestedAmount)
                ? Optional.of(true)
                : Optional.empty();
    }

    private WideArithmeticPreflight.LongSafetyCertificate<AEKey> longSafetyCertificate(
            int maximumBits) {
        WideArithmeticPreflight.LongSafetyCertificate<AEKey> cached = longSafetyCertificate;
        // Config bit幅が変わった場合だけ、同じ不変Topologyの証明器を作り直す。
        if (cached == null || cached.maximumBits() != maximumBits) {
            synchronized (this) {
                cached = longSafetyCertificate;
                if (cached == null || cached.maximumBits() != maximumBits) {
                    cached = WideArithmeticPreflight.longSafetyCertificate(
                            program,
                            key -> key.getType().getAmountPerByte(),
                            maximumBits);
                    longSafetyCertificate = cached;
                }
            }
        }
        return cached;
    }
}
