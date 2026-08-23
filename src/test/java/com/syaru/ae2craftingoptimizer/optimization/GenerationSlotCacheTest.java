package com.syaru.ae2craftingoptimizer.optimization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class GenerationSlotCacheTest {
    @Test
    void cachesNullAndInvalidatesOnlyOnGenerationOrSizeChange() {
        var loads = new AtomicInteger();
        var cache = new GenerationSlotCache<String>();
        assertNull(cache.get(1L, 3, 1, slot -> {
            loads.incrementAndGet();
            return null;
        }));
        assertNull(cache.get(1L, 3, 1, slot -> {
            loads.incrementAndGet();
            return "unexpected";
        }));
        assertEquals(1, loads.get());

        assertEquals("new", cache.get(2L, 3, 1, slot -> {
            loads.incrementAndGet();
            return "new";
        }));
        assertEquals(2, loads.get());
    }
}
