package com.syaru.ae2craftingoptimizer.optimization;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/** V2取引が同一server tickに同じtargetへ二重commitすることを防ぐ。 */
public final class TransactionalBatchTargetGuard {
    /** 異常な座標流入で一tickのSetが無制限に成長しないための固定上限。 */
    private static final int MAX_TARGETS_PER_TICK = 1_048_576;
    private static final Map<Object, TickClaims> CLAIMS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private TransactionalBatchTargetGuard() {
    }

    public static boolean tryClaim(Level level, BlockPos target) {
        Objects.requireNonNull(level, "level");
        Objects.requireNonNull(target, "target");
        return tryClaim(level, target.asLong(), level.getGameTime());
    }

    static boolean tryClaim(Object scope, long target, long gameTick) {
        Objects.requireNonNull(scope, "scope");
        // 負のtickはLevel由来ではないため、呼出側の不整合として拒否する。
        if (gameTick < 0L) {
            throw new IllegalArgumentException("gameTick must not be negative");
        }
        synchronized (CLAIMS) {
            TickClaims claims = CLAIMS.computeIfAbsent(scope, ignored -> new TickClaims());
            // tickが変わった時だけ前tickのclaimをまとめて破棄する。
            if (claims.gameTick != gameTick) {
                claims.gameTick = gameTick;
                claims.targets.clear();
            }
            // 同一targetの二重commitを、targetへ触る前に拒否する。
            if (claims.targets.contains(target)) {
                return false;
            }
            // 固定上限到達後は新しい所有権を取らずfail-closedにする。
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
