package com.syaru.ae2craftingoptimizer.integration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** Issue #167: AE2候補順、immutable capture、network別revisionの境界を固定する。 */
class Issue167PlannerSnapshotBoundaryTest {
    private static final Path MAIN = Path.of("src", "main", "java");
    private static final Path MIXINS = Path.of(
            "src", "main", "resources", "ae2_crafting_optimizer.mixins.json");

    @Test
    void candidatePruningAndGlobalPatternReplayStayRemoved() throws IOException {
        String mixins = Files.readString(MIXINS, StandardCharsets.UTF_8);
        String treeMemo = readMain(
                "com/syaru/ae2craftingoptimizer/mixin/CraftingTreeCalculationMemoMixin.java");
        String calculationMemo = readMain(
                "com/syaru/ae2craftingoptimizer/optimization/CraftingCalculationMemo.java");

        assertFalse(mixins.contains("CraftingTreeCandidatePruningMixin"));
        assertFalse(mixins.contains("CraftingServicePatternLookupCacheMixin"));
        assertFalse(existsMain("com/syaru/ae2craftingoptimizer/optimization/PatternCandidatePruner.java"));
        assertFalse(existsMain("com/syaru/ae2craftingoptimizer/optimization/PatternLookupCache.java"));
        assertTrue(treeMemo.contains("CraftingCalculationMemo.patternCandidates(job, service, key)"));
        assertTrue(calculationMemo.contains("return patterns(calculation, service, key);"));
        assertFalse(calculationMemo.contains("PatternCandidatePruner"));
    }

    @Test
    void rootCapturePreservesAe2CandidateOrderAndPublishesOnlyOneRevision() throws IOException {
        String cache = readMain(
                "com/syaru/ae2craftingoptimizer/engine/Ae2ImmutablePlanningGraphCache.java");

        assertTrue(cache.contains("List.copyOf(service.getCraftingFor(key))"));
        assertTrue(cache.contains("Collections.unmodifiableMap(new LinkedHashMap<>(nodes))"));
        assertTrue(cache.contains("List.copyOf(orderedPatterns)"));
        assertTrue(cache.contains("generationsMatch("));
        assertTrue(cache.contains("configurationRevision"));
        assertTrue(cache.contains("MAXIMUM_CACHED_KEYS_PER_DIMENSION"));
        assertTrue(cache.contains("cachedKeyCount > MAXIMUM_CACHED_KEYS_PER_DIMENSION"));
        assertFalse(cache.contains("candidates.sort"));
        assertFalse(cache.contains("candidates.remove"));
    }

    @Test
    void compiledRootCacheIncludesConfigurationRevision() throws IOException {
        String cache = readMain(
                "com/syaru/ae2craftingoptimizer/engine/Ae2ImmutablePlanningGraphCache.java");
        String coordinator = readMain(
                "com/syaru/ae2craftingoptimizer/engine/Ae2PlanningCaptureCoordinator.java");

        assertTrue(cache.contains("PlanningConfigurationRevisionTracker.current()"));
        assertTrue(cache.contains("dimension.configurationRevision != configurationRevision"));
        assertTrue(cache.contains("PlanningConfigurationRevisionTracker.isCurrent(configurationRevision)"));
        assertTrue(cache.contains("public long configurationRevision()"));
        assertTrue(coordinator.contains(
                "graphCapture.configurationRevision() != configurationRevision"));
    }

    @Test
    void fullExecutionGraphCacheIncludesConfigurationRevisionWithoutRetryRelabeling()
            throws IOException {
        String cache = readMain(
                "com/syaru/ae2craftingoptimizer/engine/Ae2CompiledCraftingGraphCache.java");

        assertTrue(cache.contains("PlanningConfigurationRevisionTracker.current()"));
        assertTrue(cache.contains("current.configurationRevision() == configurationRevision"));
        assertTrue(cache.contains("PlanningConfigurationRevisionTracker.isCurrent("));
        assertTrue(cache.contains("this.configurationRevision = configurationRevision"));
        assertFalse(cache.contains("claimRetryRebuild"));
        assertFalse(cache.contains("retryRebuildClaimed"));
        assertFalse(cache.contains("static Snapshot recompile("));
    }

