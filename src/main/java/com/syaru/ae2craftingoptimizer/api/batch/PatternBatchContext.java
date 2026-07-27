package com.syaru.ae2craftingoptimizer.api.batch;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

public final class PatternBatchContext {
    private final ICraftingProvider provider;
    private final IPatternDetails pattern;
    private final KeyCounter[] inputsPerExecution;
    private final KeyCounter outputsPerExecution;
    private final KeyCounter remainingOutputsPerExecution;
    private final Level level;
    @Nullable
    private final Direction providerSide;
    @Nullable
    private final Direction targetSide;
    private final BlockEntity target;
    private final boolean deterministicTarget;
    private final boolean providerOwnedTarget;
    @Nullable
    private final UUID craftingJobId;

    public PatternBatchContext(
            ICraftingProvider provider,
            IPatternDetails pattern,
            KeyCounter[] inputsPerExecution,
            KeyCounter outputsPerExecution,
            Level level,
            Direction providerSide,
            Direction targetSide,
            BlockEntity target,
            boolean deterministicTarget) {
        this(
                provider,
                pattern,
                inputsPerExecution,
                outputsPerExecution,
                new KeyCounter(),
                level,
                Objects.requireNonNull(providerSide, "providerSide"),
                Objects.requireNonNull(targetSide, "targetSide"),
                target,
                deterministicTarget,
                false,
                null);
    }

    private PatternBatchContext(
            ICraftingProvider provider,
            IPatternDetails pattern,
            KeyCounter[] inputsPerExecution,
            KeyCounter outputsPerExecution,
            KeyCounter remainingOutputsPerExecution,
            Level level,
            @Nullable Direction providerSide,
            @Nullable Direction targetSide,
            BlockEntity target,
            boolean deterministicTarget,
            boolean providerOwnedTarget,
            @Nullable UUID craftingJobId) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.pattern = Objects.requireNonNull(pattern, "pattern");
        this.inputsPerExecution = copyCounters(inputsPerExecution);
        this.outputsPerExecution = copyCounter(outputsPerExecution);
        this.remainingOutputsPerExecution = copyCounter(remainingOutputsPerExecution);
        this.level = Objects.requireNonNull(level, "level");
        this.providerSide = providerSide;
        this.targetSide = targetSide;
        this.target = Objects.requireNonNull(target, "target");
        this.deterministicTarget = deterministicTarget;
        this.providerOwnedTarget = providerOwnedTarget;
        this.craftingJobId = craftingJobId;
    }

    /**
     * Pattern Provider自身、またはProviderが属するマルチブロックをBatch所有者にするContext。
     *
     * <p>外部Inventoryの面情報は存在しないため、Provider所有Adapterは
     * {@link #providerOwnedTarget()}を確認してからこのContextを使用する。</p>
     */
    public static PatternBatchContext providerOwned(
            ICraftingProvider provider,
            IPatternDetails pattern,
            KeyCounter[] inputsPerExecution,
            KeyCounter outputsPerExecution,
            KeyCounter remainingOutputsPerExecution,
            Level level,
            BlockEntity target,
            UUID craftingJobId) {
        return new PatternBatchContext(
                provider,
                pattern,
                inputsPerExecution,
                outputsPerExecution,
                remainingOutputsPerExecution,
                level,
                null,
                null,
                target,
                true,
                true,
                Objects.requireNonNull(craftingJobId, "craftingJobId"));
    }

    public ICraftingProvider provider() {
        return provider;
    }

    public IPatternDetails pattern() {
        return pattern;
    }

    public KeyCounter[] copyInputsPerExecution() {
        return copyCounters(inputsPerExecution);
    }

    public KeyCounter copyOutputsPerExecution() {
        return copyCounter(outputsPerExecution);
    }

    public KeyCounter copyRemainingOutputsPerExecution() {
        return copyCounter(remainingOutputsPerExecution);
    }

    public Level level() {
        return level;
    }

    @Nullable
    public Direction providerSide() {
        return providerSide;
    }

    @Nullable
    public Direction targetSide() {
        return targetSide;
    }

    public BlockEntity target() {
        return target;
    }

    public boolean deterministicTarget() {
        return deterministicTarget;
    }

    public boolean providerOwnedTarget() {
        return providerOwnedTarget;
    }

    @Nullable
    public UUID craftingJobId() {
        return craftingJobId;
    }

    private static KeyCounter[] copyCounters(KeyCounter[] source) {
        Objects.requireNonNull(source, "source");
        KeyCounter[] copy = new KeyCounter[source.length];
        for (int index = 0; index < source.length; index++) {
            copy[index] = copyCounter(Objects.requireNonNull(source[index], "source[" + index + "]"));
        }
        return copy;
    }

    private static KeyCounter copyCounter(KeyCounter source) {
        Objects.requireNonNull(source, "source");
        KeyCounter copy = new KeyCounter();
        for (var entry : source) {
            copy.add(entry.getKey(), entry.getLongValue());
        }
        return copy;
    }
}
