# AE2 Crafting Optimizer

Forge 1.20.1 / Java 17 optimization and exact-count integration for Applied Energistics 2.

## 2.0.0-rc.7-beta.1

This is an experimental Forge beta, not stable ACO 2.0.0. Install the bundled `aco2.0.0-rc.7-beta.1_1.20.1.jar` on both client and server. Do not install duplicate copies of its embedded VM modules. The crafting channel uses protocol 5; client and server must match.

- Exact crafting-amount expressions, including `2^63-1`, `2^63`, and `10^1024-1`.
- BigInteger root amounts, exact packets, confirmation/back/replan state, and existing Exact Count limits.
- Dependency MOD versions use minimum-only admission: AE2 15.4.10 or newer. Minecraft and Forge platform ranges and structural API/Mixin checks remain.
- Optional bounded calculation profiler and explicitly armed read-only VM/AE2 replay.
- Current native VM with captured input rules, AE2-worker arithmetic and exact rational fast paths.

A newer dependency being admitted does not guarantee binary compatibility. AE2 and external CPUs retain physical execution, energy, reservation, progress, cancellation and refunds. A large amount being accepted does not make every CPU capable of executing it.

Validated against official AE2 15.4.11: clean build, 996 JUnit cases (0 failures/errors, 8 optional skips), regression scope, bundled-module/license boundaries, and 16 real AE2 GameTests. Tests and local evidence are retained in the development checkout, not this source-only publication.

Real production GUI interaction, industrial timing/TPS, huge physical CPU completion and refund/restart recovery remain unverified. No TPS improvement is claimed. NeoForge is a separate platform line and is not included in this Forge beta.

## Build

Requires Java 17. Check out the matching `release/2.0.0-rc.7-beta.1-public` branch of [AE2-VM-ACO](https://github.com/syarukasu/AE2-VM-ACO) beside this checkout as `vm`, then run:

```shell
./gradlew clean build -PacoExactVmDir=../vm/exact-engine -PacoAe2Version=15.4.11
```

The default reproducible compile pin is AE2 15.4.10. Compatibility dependencies are compile-only and are not bundled. A local dependency directory may be supplied with `-PacoLocalModsDir=/path/to/mods` and `-PacoMekanismJar=/path/to/Mekanism.jar`.

Install only `build/distributions/<version>/aco<version>_1.20.1.jar`. Files marked `NOT-FOR-INSTALLATION` are build intermediates. Matching VM source archives are emitted beside the distribution under `sources/`.

Source: LGPL-3.0-only, see LICENSE and NOTICE.md. The bundled VM is derived from TaoLe-si/AE2-VM and retains its license and attribution. [日本語](README_ja.md)
