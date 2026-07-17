package com.syaru.ae2craftingoptimizer.api.batch;

import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.stacks.KeyCounter;
import java.util.Objects;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class PatternBatchContext {
    private final ICraftingProvider provider;
    private final IPatternDetails pattern;
    private final KeyCounter[] inputsPerExecution;
    private final KeyCounter outputsPerExecution;
    private final Level level;
    private final Direction providerSide;
    private final Direction targetSide;
    private final BlockEntity target;
    private final boolean deterministicTarget;

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
        this.provider = Objects.requireNonNull(provider, "provider");
        this.pattern = Objects.requireNonNull(pattern, "pattern");
        this.inputsPerExecution = copyCounters(inputsPerExecution);
        this.outputsPerExecution = copyCounter(outputsPerExecution);
        this.level = Objects.requireNonNull(level, "level");
        this.providerSide = Objects.requireNonNull(providerSide, "providerSide");
        this.targetSide = Objects.requireNonNull(targetSide, "targetSide");
        this.target = Objects.requireNonNull(target, "target");
        this.deterministicTarget = deterministicTarget;
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

    public Level level() {
        return level;
    }

    public Direction providerSide() {
        return providerSide;
    }

    public Direction targetSide() {
        return targetSide;
    }

    public BlockEntity target() {
        return target;
    }

    public boolean deterministicTarget() {
        return deterministicTarget;
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
