from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    file = Path(path)
    text = file.read_text(encoding="utf-8")
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{path}: expected exactly one match, found {count}")
    file.write_text(text.replace(old, new, 1), encoding="utf-8")


mixin_path = "src/main/java/com/syaru/ae2craftingoptimizer/mixin/CraftingProviderRefreshCoalescingMixin.java"
Path(mixin_path).write_text(
    r'''package com.syaru.ae2craftingoptimizer.mixin;

import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.stacks.AEKey;
import appeng.me.service.CraftingService;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.optimization.ProviderPatternGenerationTracker;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = CraftingService.class, remap = false)
public abstract class CraftingProviderRefreshCoalescingMixin {
    @Unique
    private final Set<IGridNode> aco$pendingProviderRefreshes =
            Collections.newSetFromMap(new IdentityHashMap<>());

    @Unique
    private boolean aco$flushingProviderRefreshes;

    @Inject(method = "refreshNodeCraftingProvider", at = @At("HEAD"), cancellable = true)
    private void aco$queueProviderRefresh(IGridNode node, CallbackInfo ci) {
        if (aco$flushingProviderRefreshes) {
            return;
        }

        if (!ACOConfig.coalesceCraftingProviderRefreshes()) {
            return;
        }

        aco$pendingProviderRefreshes.add(node);
        ci.cancel();
    }

    @Inject(method = "refreshNodeCraftingProvider", at = @At("TAIL"))
    private void aco$publishProviderRefresh(IGridNode node, CallbackInfo ci) {
        // Publish the new generation only after AE2 replaced its provider index.
        ProviderPatternGenerationTracker.shouldRefresh(node);
    }

    @Inject(method = "onServerEndTick", at = @At("HEAD"))
    private void aco$flushProviderRefreshesAtTickEnd(CallbackInfo ci) {
        aco$flushProviderRefreshes();
    }

    @Inject(method = "beginCraftingCalculation", at = @At("HEAD"))
    private void aco$flushProviderRefreshesBeforeCalculation(
            Level level,
            ICraftingSimulationRequester requester,
            AEKey what,
            long amount,
            CalculationStrategy strategy,
            CallbackInfoReturnable<?> cir) {
        aco$flushProviderRefreshes();
    }

    @Inject(method = "addNode", at = @At("HEAD"))
    private void aco$dropPendingRefreshOnNodeAdd(IGridNode node, CompoundTag savedData, CallbackInfo ci) {
        aco$pendingProviderRefreshes.remove(node);
    }

    @Inject(method = "addNode", at = @At("RETURN"))
    private void aco$publishProviderAfterNodeAdd(IGridNode node, CompoundTag savedData, CallbackInfo ci) {
        // addNode mutates AE2's provider index directly, so invalidate only after it completes.
        ProviderPatternGenerationTracker.forget(node);
        ProviderPatternGenerationTracker.remember(node);
    }

    @Inject(method = "removeNode", at = @At("HEAD"))
    private void aco$dropPendingRefreshOnNodeRemove(IGridNode node, CallbackInfo ci) {
        aco$pendingProviderRefreshes.remove(node);
    }

    @Inject(method = "removeNode", at = @At("RETURN"))
    private void aco$publishProviderAfterNodeRemove(IGridNode node, CallbackInfo ci) {
        // removeNode mutates AE2's provider index directly, so invalidate only after it completes.
        ProviderPatternGenerationTracker.forget(node);
    }

    @Unique
    private void aco$flushProviderRefreshes() {
        if (!ACOConfig.coalesceCraftingProviderRefreshes()
                || aco$flushingProviderRefreshes
                || aco$pendingProviderRefreshes.isEmpty()) {
            return;
        }

        var pending = new ArrayList<>(aco$pendingProviderRefreshes);
        aco$pendingProviderRefreshes.clear();
        aco$flushingProviderRefreshes = true;
        try {
            CraftingService service = (CraftingService) (Object) this;
            for (IGridNode node : pending) {
                // Collapse duplicates from one tick, but let the target method finish before
                // aco$publishProviderRefresh exposes the corresponding generation.
                service.refreshNodeCraftingProvider(node);
            }
        } finally {
            aco$flushingProviderRefreshes = false;
        }
    }
}
''',
    encoding="utf-8",
)

cache_path = "src/main/java/com/syaru/ae2craftingoptimizer/engine/Ae2CompiledCraftingGraphCache.java"
replace_once(
    cache_path,
    '''        private final Map<AEKey, Optional<CompiledRootProgram<AEKey>>> rootPrograms =
                new LinkedHashMap<>();
''',
    '''        private final Map<AEKey, CompiledRootProgram<AEKey>> rootPrograms =
                new LinkedHashMap<>();
''',
)

