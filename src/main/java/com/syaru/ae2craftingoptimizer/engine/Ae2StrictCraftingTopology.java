package com.syaru.ae2craftingoptimizer.engine;

import appeng.api.config.FuzzyMode;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Compiled Root ProgramがAE2標準結果と一致するための、実MOD API側の追加証明。
 * 既定高速経路では各slotが単一候補であることまで照合し、複数候補、複数Pattern、返却物、
 * 触媒、副産物、循環、動的ファジー候補を完全に排除できない場合は生成しない。
 */
final class Ae2StrictCraftingTopology {
    private final CompiledRootProgram<AEKey> program;
    private final List<InputProof> inputProofs;
    private volatile WideArithmeticPreflight.LongSafetyCertificate<AEKey> longSafetyCertificate;

    private Ae2StrictCraftingTopology(
            CompiledRootProgram<AEKey> program,
            List<InputProof> inputProofs) {
        this.program = program;
        this.inputProofs = List.copyOf(inputProofs);
    }

    @Nullable
    static Ae2StrictCraftingTopology compile(
            Level level,
            IGrid grid,
            Ae2CompiledCraftingGraphCache.Snapshot snapshot,
            CompiledRootProgram<AEKey> program) {
        return compile(level, grid, snapshot, program, PlanningGuard.none());
    }

    @Nullable
    static Ae2StrictCraftingTopology compile(
            Level level,
            IGrid grid,
            Ae2CompiledCraftingGraphCache.Snapshot snapshot,
            CompiledRootProgram<AEKey> program,
            PlanningGuard workBudget) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(grid, "grid");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(program, "program");
        Objects.requireNonNull(workBudget, "workBudget");
        // 異なるPattern世代のProgramをSnapshotへ誤接続しない。
        if (program.generation() != snapshot.graph().generation()) {
            return null;
        }

