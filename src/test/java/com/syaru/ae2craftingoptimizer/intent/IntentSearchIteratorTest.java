package com.syaru.ae2craftingoptimizer.intent;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class IntentSearchIteratorTest {
    @Test void acceptingHintDoesNotStartNativeSearch() {
        var hint = new Object();
        var iterator = new IntentSearchIterator<>(List.of(hint).iterator(), () -> {
            fail("native search must be lazy");
            return List.of().iterator();
        });
        assertTrue(iterator.hasNext());
        assertTrue(iterator.hasNext());
        assertSame(hint, iterator.next());
    }

    @Test void rejectedHintsFallBackOnceInNativeOrderEvenWithoutHasNext() {
        var a = new Object(); var b = new Object(); var c = new Object();
        var calls = new AtomicInteger();
        var iterator = new IntentSearchIterator<>(List.of(b, b).iterator(), () -> {
            calls.incrementAndGet(); return List.of(a, b, c).iterator();
        });
        assertSame(b, iterator.next());
        assertEquals(0, calls.get());
        assertSame(a, iterator.next());
        assertSame(c, iterator.next());
        assertFalse(iterator.hasNext());
        assertThrows(NoSuchElementException.class, iterator::next);
        assertEquals(1, calls.get());
    }

    @Test void doesNotHideNativeSearchFailures() {
        var failure = new IllegalStateException("native failure");
        var iterator = new IntentSearchIterator<>(List.of().iterator(), () -> { throw failure; });
        assertSame(failure, assertThrows(IllegalStateException.class, iterator::hasNext));
    }
}