    @Test
    void planningWorkerUsesCapturedGraphInventoryAndStorageTokenOnly() throws IOException {
        String planner = readMain(
                "com/syaru/ae2craftingoptimizer/engine/Ae2AuthoritativeCraftingPlanner.java");
        String topology = readMain(
                "com/syaru/ae2craftingoptimizer/engine/Ae2StrictCraftingTopology.java");
        String referencedInventory = readMain(
                "com/syaru/ae2craftingoptimizer/engine/Ae2ReferencedInventory.java");

        assertTrue(planner.contains("Ae2ImmutablePlanningGraphCache.RootCapture"));
        assertTrue(planner.contains("StorageRevisionTracker.isCurrent(storageRevision)"));
        assertTrue(planner.contains("Ae2PlanningInventorySnapshot"));
        assertTrue(planner.contains("captureExactInventoryOnServer(capture)"));
        assertTrue(planner.contains("StorageRevisionTracker.refreshAndCapture(capture.grid())"));
        assertTrue(planner.contains("MinecraftServer server = capture.server()"));
        assertFalse(planner.contains("capture.level().getServer()"));
        assertFalse(planner.contains("Ae2CompiledCraftingGraphCache.getOrCompile"));
        assertFalse(topology.contains("getStorageService"));
        assertFalse(topology.contains("getCraftingService"));
        assertFalse(topology.contains(".isValid("));
        assertFalse(referencedInventory.contains("captureLive("));
        assertFalse(referencedInventory.contains("matchesLive("));
    }

    @Test
    void mainCalculationCaptureCopiesOnlyRootReferencedInventory() throws IOException {
        String mixin = readMain(
                "com/syaru/ae2craftingoptimizer/mixin/CraftingCalculationDiagnosticsMixin.java");
        String coordinator = readMain(
                "com/syaru/ae2craftingoptimizer/engine/Ae2PlanningCaptureCoordinator.java");

        assertTrue(mixin.contains("Ae2PlanningCaptureCoordinator.capture("));
        assertTrue(coordinator.contains("captureReferenced("));
        assertTrue(coordinator.contains("graphCapture.referencedKeys()"));
        assertFalse(mixin.contains("Ae2PlanningInventorySnapshot.capture(networkSnapshot)"));
        assertTrue(mixin.contains("recordPlanningCapture("));
        assertTrue(mixin.contains("recordAuthoritativePlanner("));
        assertTrue(mixin.contains("decisionFlowLogging"));
    }

    @Test
    void shadowQualificationChecksAllRevisionsBeforeAndAfterComparison() throws IOException {
        String shadow = readMain(
                "com/syaru/ae2craftingoptimizer/engine/Ae2CraftingShadowValidator.java");
        String revisionCheck = "if (!isCaptureCurrent(capture))";

        assertTrue(shadow.indexOf(revisionCheck) >= 0);
        assertTrue(shadow.indexOf(revisionCheck) != shadow.lastIndexOf(revisionCheck));
        assertTrue(shadow.contains("capture.patternGeneration() == ProviderPatternGenerationTracker.generation()"));
        assertTrue(shadow.contains("capture.recipeGeneration() == RecipeGenerationTracker.generation()"));
        assertTrue(shadow.contains("StorageRevisionTracker.isCurrent(capture.storageRevision())"));
    }

    @Test
    void fingerprintSerializationAndHashingStayOnTheWorkerCompileBoundary() throws IOException {
        String factory = readMain(
                "com/syaru/ae2craftingoptimizer/engine/Ae2CompiledPatternFactory.java");
        String capture = between(
                factory,
                "static Captured capture(",
                "private static boolean hasExactInputDomain");
        String fingerprint = between(
                factory,
                "private String createFingerprint()",
                "private record FingerprintInput");

        assertFalse(capture.contains("toTagGeneric()"));
        assertFalse(capture.contains("StableFingerprint.sha256"));
        assertFalse(capture.contains("StringBuilder"));
        assertTrue(fingerprint.contains("toTagGeneric(registryAccess)"));
        assertTrue(fingerprint.contains("StableFingerprint.sha256"));
    }

    @Test
    void obsoleteSynchronousBigPlanEntryCannotReintroduceLiveReads() throws IOException {
        String factory = readMain(
                "com/syaru/ae2craftingoptimizer/engine/Ae2BigCraftingPlanFactory.java");

        assertFalse(factory.contains("PreparedBigRootPlan tryCreate("));
        assertFalse(factory.contains("getStorageService"));
        assertFalse(factory.contains("Ae2CompiledCraftingGraphCache.getOrCompile"));
    }

