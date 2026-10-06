# Order snapshot candidate evidence

Date: 2026-10-05. Issue: #218.
Candidate: `2.0.0-rc.7-vm-refs.2-local`.
Native VM: `0.3.7-vm-refs.2`.
Status: local candidate, not completed runtime acceptance or a stable release.

## Changes and quantitative proof

- Acquisition now collects stock keys, templates, selected craftable keys and all
  reachable outputs before capturing candidate validity and remainders. Closed
  candidate domains use one AE2 worker with no post-capture world read scheduler.
  Network producer/emitter answers are frozen, not refreshed during arithmetic.
- `producedNbtAlternativeIsCapturedBeforeArithmeticAndNetworkChanges` captures a
  later dependency's NBT byproduct, changes live producer/validity answers, disables
  world reads, and completes with the original stock and both original crafts.
- The alternate-only catalyst case failed before the correction. For order N,
  one alternative catalyst and raw stock S in {0, floor(N/2), N}, the repaired
  plan consumes exactly one catalyst and S raw units, misses N-S raw units and
  zero primary catalysts, and schedules N crafts. N includes Long.MAX_VALUE+1
  and 10^1024-1. No quantity-sized loop or long conversion was introduced.
- Alternate containers are made available after their craft, including to later
  inputs of the parent. With N required products/containers and S alternative
  ingredients, used alternatives are S, missing primaries and containers are
  both N-S, initial container consumption is zero, and both pattern counts are N.
- An accepted NBT variant emitted as a declared output can seed the next craft,
  even when it differs from the primary input template. A single seed completes
  10^1024-1 crafts without an invented primary shortage or a world callback.
- A fluid/item-alternative fixture exposes AE2's primary-only return omission:
  seven consumed water buckets add seven bytes of return accounting in this VM.
  The explicit independently calculated return charge is added only for that
  fixture; ordinary AE2 quantity/byte comparisons stay strict.
- Existing batch-rounding, shared-byproduct, alternative-producer, rollback,
  changing-tool, wide-scaling and real AE2 oracle comparisons remain covered.
  Passing these cases is not proof for every possible mod recipe or live order.

## Performance measurement

The fixture uses real `AEProcessingPattern` objects, the production acquisition
context and VM entry, with a 48-stage shared dependency graph. Craft counts and
raw demand are calculated independently in the test and compared exactly.
Initial stock is zero, half the needed raw stock, or fully funded. Each order
uses a fresh acquisition context. All post-capture world access is disabled.

This is synthetic: service lookups and accessible stock are supplied by the
fixture. It excludes live ME stock extraction, real mod callback cost, server tick
load, pack recipe semantics and client UI latency. It is not a speedup comparison
against the previous deployed JAR and not the live creative-control-circuit test.

Fresh JVM, Java 17.0.17, four runs per quantity/stock condition:

| Quantity | Stock | Median capture+plan ms | Maximum ms |
|---|---|---:|---:|
| 1 | empty | 11.74 | 144.50 |
| 1 | partial | 14.47 | 16.29 |
| 1 | funded | 7.78 | 8.82 |
| 2 | empty | 6.09 | 7.34 |
| 2 | partial | 9.21 | 10.02 |
| 2 | funded | 5.12 | 5.80 |
| 9,220,000,000,000,000,000 | empty | 5.99 | 6.76 |
| 9,220,000,000,000,000,000 | partial | 7.13 | 8.74 |
| 9,220,000,000,000,000,000 | funded | 6.16 | 7.37 |
| Long.MAX_VALUE | empty | 6.16 | 8.20 |
| Long.MAX_VALUE | partial | 6.83 | 7.55 |
| Long.MAX_VALUE | funded | 3.94 | 5.66 |
| Long.MAX_VALUE+1 | empty | 4.99 | 6.71 |
| Long.MAX_VALUE+1 | partial | 5.67 | 7.97 |
| Long.MAX_VALUE+1 | funded | 3.71 | 4.28 |
| 10^1024-1 | empty | 5.00 | 7.03 |
| 10^1024-1 | partial | 6.16 | 6.59 |
| 10^1024-1 | funded | 5.67 | 7.50 |

The first cold case took 51.989 ms to acquire and 92.510 ms to plan/schedule the
worker, total 144.500 ms. Every case used one aggregate demand request, zero
arithmetic world reads and exact expected quantities. Raw measurements are in
`build/reports/issue218-order-snapshot/measurements.json` and copied beside the
candidate. Per-variant full-suite reports are retained under
`build/issue218-order-snapshot/`.

## Compatibility and artifact

Both upstream AE2 15.4.10 and UELM 15.5.0 passed the existing API/quantity tests,
issue regression manifest and final reobfuscated Jar-in-Jar audit. Installed
ExtendedAE Plus 1.5.5 and MEGACells 2.4.6 bytecode contracts were included; the final
upstream run also supplied installed AQE 2.2.7 to the capacity contract check.
The final distribution targets upstream 15.4.10, matching the server metadata.
No Minecraft, dedicated server or GameTest launch was performed.

Read-only binary comparison with the installed vm-refs.1 JAR found all 531 outer
classes identical, including the public BigInteger API and physical delivery
code. Only 16 nested native-VM class files changed, all owned by PatternCompiler,
NativeVmPlanningContext and VmInstructionExecution (including nested/debug-info
changes). The bundle still contains one main installation JAR and its libraries.
This comparison proves change scope, not gameplay behavior.

Candidate:
`build/distributions/2.0.0-rc.7-vm-refs.2-local/aco2.0.0-rc.7-vm-refs.2-local_1.20.1.jar`

SHA-256:
`23E241481C4143E218F26A588E2789A36461A6DB57418212C50B6C5C0F7786BA`

Installed server JAR remained:
`626F68BE573C6358DE6D082D39CA609F23CAD617BDABAA8A3CF72AF3A2E5DAE4`

## Not completed

- Arbitrary newly returned NBT states outside the captured finite domain still
  use the existing synchronized compatibility callback. Removing it without a
  valid detached semantic adapter would be unsafe and would not satisfy accuracy.
- The original copper-wire false shortage has not been reproduced from its exact
  order-time network and stock. Later saved files confirm the eight-wire pattern
  and substantial copper stock, but lack that order's grid membership, accessible
  storage, provider ordering and callback state. They do not prove the report fixed.
- Live creative-control-circuit planning under ten seconds, server-load benefits,
  wide physical completion, cancellation/refund, restart recovery and simultaneous
  orders remain unverified. Build results and test totals are not completion proof.
- The user chose candidate JAR only. No production/client replacement, world
  change, restart, GitHub push or release was performed.
