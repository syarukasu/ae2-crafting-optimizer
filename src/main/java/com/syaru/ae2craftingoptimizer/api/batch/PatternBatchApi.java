package com.syaru.ae2craftingoptimizer.api.batch;

import com.syaru.ae2craftingoptimizer.batch.SequentialPatternProviderBatchAdapter;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.resources.ResourceLocation;

public final class PatternBatchApi {
    private static final CopyOnWriteArrayList<PatternBatchAdapter> ADAPTERS = new CopyOnWriteArrayList<>();
    private static final AtomicBoolean BUILT_INS_REGISTERED = new AtomicBoolean();

    private PatternBatchApi() {
    }

    public static void registerBuiltIns() {
        if (BUILT_INS_REGISTERED.compareAndSet(false, true)) {
            register(SequentialPatternProviderBatchAdapter.INSTANCE);
        }
    }

    public static synchronized void register(PatternBatchAdapter adapter) {
        Objects.requireNonNull(adapter, "adapter");
        ResourceLocation id = Objects.requireNonNull(adapter.id(), "adapter.id()");
        for (PatternBatchAdapter existing : ADAPTERS) {
            if (existing.id().equals(id)) {
                throw new IllegalArgumentException("A pattern batch adapter is already registered as " + id);
            }
        }
        ADAPTERS.add(adapter);
        ADAPTERS.sort(Comparator.comparingInt(PatternBatchAdapter::priority).reversed());
    }

    public static synchronized boolean unregister(ResourceLocation id) {
        Objects.requireNonNull(id, "id");
        return ADAPTERS.removeIf(adapter -> adapter.id().equals(id));
    }

    public static Optional<PatternBatchAdapter> find(PatternBatchContext context) {
        Objects.requireNonNull(context, "context");
        for (PatternBatchAdapter adapter : ADAPTERS) {
            if ((context.deterministicTarget() || adapter.supportsMultipleProviderTargets())
                    && adapter.supports(context)) {
                return Optional.of(adapter);
            }
        }
        return Optional.empty();
    }

    public static PatternBatchResult commit(
            PatternBatchAdapter adapter,
            PatternBatchContext context,
            PatternBatchBudget budget) {
        Objects.requireNonNull(adapter, "adapter");
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(budget, "budget");
        PatternBatchResult result = adapter.commit(context, budget);
        if (result == null) {
            throw new IllegalStateException("Pattern batch adapter " + adapter.id() + " returned null");
        }
        if (result.acceptedExecutions() > budget.maximumExecutions()) {
            throw new IllegalStateException(
                    "Pattern batch adapter " + adapter.id() + " accepted "
                            + result.acceptedExecutions() + " execution(s) for a maximum of "
                            + budget.maximumExecutions());
        }
        return result;
    }

    public static long limitExecutions(
            PatternBatchAdapter adapter,
            PatternBatchContext context,
            long offeredExecutions) {
        Objects.requireNonNull(adapter, "adapter");
        Objects.requireNonNull(context, "context");
        if (offeredExecutions <= 0L) {
            throw new IllegalArgumentException("offeredExecutions must be positive");
        }
        long limited = adapter.limitExecutions(context, offeredExecutions);
        if (limited < 0L || limited > offeredExecutions) {
            throw new IllegalStateException(
                    "Pattern batch adapter " + adapter.id() + " limited "
                            + offeredExecutions + " execution(s) to invalid value " + limited);
        }
        return limited;
    }

    public static List<ResourceLocation> registeredAdapterIds() {
        return ADAPTERS.stream().map(PatternBatchAdapter::id).toList();
    }
}