old_root_program = '''        /**
         * 同じProvider/recipe世代ではルートごとの数式Programを再利用する。
         * 世代変更時はSnapshotごと破棄されるため、古いPattern参照は残らない。
         */
        public Optional<CompiledRootProgram<AEKey>> rootProgram(AEKey root) {
            synchronized (rootPrograms) {
                Optional<CompiledRootProgram<AEKey>> cached = rootPrograms.get(root);
                // 既に成功またはFallbackが確定したルートは、同じ世代中に再探索しない。
                if (cached != null) {
                    return cached;
                }
            }

            Optional<CompiledRootProgram<AEKey>> compiled = CompiledRootProgram.tryCompile(
                    graph,
                    root,
                    service::canEmitFor);
            if (compiled.isPresent() && touchesIncompletePattern(compiled.get())) {
                // 未コンパイルPatternを終端素材と誤認したShadow計算も作らず、直ちにAE2へ戻す。
                compiled = Optional.empty();
            }
            synchronized (rootPrograms) {
                Optional<CompiledRootProgram<AEKey>> raced = rootPrograms.get(root);
                // 別計算スレッドが先に登録した場合は、その同一世代Programを採用する。
                if (raced != null) {
                    return raced;
                }
                // 固定上限へ達した場合は古いルートを一括破棄し、無制限な常駐を防ぐ。
                if (rootPrograms.size() >= MAXIMUM_ROOT_PROGRAMS_PER_SNAPSHOT) {
                    rootPrograms.clear();
                    strictTopologies.clear();
                }
                rootPrograms.put(root, compiled);
                return compiled;
            }
        }

'''
new_root_program = '''        /**
         * 同じProvider/recipe世代では、正常にコンパイルできたルートProgramだけを再利用する。
         * 一時的なcanEmitFor不一致や更新途中の失敗は固定せず、次の要求で再評価する。
         */
        public Optional<CompiledRootProgram<AEKey>> rootProgram(AEKey root) {
            requireCurrentGenerations();
            synchronized (rootPrograms) {
                CompiledRootProgram<AEKey> cached = rootPrograms.get(root);
                if (cached != null) {
                    return Optional.of(cached);
                }
            }

            Optional<CompiledRootProgram<AEKey>> compiled = CompiledRootProgram.tryCompile(
                    graph,
                    root,
                    service::canEmitFor);
            if (compiled.isPresent() && touchesIncompletePattern(compiled.get())) {
                // 未コンパイルPatternを終端素材と誤認したShadow計算も作らず、直ちにAE2へ戻す。
                compiled = Optional.empty();
            }

            // Lazy compileの途中にprovider/recipe世代が変わった結果は、Fallbackとして固定しない。
            requireCurrentGenerations();
            if (compiled.isEmpty()) {
                return Optional.empty();
            }

            CompiledRootProgram<AEKey> candidate = compiled.orElseThrow();
            synchronized (rootPrograms) {
                CompiledRootProgram<AEKey> raced = rootPrograms.get(root);
                // 別計算スレッドが先に登録した場合は、その同一世代Programを採用する。
                if (raced != null) {
                    return Optional.of(raced);
                }
                // 固定上限へ達した場合は古いルートを一括破棄し、無制限な常駐を防ぐ。
                if (rootPrograms.size() >= MAXIMUM_ROOT_PROGRAMS_PER_SNAPSHOT) {
                    rootPrograms.clear();
                    strictTopologies.clear();
                }
                rootPrograms.put(root, candidate);
                return Optional.of(candidate);
            }
        }

        private void requireCurrentGenerations() {
            long currentPatternGeneration = ProviderPatternGenerationTracker.generation();
            long currentRecipeGeneration = RecipeGenerationTracker.generation();
            if (graph.generation() != currentPatternGeneration
                    || recipeGeneration != currentRecipeGeneration) {
                throw new StalePlanningSnapshotException(
                        new PlanningGenerationSnapshot(
                                graph.generation(),
                                0L,
                                recipeGeneration),
                        0);
            }
        }

'''
replace_once(cache_path, old_root_program, new_root_program)

replace_once(
    cache_path,
    '''        Optional<Ae2StrictCraftingTopology> strictTopology(
                Level level,
                IGrid grid,
                CompiledRootProgram<AEKey> program) {
            AEKey root = program.root();
''',
    '''        Optional<Ae2StrictCraftingTopology> strictTopology(
                Level level,
                IGrid grid,
                CompiledRootProgram<AEKey> program) {
            requireCurrentGenerations();
            AEKey root = program.root();
''',
)
replace_once(
    cache_path,
    '''            Optional<Ae2StrictCraftingTopology> compiled = Optional.ofNullable(
                    Ae2StrictCraftingTopology.compile(level, grid, this, program));
            synchronized (rootPrograms) {
''',
    '''            Optional<Ae2StrictCraftingTopology> compiled = Optional.ofNullable(
                    Ae2StrictCraftingTopology.compile(level, grid, this, program));
            requireCurrentGenerations();
            synchronized (rootPrograms) {
''',
)

