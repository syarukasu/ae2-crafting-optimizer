# AE2 Crafting Optimizer

English | [日本語](README_ja.md)

## 2.0.0 Snapshot refs.8

**Alpha snapshot, not a stable release.** This NeoForge 1.21.1 branch ports the
Forge 1.20.1 refs.8 behavior at `f93adc1`. [Release notes and reproduction instructions](docs/releases/2.0.0-snapshot-refs.8.md).

ACO is a crafting-planning and integration mod for AE2 industrial networks.

- The bundled VM fork calculates plans with BigInteger quantities throughout.
- Plans use order-local inventory and pattern observations. Supported shared demands are aggregated.
- Public BigInteger API and exact accounting remain compatible with supported consumers.
- AE2 19 stock acquisition is preserved: player requests refresh storage; requester orders use cached stock.
- Ordinary AE2 and Pattern Provider dispatch have no ACO throughput throttle. External CPUs retain their capacity and execution ownership.
- Recipe Intent supports selected GTCEu and Mekanism item-machine lookups without bypassing machine processing time, power or validation.
- Optional Advanced AE, ExtendedAE and AE2 Overclock caches remain version-dependent.
- VM diagnostics include `/aco vm recent`.

## Installation

Minecraft 1.21.1, NeoForge 21.1.247+, Java 21 and AE2 19.2.17 (19.x).
Install the same bundled ACO build on server and clients. The VM is included;
do not install a second upstream VM. Back up worlds before testing snapshots.

Only install `build/distributions/2.0.0-snapshot-refs.8/aco2.0.0-snapshot-refs.8_1.21.1.jar`.
The `NOT-FOR-INSTALLATION` intermediate lacks the bundled VM. Forge 1.20.1 artifacts
cannot be used on NeoForge.

Config: `config/ae2_crafting_optimizer-common.toml`.

## Acceptance Boundary

Compilation, headless quantity tests and binary compatibility are not live acceptance.
Client/server startup, industrial false-shortage reproduction, wide physical completion,
cancellation/refunds, restart recovery, simultaneous orders and sub-10-second planning
remain unverified on this port. No production deployment or restart was performed.

The source Forge branch's CI run [37429047093](https://github.com/syarukasu/ae2-crafting-optimizer/actions/runs/37429047093)
failed `ae2.aco_vm_ae2_lifecycle` and `ae2.aco_vm_substitution_queries`.
This port does not claim those failures are resolved.

BigInteger planning does not automatically enlarge normal AE2 CPUs. Physical execution
requires a compatible consumer; planning never manufactures outputs or bypasses receipts.

## Development

- [Project charter](docs/PROJECT_CHARTER.md)
- [Class responsibilities](docs/CLASS_RESPONSIBILITIES.md)
- [Issue #218 port scope](docs/issues/ISSUE-218.md)
- [Regression matrix](docs/issues/REGRESSION_MATRIX.tsv)
- [Current public description](docs/CURSEFORGE_DESCRIPTION.md)
- [Pinned build workflow](.github/workflows/build.yml)

VM source: [syarukasu/AE2-VM-ACO](https://github.com/syarukasu/AE2-VM-ACO),
commit `5b57a00e283d355a47ee5276ba528b2dcf7d07db`. Build only its `exact-engine`
subproject; this repository compiles the native VM with NeoForge adapters.

License: [LGPL-3.0-only](LICENSE). Both bundled VM source archives accompany the distribution.
