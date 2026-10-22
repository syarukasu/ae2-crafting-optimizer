package com.syaru.ae2craftingoptimizer.engine;

import appeng.api.crafting.IPatternDetails;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.world.level.Level;

/** Exposes the real VM execution binding IDs to the boundary fixture, not the planner result. */
public final class ProcessingVmTestBindings {
    private ProcessingVmTestBindings() {}

    public static Map<String, IPatternDetails> capture(Collection<IPatternDetails> patterns, Level level) {
        Map<String, IPatternDetails> result = new LinkedHashMap<>();
        for (var pattern : patterns) {
            var captured = Ae2CompiledPatternFactory.capture(pattern, level);
            if (captured == null || !captured.exactInputDomain()) throw new AssertionError("not exact");
            result.put(captured.fingerprint(), pattern);
        }
        return result;
    }
}
