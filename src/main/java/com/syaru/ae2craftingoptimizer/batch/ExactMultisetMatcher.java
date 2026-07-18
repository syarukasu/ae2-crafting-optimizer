package com.syaru.ae2craftingoptimizer.batch;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ExactMultisetMatcher {
    private static final int MAX_REQUIREMENTS = 64;

    private ExactMultisetMatcher() {
    }

    public static <K> boolean matchesExactly(
            Map<K, Long> available,
            List<? extends Requirement<K>> requirements) {
        Objects.requireNonNull(available, "available");
        Objects.requireNonNull(requirements, "requirements");
        if (requirements.size() > MAX_REQUIREMENTS) {
            return false;
        }

        Map<K, Long> remaining = new HashMap<>();
        for (var entry : available.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null || entry.getValue() <= 0L) {
                return false;
            }
            remaining.merge(entry.getKey(), entry.getValue(), Math::addExact);
        }

        List<ResolvedRequirement<K>> resolved = new ArrayList<>(requirements.size());
        for (Requirement<K> requirement : requirements) {
            if (requirement == null) {
                return false;
            }
            List<Candidate<K>> candidates = new ArrayList<>();
            for (var entry : remaining.entrySet()) {
                long amount = requirement.amountFor(entry.getKey());
                if (amount > 0L && amount <= entry.getValue()) {
                    candidates.add(new Candidate<>(entry.getKey(), amount));
                }
            }
            if (candidates.isEmpty()) {
                return false;
            }
            resolved.add(new ResolvedRequirement<>(List.copyOf(candidates)));
        }
        resolved.sort(Comparator.comparingInt(value -> value.candidates().size()));
        return match(resolved, 0, remaining) && remaining.values().stream().allMatch(value -> value == 0L);
    }

    private static <K> boolean match(
            List<ResolvedRequirement<K>> requirements,
            int index,
            Map<K, Long> remaining) {
        if (index == requirements.size()) {
            return remaining.values().stream().allMatch(value -> value == 0L);
        }
        for (Candidate<K> candidate : requirements.get(index).candidates()) {
            long current = remaining.getOrDefault(candidate.key(), 0L);
            if (current < candidate.amount()) {
                continue;
            }
            remaining.put(candidate.key(), current - candidate.amount());
            if (match(requirements, index + 1, remaining)) {
                return true;
            }
            remaining.put(candidate.key(), current);
        }
        return false;
    }

    @FunctionalInterface
    public interface Requirement<K> {
        /** Returns the exact amount consumed for this key, or zero if it does not match. */
        long amountFor(K key);
    }

    private record Candidate<K>(K key, long amount) {
    }

    private record ResolvedRequirement<K>(List<Candidate<K>> candidates) {
    }
}
