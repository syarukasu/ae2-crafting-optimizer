package com.syaru.ae2craftingoptimizer.intent;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Supplier;

/** #179: native lookup starts only after the machine rejects the hinted candidates. */
public final class IntentSearchIterator<T> implements Iterator<T> {
    private final Iterator<? extends T> hints;
    private final Supplier<? extends Iterator<? extends T>> lookup;
    private final Set<T> seen = Collections.newSetFromMap(new IdentityHashMap<>());
    private Iterator<? extends T> original;
    private T next;
    private boolean ready;

    public IntentSearchIterator(Iterator<? extends T> hints, Supplier<? extends Iterator<? extends T>> lookup) {
        this.hints = hints;
        this.lookup = lookup;
    }

    @Override public boolean hasNext() {
        if (ready) return true;
        while (hints.hasNext()) {
            var candidate = hints.next();
            if (seen.add(candidate)) { next = candidate; return ready = true; }
        }
        if (original == null) original = lookup.get();
        while (original.hasNext()) {
            var candidate = original.next();
            if (seen.add(candidate)) { next = candidate; return ready = true; }
        }
        return false;
    }

    @Override public T next() {
        if (!hasNext()) throw new NoSuchElementException();
        ready = false;
        T result = next;
        next = null;
        return result;
    }
}
