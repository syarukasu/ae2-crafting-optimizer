import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordingFile;

/** Read-only summary of the opt-in provider fixture profile; requires JDK 17. */
class ProviderJfrSummary {
    public static void main(String[] args) throws Exception {
        var leaves = new HashMap<String, Long>();
        var inclusive = new HashMap<String, Long>();
        var allocations = new HashMap<String, Long>();
        int samples = 0;
        try (var file = new RecordingFile(Path.of(args[0]))) {
            while (file.hasMoreEvents()) {
                RecordedEvent event = file.readEvent();
                var name = event.getEventType().getName();
                if (!name.equals("jdk.ExecutionSample") && !name.equals("jdk.ObjectAllocationSample")) continue;
                var thread = event.getThread(name.equals("jdk.ExecutionSample") ? "sampledThread" : "eventThread");
                if (thread == null || !thread.getJavaName().equals("Server thread") || event.getStackTrace() == null) continue;
                var frames = event.getStackTrace().getFrames();
                if (frames.isEmpty()) continue;
                if (name.equals("jdk.ExecutionSample")) {
                    samples++;
                    leaves.merge(method(frames.get(0)), 1L, Long::sum);
                    var seen = new HashSet<String>();
                    for (var frame : frames) {
                        String method = method(frame);
                        if (seen.add(method)) inclusive.merge(method, 1L, Long::sum);
                    }
                } else {
                    String owner = frames.stream().map(ProviderJfrSummary::method)
                            .filter(m -> m.startsWith("appeng.") || m.startsWith("com.syaru."))
                            .findFirst().orElse(method(frames.get(0)));
                    allocations.merge(event.getClass("objectClass").getName() + " <- " + owner,
                            event.getLong("weight"), Long::sum);
                }
            }
        }
        System.out.println("Server thread execution samples: " + samples);
        print("Leaf samples", leaves);
        print("Inclusive samples (not additive)", inclusive);
        print("Sampled allocation weights in bytes (estimates)", allocations);
    }

    private static String method(RecordedFrame frame) {
        return frame.getMethod().getType().getName() + "." + frame.getMethod().getName();
    }

    private static void print(String title, Map<String, Long> counts) {
        System.out.println("\n" + title);
        counts.entrySet().stream().sorted(Map.Entry.<String, Long>comparingByValue().reversed()).limit(35)
                .forEach(entry -> System.out.printf("%12d %s%n", entry.getValue(), entry.getKey()));
    }
}
