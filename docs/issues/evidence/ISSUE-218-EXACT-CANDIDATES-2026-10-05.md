# Fixed-input specialization and periodic replay search

Issue: #218. Date: 2026-10-05.
Candidate: `2.0.0-rc.7-vm-refs.4-local`, native `0.3.9-vm-refs.4`.
Local candidate only; not a stable release or full production acceptance.

## Public implementation references

- [PostgreSQL 17 expression interpreter](https://github.com/postgres/postgres/blob/REL_17_STABLE/src/backend/executor/execExprInterp.c):
  `ExecReadyInterpretedExpr` selects simple dedicated evaluators such as
  `ExecJustConst` instead of paying generic interpreter dispatch overhead.
  Applied here as fixed native input classification before live dispatch.
- [Oracle/Graal partial evaluation](https://github.com/oracle/graal/blob/master/truffle/docs/PartialEvaluation.md):
  specialize stable interpreter structure, keeping actual runtime values dynamic.
  Applied here to native input type, substitution policy and encoded key;
  not to stock, order quantities, damage transitions or arbitrary recipe results.
- [AE2 15.4.10 input implementation](https://github.com/AppliedEnergistics/Applied-Energistics-2/blob/forge/v15.4.10/src/main/java/appeng/crafting/pattern/AECraftingPattern.java):
  `Input.isValid` accepts its exact key and rejects non-matches when substitution
  is disabled. `getRemainingKey` still belongs to the recipe. These semantics,
  including full NBT identity, are preserved rather than approximated.
- [SymPy 1.14 cycle_length](https://github.com/sympy/sympy/blob/1.14.0/sympy/ntheory/generate.py#L916):
  geometric cycle-search windows advance when their search span is exhausted.
  Applied to VM trace-window scheduling, not a replacement of exact affine
  repetition validation with approximate state equality or a step-count abort.

These are manual predicate specialization and a search-scheduling correction,
not a claim to have integrated PostgreSQL, Graal, SymPy or a new compiler into
Minecraft. No dependencies, generalized solver or second executor were added.

## Correction

`NativeVmPlanningContext.LiveInput.observe` already rejected non-primary keys
locally for native crafting patterns without substitution. Its caller failed
to recognize that decision as available and acquired a live AE2 interval first.
The candidate treats the fixed rejection as prepared, filters it before batch
dispatch, and uses the same predicate for output-as-input classification.

Preparation tracks which item families contain dynamic input semantics. A new
returned key opens the live scheduler only if a relevant dynamic input exists.
A fixed input must not suppress a substitutable input in the same family.
Unknown custom/replaceable inputs still use AE2-exclusive callbacks once per
input/key observation. No future NBT-state enumeration is introduced.

The context and periodic-search schedule change against vm-refs.3. The compiler,
quantity equations, public API and physical dispatch are unchanged. Final class
comparison is recorded below; earlier context-only audit is not the final artifact.

## Quantity and work evidence

New tests use the actual private `AECraftingPattern.Input`, constructed over a
headless recipe fixture. The fixed recipe returns a different NBT key. For an
order N and supplied S in [0, N], used original inputs equal S, missing original
inputs equal N-S, and craft executions equal N. The returned variant cannot be
silently treated as the original input.

Orders: 1, 2, 1000, Long.MAX_VALUE, Long.MAX_VALUE+1, and 10^1024-1.
Stock: empty, half, sufficient. The fail-before matrix entered ten unnecessary
live intervals in total; the same matrix now enters zero, with the same exact
used/missing/craft results. For 1000 specifically:

| Stock | Used | Missing | Before live reads | After live reads |
|---|---:|---:|---:|---:|
| 0 | 0 | 1000 | 1 | 0 |
| 500 | 500 | 500 | 1 | 0 |
| 1000 | 1000 | 0 | 0 | 0 |

Closing the read boundary after capture still permits the fixed-input plan.
With substitution enabled, a single original seed produces the entire
1024-digit order by reusing the returned variant, observed exactly once.
A mixed-producer test shares the same key family between fixed and substitutable
inputs and verifies that the dynamic producer remains available.

The finite-tool replacement test retains exact ceil(N/uses) tool and replacement-
metal checks. A new test-only work assertion rejects order-sized enumeration;
it is not a runtime order or delivery cap. Eight NBT encodings vary key ordering.
For 32 uses, the before run exceeded 65536 requests and remained incomplete at
2^63 with half the required tools present. After the search-window correction,
all 120 stock/order/encoding cases completed with maximum work 194 (0.909 seconds
for the entire focused matrix, including fixture setup). The matrix was then
expanded to 65 and 257 uses to check non-power-of-two and longer periods.
All 360 expanded combinations also completed with exact tool/metal consumption.
Maximum work was 1713; the complete matrix took 5.708 seconds on upstream and
5.647 seconds on UELM, including fixture setup. These are local synthetic tests,
not industrial-order or in-game timings.

Raw before/after XML: `build/issue218-exact-candidates/before` and `focused`.
The focused after matrix took 0.275 seconds total including worker scheduling
in that run. This is not a controlled production speed comparison.

## Verification and boundaries

Upstream AE2 15.4.10 and UELM 15.5.0 full tests, API checks, issue manifest and
reobfuscated Jar-in-Jar verification passed. Each suite reported 911 tests,
zero failures/errors and two skipped tests; those skips are not acceptance.
Final XML is retained in `build/issue218-exact-candidates/upstream-final/test`
and `build/issue218-exact-candidates/uelm/test`. Final packaging uses upstream.
No Minecraft client/server was launched or modified; no release, push or
production deployment was performed.

Both runs used JDK 17.0.17 and:

```powershell
./gradlew.bat test verifyIssueRegressionManifest verifyExactVmBundle vmNativeSourcesJar --no-daemon `
  -Pae2Variant=upstream `
  '-PeaepPatternJar=C:\example\server\mods\extendedae_plus-1.5.5.jar' `
  '-PmegaCellsJar=C:\example\server\mods\megacells-forge-2.4.6-1.20.1.jar' `
  '-PaqeCapacityJar=C:\example\server\mods\aqe2.2.7_1.20.1.jar'
```

The UELM run uses `-Pae2Variant=uelm` instead. No game-launch task is included.

## Candidate audit

- Installable artifact:
  `build/distributions/2.0.0-rc.7-vm-refs.4-local/aco2.0.0-rc.7-vm-refs.4-local_1.20.1.jar`
- SHA-256: `131BB751AD9C520E12A82017A5F40E50A7E1AA9B2208E1AFD78A8A225A915B7D`.
- Size: 2930678 bytes. Native VM: `0.3.9-vm-refs.4`; exact VM: `0.1.5`.
- Against the installed vm-refs.3 artifact, all 531 outer `.class` files are
  byte-identical, including public API, physical execution and delivery classes.
- Native bundle class count remains 71. Thirteen changed class files are exactly
  NativeVmPlanningContext, VmInstructionExecution and their nested classes;
  the remaining 58 are identical. No class was added or removed. Nested debug
  line-table changes are included in the thirteen, not thirteen semantic edits.
- The bundle gate checks exact nested bytes, class boundaries, metadata,
  bootstrap and licenses. Source archives are under the candidate's `sources/`;
  they are not additional installable mods.

## Remaining boundaries

The initially intermittent finite-tool delay was reproduced with changed NBT
encodings and retained in `before-replay` and `uelm-initial`. A trace with a
one-time consumed item correctly prohibited affine replay, but the geometric
window had already grown after every new key/success and was retained for up to
1048576 iterations. Grow only after an exhausted unsuccessful search; restart
short on domain changes or success. Exact bounds and replay accounting are not
weakened. Temporary test probes were removed before packaging.

The running vm-refs.3 order-4 log showed 19067 ms total, 14628 ms of access wait,
and 3349 INPUT_VALIDITY entries. It does not identify how many entries were this
fixed-predicate path, nor does simulation=true prove the missing quantities
correct. No production time reduction is claimed for vm-refs.4 yet.

The original copper-wire false shortage, general newly returned opaque NBT,
wide physical completion, cancellation/refund, restart recovery, concurrent
orders and the final sub-ten-second industrial target remain unverified.
