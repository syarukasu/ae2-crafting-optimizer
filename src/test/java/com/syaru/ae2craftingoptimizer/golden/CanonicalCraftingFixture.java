package com.syaru.ae2craftingoptimizer.golden;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Pure-Java, version-neutral crafting fixture shared by parity tests. */
record CanonicalCraftingFixture(
        String id,
        String rootOutput,
        BigInteger requestedAmount,
        List<Recipe> recipes,
        Map<String, BigInteger> inventory,
        boolean generationStale) {
    CanonicalCraftingFixture {
        id = required(id, "fixture id");
        rootOutput = required(rootOutput, "root output");
        requestedAmount = positive(requestedAmount, "requested amount");
        recipes = List.copyOf(Objects.requireNonNull(recipes, "recipes"));
        inventory = immutablePositiveMap(inventory, "inventory", false);
    }

    String canonicalDefinition() {
        StringBuilder value = new StringBuilder(id);
        value.append('|').append(rootOutput).append('|').append(requestedAmount);
        recipes.stream()
                .sorted((left, right) -> left.id().compareTo(right.id()))
                .forEach(recipe -> value.append('|').append(recipe.canonical()));
        inventory.keySet().stream().sorted().forEach(key -> value.append('|')
                .append(key).append('=').append(inventory.get(key)));
        value.append("|stale=").append(generationStale);
        return value.toString();
    }

    record Recipe(
            String id,
            String output,
            BigInteger outputAmount,
            List<InputSlot> inputs,
            Map<String, BigInteger> remainingItems,
            boolean shaped) {
        Recipe {
            id = required(id, "recipe id");
            output = required(output, "recipe output");
            outputAmount = positive(outputAmount, "recipe output amount");
            inputs = List.copyOf(Objects.requireNonNull(inputs, "recipe inputs"));
            remainingItems = immutablePositiveMap(remainingItems, "remaining items", true);
        }

        String canonical() {
            StringBuilder value = new StringBuilder(id)
                    .append(shaped ? ":shaped" : ":shapeless")
                    .append("->").append(output).append('@').append(outputAmount);
            inputs.stream()
                    .sorted((left, right) -> left.id().compareTo(right.id()))
                    .forEach(input -> value.append('|').append(input.canonical()));
            remainingItems.keySet().stream().sorted().forEach(key -> value.append('|')
                    .append("remaining:").append(key).append('=').append(remainingItems.get(key)));
            return value.toString();
        }
    }

    record InputSlot(String id, List<String> candidates, BigInteger amount) {
        InputSlot {
            id = required(id, "input slot id");
            candidates = new ArrayList<>(Objects.requireNonNull(candidates, "candidates"));
            if (candidates.isEmpty()) {
                throw new IllegalArgumentException("input slot must contain an alternative");
            }
            candidates.replaceAll(value -> required(value, "input candidate"));
            candidates = Collections.unmodifiableList(candidates);
            amount = positive(amount, "input amount");
        }

        String canonical() {
            return id + "=" + String.join(",", candidates.stream().sorted().toList()) + '@' + amount;
        }
    }

    static InputSlot slot(String id, BigInteger amount, String... candidates) {
        return new InputSlot(id, List.of(candidates), amount);
    }

    static Recipe recipe(
            String id,
            String output,
            long outputAmount,
            List<InputSlot> inputs) {
        return new Recipe(
                id,
                output,
                BigInteger.valueOf(outputAmount),
                inputs,
                Map.of(),
                true);
    }

    static Recipe shapelessRecipe(
            String id,
            String output,
            long outputAmount,
            List<InputSlot> inputs) {
        return new Recipe(
                id,
                output,
                BigInteger.valueOf(outputAmount),
                inputs,
                Map.of(),
                false);
    }

    static Recipe recipeWithRemainder(
            String id,
            String output,
            long outputAmount,
            List<InputSlot> inputs,
            Map<String, BigInteger> remainingItems) {
        return new Recipe(
                id,
                output,
                BigInteger.valueOf(outputAmount),
                inputs,
                remainingItems,
                true);
    }

    private static Map<String, BigInteger> immutablePositiveMap(
            Map<String, BigInteger> values,
            String name,
            boolean allowEmpty) {
        Objects.requireNonNull(values, name);
        if (!allowEmpty && values.values().stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(name + " contains null");
        }
        Map<String, BigInteger> result = new LinkedHashMap<>();
        values.forEach((key, value) -> result.put(
                required(key, name + " key"),
                positive(value, name + " amount")));
        return Collections.unmodifiableMap(result);
    }

    private static String required(String value, String name) {
        String checked = Objects.requireNonNull(value, name);
        if (checked.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return checked;
    }

    private static BigInteger positive(BigInteger value, String name) {
        BigInteger checked = Objects.requireNonNull(value, name);
        if (checked.signum() <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return checked;
    }
}
