package com.syaru.ae2craftingoptimizer.diagnostics;

import com.ae2vm.addon.nativeengine.NativeVmBenchmark;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.IntSupplier;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

/** Issue #221: manual detached replay commands only. No tick callbacks or ordinary-order baselines. */
public final class ProfilerBenchmarkCommands {
    private static final int MAX_REPORTS = 8;
    private static final int MAX_DIFFERENCES = 8;
    private static final int MAX_LINE = 768;
    // Accessed only by commands on the server thread; futures contain detached replay data only.
    private static final LinkedHashMap<Long, Future<NativeVmBenchmark.Result>> REQUESTS = new LinkedHashMap<>();

    private ProfilerBenchmarkCommands() {
    }

    public static int capture(CommandSourceStack source) {
        return guarded(source, () -> {
            boolean armed = NativeVmBenchmark.armNextCapture();
            line(source, armed ? "ACO: \u6b21\u306e\u6ce8\u6587\u306e\u53d6\u8fbc\u307f\u3092\u4e88\u7d04\u3057\u307e\u3057\u305f\uff08\u6bd4\u8f03\u306f\u81ea\u52d5\u5b9f\u884c\u3057\u307e\u305b\u3093\uff09"
                    : "ACO: \u53d6\u8fbc\u307f\u306f\u4e88\u7d04\u6e08\u307f\u3067\u3059");
            return 1;
        });
    }

    public static int captures(CommandSourceStack source) {
        return guarded(source, () -> {
            line(source, "ACO capture armed=" + NativeVmBenchmark.captureArmed());
            var captures = NativeVmBenchmark.captures();
            for (int i = Math.max(0, captures.size() - MAX_REPORTS); i < captures.size(); i++) {
                var capture = captures.get(i);
                line(source, "#" + capture.order() + " " + text(capture.output()) + " requested=" + quantity(capture.requested())
                        + " supported=" + capture.supported() + " reason=" + text(capture.reason()));
                line(source, "fingerprint=" + text(capture.fingerprint()) + " generation=" + text(capture.generation()));
            }
            return Math.min(MAX_REPORTS, captures.size());
        });
    }

    public static int cancel(CommandSourceStack source) {
        return guarded(source, () -> {
            boolean cancelled = NativeVmBenchmark.cancel();
            line(source, cancelled ? "ACO replay: cancellation requested" : "ACO replay: no active comparison");
            return cancelled ? 1 : 0;
        });
    }

    public static int start(CommandSourceStack source, long order) {
        return guarded(source, () -> {
            Future<NativeVmBenchmark.Result> previous = REQUESTS.get(order);
            if (previous != null && !previous.isDone()) {
                line(source, "#" + order + " RUNNING");
                return 0;
            }
            // Never wait here. VM owns its one bounded detached replay worker.
            var future = NativeVmBenchmark.benchmark(order);
            REQUESTS.remove(order);
            REQUESTS.put(order, future);
            while (REQUESTS.size() > MAX_REPORTS) {
                REQUESTS.remove(REQUESTS.keySet().iterator().next());
            }
            line(source, "#" + order + " \u624b\u52d5\u6bd4\u8f03\u3092\u53d7\u4ed8\uff08\u5b9f\u30af\u30e9\u30d5\u30c8\u306a\u3057\uff09");
            return reportUnsafe(source, order);
        });
    }

    public static int report(CommandSourceStack source, long order) {
        return guarded(source, () -> reportUnsafe(source, order));
    }

    public static int recent(CommandSourceStack source) {
        return guarded(source, () -> {
            var orders = new LinkedHashMap<Long, Boolean>();
            var results = NativeVmBenchmark.recent();
            for (int i = Math.max(0, results.size() - MAX_REPORTS); i < results.size(); i++) {
                orders.put(results.get(i).order(), true);
            }
            REQUESTS.keySet().forEach(order -> { orders.remove(order); orders.put(order, true); });
            var recent = new ArrayList<>(orders.keySet());
            for (int i = Math.max(0, recent.size() - MAX_REPORTS); i < recent.size(); i++) {
                reportUnsafe(source, recent.get(i));
            }
            return Math.min(MAX_REPORTS, recent.size());
        });
    }

