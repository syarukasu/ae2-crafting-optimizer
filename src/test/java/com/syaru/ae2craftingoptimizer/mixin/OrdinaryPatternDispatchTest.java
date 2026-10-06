package com.syaru.ae2craftingoptimizer.mixin;

import static org.junit.jupiter.api.Assertions.*;

import appeng.api.networking.crafting.ICraftingCPU;
import com.electronwill.nightconfig.core.CommentedConfig;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import com.syaru.ae2craftingoptimizer.optimization.CraftingExecutionBudget;
import com.syaru.ae2craftingoptimizer.optimization.SequentialInstantDispatcher;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrdinaryPatternDispatchTest {
    private ModConfigSpec spec;

    @BeforeEach void setup() throws Exception {
        var field = ACOConfig.class.getDeclaredField("SPEC");
        field.setAccessible(true);
        spec = (ModConfigSpec) field.get(null);
        var config = CommentedConfig.inMemory();
        spec.correct(config);
        com.ae2vm.addon.PortConfig.set(spec, config);
        // An existing installation must be fixed without editing its old config.
        set("throttleCraftingExecution", true);
        set("maxEffectiveCoprocessorsPerCpu", 1);
        set("adaptiveBudget", true);
        set("minimumAdaptiveOperations", 1);
        set("sharedGridBudget", true);
        set("throttleNeoEcoAeExecution", true);
        set("enableInstantDispatch", true);
    }

    @AfterEach void cleanup() {
        CraftingExecutionBudget.clearAdaptiveState("issue161 test");
        com.ae2vm.addon.PortConfig.set(spec, null);
    }

    @Test void legacyEnabledConfigCannotReactivateOrdinaryThrottles() {
        assertFalse(ACOConfig.throttleCraftingExecution());
        assertFalse(ACOConfig.adaptiveCraftingExecutionBudget());
        assertFalse(ACOConfig.sharedCraftingExecutionBudget());
        assertFalse(ACOConfig.throttleNeoEcoAeExecution());
        assertTrue(ACOConfig.enableInstantPatternDispatch(), "V2 configuration is independent");
    }

    @Test void nativeCoprocessorCountsPassWithoutConfiguredOrAdaptiveCaps() {
        var owner = new Object();
        var cpu = (ICraftingCPU) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {ICraftingCPU.class}, (proxy, method, args) -> {
                    throw new AssertionError("Unexpected CPU call: " + method.getName());
                });
        for (int count : new int[] {0, 1, 64, 65, 1024, 65537, 1048576, Integer.MAX_VALUE - 1}) {
            CraftingExecutionBudget.recordExecution(owner, count, 0, 100_000_000L);
            assertEquals(count, CraftingExecutionBudget.limitCoProcessors(owner, cpu, count));
        }
        assertEquals(Integer.MAX_VALUE - 1,
                CraftingExecutionBudget.limitCoProcessors(owner, cpu, Integer.MAX_VALUE));
    }

    @Test void legacyExternalAndSharedEntryPointsIgnorePastCosts() {
        var owner = new Object();
        for (int requested : new int[] {Integer.MIN_VALUE, -1, 0, 1, 65537, 1048576, Integer.MAX_VALUE}) {
            CraftingExecutionBudget.recordExecution(owner, requested, 0, Long.MAX_VALUE);
            CraftingExecutionBudget.recordSharedExecution(null, owner, 7, Long.MAX_VALUE);
            assertEquals(requested, CraftingExecutionBudget.limitExternalOperations(owner, requested, "test"));
            assertEquals(requested, CraftingExecutionBudget.limitSharedOperations(null, owner, requested, 7));
        }
        assertEquals(Long.MAX_VALUE, CraftingExecutionBudget.remainingSharedBudgetNanos(null, 7));
    }

    @Test void ordinaryExecuteCraftingIsNotWrappedInWavesEvenWhenInstantV2IsEnabled() throws Exception {
        String hook = Files.readString(Path.of(
                "src/main/java/com/syaru/ae2craftingoptimizer/mixin/CraftingCpuLogicExecutionBudgetMixin.java"));
        assertFalse(hook.contains(";executeCrafting("), "No ordinary dispatch redirect");
        assertFalse(hook.contains("SequentialInstantDispatcher"), "No ACO wave wrapper");
        assertFalse(hook.contains("ServerTickClock"), "No ordinary dispatch timer");
        assertTrue(hook.contains("priority = 900"), "Keep add-on ownership priority");
        assertTrue(hook.contains("CraftingExecutionBudget.limitCoProcessors"), "Keep overflow repair");
    }

    @Test void legacyWaveEntryPassesEveryRequestAndKeepsTheNativeResult() {
        var owner = new Object();
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        for (int requested : new int[] {1, 65, 65537, 1048576, Integer.MAX_VALUE}) {
            CraftingExecutionBudget.recordExecution(owner, requested, 0, 100_000_000L);
            for (int nativeResult : new int[] {0, 1, requested}) {
                assertEquals(nativeResult, SequentialInstantDispatcher.executeWave(owner, requested, null, 1, actual -> {
                    assertEquals(requested, actual, "No probe, predicted cap or maximum wave");
                    calls.incrementAndGet();
                    return nativeResult;
                }));
            }
        }
        assertEquals(15, calls.get(), "One original call per request, even in the same tick");
    }

    @Test void nativeFailuresAndNoWorkAreNotRetried() {
        var failure = new IllegalStateException("provider failed");
        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> SequentialInstantDispatcher.executeWave(this, 10, null, 1, ignored -> { throw failure; })));
        assertEquals(0, SequentialInstantDispatcher.executeWave(this, 0, null, 1,
                ignored -> { throw new AssertionError("No work"); }));
    }

    @Test void externalOperationCountsDoNotLoseNativeCoprocessorHeadroom() {
        // External dispatch receives operations, not coprocessors followed by +1.
        assertEquals(Integer.MAX_VALUE,
                CraftingExecutionBudget.limitExternalOperations(this, Integer.MAX_VALUE, "test"));
    }

    @Test void retiredBudgetEntryPointsHaveNoTimingStateOrConfigReads() throws Exception {
        for (String name : new String[] {"CraftingExecutionBudget", "SequentialInstantDispatcher"}) {
            String source = Files.readString(Path.of(
                    "src/main/java/com/syaru/ae2craftingoptimizer/optimization/" + name + ".java"));
            for (String forbidden : new String[] {"ACOConfig", "System.nanoTime", "WeakHashMap",
                    "synchronized", "class AdaptiveState", "class SharedBudgetState", "class CpuTickState",
                    "calculateWaveOperations", "getAdaptiveCap"}) {
                assertFalse(source.contains(forbidden), name + " still contains " + forbidden);
            }
            assertEquals(0, Class.forName("com.syaru.ae2craftingoptimizer.optimization." + name)
                    .getDeclaredFields().length, name + " must not retain per-owner budget state");
        }
    }

    @SuppressWarnings("unchecked")
    private void set(String key, Object value) {
        ((ModConfigSpec.ConfigValue<Object>) spec.getValues().get("craftingExecution." + key)).set(value);
    }
}
