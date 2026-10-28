# AE2 Crafting Optimizer 2.0.0

Forge 1.20.1 / Java 17. Install the same version on server and clients.
AE2 15.4.10+ is admitted without an upper bound, not guaranteed compatible forever.

- Exact BigInteger expressions such as `2^63-1` and `10^1024-1`.
- Prepared whole-order planning, shared materials, surplus and finite-tool returns.
- Reduced repeated candidate/inventory queries; no item-specific exceptions.
- Exact capacity headings, including optional-mod title rewrites.
- Bounded huge quantity tooltips with scientific notation and digit counts.
- AE2/CPU execution, energy, inventory and recovery ownership is retained.

## Observed Scope

Actual Forge GUI circuit shortage planning finished in 5.634 seconds at `2^63-1`
and 7.319 seconds at `10^1024-1`, without completed-plan cache reuse. This is not
manufacturing completion, fully-funded giant-order acceptance, a TPS claim or a
guarantee for every pack. The latest display fix passed headless verification,
but has not been rechecked in a live client. NeoForge has a separate artifact.

## Build

Place the matching `release/aco-2.0.0` branch of
[AE2-VM-ACO](https://github.com/syarukasu/AE2-VM-ACO) beside this checkout as `vm`.
With Java 17:

```shell
./gradlew bundleVm verifyExactVmBundle -PacoExactVmDir=../vm/exact-engine -PacoAe2Version=15.4.11
```

Install only `build/distributions/2.0.0/aco2.0.0_1.20.1.jar`, not intermediates
marked NOT-FOR-INSTALLATION. Do not install duplicate embedded VM modules.
Optional compatibility dependencies are compile-only, not bundled.

Only runtime code/assets, build settings, README and licenses are published here.
JARs/checksums belong in Releases. Tests, logs, profiles and worlds stay private.
LGPL-3.0-only; see LICENSE and NOTICE.md. [日本語](README_ja.md)
