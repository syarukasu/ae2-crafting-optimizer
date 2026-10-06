# Issue #218: vm-refs.5-local packaging and startup evidence

Date: 2026-10-05. Local candidate only; not published or installed.

## Artifact

- ACO: `2.0.0-rc.7-vm-refs.5-local`, Forge 1.20.1, final build uses upstream AE2 15.4.10.
- Path: `build/distributions/2.0.0-rc.7-vm-refs.5-local/aco2.0.0-rc.7-vm-refs.5-local_1.20.1.jar`.
- SHA-256: `A3B71D01DCCE7CD5A9D3C0033B15889F35710AFBBB6A673AEC7D47E24A12EEFB`.
- Bytes: 2930678 (refs.4) -> 1359504 (refs.5), reduction 53.61%.
- Nested exact VM 0.1.6: 34437 bytes, 32127 compressed.
- Nested native VM 0.3.10-vm-refs.5: 159170 bytes, 149604 compressed.
- Matching source archives: exact 20641 bytes, native 71894 bytes.
- No SMTInterpol runtime/source archive, graph fixture JAR, or graph solver runtime fallback is shipped.

## Implementation boundary

`exact-engine` compiles retired graph code in `graphFixture`, with SMTInterpol
only in `graphFixtureImplementation`. ACO uses those fixtures through the
test-only `graphComparison` configuration. Historical quantity tests are kept.

The former native provider catalog still imported RecipeGraph and was updated
on add/refresh/remove even though only comparison fixtures consumed it. Remove
those injections and the fork-private catalog hook. Move the catalog and graph
adapters to `native-engine/src/testFixtures/java`. Preserve the scaled-pattern
decoder in `NativePatternCoefficients`, called by the current planning context.

Compiled class SHA-256 comparison against the locally retained refs.4 bundle:

| Component | Before | After | Retained class changes |
| --- | ---: | ---: | --- |
| Outer ACO | 531 | 531 | None, including public API and physical execution |
| Exact VM | 31 | 14 | None; 17 retired graph classes removed |
| Native VM | 71 | 64 | NativeCraftingServiceMixin, NativeVmHook, NativeVmPlanningContext |

Native adds the coefficient reader and its record; removes the old catalog and
its eight nested types. Native VM/compiler arithmetic classes are byte-identical.
No new dispatch limit, accounting conversion, JVM flag or saved-data format.

## Verification

Build JDK: Temurin 17.0.17+10. Gates: `test verifyIssueRegressionManifest
verifyExactVmBundle vmNativeSourcesJar --no-daemon`, first `-Pae2Variant=uelm`,
then `-Pae2Variant=upstream`. Installed ExtendedAE Plus 1.5.5, MEGA Cells 2.4.6 and
AQE 2.2.7 JAR paths are supplied to their contract tests.

- Fail-before: RetiredRuntimeCodeTest detects graph classes in the exact library.
- Upstream and UELM: each 913 tests, zero failures/errors, two Neo ECO runtime
  contract tests skipped because those optional fixture JARs were not supplied.
- Exact module: 24 tests, zero failures/errors/skips, including retained graph comparisons.
- A fixture split initially broke Mockito access and a mock's runtime-hook cast.
  Fix fixture visibility and declare both interfaces; no assertions disabled.
- Bundle checks: exactly two nested libraries, metadata/byte equality, bootstrap,
  required coefficient reader, forbidden graph/catalog/solver classes, licenses,
  and exactly the matching two runtime source archives.
- `jdeps` on all three built artifacts: no retired graph/catalog/SMT references;
  no direct Unsafe, LWJGL or JNA type references found. This does not exonerate
  ACO from a JVM crash or validate all transformed runtime code.
- XML reports archived in `build/issue218-packaging-cleanup/{before,uelm,upstream}`.

Tests and size reduction are packaging evidence, not production acceptance.

## Startup findings and approved recovery

Production locations:

- Server: `C:/example/server`.
- Client: `C:/example/client`.

The client's `crash-reports/crash-2026-10-05_15.19.12-client.txt` reports
`ConfigLoadingException` for `MoreThermalEvaporationCompat/config.toml`, caused
by NightConfig `ParsingException: Not enough data available`.

Two client configuration files contained only NUL bytes, both dated October 3:

| Relative config path | Bytes | Verified restored SHA-256 |
| --- | ---: | --- |
| MoreThermalEvaporationCompat/config.toml | 11848 | 147A0D0064D1A582B6FE69EA416FBB0D7076B3C5931C4C0EDAFDE34F03F45109 |
| tooltipoverhaul/tooltipoverhaul.toml | 7337 | 7F992D602A614E5B3FF0F0A327AB9C79FFDA9BD356EFE3F0A9D3FD3380952F0D |

User approved backing up/restoring these two files. Their originals are retained
under the client's `config-repair-backup-20261005-154518/`, with original hashes
checked before overwrite. MoreThermalEvaporationCompat comes from the server.
Tooltip Overhaul has no server copy: it comes from the same-version repaired
`aco-graph3-startup-test-20261004` client; its bytes also match the old
`試作・改(1)` client. This source correction was stated before restoration.
Both restored files parse with the installed NightConfig 3.6.4; SHA-256 matches
the respective source. No other configuration files were changed.

Distinct unresolved native failures:

- Server `hs_err_pid20540.log`: 15:16:34, Java 25.0.4+7, GC Thread#8,
  jvm.dll+0x3f6e1a, invalid read 0x20000000, 47.662 seconds after launch.
- Client `hs_err_pid16368.log`: 15:16:54, Java 21.0.9+10, GC Thread#8,
  jvm.dll+0xca66, invalid read 0x0000015cbb657277, 62.338 seconds after launch.

Both occur during startup, not a measured crafting request. Therefore Java 25
alone does not explain the evidence. A JVM GC failure identifies where corruption
was detected, not its origin; see Oracle's
[system crash guidance](https://docs.oracle.com/en/java/javase/24/troubleshoot/troubleshoot-system-crashes.html).
The logs do not establish an ACO root cause or an ACO fix. Windows System errors
in 15:10-15:25 showed only a DistributedCOM warning; this does not rule out hardware.

No candidate JAR deployment, launch, restart, JVM-flag change or GC switch was
performed. Startup success and native crash resolution still need a separately
authorized runtime test. Wide execution, cancellation/refund, restart recovery,
concurrent orders and the sub-ten-second production goal remain unaccepted.
Both production mods folders still contain refs.4 with SHA-256
`131BB751AD9C520E12A82017A5F40E50A7E1AA9B2208E1AFD78A8A225A915B7D`.
