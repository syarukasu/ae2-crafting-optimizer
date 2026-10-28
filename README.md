# AE2 Crafting Optimizer 2.0.0

NeoForge 1.21.1 / Java 21. Install the matching platform JAR on both sides.
This branch uses the updated shared BigInteger planning core with AE2 19 adapters.
AE2 and external CPUs retain execution, power, inventory and recovery ownership.

- Prepared whole-order/shared-material allocation and finite-tool arithmetic.
- Exact plan capacity headings, preserving optional-mod decorations and styles.
- Bounded evaluated-amount tooltips without changing native input validation.
- Version-specific source verification for the shared arithmetic core.

The GUI fix does not newly port Forge's exact-expression request protocol.
Shared arithmetic alone does not establish complete feature parity. Fifteen
focused presentation/contract checks and the bundle audit pass. No new NeoForge
client/server was launched; physical giant-order completion and runtime tool
parity remain unverified. Forge timings are not NeoForge benchmarks.

## Build

Check out [AE2-VM-ACO](https://github.com/syarukasu/AE2-VM-ACO) twice: its
`release/aco-2.0.0` branch as `vm-core`, and commit
`5b57a00e283d355a47ee5276ba528b2dcf7d07db` as `vm-host`. Keep the host checkout clean.
Supply the compile-only dependency directory described in `build.gradle`, then
run with Java 21:

```shell
./gradlew bundleVm verifyVmBundle -PacoVmForkDir=../vm-host -PacoVmCoreDir=../vm-core -PacoLocalModsDir=/path/to/mods
```

Install only `build/distributions/2.0.0/aco2.0.0_1.21.1.jar`, not intermediates
or duplicate embedded VM modules. This branch publishes source/assets, build
settings, README and licenses only. Binaries/checksums belong in Releases;
tests, logs, profiles and worlds stay out of the source publication.

LGPL-3.0-only; see LICENSE and NOTICE.md. [日本語](README_ja.md)
