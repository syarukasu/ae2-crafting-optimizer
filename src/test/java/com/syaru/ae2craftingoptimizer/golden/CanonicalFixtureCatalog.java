package com.syaru.ae2craftingoptimizer.golden;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class CanonicalFixtureCatalog {
    private CanonicalFixtureCatalog() {
    }

    static List<CanonicalCraftingFixture> all() {
        BigInteger longMax = BigInteger.valueOf(Long.MAX_VALUE);
        return List.of(
                simple(),
                shapeless(),
                repeatedSlotKey(),
                containerRemainder(),
                tagAlternative(),
                multipleProducer(),
                cycle(),
                missingInput(),
                partialInventory(),
                boundary("boundary_long_max_minus_one", longMax.subtract(BigInteger.ONE)),
                boundary("boundary_long_max", longMax),
                boundary("boundary_long_max_plus_one", longMax.add(BigInteger.ONE)),
                deepChain(),
                wideBranches(),
                sharedIntermediate(),
                staleGeneration());
    }

    private static CanonicalCraftingFixture simple() {
        return new CanonicalCraftingFixture(
                "simple_shaped",
                "iron_gear",
                BigInteger.ONE,
                List.of(CanonicalCraftingFixture.recipe(
                        "iron_gear_recipe",
                        "iron_gear",
                        1,
                        List.of(
                                CanonicalCraftingFixture.slot("north", BigInteger.ONE, "iron_ingot"),
                                CanonicalCraftingFixture.slot("east", BigInteger.ONE, "iron_ingot")))),
                Map.of("iron_ingot", BigInteger.TWO),
                false);
    }

    private static CanonicalCraftingFixture repeatedSlotKey() {
        return new CanonicalCraftingFixture(
                "same_key_multiple_slots",
                "double_plate",
                BigInteger.ONE,
                List.of(CanonicalCraftingFixture.recipe(
                        "double_plate_recipe",
                        "double_plate",
                        1,
                        List.of(
                                CanonicalCraftingFixture.slot("slot_a", BigInteger.ONE, "iron_ingot"),
                                CanonicalCraftingFixture.slot("slot_b", BigInteger.ONE, "iron_ingot")))),
                Map.of("iron_ingot", BigInteger.TWO),
                false);
    }

    private static CanonicalCraftingFixture shapeless() {
        return new CanonicalCraftingFixture(
                "simple_shapeless",
                "mixed_dust",
                BigInteger.ONE,
                List.of(CanonicalCraftingFixture.shapelessRecipe(
                        "mixed_dust_recipe",
                        "mixed_dust",
                        1,
                        List.of(
                                CanonicalCraftingFixture.slot("first", BigInteger.ONE, "red_dust"),
                                CanonicalCraftingFixture.slot("second", BigInteger.ONE, "blue_dust")))),
                Map.of("red_dust", BigInteger.ONE, "blue_dust", BigInteger.ONE),
                false);
    }

    private static CanonicalCraftingFixture containerRemainder() {
        return new CanonicalCraftingFixture(
                "container_remaining_item",
                "filled_cell",
                BigInteger.ONE,
                List.of(CanonicalCraftingFixture.recipeWithRemainder(
                        "filled_cell_recipe",
                        "filled_cell",
                        1,
                        List.of(CanonicalCraftingFixture.slot("container", BigInteger.ONE, "empty_cell")),
                        Map.of("empty_cell", BigInteger.ONE))),
                Map.of("empty_cell", BigInteger.ONE),
                false);
    }

    private static CanonicalCraftingFixture tagAlternative() {
        return new CanonicalCraftingFixture(
                "tag_alternative_concrete_key",
                "alloy",
                BigInteger.ONE,
                List.of(CanonicalCraftingFixture.recipe(
                        "alloy_recipe",
                        "alloy",
                        1,
                        List.of(CanonicalCraftingFixture.slot(
                                "metal_tag",
                                BigInteger.ONE,
                                "minecraft:copper_ingot",
                                "minecraft:iron_ingot")))),
                Map.of("minecraft:iron_ingot", BigInteger.ONE),
                false);
    }

    private static CanonicalCraftingFixture multipleProducer() {
        return new CanonicalCraftingFixture(
                "multiple_producers_not_qualified",
                "ambiguous_output",
                BigInteger.ONE,
                List.of(
                        CanonicalCraftingFixture.recipe(
                                "ambiguous_a",
                                "ambiguous_output",
                                1,
                                List.of(CanonicalCraftingFixture.slot("input", BigInteger.ONE, "a"))),
                        CanonicalCraftingFixture.recipe(
                                "ambiguous_b",
                                "ambiguous_output",
                                1,
                                List.of(CanonicalCraftingFixture.slot("input", BigInteger.ONE, "b")))),
                Map.of("a", BigInteger.ONE),
                false);
    }

    private static CanonicalCraftingFixture cycle() {
        return new CanonicalCraftingFixture(
                "cycle",
                "cycle_a",
                BigInteger.ONE,
                List.of(
                        CanonicalCraftingFixture.recipe(
                                "cycle_a_recipe",
                                "cycle_a",
                                1,
                                List.of(CanonicalCraftingFixture.slot("input", BigInteger.ONE, "cycle_b"))),
                        CanonicalCraftingFixture.recipe(
                                "cycle_b_recipe",
                                "cycle_b",
                                1,
                                List.of(CanonicalCraftingFixture.slot("input", BigInteger.ONE, "cycle_a")))),
                Map.of(),
                false);
    }

    private static CanonicalCraftingFixture missingInput() {
        return new CanonicalCraftingFixture(
                "missing_input",
                "missing_result",
                BigInteger.ONE,
                List.of(CanonicalCraftingFixture.recipe(
                        "missing_result_recipe",
                        "missing_result",
                        1,
                        List.of(CanonicalCraftingFixture.slot("input", BigInteger.ONE, "missing_material")))),
                Map.of(),
                false);
    }

    private static CanonicalCraftingFixture partialInventory() {
        return new CanonicalCraftingFixture(
                "partial_inventory",
                "advanced_part",
                BigInteger.valueOf(3L),
                List.of(
                        CanonicalCraftingFixture.recipe(
                                "advanced_part_recipe",
                                "advanced_part",
                                1,
                                List.of(CanonicalCraftingFixture.slot("plate", BigInteger.TWO, "plate"))),
                        CanonicalCraftingFixture.recipe(
                                "plate_recipe",
                                "plate",
                                1,
                                List.of(CanonicalCraftingFixture.slot("ingot", BigInteger.ONE, "ingot")))),
                Map.of("plate", BigInteger.ONE, "ingot", BigInteger.TWO),
                false);
    }

    private static CanonicalCraftingFixture boundary(String id, BigInteger request) {
        return new CanonicalCraftingFixture(
                id,
                "boundary_output",
                request,
                List.of(CanonicalCraftingFixture.recipe(
                        id + "_recipe",
                        "boundary_output",
                        1,
                        List.of(CanonicalCraftingFixture.slot("input", BigInteger.ONE, "boundary_input")))),
                Map.of("boundary_input", request),
                false);
    }

    private static CanonicalCraftingFixture deepChain() {
        List<CanonicalCraftingFixture.Recipe> recipes = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            String output = "deep_" + index;
            String input = index == 0 ? "deep_source" : "deep_" + (index - 1);
            recipes.add(CanonicalCraftingFixture.recipe(
                    "deep_recipe_" + index,
                    output,
                    1,
                    List.of(CanonicalCraftingFixture.slot("input_" + index, BigInteger.ONE, input))));
        }
        return new CanonicalCraftingFixture(
                "deep_dependency_chain",
                "deep_19",
                BigInteger.valueOf(8L),
                recipes,
                Map.of("deep_source", BigInteger.valueOf(8L)),
                false);
    }

    private static CanonicalCraftingFixture wideBranches() {
        List<CanonicalCraftingFixture.Recipe> recipes = new ArrayList<>();
        List<CanonicalCraftingFixture.InputSlot> inputs = new ArrayList<>();
        for (int index = 0; index < 24; index++) {
            String branch = "branch_" + index;
            recipes.add(CanonicalCraftingFixture.recipe(
                    "branch_recipe_" + index,
                    branch,
                    1,
                    List.of(CanonicalCraftingFixture.slot(
                            "branch_input_" + index,
                            BigInteger.ONE,
                            "source_" + index))));
            inputs.add(CanonicalCraftingFixture.slot(
                    "branch_slot_" + index,
                    BigInteger.ONE,
                    branch));
        }
        recipes.add(CanonicalCraftingFixture.recipe(
                "wide_root_recipe",
                "wide_root",
                1,
                inputs));
        Map<String, BigInteger> inventory = new LinkedHashMap<>();
        for (int index = 0; index < 24; index++) {
            inventory.put("source_" + index, BigInteger.ONE);
        }
        return new CanonicalCraftingFixture(
                "wide_dependency_tree",
                "wide_root",
                BigInteger.ONE,
                recipes,
                inventory,
                false);
    }

    private static CanonicalCraftingFixture sharedIntermediate() {
        return new CanonicalCraftingFixture(
                "shared_intermediate",
                "final_product",
                BigInteger.valueOf(4L),
                List.of(
                        CanonicalCraftingFixture.recipe(
                                "final_product_recipe",
                                "final_product",
                                1,
                                List.of(
                                        CanonicalCraftingFixture.slot("first", BigInteger.ONE, "shared_part"),
                                        CanonicalCraftingFixture.slot("second", BigInteger.ONE, "shared_part"))),
                        CanonicalCraftingFixture.recipe(
                                "shared_part_recipe",
                                "shared_part",
                                1,
                                List.of(CanonicalCraftingFixture.slot("source", BigInteger.ONE, "shared_source")))),
                Map.of("shared_source", BigInteger.valueOf(8L)),
                false);
    }

    private static CanonicalCraftingFixture staleGeneration() {
        return new CanonicalCraftingFixture(
                "generation_changed",
                "stale_output",
                BigInteger.ONE,
                List.of(CanonicalCraftingFixture.recipe(
                        "stale_recipe",
                        "stale_output",
                        1,
                        List.of(CanonicalCraftingFixture.slot("input", BigInteger.ONE, "stale_input")))),
                Map.of("stale_input", BigInteger.ONE),
                true);
    }
}
