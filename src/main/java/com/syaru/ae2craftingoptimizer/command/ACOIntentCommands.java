package com.syaru.ae2craftingoptimizer.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import com.syaru.ae2craftingoptimizer.diagnostics.RuntimePlanCommands;
import com.syaru.ae2craftingoptimizer.diagnostics.ProfilerBenchmarkCommands;
import com.syaru.ae2craftingoptimizer.diagnostics.ProfilerBridge;
import com.syaru.ae2craftingoptimizer.diagnostics.network.ProfilerNetwork;
import com.syaru.ae2craftingoptimizer.gtceu.GTCEuRecipeIntentFastPath;
import com.syaru.ae2craftingoptimizer.intent.RecipeIntent;
import com.syaru.ae2craftingoptimizer.intent.RecipeIntentRegistry;
import com.syaru.ae2craftingoptimizer.optimization.OptimizationMetrics;
import java.util.List;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public final class ACOIntentCommands {
    private ACOIntentCommands() {
    }

    public static void register(
            CommandDispatcher<CommandSourceStack> dispatcher,
            CommandBuildContext buildContext) {
        dispatcher.register(Commands.literal("aco")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("profiler")
                        .executes(context -> ProfilerNetwork.open(context.getSource()))
                        .then(Commands.literal("runtime")
                                .then(Commands.literal("status").executes(context -> RuntimePlanCommands.status(context.getSource())))
                                .then(Commands.literal("cancel").executes(context -> RuntimePlanCommands.cancel(context.getSource())))
                                .then(Commands.literal("near")
                                        .then(Commands.argument("origin", BlockPosArgument.blockPos())
                                                .then(Commands.argument("item", ResourceLocationArgument.id())
                                                        .then(Commands.argument("amount", StringArgumentType.word())
                                                                .executes(context -> RuntimePlanCommands.start(context.getSource(),
                                                                        BlockPosArgument.getBlockPos(context, "origin"),
                                                                        ResourceLocationArgument.getId(context, "item"), StringArgumentType.getString(context, "amount"))))))))
                        .then(Commands.literal("capture")
                                .executes(context -> ProfilerBenchmarkCommands.capture(context.getSource()))
                                .then(Commands.literal("recent")
                                        .executes(context -> ProfilerBenchmarkCommands.captures(context.getSource()))))
                        .then(Commands.literal("benchmark")
                                .then(Commands.literal("cancel")
                                        .executes(context -> ProfilerBenchmarkCommands.cancel(context.getSource())))
                                .then(Commands.argument("order", LongArgumentType.longArg(1))
                                        .executes(context -> ProfilerBenchmarkCommands.start(context.getSource(),
                                                LongArgumentType.getLong(context, "order"))))
                                .then(Commands.literal("recent")
                                        .executes(context -> ProfilerBenchmarkCommands.recent(context.getSource())))
                                .then(Commands.literal("report")
                                        .then(Commands.argument("order", LongArgumentType.longArg(1))
                                                .executes(context -> ProfilerBenchmarkCommands.report(context.getSource(),
                                                        LongArgumentType.getLong(context, "order"))))))
                        .then(Commands.literal("recent")
                                .executes(context -> recentProfiles(context.getSource(), 10))
                                .then(Commands.argument("limit", IntegerArgumentType.integer(1, 64))
                                        .executes(context -> recentProfiles(context.getSource(),
                                                IntegerArgumentType.getInteger(context, "limit"))))))
                .then(Commands.literal("vm")
                        .then(Commands.literal("recent")
                                .executes(context -> recentVm(context.getSource(), 10))
                                .then(Commands.argument("limit", IntegerArgumentType.integer(1, 64))
                                        .executes(context -> recentVm(context.getSource(), IntegerArgumentType.getInteger(context, "limit"))))))
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

    private static int recentProfiles(CommandSourceStack source, int limit) {
        // Issue #221: console diagnostics never require a client or submit calculations.
        var snapshot = ProfilerBridge.snapshot();
        source.sendSuccess(() -> Component.literal("ACO profiler: " + snapshot.statusText()
                + " (calculation only; CPU/wait/phase timings overlap, do not sum)"), false);
        int first = Math.max(0, snapshot.profiles().size() - limit);
        for (int i = first; i < snapshot.profiles().size(); i++) {
            var sample = snapshot.profiles().get(i);
            source.sendSuccess(() -> Component.literal(sample.summary()), false);
            source.sendSuccess(() -> Component.literal("nanos=" + sample.nanosText()
                    + " counters=" + sample.countersText()), false);
        }
        return snapshot.profiles().size() - first;
    }

    private static int recentVm(CommandSourceStack source, int limit) {
        var samples = com.ae2vm.addon.nativeengine.NativeVm.recentCalculations();
        source.sendSuccess(() -> Component.literal("VM recent calculations (not physical craft completion):"), false);
        int first = Math.max(0, samples.size() - limit);
        for (int i = first; i < samples.size(); i++) {
            var sample = samples.get(i);
            var amount = sample.requested().bitLength() <= 1024 ? sample.requested().toString()
                    : "BigInteger(" + sample.requested().bitLength() + " bits)";
            source.sendSuccess(() -> Component.literal("order=" + sample.order() + " " + sample.output()
                    + " x" + amount + " status=" + sample.status() + " elapsedMs=" + sample.elapsedMillis()
                    + " " + sample.timing()), false);
        }
        return samples.size() - first;
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
        source.sendSuccess(() -> Component.literal("Cleared " + count + " ACO recipe intents."), true);
        return count;
    }
}
