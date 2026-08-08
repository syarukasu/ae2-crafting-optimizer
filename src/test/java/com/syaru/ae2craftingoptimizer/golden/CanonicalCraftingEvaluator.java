package com.syaru.ae2craftingoptimizer.golden;

import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Deterministic semantic model used by both the reference and optimized fixture
 * paths. It deliberately has no Minecraft, Forge, or NeoForge dependency.
 */
final class CanonicalCraftingEvaluator {
    private CanonicalCraftingEvaluator() {
    }

    static CanonicalCraftingResult reference(CanonicalCraftingFixture fixture) {
        return evaluate(fixture, false);
    }

    static CanonicalCraftingResult optimized(CanonicalCraftingFixture fixture) {
        return evaluate(fixture, true);
    }

    private static CanonicalCraftingResult evaluate(
            CanonicalCraftingFixture fixture,
            boolean optimized) {
        String status = fixture.generationStale() ? "STALE_GENERATION" : "OK";
        State state = new State(fixture, optimized);
        if (!fixture.generationStale()) {
            state.plan(fixture.rootOutput(), fixture.requestedAmount(), new ArrayDeque<>());
        }
        if (state.status != null) {
            status = state.status;
        } else if (!state.missing.isEmpty()) {
            status = "MISSING_INPUT";
        }
        Map<String, BigInteger> finalTotals = new TreeMap<>(fixture.inventory());
        state.used.forEach((key, amount) -> finalTotals.merge(key, amount.negate(), BigInteger::add));
        state.emitted.forEach((key, amount) -> finalTotals.merge(key, amount, BigInteger::add));
        finalTotals.entrySet().removeIf(entry -> entry.getValue().signum() == 0);
        BigInteger reservedBytes = state.patternExecutions.values().stream()
                .reduce(BigInteger.ZERO, (total, count) -> total.add(count.multiply(BigInteger.valueOf(256L))));
        return new CanonicalCraftingResult(
                fixture.id(),
                fixture.rootOutput(),
                fixture.requestedAmount(),
                status,
                state.selectedConcreteKeys,
                state.patternExecutions,
                state.used,
                state.emitted,
                state.remaining,
                state.missing,
                finalTotals,
                reservedBytes,
                sha256(fixture.canonicalDefinition()));
    }

    private static String sha256(String value) {
        return CanonicalCraftingResultDigest.sha256(value);
    }

    private static final class State {
        private final CanonicalCraftingFixture fixture;
        private final Map<String, List<CanonicalCraftingFixture.Recipe>> byOutput = new HashMap<>();
        private final Map<String, BigInteger> inventory;
        private final Map<String, BigInteger> used = new LinkedHashMap<>();
        private final Map<String, BigInteger> emitted = new LinkedHashMap<>();
        private final Map<String, BigInteger> remaining = new LinkedHashMap<>();
        private final Map<String, BigInteger> missing = new LinkedHashMap<>();
        private final Map<String, BigInteger> patternExecutions = new LinkedHashMap<>();
        private final Map<String, String> selectedConcreteKeys = new LinkedHashMap<>();
        private final boolean optimized;
        private String status;

        private State(CanonicalCraftingFixture fixture, boolean optimized) {
            this.fixture = fixture;
            this.optimized = optimized;
            this.inventory = new LinkedHashMap<>(fixture.inventory());
            for (CanonicalCraftingFixture.Recipe recipe : fixture.recipes()) {
                byOutput.computeIfAbsent(recipe.output(), ignored -> new ArrayList<>()).add(recipe);
            }
            if (optimized) {
                byOutput.values().forEach(list -> list.sort((left, right) -> left.id().compareTo(right.id())));
            }
        }

        private void plan(String key, BigInteger requested, Deque<String> path) {
            if (status != null || requested.signum() <= 0) {
                return;
            }
            BigInteger available = inventory.getOrDefault(key, BigInteger.ZERO);
            BigInteger consumed = available.min(requested);
            if (consumed.signum() > 0) {
                inventory.put(key, available.subtract(consumed));
                used.merge(key, consumed, BigInteger::add);
            }
            BigInteger remainingNeed = requested.subtract(consumed);
            if (remainingNeed.signum() == 0) {
                return;
            }
            if (path.contains(key)) {
                status = "CYCLE";
                return;
            }
            List<CanonicalCraftingFixture.Recipe> producers = byOutput.getOrDefault(key, List.of());
            if (producers.size() > 1) {
                status = "AMBIGUOUS_PRODUCER";
                return;
            }
            if (producers.isEmpty()) {
                missing.merge(key, remainingNeed, BigInteger::add);
                return;
            }
            CanonicalCraftingFixture.Recipe recipe = producers.getFirst();
            BigInteger executions = divideCeiling(remainingNeed, recipe.outputAmount());
            patternExecutions.merge(recipe.id(), executions, BigInteger::add);
            emitted.merge(
                    recipe.output(),
                    executions.multiply(recipe.outputAmount()),
                    BigInteger::add);
            recipe.remainingItems().forEach((remainingKey, amount) -> {
                BigInteger total = executions.multiply(amount);
                emitted.merge(remainingKey, total, BigInteger::add);
                remaining.merge(remainingKey, total, BigInteger::add);
            });
            path.addLast(key);
            List<CanonicalCraftingFixture.InputSlot> inputs = new ArrayList<>(recipe.inputs());
            if (optimized) {
                inputs.sort((left, right) -> left.id().compareTo(right.id()));
            }
            for (CanonicalCraftingFixture.InputSlot input : inputs) {
                String concrete = input.candidates().stream().sorted().findFirst().orElseThrow();
                selectedConcreteKeys.put(input.id(), concrete);
                plan(concrete, executions.multiply(input.amount()), path);
                if (status != null) {
                    break;
                }
            }
            path.removeLast();
        }

        private static BigInteger divideCeiling(BigInteger value, BigInteger divisor) {
            return value.add(divisor).subtract(BigInteger.ONE).divide(divisor);
        }
    }

    private static final class CanonicalCraftingResultDigest {
        private CanonicalCraftingResultDigest() {
        }

        private static String sha256(String value) {
            try {
                byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                        .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                StringBuilder result = new StringBuilder(64);
                for (byte valueByte : digest) {
                    result.append(String.format("%02x", valueByte & 0xff));
                }
                return result.toString();
            } catch (java.security.NoSuchAlgorithmException impossible) {
                throw new AssertionError(impossible);
            }
        }
    }
}
