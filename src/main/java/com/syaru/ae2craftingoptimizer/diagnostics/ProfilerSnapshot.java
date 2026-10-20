package com.syaru.ae2craftingoptimizer.diagnostics;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** Bounded presentation values. None of these values may be used for quantity/accounting decisions. */
public record ProfilerSnapshot(boolean enabled, boolean detailed, boolean available, boolean truncated,
        List<ProfilerSnapshot.Sample> profiles) {
    public static final int MAX_HISTORY = 64;
    public static final int MAX_JSON_BYTES = 128 * 1024;
    public static final int MAX_TEXT = 512;
    public static final int MAX_REQUESTED_TEXT = 2048;
    public static final int MAX_MAP_ENTRIES = 128;
    public static final int MAX_KEY_TEXT = 160;
    private static final Gson JSON = new Gson();
    public static final String UNKNOWN = "\u4e0d\u660e";

    public ProfilerSnapshot {
        profiles = List.copyOf(profiles);
        if (profiles.size() > MAX_HISTORY) {
            throw new IllegalArgumentException("Too many profiler samples");
        }
        detailed &= enabled;
    }

    public String statusText() {
        return "enabled=" + enabled + " detailed=" + detailed + " available=" + available
                + " samples=" + profiles.size() + (truncated ? " truncated=true" : "");
    }

    public byte[] encodeJson() {
        var remaining = new ArrayList<>(profiles);
        boolean shortened = truncated;
        while (true) {
            byte[] json = JSON.toJson(new ProfilerSnapshot(enabled, detailed, available, shortened, remaining))
                    .getBytes(StandardCharsets.UTF_8);
            if (json.length <= MAX_JSON_BYTES) {
                return json;
            }
            // Issue #221: retain newest samples and signal omission, never send an oversized packet.
            remaining.remove(0);
            shortened = true;
        }
    }

    public static ProfilerSnapshot decodeJson(byte[] bytes) {
        if (bytes.length > MAX_JSON_BYTES) {
            throw new IllegalArgumentException("Profiler JSON exceeds packet cap");
        }
        JsonObject root = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject();
        JsonArray profiles = root.getAsJsonArray("profiles");
        if (profiles == null || profiles.size() > MAX_HISTORY) {
            throw new IllegalArgumentException("Invalid profiler history");
        }
        List<Sample> samples = new ArrayList<>();
        for (JsonElement element : profiles) {
            JsonObject value = element.getAsJsonObject();
            samples.add(new Sample(readLong(value.get("order")), readText(value, "output", MAX_TEXT),
                    readText(value, "requested", MAX_REQUESTED_TEXT), readText(value, "outcome", MAX_TEXT),
                        readText(value, "reason", MAX_TEXT), readText(value, "route", MAX_TEXT),
                    readMap(value.getAsJsonObject("nanos")), readMap(value.getAsJsonObject("counters")),
                    value.has("eligibility") ? readText(value, "eligibility", MAX_TEXT) : UNKNOWN,
                    value.has("fallback") ? readText(value, "fallback", MAX_TEXT) : UNKNOWN));
        }
        return new ProfilerSnapshot(readBoolean(root, "enabled"), readBoolean(root, "detailed"),
                readBoolean(root, "available"), readBoolean(root, "truncated"), samples);
    }

    private static boolean readBoolean(JsonObject value, String key) {
        var element = value.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isBoolean()) {
            throw new IllegalArgumentException("Invalid profiler flag");
        }
        return element.getAsBoolean();
    }

    private static long readLong(JsonElement value) {
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Invalid profiler measurement");
        }
        return value.getAsBigDecimal().longValueExact();
    }

    private static String readText(JsonObject value, String key, int maximum) {
        var element = value.get(key);
        if (element == null || !element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException("Invalid profiler text");
        }
        String text = element.getAsString();
        if (text.length() > maximum) {
            throw new IllegalArgumentException("Profiler text exceeds cap");
        }
        return text;
    }

    private static Map<String, Long> readMap(JsonObject object) {
        if (object == null || object.size() > MAX_MAP_ENTRIES) {
            throw new IllegalArgumentException("Invalid profiler map");
        }
        var result = new LinkedHashMap<String, Long>();
        for (var entry : object.entrySet()) {
            if (entry.getKey().length() > MAX_KEY_TEXT) {
                throw new IllegalArgumentException("Profiler key exceeds cap");
            }
            result.put(entry.getKey(), readLong(entry.getValue()));
        }
        return result;
    }

    public static String boundedText(String value, int maximum) {
        if (value == null || value.isEmpty()) {
            return UNKNOWN;
        }
        int end = Math.min(maximum, value.length());
        if (end < value.length() && Character.isHighSurrogate(value.charAt(end - 1))) {
            end--;
        }
        StringBuilder text = new StringBuilder(end);
        for (int i = 0; i < end; i++) {
            char c = value.charAt(i);
            text.append(Character.isISOControl(c) || c == '\u00a7' ? ' ' : c);
        }
        return text.toString();
    }

    private static Map<String, Long> boundedMap(Map<String, Long> source) {
        // Core metrics precede bounded per-type diagnostics even if a future VM exceeds the cap.
        var sorted = new TreeMap<String, Long>(Comparator
                .comparing((String key) -> key.startsWith("patternType:") || key.startsWith("remainder:"))
                .thenComparing(Comparator.naturalOrder()));
        if (source != null) {
            for (var entry : source.entrySet()) {
                Long value = entry.getValue();
                sorted.put(boundedText(entry.getKey(), MAX_KEY_TEXT), value == null || value < 0 ? -1 : value);
                if (sorted.size() > MAX_MAP_ENTRIES) {
                    sorted.pollLastEntry();
                }
            }
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(sorted));
    }

    public static String countText(Long value) {
        return value == null || value < 0 ? UNKNOWN : Long.toString(value);
    }

    public static String nanosText(Long value) {
        return value == null || value < 0 ? UNKNOWN : String.format(Locale.ROOT, "%.3f ms", value / 1_000_000.0);
    }

    public record Sample(long order, String output, String requested, String outcome, String reason,
            String route, Map<String, Long> nanos, Map<String, Long> counters, String eligibility, String fallback) {
        public Sample(long order, String output, String requested, String outcome, String reason,
                String route, Map<String, Long> nanos, Map<String, Long> counters) {
            this(order, output, requested, outcome, reason, route, nanos, counters, UNKNOWN, UNKNOWN);
        }
        public Sample {
            output = boundedText(output, MAX_TEXT);
            requested = boundedText(requested, MAX_REQUESTED_TEXT);
            outcome = boundedText(outcome, MAX_TEXT);
            reason = boundedText(reason, MAX_TEXT);
            route = boundedText(route, MAX_TEXT);
            nanos = boundedMap(nanos);
            counters = boundedMap(counters);
            eligibility = boundedText(eligibility, MAX_TEXT);
            fallback = boundedText(fallback, MAX_TEXT);
        }

        public String summary() {
            return "#" + countText(order) + " " + output + " x" + requested + " outcome=" + outcome
                    + " reason=" + reason + " route=" + route
                    + " eligibility=" + eligibility + " fallback=" + fallback;
        }

        /** Largest observed interval, not a disjoint breakdown or an inferred root cause. */
        public Map.Entry<String, Long> largestMeasuredInterval() {
            Map.Entry<String, Long> largest = null;
            for (String key : List.of("initializationQueue", "workerQueue", "stock", "prepare", "vm", "finalize")) {
                Long value = nanos.get(key);
                if (value != null && value >= 0 && (largest == null || value > largest.getValue())) {
                    largest = Map.entry(key, value);
                }
            }
            return largest;
        }

        public String cacheEfficiency(String prefix) {
            Long hits = counters.get(prefix + "Hits");
            Long misses = counters.get(prefix + "Misses");
            if (hits == null || misses == null || hits < 0 || misses < 0 || (hits == 0 && misses == 0)) {
                return UNKNOWN;
            }
            // Counts are observed longs, not quantities. Convert before addition to avoid long overflow.
            double percent = 100.0 * hits.doubleValue() / (hits.doubleValue() + misses.doubleValue());
            return String.format(Locale.ROOT, "%.1f%% (%d / (%d + %d))", percent, hits, hits, misses);
        }

        public String nanosText() {
            return mapText(nanos, true);
        }

        public String countersText() {
            return mapText(counters, false);
        }

        private static String mapText(Map<String, Long> values, boolean timing) {
            if (values.isEmpty()) {
                return UNKNOWN;
            }
            List<String> text = new ArrayList<>();
            values.forEach((key, value) -> text.add(key + "="
                    + (timing ? ProfilerSnapshot.nanosText(value) : countText(value))));
            return String.join(", ", text);
        }
    }
}
