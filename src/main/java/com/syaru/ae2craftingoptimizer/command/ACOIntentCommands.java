package com.syaru.ae2craftingoptimizer.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import appeng.api.stacks.AEItemKey;
import com.syaru.ae2craftingoptimizer.integration.OptionalAqeBigCraftingExecution;
import com.syaru.ae2craftingoptimizer.gtceu.GTCEuRecipeIntentFastPath;
import com.syaru.ae2craftingoptimizer.intent.RecipeIntent;
import com.syaru.ae2craftingoptimizer.intent.RecipeIntentRegistry;
import com.syaru.ae2craftingoptimizer.mekanism.MekanismRecipeIntentFastPath;
import com.syaru.ae2craftingoptimizer.optimization.OptimizationMetrics;
import java.util.List;
import java.math.BigInteger;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.UuidArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.network.chat.Component;

public final class ACOIntentCommands {
    private ACOIntentCommands() {
    }

    public static void register(
            CommandDispatcher<CommandSourceStack> dispatcher,
            CommandBuildContext buildContext) {
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
                                .executes(context -> clear(context.getSource()))))
                .then(Commands.literal("bigcraft")
                        .then(Commands.literal("submit")
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .then(Commands.argument("item", ItemArgument.item(buildContext))
                                                .then(Commands.argument("amount", StringArgumentType.greedyString())
                                                        .executes(context -> submitBigCraft(
                                                                context.getSource(),
                                                                BlockPosArgument.getLoadedBlockPos(context, "pos"),
                                                                AEItemKey.of(ItemArgument.getItem(context, "item")
                                                                        .createItemStack(1, false)),
                                                                parseBigCount(StringArgumentType.getString(
                                                                        context, "amount"))))))))
                        .then(Commands.literal("status")
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(context -> OptionalAqeBigCraftingExecution.status(
                                                context.getSource(),
                                                BlockPosArgument.getLoadedBlockPos(context, "pos")))))
                        .then(Commands.literal("cancel")
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .then(Commands.argument("job", UuidArgument.uuid())
                                                .executes(context -> OptionalAqeBigCraftingExecution.cancel(
                                                        context.getSource(),
                                                        BlockPosArgument.getLoadedBlockPos(context, "pos"),
                                                        UuidArgument.getUuid(context, "job"))))))));
    }

    private static int submitBigCraft(
            CommandSourceStack source,
            net.minecraft.core.BlockPos position,
            AEItemKey output,
            BigInteger amount) {
        if (output == null) {
            source.sendFailure(Component.literal("The selected item cannot be represented as an AE2 key."));
            return 0;
        }
        return OptionalAqeBigCraftingExecution.submit(source, position, output, amount);
    }

    /** Accepts ordinary decimal counts plus readable 10^N-1 and 2^N-1 experiment notation. */
    private static BigInteger parseBigCount(String raw) {
        String normalized = raw.replace(" ", "").replace("_", "");
        try {
            if (normalized.matches("10\\^[0-9]+-1")) {
                int exponent = Integer.parseInt(normalized.substring(3, normalized.length() - 2));
                return BigInteger.TEN.pow(exponent).subtract(BigInteger.ONE);
            }
            if (normalized.matches("2\\^[0-9]+-1")) {
                int exponent = Integer.parseInt(normalized.substring(2, normalized.length() - 2));
                return BigInteger.TWO.pow(exponent).subtract(BigInteger.ONE);
            }
            BigInteger value = new BigInteger(normalized);
            if (value.signum() <= 0) {
                throw new IllegalArgumentException("amount must be positive");
            }
            return value;
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException(
                    "Invalid BigInteger amount. Use a positive decimal, 10^N-1, or 2^N-1.",
                    failure);
        }
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
