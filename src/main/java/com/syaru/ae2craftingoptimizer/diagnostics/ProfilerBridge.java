package com.syaru.ae2craftingoptimizer.diagnostics;

import com.ae2vm.addon.nativeengine.NativeVmProfiler;
import com.syaru.ae2craftingoptimizer.config.ACOConfig;
import java.util.ArrayList;
import java.util.List;

/** Issue #221: observes only the public VM profiler DTO; never acquires a plan or grid. */
public final class ProfilerBridge {
    private ProfilerBridge() {
    }

    public static void configure() {
        try {
            NativeVmProfiler.configure(ACOConfig.profilerEnabled(), ACOConfig.profilerDetailedEnabled());
        } catch (LinkageError | RuntimeException unavailable) {
            // Diagnostic failures must not prevent configuration or change calculation results.
        }
    }

    public static ProfilerSnapshot snapshot() {
        boolean enabled = ACOConfig.profilerEnabled();
        boolean detailed = ACOConfig.profilerDetailedEnabled();
        try {
            List<NativeVmProfiler.Profile> recent = NativeVmProfiler.recent();
            List<ProfilerSnapshot.Sample> samples = new ArrayList<>();
            boolean truncated = recent.size() > ProfilerSnapshot.MAX_HISTORY;
            for (int i = Math.max(0, recent.size() - ProfilerSnapshot.MAX_HISTORY); i < recent.size(); i++) {
                var profile = recent.get(i);
                var sample = copyOf(profile);
                truncated |= !sample.output().equals(profile.output())
                        || !sample.requested().equals(profile.requested())
                        || !sample.outcome().equals(profile.outcome())
                        || !sample.reason().equals(profile.reason())
                        || !sample.route().equals(profile.route())
                        || !sample.nanos().equals(profile.nanos())
                        || !sample.counters().equals(profile.counters());
                samples.add(sample);
            }
            return new ProfilerSnapshot(enabled, detailed, true, truncated, samples);
        } catch (LinkageError | RuntimeException unavailable) {
            return new ProfilerSnapshot(enabled, detailed, false, false, List.of());
        }
    }

    static ProfilerSnapshot.Sample copyOf(NativeVmProfiler.Profile profile) {
        return new ProfilerSnapshot.Sample(profile.order(), profile.output(), profile.requested(),
                        profile.outcome(), profile.reason(), profile.route(), profile.nanos(), profile.counters(),
                        profile.optimizationEligibility(), profile.fallbackReason());
    }
}