    private static int reportUnsafe(CommandSourceStack source, long order) {
        Future<NativeVmBenchmark.Result> future = REQUESTS.get(order);
        NativeVmBenchmark.Result result;
        if (future != null) {
            if (!future.isDone()) {
                line(source, "#" + order + " RUNNING");
                return 1;
            }
            try {
                // Zero timeout even after isDone(): no blocking server-thread result retrieval.
                result = future.get(0, TimeUnit.NANOSECONDS);
            } catch (TimeoutException pending) {
                line(source, "#" + order + " RUNNING");
                return 1;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                line(source, "#" + order + " INTERRUPTED");
                return 0;
            } catch (java.util.concurrent.CancellationException cancelled) {
                line(source, "#" + order + " CANCELLED");
                return 0;
            } catch (java.util.concurrent.ExecutionException failed) {
                line(source, "#" + order + " FAILED: " + text(failed.getMessage()));
                return 0;
            }
        } else {
            result = NativeVmBenchmark.report(order).orElse(null);
        }
        if (result == null) {
            line(source, "#" + order + " UNAVAILABLE: \u7d50\u679c\u306a\u3057\uff08\u672a\u5b9f\u884c\u30fb\u5931\u52b9\u3092\u542b\u3080\uff09");
            return 0;
        }
        resultLines(result).forEach(value -> line(source, value));
        return 1;
    }

    static List<String> resultLines(NativeVmBenchmark.Result result) {
        List<String> lines = new ArrayList<>();
        String parity = switch (result.status()) {
            case MATCH -> "\u4e00\u81f4";
            case DIFFERENT -> "\u4e0d\u4e00\u81f4";
            default -> ProfilerSnapshot.UNKNOWN;
        };
        lines.add("#" + result.order() + " status=" + result.status() + " parity=" + parity
                + " VM=" + ProfilerSnapshot.nanosText(result.vmNanos())
                + " AE2=" + ProfilerSnapshot.nanosText(result.ae2Nanos()));
        lines.add("reason=" + text(result.reason()) + " (detached subset; calculation only; no speedup claim)");
        int count = Math.min(MAX_DIFFERENCES, result.differences().size());
        for (int i = 0; i < count; i++) {
            var difference = result.differences().get(i);
            lines.add("difference " + text(difference.field()) + " " + text(difference.key())
                    + " VM=" + text(difference.vm()) + " AE2=" + text(difference.ae2()));
        }
        if (count < result.differences().size()) {
            lines.add("differences omitted=" + (result.differences().size() - count));
        }
        return lines.stream().map(value -> ProfilerSnapshot.boundedText(value, MAX_LINE)).toList();
    }

    private static int guarded(CommandSourceStack source, IntSupplier command) {
        if (!source.hasPermission(2)) {
            return 0;
        }
        try {
            return command.getAsInt();
        } catch (RuntimeException | LinkageError unavailable) {
            source.sendFailure(Component.literal("ACO benchmark UNAVAILABLE: " + text(unavailable.getMessage())));
            return 0;
        }
    }

    private static String quantity(BigInteger value) {
        return value == null ? ProfilerSnapshot.UNKNOWN : value.bitLength() > 8192
                ? "BigInteger(" + value.bitLength() + " bits)" : ProfilerSnapshot.boundedText(value.toString(), 256);
    }

    private static String text(String value) {
        return ProfilerSnapshot.boundedText(value, 256);
    }

    private static void line(CommandSourceStack source, String value) {
        source.sendSuccess(() -> Component.literal(ProfilerSnapshot.boundedText(value, MAX_LINE)), false);
    }
}
