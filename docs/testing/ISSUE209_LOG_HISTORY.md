# Private server history audit (#209)

The read-only tool is `tools/diagnostics/summarize-server-logs.ps1`. It streams
plain and gzip logs, records filename/line/known version/event/reason/duration,
and leaves originals unchanged. Its report stays outside the repository: raw
logs can contain player and environment data and must not be published here.
The detailed local report includes 263 files with zero read errors.

## Confirmed observations

| Evidence | Observation |
| --- | --- |
| 2026-08-03-8.log.gz:2189 | ultimate_control_circuit legacy standard route took 27451 ms |
| 2026-08-08-3.log.gz:3455 | experimental_quantum_core legacy standard route took 9826 ms |
| debug-4.log.gz:117635 | supreme_control_circuit x100 began at September 15 11:32:40 |
| debug-4.log.gz:117639 | ACO declined it: AMBIGUOUS_PRODUCER / MULTIPLE_PRODUCERS |
| debug-4.log.gz:117912 | Same root x1 began at 11:35:14 |
| debug-4.log.gz:117915 | Same multiple-producer decline for x1 |
| debug-4.log.gz:118016 | Thunderbolt reports x1 finished in 148926.029 ms, selected vanilla, CraftingPlan result |
| debug-5.log.gz:64039 | An exact_completed event exists on September 15; this is not proof all wide jobs complete |
| debug.log:81 | September 18 startup identifies 2.0.0-rc.3, not the local development tree |
| debug.log:116966-116972 | ACO begins/declines helium_plasma x50000; Thunderbolt reports completion in 135.107 ms |

Across debug archives and current debug.log, planning_declined line counts are:
INCOMPLETE_GRAPH_SNAPSHOT 29004; AMBIGUOUS_PRODUCER 727; UNSUPPORTED_TOPOLOGY 150;
GENERATION_CHANGED 35; CYCLE 3; UNSUPPORTED_PATTERN 3. These are observed log
lines, potentially overlapping/repeated attempts, **not unique user orders**.
Do not add stdout/latest duplicates to claim a larger failure count.

## Interpretation limits

Missing ACO planning_complete records do not prove all calculations stalled:
another mod records finished results. The return-observer boundary must be made
reliable. A missing-pattern warning is not proof the user omitted the pattern.
Do not invent the unavailable pattern identity or equate recipe registration
with captured provider eligibility. ACO decline counts do not measure wall time
or prove inventory correctness. No same-snapshot before/after creative-circuit
speedup or server-thread CPU improvement can be inferred from this audit.

## New normal-log output

`diagnostics.logPlanningStatistics=true` emits at most two INFO lines per 60
seconds while activity occurred or an observed calculation is still active.
They appear in the normal console and logs/latest.log. The first line reports
window starts/completions/aborts/active, pre-wrapper route, exact sidecars,
shortage plans and measured average/maximum completion time. The second shows
cumulative existing cache/capture/planner counters and up to three decline
reasons (remaining reason counts combined). It does not claim a speedup or VM
activation. No planning/job inventories are scanned to produce these lines.
Detailed per-order records remain under logCraftingDecisionFlow in debug.log.
`planComplete` means planning completed, NOT physical crafting completed.
`aborted` combines an unobserved result on exit (cancellation/error); it is not
silently relabeled as success or missing stock.
