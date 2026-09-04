package com.syaru.ae2craftingoptimizer.engine;

import appeng.api.crafting.IPatternDetails;
import appeng.api.stacks.AEKey;
import java.util.Optional;

/** Plannerが読む、同一Pattern/recipe revisionへ固定した不変グラフ境界。 */
interface Ae2PlanningGraphSnapshot {
    CompiledCraftingGraph<AEKey> graph();

    long recipeGeneration();

    String id(IPatternDetails pattern);

    IPatternDetails pattern(String id);

    int registeredPatternCount(AEKey output);

    boolean isIncompletelyCompiled(AEKey output);

    boolean isEmittable(AEKey key);

    boolean hasExactlyOneFullyCompiledPattern(AEKey output);

    boolean hasExactInputDomain(String patternId);

    CompiledRootProgram.Outcome<AEKey> rootProgramOutcome(AEKey root);

    default CompiledRootProgram.Outcome<AEKey> rootProgramOutcome(
            AEKey root,
            PlanningGuard guard) {
        guard.checkpoint(0);
        return rootProgramOutcome(root);
    }

    Optional<CompiledRootProgram.Outcome<AEKey>> cachedRootProgramOutcome(AEKey root);

    Optional<Ae2StrictCraftingTopology> strictTopology(CompiledRootProgram<AEKey> program);

    default Optional<Ae2StrictCraftingTopology> strictTopology(
            CompiledRootProgram<AEKey> program,
            PlanningGuard guard) {
        guard.checkpoint(0);
        return strictTopology(program);
    }

    Optional<Ae2StrictCraftingTopology> cachedStrictTopology(AEKey root);
}
