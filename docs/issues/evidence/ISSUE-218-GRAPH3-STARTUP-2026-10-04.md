# Graph.3 Startup Repair Evidence

Status: local candidate, NOT runtime-accepted or released.

## Scope

- Keep graph.2's graph/SMT planning, shared catalog and exact API.
- Repair the concrete MEGACells bootstrap context failure from
  `crash-2026-10-03_22.45.27-fml.txt`, without changing mod initialization order.
- Match the Gradle client/server profiles to the distributed native planner.
- No production deployment, config repair, Minecraft/server launch or reboot.

## Concrete Regression

The supplied server trace calls MEGACells 2.4.6 `ForgePlatform.<clinit>` from
AE2MEGAThings 1.0.8/Kotlin loading. Forge's FMLJavaModLoadingContext.get casts
the active thread's language extension to the Java type, which fails for Kotlin.
The installed ForgePlatform bytecode contains precisely this lookup followed
by getModEventBus and the static field assignment.

MegaCellsModContext obtains the registered megacells container's extension via
a separate ModLoadingContext. The Mixin redirects only the original lookup.
It does not force any mod constructor, alter the thread-local context, inject
into Forge globally, suppress an initializer, or register on ACO's event bus.
The optional redirect has no direct MEGACells class linkage. require=0 permits
a future upstream implementation without the old call to remain unmodified;
the pinned installed JAR is separately checked to have exactly one call site.

Forge source references, checked against local 47.4.18 bytecode:

- https://github.com/MinecraftForge/MinecraftForge/blob/1.20.x/fmlcore/src/main/java/net/minecraftforge/fml/ModLoadingContext.java
- https://github.com/MinecraftForge/MinecraftForge/blob/1.20.x/javafmllanguage/src/main/java/net/minecraftforge/fml/javafmlmod/FMLJavaModLoadingContext.java

## Verification

- Before: real Forge context-cast reproduction fails with ClassCastException.
  Evidence: `build/issue218-megacells-context-before.xml`.
- After: foreign-language, foreign-Java, own, absent and missing-owner cases
  pass without changing the caller's active context.
- Installed MEGACells bytecode target and compiled redirect annotation checks
  pass. This is not an actual Mixin transformation/Forge launch acceptance.
- Upstream AE2 15.4.10 and UELM 15.5.0-uelm: build,
  verifyIssueRegressionManifest and verifyExactVmBundle pass.
- Each suite: 890 tests, 887 passed, 3 skipped, 0 failures/errors.
  Skips: optional AQE supplied-JAR capacity and Neo ECO 20.3/20.4 JAR contracts.
  No claim is made for these optional checks. Reports are preserved under
  `build/issue218-startup/{upstream,uelm}/test-results/`.
- The first full-suite attempt supplied an incorrect ExtendedAE Plus path and
  failed four fixture-loading tests. The corrected installed path is
  `C:/example/server/mods/extendedae_plus-1.5.5.jar`;
  both subsequent complete runs passed. This was a test invocation error,
  not evidence of a repaired product regression.
- Java build/test runtime: Temurin 17.0.17; Gradle 8.8; Forge 47.4.18.
- Final outer 531 classes, exact engine 31 classes and native adapter 37
  classes target Java 17 (major 61). SMT's 682 classes target Java 8 (52).
- All JAR CRCs pass; no class ownership overlaps across the nested archives.
- Compared with graph.2's final candidate: all 31 exact, 37 native, 682 SMT
  and 92 public API class bytes are unchanged. Only five outer classes differ:
  two new context-compatibility classes, MixinFeatureCatalog, OptimizationFeature
  and its compiler-generated switch class. Metadata adds the optional Mixin and
  new outer version. There is no retired-calculator rollback.

## Candidate

Final UELM-compiled candidate (same combined compatibility contract as graph.2):

`build/distributions/2.0.0-rc.7-graph.3-local/aco2.0.0-rc.7-graph.3-local_1.20.1.jar`

SHA-256: `A8217B236AC18593D5836F5075B50C584B75B5E7EFA3C42981DF202A1C88E0A2`

Size: 2,851,485 bytes. Contains exact 0.1.5, native 0.3.5-graph.2 and
SMTInterpol 2.5-1388-ga5a4ab0c. The engine versions are intentionally unchanged.
Corresponding SMT sources remain beside the runtime JAR under `sources/`.
The upstream-compiled comparison artifact is retained under
`build/issue218-startup/upstream/`, SHA-256
`77A8E501448A959E7202756FD7FF8A1D2FA314B94E4EC871C7DF68FF9309B7EF`.

## Isolated Startup Test (2026-10-04, JST)

The user subsequently authorized starting both sides. Tested the exact final
candidate above, without changing production JARs, configuration or worlds.

- Server copy: `C:/example/workspace/diagnostics/aco-graph3-startup-20261004/server`.
  Same 296 active top-level mod JARs except graph.1 replaced with graph.3.
  Copied config/scripts, world serverconfig and datapacks, but not production
  terrain/player data or Bukkit plugins. Used a new `aco-startup-test-world`,
  localhost port 25566 and the copied `run - コピー.bat` launcher.
  Arclight 1.0.6-SNAPSHOT-6de9fec, Forge 47.4.18, Temurin 25.0.4+7, G1,
  original 8/16 GiB heap and compiler-directive arguments.
