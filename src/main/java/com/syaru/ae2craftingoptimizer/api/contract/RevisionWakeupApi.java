package com.syaru.ae2craftingoptimizer.api.contract;

import java.lang.ref.WeakReference;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Event-only revision notification. Listener references are weak and registrations have an
 * explicit close lifecycle; notifications never replace a durable receipt.
 */
public final class RevisionWakeupApi {
    private static final CopyOnWriteArrayList<ListenerReference> LISTENERS = new CopyOnWriteArrayList<>();

    private RevisionWakeupApi() {
    }

    public static RevisionWakeupRegistration register(RevisionWakeupListener listener) {
        ListenerReference reference = new ListenerReference(listener);
        LISTENERS.add(reference);
        return new RevisionWakeupRegistration(() -> LISTENERS.remove(reference));
    }

    public static void publish(BatchTargetRevision revision) {
        Objects.requireNonNull(revision, "revision");
        for (ListenerReference reference : LISTENERS) {
            RevisionWakeupListener listener = reference.listener.get();
            if (listener == null) {
                LISTENERS.remove(reference);
                continue;
            }
            listener.onRevision(revision);
        }
    }

    private static final class ListenerReference {
        private final WeakReference<RevisionWakeupListener> listener;

        private ListenerReference(RevisionWakeupListener listener) {
            this.listener = new WeakReference<>(Objects.requireNonNull(listener, "listener"));
        }
    }
}
