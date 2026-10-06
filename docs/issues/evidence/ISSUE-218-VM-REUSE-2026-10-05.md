# Compiled VM block reuse

Issue: #218. Date: 2026-10-05.
Candidate: `2.0.0-rc.7-vm-refs.3-local`, native VM `0.3.8-vm-refs.3`.
Local candidate only, not a stable release or completed production acceptance.

## Requested behavior

Optimization means preserving the same tool reuse, returns, used quantities,
missing quantities and capacity charges with less repeated work. Keep the
order-local snapshot and a single BigInteger VM. Do not replace a damaged tool
with a pristine one, omit its return, guess arbitrary NBT behavior, or throttle
ordinary Pattern Provider execution.

## Runtime change

Only `VmInstructionExecution` changes its behavior relative to vm-refs.2:
`ProcessMetadata` lazily retains the compiled block for its order. Previously,
every interpreted block called `compileIfAbsent` and `getCompiled`, including
output/input metadata validation before the compiler's existing cache lookup.
The compiled block is now resolved once per demanded pattern. Purely aggregated
patterns and unused alternative producers remain lazy.

Reference acquisition, candidate validity, remainder observations, ordered
branch trials, exact demand aggregation and public BigInteger API are unchanged.
There is no additional runtime calculator, thread, config option or delivery cap.
The reference capture from vm-refs.2 remains active; this is not a rollback.

## Independent quantity and work evidence

`VmCompiledPatternReuseTest` first failed with 170 primary metadata reads. The
before/after reports retain exact quantities, pattern counts and byte charges
for a finite 32-use tool with empty, partial and funded stock. Orders cover
1, 2, 1000, Long.MAX_VALUE, Long.MAX_VALUE+1 and 10^1024-1.

For 1000 crafts, the partially stocked case drops primary metadata reads from
170 to 2. VM request work remains 339; the change removes redundant metadata
work, not required inventory operations or repeated tool wear transitions.
Do not equate this call-count reduction with an 85x total planning speedup.

The connected `NativeVmReferenceBatchTest` additionally includes a replacement
tool manufacturing pattern, consuming three metal units per new tool. For N
crafts and T initial tools, required tools are ceil(N/32), manufactured tools
are ceil(N/32)-T, and consumed metal is exactly three times that difference.
Only one primary state is observed during capture; at most 32 different tool
states are observed across the entire order, including 1024-digit orders.
These are deterministic fixtures, not evidence of every mod's tool semantics.

## Archived arithmetic comparison

`NativeVmArchiveComparisonTest` gives both arithmetic implementations the same
production-context capture of native AE2 processing patterns and the same stock.
It disables all post-capture world reads. The synthetic shared graph has 24
stages; quantities are 1, 50, 100, Long.MAX_VALUE+1 and 10^1024-1, with empty,
partial and funded stock. Four repetitions alternate execution order. Independent
forward demand propagation verifies every raw quantity and pattern count.
Byte charges also agree with the archived ordered VM.

The earlier focused run's warm median arithmetic times, grouped across quantities:

| Stock | Archived source ms | Candidate ms | Archived requests | Candidate requests |
|---|---:|---:|---:|---:|
| empty | 1.34 | 0.93 | 26 | 1 |
| partial | 2.15 | 1.18 | 68 | 1 |
| funded | 1.37 | 0.80 | 47 | 1 |

These timings include neither live stock extraction nor server/client latency.
The archive source uses the current compiler and snapshot adapter. They are not
a measured before/after of the installed JAR, nor proof that every workload is
faster. The current exact demand aggregation is retained; the useful archived
compiled-block reuse is restored without discarding subsequent accounting fixes.

## Historical identity limitation

The source fixture is from
`build/issue218-cleanup/upstream/aco-vm-native-0.3.3-dev.19-sources.jar`, SHA-256:
`E30EA71F785B511EB8B4554902B9B0504EAC750C3CF81F07151B419EE4FDE8CD`.
It is test-only and is not included in the candidate JAR.

The release record describes dev.20 as a diagnostics-only step. However, the
current dev.20-named distribution has SHA-256
`47B9101927013D1374AD9DCDA7BF1143380162BCAC45E25F392471B0D11A713D`, while the
historical record identifies
`358203CB49AE453FBECA552FF2B1F658ED846982C169D4FEA178FC7D8683BACA`.
Recovery copies instead have
`8A81E3859DA63199B0A66F080F94AB62671AEAEED58AD4C641A3E1D9B6B8D8B0`.
An original authenticated dev.20 binary comparison is therefore not established.
File names must not be used as proof of historical artifact identity.

## Acceptance boundaries

Both upstream AE2 15.4.10 and UELM 15.5.0 full test suites, API contracts, regression
manifest and reobfuscated bundle checks were run. Installed EAEP 1.5.5, MEGACells
2.4.6 and AQE 2.2.7 artifacts were supplied to the relevant contract checks.
The final distribution targets upstream AE2. These are local verification gates,
not actual startup, client connection, execution or production timing evidence.

Arbitrary newly returned NBT outside the captured domain still uses synchronized
callbacks when encountered. The original copper-wire false shortage is not
reproduced from its exact live snapshot. Live sub-ten-second creative-circuit
planning, wide completion, cancellation/refund, restart recovery and concurrent
orders remain unverified. No production/client file, world, order, server process,
GitHub release or remote branch was changed.

## Candidate artifact

Final upstream JAR: `aco2.0.0-rc.7-vm-refs.3-local_1.20.1.jar`.
SHA-256: `2E860A3EF4EA68E2EDA01EE3F4ABAF559EF8F2EA2E2A809F27A7E8EB44CE0AAE`.

Binary comparison against vm-refs.2 found every one of the 531 outer classes
byte-identical, including public API and delivery code. The only changed native
classes belong to VmInstructionExecution (including nested/debug-line changes).
No archived test calculator or benchmark class is present in either runtime JAR.
The server's installed vm-refs.1 still hashes to
`626F68BE573C6358DE6D082D39CA609F23CAD617BDABAA8A3CF72AF3A2E5DAE4`.

Reports are copied beside the candidate as `tool-before.json`, `tool-after.json`
and `archive-snapshot-comparison.json`; per-variant test XML is retained under
`build/issue218-vm-reuse/`. Historical labels in the original before report are
retained as raw evidence; the archive identity caveat above applies to them.
