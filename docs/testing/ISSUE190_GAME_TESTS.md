# Issue #190: real AE2 acceptance baseline

## Commands

Use JDK 17. These commands run an isolated Forge GameTest server, not a client
or the production Arclight server. Test worlds/logs are under `build/gametest-ae2`.

```powershell
./gradlew.bat runGameTestServer -Pae2Variant=upstream --no-daemon
./gradlew.bat runGameTestServer -Pae2Variant=uelm --no-daemon
./gradlew.bat runGameTestServer -Pae2Variant=upstream -PacoGameTestScope=all --no-daemon
```

The default `aco` scope runs only ACO's acceptance plots. `all` includes every
shipped AE2 test and does not suppress any upstream failure. Do not equate the
focused scope passing with the full suite passing.

The test mod lives in `src/gameTest`, outside the distribution JAR. It uses AE2's
own plot builder and Minecraft's JUnit reporter. The task removes old reports
before launch, requires every registered test name to appear exactly once, rejects
failed/skipped tests and unapplied ACO Mixins, and requires ACO to be enabled.
Forge returning exit code zero without executing tests is a failure.

## Finite-stock tests

- `aco_processing_completion`: an actual inscriber converts 10 of 32 crystals
  into dust. Both CPUs finish idle; ME contains exactly 22 crystals and 10 dust.
- `aco_cancel_before_delivery`: reserve a 10-item processing order without a
  machine, cancel, and require all 32 crystals returned and zero dust.
- `aco_competing_reservations`: compute two 4-item plans from the same initial
  five crystals; submit to different real CPUs in the same tick. Exactly the
  first submission succeeds; real processing leaves one crystal and four dust.
- `aco_returned_containers`: a real molecular assembler makes two vanilla cakes.
  Six milk buckets, four sugar, two eggs and six wheat are consumed; each input
  has one remaining unit, with two cakes and six returned empty buckets.

No output is inserted by the test to simulate machine completion. Only finite
initial materials and encoded patterns are supplied. Cake initialization runs
once in the test sequence, not a structure placement callback which can replay.
Crafting is requested through the real grid-linked simulation requester.

## Evidence and limitations

Both Forge dependency variants also passed `test build verifyIssueRegressionManifest`:
632 tests each, 630 passed and two optional Neo ECO JAR checks skipped. The test
classes are absent from the distribution JAR. These are local unreleased builds
with the existing rc.4 version string, NOT replacements for the published rc.4.

2026-09-21: upstream AE2 15.4.10 and UELM 15.5.0 focused scopes each passed all four tests. Earlier
all-scope runs executed 56 tests (before the fourth ACO plot was added); the
existing `ae2.import_from_cauldron` test failed with ACO enabled AND with its
master switch disabled. This comparison does not establish behavior with ACO
uninstalled, and does not justify changing industrial liquid recipes.

The comparison also exposed a fixture using an unlinked `BaseActionSource`:
standard AE2 could not plan, while the accelerated route did. The competing
reservation fixture now uses AE2's `MachineSource` pattern. The behavior difference
for an unlinked simulation requester is tracked separately in
[Issue #206](https://github.com/syarukasu/ae2-crafting-optimizer/issues/206) and
still requires its own correctness fix and real regression check.

These tests cover ordinary long quantities on real AE2. They do NOT prove:

- long-overflow physical processing through GTCEu/Mekanism/AAC/AQE;
- dynamic/interleaved/cyclic BigInteger physical execution;
- restart recovery or in-flight machine cancellation;
- production-pack simultaneous giant orders, MSPT, allocation cost or latency;
- NeoForge runtime compatibility or the final sub-ten-second full recipe tree.

Do not close #190, mark all historical runtime tests verified, or publish a
completion claim based only on this baseline. Private XML/log evidence is retained
under the sibling artifacts/2026-09-21-runtime-baseline directory.
