package com.syaru.ae2craftingoptimizer.diagnostics;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.CalculationStrategy;
import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingSimulationRequester;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import com.ae2vm.addon.nativeengine.NativeVm;
import com.syaru.ae2craftingoptimizer.api.big.BigCraftingEngineApi;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/** #236: explicit live-grid planning only; no chunk loading, stock mutation or craft submission. */
public final class RuntimePlanCommands {
    record Host(IActionHost action, BlockPos position) { }
    record Selection(IActionHost action, IGrid grid, BlockPos position) { }
    private record Request(MinecraftServer server, Future<ICraftingPlan> future, long started, String label) { }
    private static Request active;

    private RuntimePlanCommands() { }

    static BigInteger amount(String value, BigInteger maximum) {
        if (value.length() > 17000) throw new IllegalArgumentException("Quantity is too long");
        var result = new BigInteger(value);
        if (result.signum() <= 0 || result.compareTo(maximum) > 0)
            throw new IllegalArgumentException("Quantity is outside the exact planning range");
        return result;
    }

    static Selection select(List<Host> hosts, AEKey output) {
        var checked = Collections.newSetFromMap(new IdentityHashMap<IGrid, Boolean>());
        Selection selected = null;
        for (var host : hosts) {
            var node = host.action().getActionableNode();
            if (node == null || !node.isActive()) continue;
            var grid = node.getGrid();
            if (!checked.add(grid) || grid.getCraftingService().getCraftingFor(output).isEmpty()) continue;
            if (selected != null) throw new IllegalArgumentException("Multiple loaded grids can craft this item; choose a closer origin");
            selected = new Selection(host.action(), grid, host.position());
        }
        if (selected == null) throw new IllegalArgumentException("No active loaded grid near this origin advertises this item");
        return selected;
    }

    public static synchronized int start(CommandSourceStack source, BlockPos origin, ResourceLocation item, String quantity) {
        if (!source.hasPermission(2)) return 0;
        try {
            if (!source.getServer().isSameThread()) throw new IllegalStateException("Runtime planning must be requested on the server thread");
            if (active != null && !active.future().isDone()) throw new IllegalStateException("A diagnostic plan is already running");
            var requested = amount(quantity, BigCraftingEngineApi.maximumSupportedAmount());
            var output = AEItemKey.of(BuiltInRegistries.ITEM.getOptional(item)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown item identifier")));
            var level = source.getLevel();
            var hosts = new ArrayList<Host>();
            // Inspect existing loaded block entities, never getChunk() or a force-load ticket.
            for (int x = (origin.getX() >> 4) - 2; x <= (origin.getX() >> 4) + 2; x++)
                for (int z = (origin.getZ() >> 4) - 2; z <= (origin.getZ() >> 4) + 2; z++) {
                    var chunk = level.getChunkSource().getChunkNow(x, z);
                    if (chunk == null) continue;
                    for (var entity : chunk.getBlockEntities().values())
                        if (entity instanceof IActionHost host) hosts.add(new Host(host, entity.getBlockPos()));
                }
            var selected = select(hosts, output);
            var action = IActionSource.ofMachine(selected.action());
            var requester = new ICraftingSimulationRequester() {
                public IActionSource getActionSource() { return action; }
                public IGridNode getGridNode() {
                    var current = selected.action().getActionableNode();
                    return current != null && current.getGrid() == selected.grid() ? current : null;
                }
            };
            long started = System.nanoTime();
            var future = NativeVm.calculate(selected.grid(), level, requester, output, requested,
                    CalculationStrategy.REPORT_MISSING_ITEMS);
            var label = item + " x" + number(requested) + " at " + selected.position().toShortString();
            active = new Request(source.getServer(), future, started, label);
            line(source, "ACO runtime-grid diagnostic: " + label + " (machine requester; no craft submission)");
            return 1;
        } catch (RuntimeException | LinkageError failure) {
            source.sendFailure(Component.literal("ACO runtime diagnostic rejected: " + safe(failure)));
            return 0;
        }
    }

    public static synchronized int status(CommandSourceStack source) {
        if (!source.hasPermission(2)) return 0;
        if (active == null || active.server() != source.getServer()) { line(source, "ACO runtime diagnostic: no request"); return 0; }
        var future = active.future();
        line(source, "ACO runtime diagnostic: " + active.label() + " requestAgeMs="
                + TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - active.started())
                + " (request age is NOT planning elapsed; use /aco profiler recent)");
        if (future.isCancelled()) { line(source, "status=CANCELLED; no completed plan"); return 1; }
        if (!future.isDone()) { line(source, "status=RUNNING; no completed plan"); return 1; }
        try {
            var plan = future.get(0, TimeUnit.NANOSECONDS);
            var view = BigCraftingEngineApi.inspectBigIntegerPlan(plan);
            if (view.isPresent()) {
                var exact = view.orElseThrow();
                line(source, "status=COMPLETE simulation=" + exact.simulation() + " exactRequested=" + number(exact.exactRequestedAmount())
                        + " exactBytes=" + number(exact.exactBytes()) + " patterns=" + exact.patternTimes().size()
                        + " usedKeys=" + exact.usedItems().size() + " missingKeys=" + exact.missingItems().size());
            } else line(source, "status=COMPLETE simulation=" + plan.simulation() + " exactMetadata=unavailable");
            return 1;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            source.sendFailure(Component.literal("ACO runtime diagnostic status interrupted"));
        } catch (java.util.concurrent.TimeoutException pending) {
            line(source, "status=RUNNING; no completed plan");
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.CancellationException failure) {
            source.sendFailure(Component.literal("ACO runtime diagnostic failed: " + safe(failure)));
        }
        return 0;
    }

    public static synchronized int cancel(CommandSourceStack source) {
        if (!source.hasPermission(2)) return 0;
        boolean cancelled = active != null && active.server() == source.getServer()
                && !active.future().isDone() && active.future().cancel(true);
        line(source, cancelled ? "ACO runtime diagnostic: cancellation requested; not completion"
                : "ACO runtime diagnostic: no running request");
        return cancelled ? 1 : 0;
    }

    public static synchronized void clear(MinecraftServer server) {
        if (active == null || active.server() != server) return;
        if (!active.future().isDone()) active.future().cancel(true);
        active = null;
    }

    private static String number(BigInteger value) { return value.bitLength() <= 256 ? value.toString() : "BigInteger(" + value.bitLength() + "bits)"; }
    private static String safe(Throwable failure) {
        var message = failure.getClass().getSimpleName() + ":" + String.valueOf(failure.getMessage()).replace('\n', ' ').replace('\r', ' ');
        return message.substring(0, Math.min(240, message.length()));
    }
    private static void line(CommandSourceStack source, String text) { source.sendSuccess(() -> Component.literal(text), false); }
}
