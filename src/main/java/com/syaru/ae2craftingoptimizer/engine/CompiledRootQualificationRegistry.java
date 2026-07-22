package com.syaru.ae2craftingoptimizer.engine;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * AE2標準計画とのShadow一致実績を、世代付きRoot Program単位で記録する。
 * Programは世代Snapshotに所有されるため、Pattern変更後の新Programへ古い実績を持ち越さない。
 */
public final class CompiledRootQualificationRegistry {
    private static final Map<CompiledRootProgram<?>, Qualification> QUALIFICATIONS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private CompiledRootQualificationRegistry() {
    }

    public static void recordMatch(CompiledRootProgram<?> program) {
        Objects.requireNonNull(program, "program");
        synchronized (QUALIFICATIONS) {
            Qualification current = QUALIFICATIONS.getOrDefault(program, Qualification.NEW);
            // 一度不一致になった同一世代Programは、一致回数を後から増やして再採用しない。
            if (current.rejected()) {
                return;
            }
            int matches = current.matches();
            // 長時間サーバーでも整数overflowしないよう、最大値で飽和させる。
            if (matches < Integer.MAX_VALUE) {
                matches++;
            }
            QUALIFICATIONS.put(program, new Qualification(matches, false));
        }
    }

    public static void recordMismatch(CompiledRootProgram<?> program) {
        Objects.requireNonNull(program, "program");
        synchronized (QUALIFICATIONS) {
            Qualification current = QUALIFICATIONS.getOrDefault(program, Qualification.NEW);
            QUALIFICATIONS.put(program, new Qualification(current.matches(), true));
        }
    }

    public static boolean isQualified(CompiledRootProgram<?> program, int requiredMatches) {
        Objects.requireNonNull(program, "program");
        // 0は管理者が明示的にShadow待機を省略するための設定値。
        if (requiredMatches <= 0) {
            return true;
        }
        synchronized (QUALIFICATIONS) {
            Qualification current = QUALIFICATIONS.getOrDefault(program, Qualification.NEW);
            return !current.rejected() && current.matches() >= requiredMatches;
        }
    }

    static Qualification qualification(CompiledRootProgram<?> program) {
        synchronized (QUALIFICATIONS) {
            return QUALIFICATIONS.getOrDefault(program, Qualification.NEW);
        }
    }

    public static void clear() {
        synchronized (QUALIFICATIONS) {
            QUALIFICATIONS.clear();
        }
    }

    record Qualification(int matches, boolean rejected) {
        private static final Qualification NEW = new Qualification(0, false);
    }
}
