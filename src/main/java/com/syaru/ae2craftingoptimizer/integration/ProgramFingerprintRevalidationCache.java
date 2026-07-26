package com.syaru.ae2craftingoptimizer.integration;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * 現在のPattern/recipe世代で再検証済みの数式Program指紋を保持する。
 *
 * <p>無関係なProvider更新後も、同じJobを毎tick再コンパイルしないためのController内キャッシュ。</p>
 */
final class ProgramFingerprintRevalidationCache {
    /** 未初期化状態を実在する非負世代番号と区別する番兵値。 */
    private static final long UNINITIALIZED_GENERATION = Long.MIN_VALUE;

    private long patternGeneration = UNINITIALIZED_GENERATION;
    private long recipeGeneration = UNINITIALIZED_GENERATION;
    private final Set<String> fingerprints = new HashSet<>();

    synchronized boolean contains(
            long currentPatternGeneration,
            long currentRecipeGeneration,
            String fingerprint) {
        refreshGeneration(
                currentPatternGeneration,
                currentRecipeGeneration);
        return fingerprints.contains(requireFingerprint(fingerprint));
    }

    synchronized void record(
            long currentPatternGeneration,
            long currentRecipeGeneration,
            String fingerprint) {
        refreshGeneration(
                currentPatternGeneration,
                currentRecipeGeneration);
        fingerprints.add(requireFingerprint(fingerprint));
    }

    private void refreshGeneration(
            long currentPatternGeneration,
            long currentRecipeGeneration) {
        // 同じ世代では、既に証明済みの指紋集合をそのまま再利用する。
        if (patternGeneration == currentPatternGeneration
                && recipeGeneration == currentRecipeGeneration) {
            return;
        }
        patternGeneration = currentPatternGeneration;
        recipeGeneration = currentRecipeGeneration;
        fingerprints.clear();
    }

    private static String requireFingerprint(String fingerprint) {
        String checked = Objects.requireNonNull(
                        fingerprint, "fingerprint")
                .trim();
        // 空指紋は異なるProgram同士を同一視できるため、キャッシュへ入れない。
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(
                    "fingerprint must not be blank");
        }
        return checked;
    }
}
