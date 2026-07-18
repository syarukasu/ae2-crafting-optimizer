package com.syaru.ae2craftingoptimizer.optimization;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** Allows at most one native aggregate push to one target in a server tick. */
public final class NativeBatchTargetGuard {
    private static final int MAX_TARGETS_PER_TICK = 1_048_576;
    private static final Map<Object, TickClaims> CLAIMS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private NativeBatchTargetGuard() {
    }

    public static boolean tryClaim(Level level, BlockPos target) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(target, "target");
        return tryClaim(level, target.asLong(), level.getGameTime());
    }

    static boolean tryClaim(Object scope, long target, long gameTick) {
        Objects.requireNonNull(scope, "scope");
        if (gameTick < 0L) {
            throw new IllegalArgumentException("gameTick must not be negative");
        }
        synchronized (CLAIMS) {
            TickClaims claims = CLAIMS.computeIfAbsent(scope, ignored -> new TickClaims());
            if (claims.gameTick != gameTick) {
                claims.gameTick = gameTick;
                claims.targets.clear();
            }
            if (claims.targets.contains(target)) {
                return false;
            }
            if (claims.targets.size() >= MAX_TARGETS_PER_TICK) {
                return false;
            }
            claims.targets.add(target);
            return true;
        }
    }

    public static void clear() {
        synchronized (CLAIMS) {
            CLAIMS.clear();
        }
    }

    private static final class TickClaims {
        private long gameTick = Long.MIN_VALUE;
        private final Set<Long> targets = new HashSet<>();
    }
}