- Client copy: Prism instance `aco-graph3-startup-test-20261004`, alongside
  `syarukasutime1.2.46`. Same 320 active top-level mod JARs except graph.1
  replaced with graph.3. No saves or server list copied. Forge 47.4.20,
  Temurin JRE 21.0.9+10, G1, original 8000/12096 MiB heap; windowed mode.
  The test instance name and managed-pack flag distinguish it from production.
- Both test JVMs export only `gripe._90.megacells.forge.ForgePlatform` through
  Mixin's debug exporter. Actual exported bytecode on BOTH sides redirects the
  static context lookup through `MegaCellsModContext.get()` and retains the
  original event-bus assignment. This is now actual transformation evidence,
  not only a compiled-annotation test.

### First Server Attempt: Existing Corrupted Configuration

The 16:56:58 startup reached ACO initialization and the repaired MEGACells
bootstrap. It stopped at 16:58:14 on `ConfigLoadingException` for
`config/Mekanism/mekanismmoremachine-storage.toml`: NightConfig reported
`Not enough data available`. Crash report:
`server/crash-reports/crash-2026-10-04_16.58.14-server.txt` under the test root.

Fourteen server TOMLs were filled entirely with NUL bytes; they were not empty
files. The test copies and production originals had identical SHA-256 values.
Two independent client TOMLs were also NUL-filled. This proves the copied data
was already corrupt, NOT when or which process originally corrupted it.

Only test copies were repaired, preserving originals and before/after SHA-256
manifests under each test Minecraft root's `config-before-repair/`. Server
sources were parseable matching paths in the production client. Client sources
were the production server's MoreThermalEvaporationCompat config and the
`試作・改(1)` instance's tooltipoverhaul config, with the same tooltipoverhaul
1.4.3 mod. No production configuration was rewritten; these test substitutions
do not establish that their values are suitable for production.

After repair, Python tomllib parsed all 198 server config/serverconfig TOMLs and
all 257 client config TOMLs. The complete repaired-path list and source hashes
are in the retained manifests, not inferred from an error count.

### Second Server Attempt: Started

- 17:02:33: launcher started.
- 17:02:55.505: the MEGACells context Mixin was applied.
- 17:03:07.942: ACO initialized; AQE recognized BigInteger API v3.
- 17:03:53.710: `Done (9.062s)!`; listening on 127.0.0.1:25566, PID 26832.
  The 9.062 seconds is the server's final stage timer, NOT total JVM startup.
- 17:04:35: requested console shutdown with Ctrl-C after the tool's `stop`
  input was not submitted by JLine. The normal server shutdown hook saved
  players/worlds and ran ACO/AQE lifecycle shutdown.
- 17:04:40.940: Arclight reported one remaining `pool-3-thread-1` and force
  exited. Do not label this a warning-free/clean-thread shutdown. The owner of
  that unnamed pool was not established. Java exited and the test port closed.

Server logs are retained in `server/logs/`; first-attempt copies are in
`server/config-before-repair/attempt-1-{latest,debug}.log`.

### Client Attempt: Title Frame Rendered

- 17:05:08: Prism launched the isolated client, PID 19860.
- 17:05:56.535: the MEGACells context Mixin was applied.
- 17:06:54.571: ACO initialized; AQE selected `aco:big_crafting_v3`.
- 17:07:10.825: Lightspeed reported title-screen startup in 122 seconds.
- 17:08:18: a real F2 keypress saved the title screen, with 395 loaded mods.
  The image contains the rendered panorama and normal menu buttons. The render
  thread sample was in normal FPS limiting, not blocked in ACO initialization.
  Computer Use window captures were white even then; only the game's own F2
  image proves rendered frame contents. User-visible desktop presentation has
  been asked about and is not independently confirmed by that image.

The isolated client is left open at its title screen; the test server is off.
No server join or crafting request was performed. No new `hs_err` file or client
crash report was present at the check. This single run does not rule out the
previous intermittent native/Windows failure.

Retained client evidence under
`C:/example/workspace/diagnostics/aco-graph3-startup-20261004/client/`:
`startup-latest.log`, `startup-debug.log`, `title-screen.png`,
`ForgePlatform.class`, `config-repair-manifest.csv`, and `SHA256.csv`.
The parent folder also contains `client-title-thread-dump.txt`.

## Unresolved Acceptance

The context repair addresses the recorded Forge ClassCastException. It does not
identify the writer of the Windows corrupt PTEs (0x1A/0x41792), and it is not
evidence that the client C2 or server GC native faults are cured. ACO-triggered
timing/load changes are neither proved nor ruled out by this work.

The isolated server startup and client title-frame checks above are now recorded.
Production startup with its original world/plugins, client/server join and any
crafting request remain untested. Production files remain unchanged, including
the corrupt configurations and graph.1 JAR. Wide execution, cancellation/refund,
restart recovery, concurrent orders and sub-10-second production planning remain
pending. Graph selection-to-execution and capacity-policy gaps remain as recorded
in ISSUE-218; this startup patch is not a claim of a finished ACO 2.0.0.
