package com.syaru.ae2craftingoptimizer.api.big;

import appeng.api.stacks.AEKey;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/** CPU所有者ごとのBigInteger Hostを、GCではなく明示的なライフサイクルで管理する。 */
public final class BigCraftingHostRegistry {
    private static final Map<Object, Registration> HOSTS = new IdentityHashMap<>();
    private static long nextGeneration;

    private BigCraftingHostRegistry() {
    }

    /** 同一所有者の旧世代を閉じてから新しい登録ハンドルを返す。 */
    public static BigCraftingHostRegistration register(
            Object owner,
            BigCraftingHostRuntime<AEKey> runtime) {
        Objects.requireNonNull(owner, "owner");
        Objects.requireNonNull(runtime, "runtime");
        Registration previous;
        Registration replacement;
        synchronized (HOSTS) {
            previous = HOSTS.remove(owner);
            if (previous != null) {
                previous.markClosed();
            }
            replacement = new Registration(owner, runtime, ++nextGeneration);
            HOSTS.put(owner, replacement);
        }
        if (previous != null) {
            previous.closeRuntime();
        }
        return replacement;
    }

    public static Optional<BigCraftingHostRuntime<AEKey>> find(Object owner) {
        Objects.requireNonNull(owner, "owner");
        synchronized (HOSTS) {
            Registration registration = HOSTS.get(owner);
            return registration == null || registration.isClosed()
                    ? Optional.empty()
                    : Optional.of(registration.runtime());
        }
    }

    public static Optional<BigCraftingHostRegistration> findRegistration(Object owner) {
        Objects.requireNonNull(owner, "owner");
        synchronized (HOSTS) {
            Registration registration = HOSTS.get(owner);
            return registration == null || registration.isClosed()
                    ? Optional.empty()
                    : Optional.of(registration);
        }
    }

    /** 実行中のServer tickが安全に走査できる不変Snapshotを返す。 */
    public static Map<Object, BigCraftingHostRuntime<AEKey>> snapshot() {
        synchronized (HOSTS) {
            Map<Object, BigCraftingHostRuntime<AEKey>> copy = new IdentityHashMap<>();
            HOSTS.forEach((owner, registration) -> {
                if (!registration.isClosed()) {
                    copy.put(owner, registration.runtime());
                }
            });
            return Map.copyOf(copy);
        }
    }

    /** 旧APIの明示解除。新規コードは登録ハンドルをcloseする。 */
    public static void unregister(Object owner) {
        Registration removed;
        synchronized (HOSTS) {
            removed = HOSTS.remove(Objects.requireNonNull(owner, "owner"));
            if (removed != null) {
                removed.markClosed();
            }
        }
        if (removed != null) {
            removed.closeRuntime();
        }
    }

    /** サーバー終了時に全Hostを閉じ、所有者への強参照を解放する。 */
    public static void clear() {
        List<Registration> removed;
        synchronized (HOSTS) {
            removed = List.copyOf(HOSTS.values());
            HOSTS.clear();
            removed.forEach(Registration::markClosed);
        }
        RuntimeException firstFailure = null;
        for (Registration registration : removed) {
            try {
                registration.closeRuntime();
            } catch (RuntimeException failure) {
                if (firstFailure == null) {
                    firstFailure = failure;
                }
            }
        }
        if (firstFailure != null) {
            throw firstFailure;
        }
    }

    public static int registrationCount() {
        synchronized (HOSTS) {
            return HOSTS.size();
        }
    }

    private static final class Registration implements BigCraftingHostRegistration {
        private final Object owner;
        private final BigCraftingHostRuntime<AEKey> runtime;
        private final long generation;
        private final AtomicBoolean closed = new AtomicBoolean();
        private final CopyOnWriteArrayList<Runnable> closeActions = new CopyOnWriteArrayList<>();

        private Registration(Object owner, BigCraftingHostRuntime<AEKey> runtime, long generation) {
            this.owner = owner;
            this.runtime = runtime;
            this.generation = generation;
        }

        @Override
        public Object ownerIdentity() { return owner; }

        @Override
        public UUID runtimeIdentity() { return runtime.runtimeId(); }

        @Override
        public long generation() { return generation; }

        @Override
        public boolean isClosed() { return closed.get(); }

        @Override
        public BigCraftingHostSnapshot snapshot(
                long revision,
                BigCraftingHostBackendState backendState) {
            synchronized (HOSTS) {
                if (HOSTS.get(owner) != this || isClosed()) {
                    throw new IllegalStateException("crafting host registration is stale");
                }
                return runtime.snapshot(revision, backendState);
            }
        }

        @Override
        public void onClose(Runnable cleanup) {
            Objects.requireNonNull(cleanup, "cleanup");
            if (isClosed()) {
                cleanup.run();
                return;
            }
            closeActions.add(cleanup);
            if (isClosed() && closeActions.remove(cleanup)) {
                cleanup.run();
            }
        }

        @Override
        public void close() {
            boolean removed = false;
            synchronized (HOSTS) {
                if (!closed.get() && HOSTS.get(owner) == this) {
                    HOSTS.remove(owner);
                    removed = true;
                }
                closed.set(true);
            }
            if (removed) {
                closeRuntime();
            }
        }

        private void markClosed() {
            closed.set(true);
        }

        private void closeRuntime() {
            RuntimeException firstFailure = null;
            for (Runnable action : closeActions) {
                try {
                    action.run();
                } catch (RuntimeException failure) {
                    if (firstFailure == null) {
                        firstFailure = failure;
                    }
                }
            }
            closeActions.clear();
            try {
                runtime.close();
            } catch (RuntimeException failure) {
                if (firstFailure == null) {
                    firstFailure = failure;
                }
            }
            if (firstFailure != null) {
                throw firstFailure;
            }
        }

        private BigCraftingHostRuntime<AEKey> runtime() { return runtime; }
    }
}
