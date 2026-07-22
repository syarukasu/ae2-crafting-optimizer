package com.syaru.ae2craftingoptimizer.engine;

import appeng.api.config.FuzzyMode;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGrid;
import appeng.api.networking.crafting.ICraftingService;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Compiled Root ProgramがAE2標準結果と一致するための、実MOD API側の追加証明。
 * タグ候補、複数Pattern、返却物、触媒、循環、ファジー候補を完全に排除できない場合は生成しない。
 */
final class Ae2StrictCraftingTopology {
    private final CompiledRootProgram<AEKey> program;
    private final List<InputProof> inputProofs;

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
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(grid, "grid");
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(program, "program");
        // 異なるPattern世代のProgramをSnapshotへ誤接続しない。
        if (program.generation() != snapshot.graph().generation()) {
            return null;
        }

        ICraftingService service = grid.getCraftingService();
        List<InputProof> proofs = new ArrayList<>();
        // 配列Programの全ノードを一度だけ検証し、再帰や共有中間素材の二重訪問を避ける。
        for (int node = 0; node < program.nodeCount(); node++) {
            // 計算キャンセル時は検証を継続せず、AE2呼出側へFallbackする。
            if (Thread.currentThread().isInterrupted()) {
                return null;
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
                    || details.getInputs().length != pattern.inputs().size()
                    || details.getInputs().length != program.inputCountAt(node)) {
                return null;
            }

            // slotごとの候補、キー、量、ファジー在庫、ファジーPatternを厳密に照合する。
            for (int slot = 0; slot < details.getInputs().length; slot++) {
                CompiledPattern.InputSlot<AEKey> compiledInput = pattern.inputs().get(slot);
                IPatternDetails.IInput realInput = details.getInputs()[slot];
                // 複数候補を一つへ決め打ちすると在庫利用結果が変わるため、必ずFallbackする。
                if (compiledInput.alternatives().size() != 1
                        || realInput.getPossibleInputs().length != 1) {
                    return null;
                }
                CompiledPattern.Stack<AEKey> compiledStack = compiledInput.alternatives().get(0);
                AEKey expectedKey = program.inputKeyAt(node, slot);
                // 平坦化前後でキーまたは一回入力量が異なるProgramは採用しない。
                if (!compiledStack.key().equals(expectedKey)
                        || compiledStack.amount() != program.inputAmountAt(node, slot)) {
                    return null;
                }
                // 実Patternが期待キーを受け付けない場合は、コンパイル結果が不正なのでFallbackする。
                if (!realInput.isValid(expectedKey, level)) {
                    return null;
                }
                // 完全一致Patternがない入力で別のファジーcraftableが見つかる場合もAE2へ戻す。
                if (service.getCraftingFor(expectedKey).isEmpty()) {
                    AEKey fuzzy = service.getFuzzyCraftable(
                            expectedKey,
                            candidate -> realInput.isValid(candidate, level));
                    // 期待キー以外のPattern候補が存在すれば選択順を証明できない。
                    if (fuzzy != null && !fuzzy.equals(expectedKey)) {
                        return null;
                    }
                }
                proofs.add(new InputProof(expectedKey, realInput, level));
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
            AEKey expected,
            IPatternDetails.IInput input,
            Level level) {
        // KeyCounterのprimary-key索引を使い、無関係なME在庫全体は走査しない。
        for (var candidateEntry : inventory.findFuzzy(expected, FuzzyMode.IGNORE_ALL)) {
            AEKey candidate = candidateEntry.getKey();
            // 完全一致以外の有効候補だけを曖昧性として扱う。
            if (!candidate.equals(expected) && input.isValid(candidate, level)) {
                return true;
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
        return WideArithmeticPreflight.requiresWideArithmetic(
                root,
                requestedAmount,
                program.patternsByOutput(),
                key -> key.getType().getAmountPerByte(),
                maximumBits);
    }

    private record InputProof(
            AEKey expected,
            IPatternDetails.IInput input,
            Level level) {
        private InputProof {
            Objects.requireNonNull(expected, "expected");
            Objects.requireNonNull(input, "input");
            Objects.requireNonNull(level, "level");
        }
    }
}