        ICraftingService service = grid.getCraftingService();
        List<InputProof> proofs = new ArrayList<>();
        // 配列Programの全ノードを一度だけ検証し、再帰や共有中間素材の二重訪問を避ける。
        for (int node = 0; node < program.nodeCount(); node++) {
            workBudget.checkpoint(node + 1);
            // 64ノード単位の検証対象を反復し、割込みをAE2 long経路へ黙って戻さない。
            if (Thread.currentThread().isInterrupted()) {
                throw new PlanningCancelledException(node);
            }
            AEKey key = program.keyAt(node);
            boolean currentlyEmittable = service.canEmitFor(key);
            // コンパイル時にEmitterだったキーは、現在も同じ供給経路を持つ必要がある。
            if (program.isEmittableAt(node)) {
                // Provider世代通知より先にEmitter状態が変わった場合も古いProgramを採用しない。
                if (!currentlyEmittable) {
                    return null;
                }
                continue;
            }
            // 非Emitterとして展開したキーが後からEmitterになった場合は、AE2の優先順位と異なるため拒否する。
            if (currentlyEmittable) {
                return null;
            }

            CompiledPattern<AEKey> pattern = program.patternAt(node);
            // Patternなし終端はAE2側にも未コンパイルPatternが存在しないことを証明する。
            if (pattern == null) {
                if (!snapshot.graph().patternsFor(key).isEmpty()
                        || snapshot.isIncompletelyCompiled(key)
                        || snapshot.registeredPatternCount(key) != 0) {
                    return null;
                }
                continue;
            }

            // AE2登録側とCompiled Graph側の双方でPatternが一つだけでなければ選択結果を証明できない。
            if (!snapshot.hasExactlyOneFullyCompiledPattern(key)) {
                return null;
            }
            CompiledPattern<AEKey> graphPattern = snapshot.graph().patternsFor(key).get(0);
            // Root Programが保持するPattern参照と現在SnapshotのPattern参照が一致しなければ古いProgramである。
            if (graphPattern != pattern) {
                return null;
            }
            // 副産物、返却物、触媒返却を含む複数出力はCompiled Root Programでは扱わない。
            if (pattern.outputs().size() != 1 || pattern.outputAmount(key) <= 0L) {
                return null;
            }

            IPatternDetails details = snapshot.pattern(pattern.id());
            // 実Patternのslot数と平坦化済み入力数が一致する場合だけ添字対応を検証する。
            if (details == null
                    || !details.getClass().getName().startsWith("appeng.")
                    || details.getInputs().length != pattern.inputs().size()
                    || details.getInputs().length != program.inputCountAt(node)) {
                return null;
            }

            // slotごとの全候補、キー、量、ファジー在庫、ファジーPatternを厳密に照合する。
            for (int slot = 0; slot < details.getInputs().length; slot++) {
                CompiledPattern.InputSlot<AEKey> compiledInput = pattern.inputs().get(slot);
                IPatternDetails.IInput realInput = details.getInputs()[slot];
                var realAlternatives = realInput.getPossibleInputs();
                // Issue #156の既定高速経路は、選択順が不要な単一候補slotだけを扱う。
                if (realAlternatives.length != 1) {
                    return null;
                }
                int compiledAlternatives =
                        program.inputAlternativeCountAt(
                                node,
                                slot);
                /*
                 * Compiled Graph、配列Program、実Patternの候補件数が一致しない場合は、
                 * Plannerの選択結果を物理assembleへ安全に渡せない。
                 */
                if (compiledInput.alternatives().size()
                                != realAlternatives.length
                        || compiledAlternatives
                                != realAlternatives.length) {
                    return null;
                }
                Set<AEKey> expectedKeys =
                        new LinkedHashSet<>(
                                realAlternatives.length);
                // 実Patternが列挙した候補順で、三つの表現が完全一致することを証明する。
                for (int alternative = 0;
                        alternative
                                < realAlternatives.length;
                        alternative++) {
                    CompiledPattern.Stack<AEKey> compiledStack =
                            compiledInput.alternatives()
                                    .get(alternative);
                    var realStack =
                            realAlternatives[alternative];
                    AEKey expectedKey =
                            program.inputAlternativeKeyAt(
                                    node,
                                    slot,
                                    alternative);
                    long expectedAmount =
                            program.inputAlternativeAmountAt(
                                    node,
                                    slot,
                                    alternative);
                    long realAmount;
                    try {
                        realAmount = Math.multiplyExact(
                                realStack.amount(),
                                realInput.getMultiplier());
                    } catch (ArithmeticException overflow) {
                        return null;
                    }
                    /*
                     * キー、係数、PatternのisValidが一つでも違えば、
                     * 古いProgramまたは非決定的入力としてFallbackする。
                     */
                    if (!compiledStack.key()
                                    .equals(expectedKey)
                            || compiledStack.amount()
                                    != expectedAmount
                            || !realStack.what()
                                    .equals(expectedKey)
                            || realAmount
                                    != expectedAmount
                            || !realInput.isValid(
                                    expectedKey,
                                    level)
                            || realInput.getRemainingKey(
                                    expectedKey) != null
                            || !expectedKeys.add(
                                    expectedKey)) {
                        return null;
                    }
                }
                // 列挙外のファジーPatternが選ばれ得る場合は、候補集合を完全証明できない。
                for (AEKey expectedKey :
                        expectedKeys) {
                    if (service.getCraftingFor(
                                    expectedKey)
                            .isEmpty()) {
                        AEKey fuzzy =
                                service.getFuzzyCraftable(
                                        expectedKey,
                                        candidate ->
                                                realInput.isValid(
                                                        candidate,
                                                        level));
                        // 明示候補集合にないPatternをAE2が選べる場合は高速経路へ入れない。
                        if (fuzzy != null
                                && !expectedKeys.contains(
                                        fuzzy)) {
                            return null;
                        }
                    }
                }
                proofs.add(new InputProof(
                        expectedKeys,
                        realInput,
                        level));
            }
        }
        return new Ae2StrictCraftingTopology(program, proofs);
    }

    /** 現在在庫にAE2が選び得る別NBT等がないことを、対象primary keyだけで確認する。 */
    boolean acceptsInventory(KeyCounter inventory) {
        Objects.requireNonNull(inventory, "inventory");
        // 入力ごとのprimary-key bucketだけを調べ、完全一致以外の有効候補を拒否する。
        for (InputProof proof : inputProofs) {
            // 別候補が一つでもあればAE2と同じ在庫選択を証明できない。
            if (hasFuzzyInventoryAlternative(
                    inventory,
                    proof.expected(),
                    proof.input(),
                    proof.level())) {
                return false;
            }
        }
        return true;
    }

    /** 計算終了時にもEmitter状態とファジー候補が変化していないことを再検証する。 */
    boolean remainsValid(IGrid grid) {
        ICraftingService service = grid.getCraftingService();
        // Emitterの追加・削除が世代通知前に起きても、古い計算結果を返さない。
        for (int node = 0; node < program.nodeCount(); node++) {
            // コンパイル時と現在のEmitter判定が異なる場合は再計算へ戻す。
            if (program.isEmittableAt(node) != service.canEmitFor(program.keyAt(node))) {
                return false;
            }
        }
        return acceptsInventory(grid.getStorageService().getCachedInventory());
    }

    private static boolean hasFuzzyInventoryAlternative(
            KeyCounter inventory,
            Set<AEKey> expected,
            IPatternDetails.IInput input,
            Level level) {
        // 明示候補ごとのprimary-key bucketだけを調べ、無関係なME在庫全体は走査しない。
        for (AEKey anchor :
                expected) {
            for (var candidateEntry :
                    inventory.findFuzzy(
                            anchor,
                            FuzzyMode.IGNORE_ALL)) {
                AEKey candidate =
                        candidateEntry.getKey();
                // 列挙外かつ有効な候補だけを、Planner結果を変え得る曖昧性として扱う。
                if (!expected.contains(candidate)
                        && input.isValid(
                                candidate,
                                level)) {
                    return true;
                }
            }
        }
        return false;
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
        // 保守的上界だけでwideを確定せず、従来の正確なBigInteger計画で最終判定する。
        boolean exactWide = WideArithmeticPreflight.requiresWideArithmetic(
                root,
                requestedAmount,
                program,
                key -> key.getType().getAmountPerByte(),
                maximumBits);
        // 正確にlong内と確定した最大量も記録し、代替候補を持つ同一Rootの再計算を省く。
        if (!exactWide) {
            certificate.recordExactSafe(requestedAmount);
        }
        return exactWide;
    }

    Optional<Boolean> cachedLongSafetyCertificate(
            BigInteger requestedAmount,
            int maximumBits) {
        WideArithmeticPreflight.LongSafetyCertificate<AEKey> cached = longSafetyCertificate;
        // cold pathでは証明器を新規作成せず、非同期Planner側へ仕事を残す。
        if (cached == null || cached.maximumBits() != maximumBits) {
            return Optional.empty();
        }
        // 未証明の大きい注文はfalseと固定せず、exact captureを選ばせるためemptyを返す。
        return cached.certifiesCached(requestedAmount)
                ? Optional.of(true)
                : Optional.empty();
    }

    private WideArithmeticPreflight.LongSafetyCertificate<AEKey> longSafetyCertificate(int maximumBits) {
        WideArithmeticPreflight.LongSafetyCertificate<AEKey> cached = longSafetyCertificate;
        // Config bit幅が変わった場合だけ、同じ世代の安全証明を作り直す。
        if (cached == null || cached.maximumBits() != maximumBits) {
            synchronized (this) {
                cached = longSafetyCertificate;
                // 同じTopologyを共有する非同期計算が証明器を重複作成しないよう一度だけ確定する。
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

    private record InputProof(
            Set<AEKey> expected,
            IPatternDetails.IInput input,
            Level level) {
        private InputProof {
            expected = Set.copyOf(
                    Objects.requireNonNull(
                            expected,
                            "expected"));
            Objects.requireNonNull(input, "input");
            Objects.requireNonNull(level, "level");
            // 空候補集合ではPattern slotを再検証できないため構築を拒否する。
            if (expected.isEmpty()) {
                throw new IllegalArgumentException(
                        "input proof must contain candidates");
            }
        }
    }
}
