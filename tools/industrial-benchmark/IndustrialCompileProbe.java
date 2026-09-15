import com.google.gson.*;
import com.syaru.ae2craftingoptimizer.engine.*;
import java.math.BigInteger;
import java.nio.file.*;
import java.util.*;

/** Explicit diagnostic, not a successful end-to-end crafting benchmark. */
class IndustrialCompileProbe {
    record Key(String kind, String id, String nbt) {}

    static Key key(JsonObject value) {
        return new Key(value.get("kind").getAsString(), value.get("id").getAsString(),
                value.get("nbt").isJsonNull() ? null : value.get("nbt").getAsString());
    }

    static long count(JsonObject value) {
        String text = value.get("amount").getAsString();
        if (!text.matches("[1-9][0-9]*")) throw new IllegalArgumentException("Invalid count: " + text);
        return new BigInteger(text).longValueExact();
    }

    static CompiledPattern<Key> pattern(JsonObject recipe) {
        var inputs = new ArrayList<CompiledPattern.InputSlot<Key>>();
        for (var raw : recipe.getAsJsonArray("inputs")) {
            var alternatives = new ArrayList<CompiledPattern.Stack<Key>>();
            for (var value : raw.getAsJsonObject().getAsJsonArray("alternatives")) {
                var stack = value.getAsJsonObject();
                alternatives.add(new CompiledPattern.Stack<>(key(stack), count(stack)));
            }
            inputs.add(new CompiledPattern.InputSlot<>(alternatives));
        }
        var outputs = new LinkedHashMap<Key, Long>();
        for (var value : recipe.getAsJsonArray("outputs")) {
            var stack = value.getAsJsonObject();
            outputs.merge(key(stack), count(stack), Math::addExact);
        }
        return new CompiledPattern<>(recipe.get("id").getAsString(), inputs, outputs, true);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("normalized-recipes.json output.json");
        long started = System.nanoTime();
        var path = Path.of(args[0]);
        JsonArray recipes;
        try (var reader = Files.newBufferedReader(path)) { recipes = JsonParser.parseReader(reader).getAsJsonArray(); }
        var target = new Key("item", "evolvedmekanism:creative_control_circuit", null);
        var accepted = new ArrayList<CompiledPattern<Key>>();
        var omitted = new ArrayList<Map<String, Object>>();
        var roots = new ArrayList<CompiledPattern<Key>>();
        for (var element : recipes) {
            var row = element.getAsJsonObject();
            if (!row.getAsJsonArray("unsupported").isEmpty() || row.getAsJsonArray("outputs").isEmpty()) {
                omitted.add(Map.of("id", row.get("id").getAsString(), "reasons", row.get("unsupported")));
                continue;
            }
            var pattern = pattern(row);
            accepted.add(pattern);
            if (pattern.outputs().containsKey(target)) roots.add(pattern);
        }
        if (roots.isEmpty()) throw new IllegalStateException("No captured creative circuit producer");
        long decoded = System.nanoTime();
        var measurements = new ArrayList<Map<String, Object>>();
        for (var root : roots) {
            var candidates = accepted.stream().filter(p -> !p.outputs().containsKey(target) || p == root).toList();
            long start = System.nanoTime();
            var graph = CompiledCraftingGraph.compile(1, candidates);
            long indexed = System.nanoTime();
            var lastVisited = new Key[1];
            var outcome = CompiledRootProgram.compile(graph, target, key -> {
                lastVisited[0] = key;
                return false;
            });
            long finished = System.nanoTime();
            var measurement = new LinkedHashMap<String, Object>(Map.of("rootRecipe", root.id(), "candidatePatterns", candidates.size(),
                    "graphMillis", (indexed - start) / 1e6, "rootCompileMillis", (finished - indexed) / 1e6,
                    "failure", outcome.failure().name(), "accepted", outcome.program().isPresent()));
            if (outcome.failure() == RootProgramFailure.MULTIPLE_PRODUCERS) {
                measurement.put("rejectedKey", lastVisited[0]);
                measurement.put("rejectedCandidates", graph.patternsFor(lastVisited[0]).stream()
                        .map(CompiledPattern::id).toList());
            }
            measurements.add(measurement);
        }
        // A separate one-machine arithmetic check verifies captured quantities. Its
        // inputs intentionally remain terminal and it MUST NOT be called the full tree.
        var oneStage = new ArrayList<Map<String, Object>>();
        for (var root : roots) {
            for (var amount : List.of(BigInteger.ONE, BigInteger.valueOf(100),
                    new BigInteger("9220000000000000000"), BigInteger.valueOf(Long.MAX_VALUE),
                    BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE), BigInteger.TEN.pow(64))) {
                if (root.inputs().stream().anyMatch(slot -> slot.alternatives().size() != 1)) {
                    throw new IllegalStateException("One-stage fixture requires explicit concrete inputs");
                }
                long start = System.nanoTime();
                var program = CompiledRootProgram.tryCompile(CompiledCraftingGraph.compile(1, List.of(root)),
                        target, ignored -> false).orElseThrow();
                var plan = program.planBig(amount, program.captureBigInventory(k -> BigInteger.ZERO,
                        BigCountMath.HARD_MAXIMUM_BITS), PlanningGuard.none(), BigCountMath.HARD_MAXIMUM_BITS);
                var yield = BigInteger.valueOf(root.outputAmount(target));
                var divisions = amount.divideAndRemainder(yield);
                var times = divisions[0].add(divisions[1].signum() == 0 ? BigInteger.ZERO : BigInteger.ONE);
                var expected = new LinkedHashMap<Key, BigInteger>();
                for (var input : root.inputs()) {
                    var ingredient = input.alternatives().get(0);
                    expected.merge(ingredient.key(), times.multiply(BigInteger.valueOf(ingredient.amount())), BigInteger::add);
                }
                if (!plan.missing().equals(expected) || !plan.usedInventory().isEmpty()
                        || !plan.patternExecutions().equals(Map.of(root.id(), times))) {
                    throw new AssertionError("Captured recipe accounting mismatch: " + root.id());
                }
                oneStage.add(Map.of("rootRecipe", root.id(), "amount", amount.toString(),
                        "executions", times.toString(), "elapsedMillis", (System.nanoTime() - start) / 1e6,
                        "scope", "ONE_MACHINE_ONLY_NOT_FULL_DEPENDENCIES", "exactAccounting", true));
            }
        }
        var report = new LinkedHashMap<String, Object>();
        report.put("status", "NOT_END_TO_END_ACCEPTED");
        report.put("inputSha256", HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(path))));
        report.put("decodeMillis", (decoded - started) / 1e6);
        report.put("capturedRecipes", recipes.size());
        report.put("eligibleDiagnosticPatterns", accepted.size());
        report.put("omittedRecipes", omitted);
        report.put("fullCandidateCompile", measurements);
        report.put("oneMachineArithmetic", oneStage);
        report.put("limitations", List.of("No ME provider order, inventory or submittable plan was captured.",
                "Machine conditions/catalysts are recorded in normalized data; machines are not executed.",
                "Unsupported recipes are omitted only for this diagnostic, not silently treated as raw inputs.",
                "These times must not be reported as the requested full-plan latency."));
        try (var writer = Files.newBufferedWriter(Path.of(args[1]))) {
            new GsonBuilder().setPrettyPrinting().create().toJson(report, writer);
        }
        System.out.println(new Gson().toJson(measurements));
        System.out.println("Exact one-machine quantity checks: " + oneStage.size() + "; full acceptance: NOT PASSED");
    }
}
