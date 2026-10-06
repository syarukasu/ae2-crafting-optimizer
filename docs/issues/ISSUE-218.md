# Issue #218: refs.8 snapshot parity on NeoForge 1.21.1

Status: Ready

## Scope and authority

Port the Forge refs.8 source at f93adc1 and VM fork at
5b57a00e283d355a47ee5276ba528b2dcf7d07db to NeoForge 1.21.1.
The user authorizes an Alpha upload and an accurate public description, not
production deployment. Preserve the unrelated local ISSUE-208.md and backups.

## Current problem

The existing 1.21.1 rc.5 build uses the old planner and ordinary dispatch budgets.
Relabeling it would not provide the approved refs.8 behavior.

## Ownership and invariants

- VM owns calculation using order-local stock and pattern observations.
- All authoritative VM quantities remain BigInteger.
- ACO retains its public API, exact accounting and result handoff.
- AE2 and add-ons retain physical execution, CPU structure, power and timing.
- Ordinary dispatch has no ACO time/count throttle.
- Keep NeoForge networking, registry-aware persistence and data components.
- No post-ownership fallback, synthetic outputs, or silent quantity saturation.

## Implementation and verification

Merge shared semantic changes from the Forge rc.5 baseline, retaining 1.21
platform adaptations. Build the pinned VM sources with 1.21 native adapters
and include the exact and native artifacts using Jar-in-Jar metadata.
Check API contracts, quantity fixtures, native AE2 descriptors, optional hooks,
bundle contents and licenses. Run existing tests and new port regressions.
Do not turn ignored/unsupported integration hooks into advertised features.

## Acceptance boundary

This is an Alpha snapshot, not a stable release. Wide physical completion,
cancellation/refund, restart recovery, concurrent orders and sub-10-second
industrial acceptance remain pending. Compilation is not runtime acceptance.

## Owners

NativeVm/NativeVmPlanningContext: observations and calculation scheduling.
VmBigIntegerAccounting: exact inventory and result bridge, not pattern feeding.
CraftingExecutionBudget: compatibility facade without ordinary throughput caps.
Loader entrypoints, mixins, codecs and network packets retain NeoForge ownership.