    @Test
    void networkStorageRevisionCoversAmountsAndMountTopology() throws IOException {
        String mixin = readMain(
                "com/syaru/ae2craftingoptimizer/mixin/NetworkStorageCraftingPlanGenerationMixin.java");
        String tracker = readMain(
                "com/syaru/ae2craftingoptimizer/optimization/StorageRevisionTracker.java");
        String serviceMixin = readMain(
                "com/syaru/ae2craftingoptimizer/mixin/StorageServiceCraftingPlanGenerationMixin.java");
        String revisionState = readMain(
                "com/syaru/ae2craftingoptimizer/optimization/StorageRevisionState.java");

        assertTrue(mixin.contains("method = \"insert\""));
        assertTrue(mixin.contains("method = \"extract\""));
        assertTrue(mixin.contains("method = \"mount\""));
        assertTrue(mixin.contains("method = \"unmount\""));
        assertTrue(tracker.contains("refreshAndCapture"));
        assertTrue(tracker.contains("RevisionToken"));
        assertTrue(serviceMixin.contains("method = \"postWatcherUpdate\""));
        assertTrue(serviceMixin.contains("method = \"updateCachedStacks\""));
        assertFalse(tracker.contains("WeakHashMap"));
        assertFalse(tracker.contains("synchronized (LOCK)"));
        assertFalse(tracker.contains("copyObservedInventory"));
        assertFalse(revisionState.contains("AtomicBoolean"));
        assertTrue(serviceMixin.contains("aco$storageRevision.advance()"));
    }

    @Test
    void dedupRegistrationUsesTheRevisionCapturedByTheActualCalculation() throws IOException {
        String mixin = readMain(
                "com/syaru/ae2craftingoptimizer/mixin/CraftingServiceCalculationDeduplicationMixin.java");
        String context = readMain(
                "com/syaru/ae2craftingoptimizer/optimization/CraftingCalculationSnapshotContext.java");
        String planner = readMain(
                "com/syaru/ae2craftingoptimizer/engine/Ae2AuthoritativeCraftingPlanner.java");

        assertTrue(mixin.contains("CraftingCalculationSnapshotContext.finish()"));
        assertTrue(mixin.contains("CraftingCalculationSnapshotContext.begin(requester, actionSource)"));
        assertFalse(context.contains("CraftingCalculation revisions were not captured"));
        assertTrue(context.contains("patternGeneration"));
        assertTrue(context.contains("recipeGeneration"));
        assertTrue(context.contains("configurationRevision"));
        assertTrue(mixin.contains("CraftingCalculationSnapshotContext.CalculationRevision"));
        assertTrue(readMain(
                        "com/syaru/ae2craftingoptimizer/mixin/CraftingCalculationDiagnosticsMixin.java")
                .contains("aco$captureActualActionSource"));
        assertTrue(readMain(
                        "com/syaru/ae2craftingoptimizer/mixin/CraftingCalculationDiagnosticsMixin.java")
                .contains("CraftingCalculationSnapshotContext.actionSource(requester)"));
        assertFalse(readMain(
                        "com/syaru/ae2craftingoptimizer/optimization/CraftingCalculationDeduplicator.java")
                .contains("requester.getActionSource()"));
        assertTrue(readMain(
                        "com/syaru/ae2craftingoptimizer/optimization/CraftingCalculationDeduplicator.java")
                .contains("ActionSourceIdentity"));
        assertTrue(readMain(
                        "com/syaru/ae2craftingoptimizer/optimization/CraftingCalculationDeduplicator.java")
                .contains("requestKey.matchesCurrent(grid)"));
        assertTrue(planner.contains("ACO authoritative planner failed for"));
        assertFalse(planner.contains("logFallbackOnce"));
        assertFalse(planner.contains("shouldRetryRootProgram"));
    }