replace_once(
    "src/main/java/com/syaru/ae2craftingoptimizer/engine/Ae2AuthoritativeCraftingPlanner.java",
    '''                CraftingFallbackDiagnostics.record(output, capture.patternGeneration(), capture.recipeGeneration(),
                        FallbackReasonCode.AMBIGUOUS_PRODUCER);
''',
    '''                CraftingFallbackDiagnostics.record(output, capture.patternGeneration(), capture.recipeGeneration(),
                        FallbackReasonCode.NO_COMPILED_PROGRAM);
''',
)

replace_once(
    "src/main/java/com/syaru/ae2craftingoptimizer/optimization/FallbackReasonCode.java",
    '''public enum FallbackReasonCode {
    AMBIGUOUS_PRODUCER,
''',
    '''public enum FallbackReasonCode {
    NO_COMPILED_PROGRAM,
    AMBIGUOUS_PRODUCER,
''',
)

replace_once(
    "gradle.properties",
    "mod_version=1.5.19",
    "mod_version=1.5.20",
)

replace_once(
    "CHANGELOG.md",
    "## [Unreleased]\n",
    '''## [Unreleased]

## [1.5.20] - 2026-08-16

### Fixed

- Published crafting-provider generations only after AE2 finished replacing its
  provider index, preventing an asynchronous planner from accepting a new
  generation with an old graph.
- Revalidated provider and recipe generations around lazy root-program and
  strict-topology compilation.
- Cached only successful root programs, so a transient `canEmitFor` mismatch no
  longer pins `NO_COMPILED_PROGRAM` for the lifetime of a graph snapshot.
- Reported a missing compiled root program separately from producer ambiguity;
  CPU suitability remains a later submission diagnostic and is not used as the
  planner failure reason.

''',
)

release_notes = '''# AE2 Crafting Optimizer 1.5.20

This hotfix resolves issue #103, where asynchronous planning could observe a
provider generation that had already advanced while AE2's crafting-provider
index was still on the previous state.

## Fixed

- Provider generation changes are now published after AE2 completes provider
  refresh, add, and remove mutations.
- Lazy root-program and strict-topology compilation verifies the provider and
  recipe generations before accepting its result.
- Failed root-program compilation is no longer negatively cached. A temporary
  `canEmitFor` mismatch can therefore recover on the next attempt.
- `NO_COMPILED_PROGRAM` is recorded as its own planner diagnostic instead of
  being folded into `AMBIGUOUS_PRODUCER` or confused with later CPU capacity
  selection.

## Compatibility

- NeoForge 1.21.1 / Java 21 / AE2 19.2.17.
- Forge 1.20.1 compatibility is released from the matching maintenance branch.
'''
Path("RELEASE_NOTES_1.5.20.md").write_text(release_notes, encoding="utf-8")

mixin_test = r'''package com.syaru.ae2craftingoptimizer.mixin;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class CraftingProviderRefreshGenerationContractTest {
    private static final Path SOURCE = Path.of(
            "src/main/java/com/syaru/ae2craftingoptimizer/mixin/"
                    + "CraftingProviderRefreshCoalescingMixin.java");

    @Test
    void providerGenerationIsPublishedAfterAe2Mutation() throws Exception {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains(
                "@Inject(method = \"refreshNodeCraftingProvider\", at = @At(\"TAIL\"))"));
        assertTrue(source.contains(
                "private void aco$publishProviderRefresh(IGridNode node, CallbackInfo ci)"));

        int queueStart = source.indexOf("private void aco$queueProviderRefresh");
        int queueEnd = source.indexOf(
                "@Inject(method = \"refreshNodeCraftingProvider\", at = @At(\"TAIL\"))");
        assertTrue(queueStart >= 0 && queueEnd > queueStart);
        assertFalse(
                source.substring(queueStart, queueEnd)
                        .contains("ProviderPatternGenerationTracker.shouldRefresh(node)"),
                "refresh HEAD must not publish a generation before AE2 updates its index");

        int flushStart = source.indexOf("private void aco$flushProviderRefreshes()");
        assertTrue(flushStart >= 0);
        assertFalse(
                source.substring(flushStart)
                        .contains(
                                "ProviderPatternGenerationTracker.shouldRefresh(node);\n"
                                        + "                service.refreshNodeCraftingProvider(node);"),
                "coalesced refresh must not publish before calling AE2");
    }

    @Test
    void nodeAddAndRemovePublishAtReturn() throws Exception {
        String source = Files.readString(SOURCE);

        assertTrue(source.contains("@Inject(method = \"addNode\", at = @At(\"RETURN\"))"));
        assertTrue(source.contains("@Inject(method = \"removeNode\", at = @At(\"RETURN\"))"));

        int addHead = source.indexOf("private void aco$dropPendingRefreshOnNodeAdd");
        int addReturn = source.indexOf("private void aco$publishProviderAfterNodeAdd");
        assertTrue(addHead >= 0 && addReturn > addHead);
        assertFalse(
                source.substring(addHead, addReturn)
                        .contains("ProviderPatternGenerationTracker.forget(node)"));

        int removeHead = source.indexOf("private void aco$dropPendingRefreshOnNodeRemove");
        int removeReturn = source.indexOf("private void aco$publishProviderAfterNodeRemove");
        assertTrue(removeHead >= 0 && removeReturn > removeHead);
        assertFalse(
                source.substring(removeHead, removeReturn)
                        .contains("ProviderPatternGenerationTracker.forget(node)"));
    }
}
'''
mixin_test_path = Path(
    "src/test/java/com/syaru/ae2craftingoptimizer/mixin/"
    "CraftingProviderRefreshGenerationContractTest.java"
)
mixin_test_path.parent.mkdir(parents=True, exist_ok=True)
mixin_test_path.write_text(mixin_test, encoding="utf-8")

