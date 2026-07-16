package com.syaru.ae2craftingoptimizer.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.syaru.ae2craftingoptimizer.gtceu.GTCEuRecipeIntentFastPath;
import com.syaru.ae2craftingoptimizer.intent.RecipeIntent;
import com.syaru.ae2craftingoptimizer.intent.RecipeIntentRegistry;
import com.syaru.ae2craftingoptimizer.mekanism.MekanismRecipeIntentFastPath;
import com.syaru.ae2craftingoptimizer.optimization.OptimizationMetrics;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class ACOIntentCommands {
    private ACOIntentCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("aco")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("stats")
                        .executes(context -> showStats(context.getSource()))
                        .then(Commands.literal("reset")
                                .executes(context -> resetStats(context.getSource()))))
                .then(Commands.literal("intents")
                        .executes(context -> showCount(context.getSource()))
                        .then(Commands.literal("list")
                                .executes(context -> list(context.getSource(), 10))
                                .then(Commands.argument("limit", IntegerArgumentType.integer(1, 100))
                                        .executes(context -> list(
                                                context.getSource(),
                                                IntegerArgumentType.getInteger(context, "limit")))))
                        .then(Commands.literal("clear")
                                .executes(context -> clear(context.getSource())))));
    }

    private static int showStats(CommandSourceStack source) {
        List<String> lines = OptimizationMetrics.summaryLines();
        source.sendSuccess(() -> Component.literal("ACO optimization statistics:"), false);
        for (String line : lines) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return lines.size();
    }

    private static int resetStats(CommandSourceStack source) {
        OptimizationMetrics.reset();
        source.sendSuccess(() -> Component.literal("Reset ACO optimization statistics."), true);
        return 1;
    }

    private static int showCount(CommandSourceStack source) {
        int count = RecipeIntentRegistry.size();
        source.sendSuccess(() -> Component.literal("ACO recipe intents: " + count), false);
        return count;
    }

    private static int list(CommandSourceStack source, int limit) {
        List<RecipeIntent> intents = RecipeIntentRegistry.snapshot();
        if (intents.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No ACO recipe intents are currently cached."), false);
            return 0;
        }
        int count = Math.min(limit, intents.size());
        source.sendSuccess(() -> Component.literal("Showing " + count + " / " + intents.size() + " ACO recipe intents:"), false);
        for (int i = intents.size() - count; i < intents.size(); i++) {
            RecipeIntent intent = intents.get(i);
            source.sendSuccess(() -> Component.literal(
                    intent.patternDefinitionId()
                            + " x"
                            + intent.patternExecutions()
                            + " -> "
                            + intent.dimension()
                            + " "
                            + intent.targetPos().toShortString()
                            + " "
                            + intent.targetSide()
                            + " outputs="
                            + intent.outputs()), false);
        }
        return count;
    }

    private static int clear(CommandSourceStack source) {
        int count = RecipeIntentRegistry.size();
        RecipeIntentRegistry.clear("command");
        GTCEuRecipeIntentFastPath.clearIndexes("command");
        MekanismRecipeIntentFastPath.clearIndexes("command");
        source.sendSuccess(() -> Component.literal("Cleared " + count + " ACO recipe intents."), true);
        return count;
    }
}