    @Test
    void livePlanningCaptureRequiresTheServerThreadAndConfigRevision() throws IOException {
        String calculationMixin = readMain(
                "com/syaru/ae2craftingoptimizer/mixin/CraftingCalculationDiagnosticsMixin.java");
        String serviceMixin = readMain(
                "com/syaru/ae2craftingoptimizer/mixin/CraftingServiceCalculationDeduplicationMixin.java");
        String providerRefreshMixin = readMain(
                "com/syaru/ae2craftingoptimizer/mixin/CraftingProviderRefreshCoalescingMixin.java");
        String coordinator = readMain(
                "com/syaru/ae2craftingoptimizer/engine/Ae2PlanningCaptureCoordinator.java");
        String deduplicator = readMain(
                "com/syaru/ae2craftingoptimizer/optimization/CraftingCalculationDeduplicator.java");

        assertTrue(calculationMixin.contains("ServerPlanningThreadGuard.canCapture(level)"));
        assertTrue(serviceMixin.contains("ServerPlanningThreadGuard.canCapture(level)"));
        assertTrue(providerRefreshMixin.contains("ServerPlanningThreadGuard.canCapture(level)"));
        assertTrue(coordinator.contains("PlanningConfigurationRevisionTracker.isCurrent"));
        assertTrue(deduplicator.contains("configurationRevision"));
        assertTrue(deduplicator.contains("PlanningConfigurationRevisionTracker.isCurrent"));
    }

    @Test
    void lifecycleClearsOnlyIndexesAndImmutablePlanningCaches() throws IOException {
        String lifecycle = readMain(
                "com/syaru/ae2craftingoptimizer/lifecycle/ACOServerLifecycle.java");
        String deduplicator = readMain(
                "com/syaru/ae2craftingoptimizer/optimization/CraftingCalculationDeduplicator.java");

        assertTrue(lifecycle.contains("CraftingCalculationDeduplicator.clear(reason)"));
        assertTrue(lifecycle.contains("Ae2ImmutablePlanningGraphCache.clear()"));
        assertFalse(deduplicator.substring(
                        deduplicator.indexOf("public static void clear(String reason)"),
                        deduplicator.indexOf("public static void clearCompleted"))
                .contains("cancel("));

        String reloadHandler = between(
                lifecycle,
                "private static void onDatapackSync",
                "private static void onServerStopping");
        assertTrue(
                reloadHandler.indexOf("RecipeGenerationTracker.invalidate()")
                        < reloadHandler.indexOf("clearReloadSensitiveState(\"server data reload\")"),
                "recipe revision must change before reload-sensitive caches are cleared");
    }

    @Test
    void calculationCachesAreOwnedByEachCraftingService() throws IOException {
        String mixin = readMain(
                "com/syaru/ae2craftingoptimizer/mixin/CraftingServiceCalculationDeduplicationMixin.java");
        String access = readMain(
                "com/syaru/ae2craftingoptimizer/access/CraftingCalculationCacheAccess.java");
        String deduplicator = readMain(
                "com/syaru/ae2craftingoptimizer/optimization/CraftingCalculationDeduplicator.java");

        assertTrue(mixin.contains("CraftingCalculationDeduplicator.createServiceState()"));
        assertTrue(mixin.contains("implements CraftingServiceCalculationHookAccess, CraftingCalculationCacheAccess"));
        assertTrue(access.contains("aco$getCraftingCalculationCacheState"));
        assertTrue(deduplicator.contains("private static ServiceState state(CraftingService craftingService)"));
        assertTrue(deduplicator.contains("Map<ServiceState, Boolean> REGISTERED_STATES"));
        assertTrue(deduplicator.contains("new WeakHashMap<>()"));
        assertFalse(deduplicator.contains("WeakHashMap<CraftingService"));
        assertFalse(deduplicator.contains("Map<CraftingService"));
    }

    @Test
    void calculationLocalMemoDropsValuesWhenPatternOrRecipeGenerationChanges() throws IOException {
        String memo = readMain(
                "com/syaru/ae2craftingoptimizer/optimization/CraftingCalculationMemo.java");

        assertTrue(memo.contains("refreshAfterGenerationChange()"));
        assertTrue(memo.contains("ProviderPatternGenerationTracker.generation()"));
        assertTrue(memo.contains("RecipeGenerationTracker.generation()"));
        assertTrue(memo.contains("emittable.clear()"));
        assertTrue(memo.contains("patterns.clear()"));
        assertTrue(memo.contains("possibleInputs.clear()"));
        assertTrue(memo.contains("fuzzy.clear()"));
        assertTrue(memo.contains("remaining.clear()"));
        assertTrue(memo.contains("validInputs.clear()"));
        assertTrue(memo.contains("!isImmutableAe2Input(input)"));
        assertTrue(memo.contains("!isPureProcessingInput(input)"));
    }