cache_test = r'''package com.syaru.ae2craftingoptimizer.engine;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Ae2CompiledCraftingGraphCacheContractTest {
    private static final Path CACHE_SOURCE = Path.of(
            "src/main/java/com/syaru/ae2craftingoptimizer/engine/"
                    + "Ae2CompiledCraftingGraphCache.java");
    private static final Path PLANNER_SOURCE = Path.of(
            "src/main/java/com/syaru/ae2craftingoptimizer/engine/"
                    + "Ae2AuthoritativeCraftingPlanner.java");

    @Test
    void rootProgramCacheStoresOnlySuccessfulPrograms() throws Exception {
        String source = Files.readString(CACHE_SOURCE);

        assertTrue(source.contains(
                "Map<AEKey, CompiledRootProgram<AEKey>> rootPrograms"));
        assertFalse(source.contains(
                "Map<AEKey, Optional<CompiledRootProgram<AEKey>>> rootPrograms"));
        assertTrue(source.contains("if (compiled.isEmpty())"));
        assertTrue(source.contains("rootPrograms.put(root, candidate);"));
        assertFalse(source.contains("rootPrograms.put(root, compiled);"));
    }

    @Test
    void lazyCompilationRejectsAStaleGeneration() throws Exception {
        String source = Files.readString(CACHE_SOURCE);

        assertTrue(source.contains("private void requireCurrentGenerations()"));
        assertTrue(source.contains(
                "long currentPatternGeneration = ProviderPatternGenerationTracker.generation();"));
        assertTrue(source.contains(
                "long currentRecipeGeneration = RecipeGenerationTracker.generation();"));

        int rootMethod = source.indexOf(
                "public Optional<CompiledRootProgram<AEKey>> rootProgram(AEKey root)");
        int compile = source.indexOf("CompiledRootProgram.tryCompile(", rootMethod);
        int firstGuard = source.indexOf("requireCurrentGenerations();", rootMethod);
        int secondGuard = source.indexOf("requireCurrentGenerations();", firstGuard + 1);
        assertTrue(rootMethod >= 0 && firstGuard < compile && secondGuard > compile);
    }

    @Test
    void missingProgramHasItsOwnDiagnostic() throws Exception {
        String planner = Files.readString(PLANNER_SOURCE);

        assertTrue(planner.contains("FallbackReasonCode.NO_COMPILED_PROGRAM"));
        assertTrue(planner.contains("BigIntegerPlanDeclineReason.NO_COMPILED_PROGRAM"));
        assertTrue(planner.contains("\"no compiled root program\""));
    }
}
'''
cache_test_path = Path(
    "src/test/java/com/syaru/ae2craftingoptimizer/engine/"
    "Ae2CompiledCraftingGraphCacheContractTest.java"
)
cache_test_path.parent.mkdir(parents=True, exist_ok=True)
cache_test_path.write_text(cache_test, encoding="utf-8")

Path(".github/workflows/apply-issue-103.yml").unlink()
Path("tools/apply_issue_103_patch.py").unlink()

# Do not push a partially transformed tree.
for path in (
    mixin_path,
    cache_path,
    "src/main/java/com/syaru/ae2craftingoptimizer/engine/Ae2AuthoritativeCraftingPlanner.java",
    "src/main/java/com/syaru/ae2craftingoptimizer/optimization/FallbackReasonCode.java",
    "gradle.properties",
    "CHANGELOG.md",
    "RELEASE_NOTES_1.5.20.md",
    str(mixin_test_path),
    str(cache_test_path),
):
    if not Path(path).is_file():
        raise RuntimeError(f"missing transformed file: {path}")
