# Issue 218: VM reference repair evidence

## Scope

Local candidate only: `2.0.0-rc.7-vm-refs.1-local`. The user selected the previous
BigInteger VM calculator and requested reference repair, not another graph/SMT
calculator redesign. The public BigInteger API and execution ownership remain
unchanged. No production JAR replacement, deployment, release or restart was
performed for this planner repair.

## Production observation

The 2026-10-04 18:43:30 thread dump is stored at
`C:\example\workspace\diagnostics\aco-planner-20261004\threads-1.txt`.
It shows the server in `NativeOrderSnapshot.Capture.candidate` /
`AECraftingPattern.isItemValid`, and an AE2 worker in `SMTInterpol.checkSat` /
`IntegerRecipeSolver.solve`. This identifies separate capture and solving work;
the dump alone is not an end-to-end timing comparison.

## Connected repair

- Use the existing `PatternCompiler` / `CraftingVM` BigInteger arithmetic and
  the AE2 calculation pool. No second executor or parallel ACO calculator.
- Capture stock once. Prepare reachable declared pattern metadata and actual
  candidate observations for this order, not every craftable in the network.
- Cache validity and remainder, including null, by input identity and complete
  AEKey. NBT variants are distinct; a later order captures fresh answers.
- Batch uncached candidate observations inside the existing AE2 read boundary.
  Do not recursively enumerate hypothetical future durability or returned-NBT
  states before planning.
- Skip the AE2 scheduler-only placeholder for fully detached inputs. When the
  placeholder is needed, its constructor bypasses old ACO shadow/authoritative
  planning capture before configuration lookup.
- Read ExtendedAE Plus scale coefficients as BigInteger before long overflow;
  retain original pattern bindings and distinguish wide outputs from wide bytes.
- Keep the graph.3 MEGACells owner-context startup repair. Graph adapters are
  excluded from the active native JAR. The pure graph library and SMT dependency
  remain packaged but are not the connected calculation route.

## Targeted verification

`NativeVmReferenceBatchTest.repeatedTaggedCatalystUsesOneObservationPerOrder`
failed before the cache repair with repeated-callback verification failure. It
now proves one validity and one remainder callback for a tagged reusable input
at a quantity above long range, with exact crafts and only one catalyst consumed.

Additional connected-VM fixtures verify:

- Prepared NBT alternatives finish after `NativeVmAccess.finish()` prohibits all
  subsequent world reads. Empty, partial and full stocks are checked at quantities
  1, 2, Long.MAX_VALUE and `10^1024 - 1`, including exact used/missing counts.
- A new order re-reads validity; answers do not leak between orders.
- Preparing one craft does not enumerate future returned-NBT states.
- An unused invalid alternative does not fail the order; selecting it exposes
  the exception instead of fabricating a valid result.
- Installed ExtendedAE Plus 1.5.5 input-only/output-only scaled wrappers preserve
  quantities across long overflow and retain the original execution binding.
- The compiled scheduler-constructor guard returns before old ACO configuration
  and capture calls. Existing scheduling and public API contracts still pass.

## Build

Working directory:
`C:\example\workspace\aco-rc4-vm\forge-1201`.
Java: `C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot`.

The following gates completed for both `upstream` (AE2 15.4.10) and `uelm`
(AE2 15.5.0-uelm), with UELM built last:

```powershell
.\gradlew.bat test verifyIssueRegressionManifest bundleVm verifyExactVmBundle vmNativeSourcesJar `
  '-PeaepPatternJar=C:\example\server\mods\extendedae_plus-1.5.5.jar' `
  '-PmegaCellsJar=C:\example\server\mods\megacells-forge-2.4.6-1.20.1.jar' `
  '-Pae2Variant=<upstream-or-uelm>' --no-daemon --console=plain
```

Both builds succeeded. Final UELM XML aggregate: 896 tests, 0 failures, 0 errors,
3 skipped optional AQE/Neo ECO contract tests. The real EAEP and MEGACells checks
were supplied their installed JARs and ran. Bundle gates verify reobfuscated
nested classes, native bootstrap, expected active VM classes and licensing.

Installable candidate (one JAR, with Jar-in-Jar dependencies):
`build/distributions/2.0.0-rc.7-vm-refs.1-local/aco2.0.0-rc.7-vm-refs.1-local_1.20.1.jar`.
Native component: `0.3.6-vm-refs.1`; exact library: `0.1.5`.
Size: 2,928,828 bytes.
SHA-256: `626F68BE573C6358DE6D082D39CA609F23CAD617BDABAA8A3CF72AF3A2E5DAE4`.

## Unaccepted boundaries

An opaque candidate first discovered while solving, including a previously
unseen returned NBT state, still needs an AE2-exclusive callback. This candidate
does not claim arbitrary world-dependent callbacks are all frozen at T0, nor
that all reference waits have disappeared.

Production order latency, server occupancy, game startup, wide physical
execution, cancellation/refund, restart recovery, simultaneous orders and the
sub-10-second final goal are unverified for this candidate. Test success is not
evidence of those outcomes. This is not a stable release.