    @Test
    void completedPlanCacheMaterializesIndependentAe2Counters() throws IOException {
        String snapshot = readMain(
                "com/syaru/ae2craftingoptimizer/optimization/CompletedCraftingPlanSnapshot.java");
        String deduplicator = readMain(
                "com/syaru/ae2craftingoptimizer/optimization/CraftingCalculationDeduplicator.java");

        assertTrue(snapshot.contains("materializeCounter(usedItems)"));
        assertTrue(snapshot.contains("materializeCounter(emittedItems)"));
        assertTrue(snapshot.contains("materializeCounter(missingItems)"));
        assertTrue(deduplicator.contains("entry.snapshot.materialize()"));
        assertFalse(deduplicator.contains("completedFuture(entry.plan)"));
    }

    @Test
    void exactByteAccountingRejectsSharedDagAndProviderRefreshKeepsRevisionedDedup() throws IOException {
        String topology = readMain(
                "com/syaru/ae2craftingoptimizer/engine/Ae2StrictCraftingTopology.java");
        String byteCounter = readMain(
                "com/syaru/ae2craftingoptimizer/engine/BigExactCraftingByteCounter.java");
        String invalidation = readMain(
                "com/syaru/ae2craftingoptimizer/mixin/CraftingServiceInvalidationMixin.java");

        assertTrue(topology.contains("hasUniqueInputOccurrencePerKey()"));
        assertTrue(byteCounter.contains("requires a tree-shaped single-occurrence input graph"));
        assertFalse(invalidation.contains("CraftingCalculationDeduplicator.clear"));
    }

    @Test
    void checkedByteGuardPreservesAe2FiniteSaturation() throws IOException {
        String mixin = readMain(
                "com/syaru/ae2craftingoptimizer/mixin/CraftingSimulationStateCheckedMathMixin.java");

        assertTrue(mixin.contains("!Double.isFinite(amount)"));
        assertTrue(mixin.contains("!Double.isFinite(next)"));
        assertFalse(mixin.contains("next >= Long.MAX_VALUE"));
    }

    @Test
    void coldCaptureClassifiesWideArithmeticBeforeStaleFallback() throws IOException {
        String planner = readMain(
                "com/syaru/ae2craftingoptimizer/engine/Ae2AuthoritativeCraftingPlanner.java");
        String attempt = between(
                planner,
                "private static ICraftingPlan tryPlanAttempt(",
                "private static ICraftingPlan createBigIntegerSimulationPlan(");
        int wideClassification = attempt.indexOf("topology.mightRequireWideArithmetic(");
        int staleCheck = attempt.indexOf("capture.requireCurrentGenerations();");

        assertTrue(wideClassification >= 0, "wide arithmetic classification must remain present");
        assertTrue(staleCheck > wideClassification,
                "a cold wide request must not fall back to AE2 long arithmetic before classification");
    }

    @Test
    void providerGenerationPublishesOnlyAfterAe2IndexMutation() throws IOException {
        String refreshMixin = readMain(
                "com/syaru/ae2craftingoptimizer/mixin/CraftingProviderRefreshCoalescingMixin.java");
        String queueHead = between(
                refreshMixin,
                "private void aco$queueProviderRefresh",
                "@Inject(method = \"onServerEndTick\"");
        String refreshTail = between(
                refreshMixin,
                "at = @At(\"TAIL\"), require = 1",
                "@Inject(method = \"addNode\", at = @At(\"HEAD\"))");

        assertFalse(queueHead.contains("ProviderPatternGenerationTracker.shouldRefresh"));
        assertTrue(refreshTail.contains("ProviderPatternGenerationTracker.shouldRefresh(node)"));
        assertTrue(refreshMixin.contains("method = \"addNode\", at = @At(\"RETURN\")"));
        assertTrue(refreshMixin.contains("method = \"removeNode\", at = @At(\"RETURN\")"));
    }

    private static String readMain(String relativePath) throws IOException {
        return Files.readString(MAIN.resolve(relativePath), StandardCharsets.UTF_8);
    }

    private static boolean existsMain(String relativePath) {
        return Files.exists(MAIN.resolve(relativePath));
    }

    private static String between(String source, String start, String end) {
        int startIndex = source.indexOf(start);
        int endIndex = source.indexOf(end, startIndex + start.length());
        if (startIndex < 0 || endIndex < 0) {
            throw new AssertionError("source markers were not found: " + start + " / " + end);
        }
        return source.substring(startIndex, endIndex);
    }
}
