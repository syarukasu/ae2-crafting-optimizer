package com.syaru.ae2craftingoptimizer.intent;

import static org.junit.jupiter.api.Assertions.*;

import com.electronwill.nightconfig.core.CommentedConfig;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RecipeIntentRegistryTest {
    private static final ResourceLocation DIMENSION = new ResourceLocation("minecraft", "overworld");
    private ForgeConfigSpec spec;

    @BeforeEach void setup() throws Exception {
        var field = ACOConfig.class.getDeclaredField("SPEC");
        field.setAccessible(true);
        spec = (ForgeConfigSpec) field.get(null);
        var config = CommentedConfig.inMemory();
        spec.correct(config);
        spec.setConfig(config);
        RecipeIntentRegistry.clear("test setup");
    }

    @AfterEach void cleanup() {
        RecipeIntentRegistry.clear("test cleanup");
        spec.setConfig(null);
    }

    @Test void sameTickPushesDoNotInspectUnexpiredLocations() throws Exception {
        for (int i = 0; i < 1024; i++) RecipeIntentRegistry.record(intent(i, 10, 200));
        var inspections = new AtomicLong();
        var field = RecipeIntentRegistry.class.getDeclaredField("INTENTS");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var locations = (Map<IntentLocation, ArrayDeque<RecipeIntent>>) field.get(null);
        locations.replaceAll((key, queue) -> new CountingDeque(queue, inspections));
        long start = System.nanoTime();
        for (int i = 0; i < 1024; i++) {
            RecipeIntentRegistry.record(intent(i, 10, 200));
            assertEquals(2, RecipeIntentRegistry.find(DIMENSION, new BlockPos(i, 0, 0),
                    Direction.WEST, 10).get(0).patternExecutions());
        }
        System.out.printf("intent-cleanup locations=1024 pushes=1024 headReads=%d elapsedNs=%d%n",
                inspections.get(), System.nanoTime() - start);
        assertEquals(1024, RecipeIntentRegistry.size());
        assertTrue(inspections.get() < 1024, "Unexpired hints were scanned per push: " + inspections.get());
        RecipeIntentRegistry.cleanupExpired(200);
        assertEquals(0, RecipeIntentRegistry.size());
    }

    @Test void shorterTtlBehindUnexpiredHeadIsRemovedAtItsDeadline() {
        RecipeIntentRegistry.record(intent(0, 10, 200));
        RecipeIntentRegistry.record(intent(0, 11, 20));
        assertEquals(List.of(intent(0, 10, 200)), RecipeIntentRegistry.find(
                DIMENSION, BlockPos.ZERO, Direction.WEST, 20));
        assertEquals(1, RecipeIntentRegistry.size());
        assertEquals(1, RecipeIntentRegistry.findNearby(DIMENSION, BlockPos.ZERO, 1, 20, 10).size());
        RecipeIntentRegistry.cleanupExpired(200);
        assertTrue(RecipeIntentRegistry.findNearby(DIMENSION, BlockPos.ZERO, 1, 200, 10).isEmpty());
    }

    @Test void coalescingExtendsExpiryWithoutDelayingOtherEntries() {
        RecipeIntentRegistry.record(intent(0, 10, 20));
        RecipeIntentRegistry.record(intent(0, 10, 50));
        RecipeIntentRegistry.record(intent(1, 10, 30));
        RecipeIntentRegistry.cleanupExpired(20);
        assertEquals(2, RecipeIntentRegistry.size());
        assertEquals(2, RecipeIntentRegistry.snapshot().get(0).patternExecutions());
        RecipeIntentRegistry.cleanupExpired(30);
        assertEquals(1, RecipeIntentRegistry.size());
        RecipeIntentRegistry.cleanupExpired(50);
        assertEquals(0, RecipeIntentRegistry.size());
    }

    @Test void clearAndBackwardsClockDoNotKeepOldExpirationBound() {
        RecipeIntentRegistry.record(intent(0, 100, 200));
        RecipeIntentRegistry.clear("world change");
        RecipeIntentRegistry.record(intent(0, -10, -1));
        assertEquals(1, RecipeIntentRegistry.find(DIMENSION, BlockPos.ZERO, Direction.WEST, -2).size());
        assertTrue(RecipeIntentRegistry.find(DIMENSION, BlockPos.ZERO, Direction.WEST, -1).isEmpty());
        RecipeIntentRegistry.record(intent(0, 10, Long.MAX_VALUE));
        RecipeIntentRegistry.cleanupExpired(Long.MAX_VALUE - 1);
        assertEquals(1, RecipeIntentRegistry.size());
        RecipeIntentRegistry.cleanupExpired(Long.MAX_VALUE);
        assertEquals(0, RecipeIntentRegistry.size());
    }

    @Test void capacityEvictionLeavesSpatialIndexAndExpiryConsistent() {
        int maximum = ACOConfig.getMaximumRecipeIntentEntries();
        for (int i = 0; i <= maximum; i++) RecipeIntentRegistry.record(intent(i, 10, 100));
        assertEquals(maximum, RecipeIntentRegistry.size());
        assertTrue(RecipeIntentRegistry.find(DIMENSION, BlockPos.ZERO, Direction.WEST, 10).isEmpty());
        RecipeIntentRegistry.cleanupExpired(100);
        assertEquals(0, RecipeIntentRegistry.size());
        assertTrue(RecipeIntentRegistry.findNearby(DIMENSION, BlockPos.ZERO, 10, 100, 10).isEmpty());
    }

    private static RecipeIntent intent(int position, long created, long expires) {
        return new RecipeIntent(DIMENSION, new BlockPos(position - 1, 0, 0), Direction.EAST,
                new BlockPos(position, 0, 0), Direction.WEST, "test:pattern",
                List.of(), List.of(), List.of(), 1, created, expires);
    }

    private static final class CountingDeque extends ArrayDeque<RecipeIntent> {
        private final AtomicLong inspections;
        CountingDeque(ArrayDeque<RecipeIntent> values, AtomicLong inspections) {
            super(values);
            this.inspections = inspections;
        }
        @Override public RecipeIntent peekFirst() {
            inspections.incrementAndGet();
            return super.peekFirst();
        }
        @Override public java.util.Iterator<RecipeIntent> iterator() {
            var source = super.iterator();
            return new java.util.Iterator<>() {
                public boolean hasNext() { return source.hasNext(); }
                public RecipeIntent next() { inspections.incrementAndGet(); return source.next(); }
                public void remove() { source.remove(); }
            };
        }
    }
}
