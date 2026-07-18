package com.syaru.ae2craftingoptimizer.api.batch.v2;

import com.syaru.ae2craftingoptimizer.api.batch.PatternBatchContext;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.resources.ResourceLocation;
import com.syaru.ae2craftingoptimizer.batch.Ae2BatchSourceReconciler;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PatternBatchV2Api {
    private static final CopyOnWriteArrayList<TransactionalPatternBatchAdapter> ADAPTERS =
            new CopyOnWriteArrayList<>();
    private static final Map<ResourceLocation, TransactionalPatternBatchAdapter> ADAPTERS_BY_ID =
            new ConcurrentHashMap<>();
    private static final Map<ResourceLocation, BatchSourceReconciler> SOURCES = new ConcurrentHashMap<>();
    private static final AtomicBoolean BUILT_INS_REGISTERED = new AtomicBoolean();

    private PatternBatchV2Api() {
    }

    public static void registerBuiltIns() {
        if (BUILT_INS_REGISTERED.compareAndSet(false, true)) {
            registerSource(Ae2BatchSourceReconciler.INSTANCE);
        }
    }

    public static synchronized void registerAdapter(TransactionalPatternBatchAdapter adapter) {
        Objects.requireNonNull(adapter, "adapter");
        if (ADAPTERS_BY_ID.putIfAbsent(adapter.id(), adapter) != null) {
            throw new IllegalArgumentException("duplicate V2 batch adapter " + adapter.id());
        }
        ADAPTERS.add(adapter);
        ADAPTERS.sort(Comparator.comparingInt(TransactionalPatternBatchAdapter::priority).reversed());
    }

    public static void registerSource(BatchSourceReconciler source) {
        Objects.requireNonNull(source, "source");
        if (SOURCES.putIfAbsent(source.id(), source) != null) {
            throw new IllegalArgumentException("duplicate V2 batch source " + source.id());
        }
    }

    public static Optional<TransactionalPatternBatchAdapter> find(PatternBatchContext context) {
        return ADAPTERS.stream().filter(adapter -> adapter.supports(context)).findFirst();
    }

    public static Optional<TransactionalPatternBatchAdapter> adapter(ResourceLocation id) {
        return Optional.ofNullable(ADAPTERS_BY_ID.get(id));
    }

    public static Optional<BatchSourceReconciler> source(ResourceLocation id) {
        return Optional.ofNullable(SOURCES.get(id));
    }

    public static List<ResourceLocation> registeredAdapterIds() {
        return ADAPTERS.stream().map(TransactionalPatternBatchAdapter::id).toList();
    }
}
