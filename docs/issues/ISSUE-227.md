# Issue #227: Forge 1.20.1 processing BigInteger requests

Status: local candidate built and contract-verified; no deployment or publication.

## Baseline

- Issue: https://github.com/syarukasu/ae2-crafting-optimizer/issues/227
- Forge source: `9be6cf57b6a6a359a1318ef21c33b5fed96db253`
  (`release/2.0.0-rc.7-beta.2-public`).
- VM source: `02bf2375ee723fb94519bb814b3975ef370b7add`, matching public CI.
- Local branch: `feature/227-processing-bigint-1201`.
- Runtime target: Minecraft 1.20.1, Forge 47.4.18, Java 17, AE2 15.4.10.

## Design

Release preparation: Ready. User authorized GitHub prereleases for both platforms.
Forge version: `2.0.0-rc.7-beta.2-processing.1`;
tag: `v2.0.0-rc.7-beta.2-processing.1-1.20.1`.
Publish the tested source and rebuilt JAR with developer documentation and checksums.
Keep external machine and cross-owner restart acceptance unverified. No deployment.

Add `api.processing.ProcessingPatternBatchTarget` and immutable
`ProcessingPatternBatchRequest`. Keep existing public API members, signatures,
save schemas, table-only API eligibility, and table worker behavior unchanged.

The typed processing adapter reuses the existing exact escrow and receipt
transaction. Quantities are BigInteger throughout; per-execution coefficients
remain AE2 long values. The adapter performs no recipe simulation or independent
inventory debit/credit. Machine eligibility is checked before acquisition; an
accepted request is polled without repeating mutable eligibility checks.

Connect both legacy plan submission and Forge's current VM sidecar binding to
the additional physical-plan entry points. Do not modify VM calculation,
network transfer rates, machine speed, CPU limits, configs, or recipe contents.

If accepted processing ownership or its receipt disappears, quarantine instead
of assuming rollback, retrying, or refunding. Confirmed cancellation refunds
unused inputs; completed verified output is credited once. Existing table-only
recovery is unaffected.

## Verification scope

- Actual AE2 processing pattern NBT encoding, fluids, sparse slots and multiple outputs.
- Mixed processing/table DAGs at 1, Long.MAX_VALUE, Long.MAX_VALUE+1, 10^64,
  and floor((10^1024-1)/64) executions.
- Public CPU API and VM sidecar submission above Long.MAX_VALUE, save/load
  after every advance, final output, zero outstanding escrow and no worker jobs.
- Cancellation, lost/foreign receipts, disappearing workers, wrong output,
  eligibility changes and unloaded targets.
- Existing table/escrow/plan regressions, and a public API descriptor comparison
  against classes compiled from the unmodified Forge baseline.

World, stock and machine endpoints are contract doubles. This is not packaged
OAE/AQE runtime acceptance or proof of atomic disk recovery across two mods.
Native machine adapters must implement the new API. Expanded input counts,
dynamic batch results and cyclic physical execution retain existing limits.

## Results (2026-10-23)

- 92 targeted regression tests passed, zero failures or skips; 13 processing tests.
  Existing table, escrow, quantity and planner tests were imported from the local
  beta.2 source checkout because the public release checkout omitted its tests.
- Each mixed-DAG case finished with exactly `n` final items, no remaining escrow
  or worker receipts, and one input withdrawal batch plus one final insertion batch.
- The VM sidecar -> public CPU API case finished with exactly
  `9,223,372,036,854,775,808` final items. Each of its three processing requests
  retained that same execution count, including after codec reloads.
- 92 baseline API class files retained all public declarations/JVM descriptors.
  Evidence: `build/compat/public-api.json`.
- `build` passed, including reobfuscation and nested-VM byte/bootstrap/license
  verification. VM source remained unchanged at the pinned commit.
- Candidate: `build/distributions/2.0.0-rc.7-beta.2-processing.1-local/aco2.0.0-rc.7-beta.2-processing.1-local_1.20.1.jar`
- Size: 1,486,366 bytes.
- SHA-256: `6c7eb35516ca767dc4dda0657e1c0bbe0d3809d90ab596a71c0e775391b00cec`.
- Reports: `build/reports/tests/test/index.html`, `candidate-build.log`.

Reproduction (PowerShell with Java 17):

```powershell
git clone https://github.com/syarukasu/AE2-VM-ACO.git .ci/vm
git -C .ci/vm checkout --detach 02bf2375ee723fb94519bb814b3975ef370b7add
./gradlew.bat build '-PacoExactVmDir=.ci/vm/exact-engine' '-Pmod_version=2.0.0-rc.7-beta.2-processing.1-local'
```

This proves the ACO processing request and accounting boundary in a controlled
fixture, not real OAE machine completion, multiplayer use or server restart.

## Prerelease validation

`2.0.0-rc.7-beta.2-processing.1`: build passed including nested-VM verification;
92 tests passed without skips. Public API descriptors: 92 baseline classes unchanged.
Installable JAR SHA-256: `5a6f53a71f91f35cd3622fa1c3f7f503659495bb7a8e2a81b5230603633ca7fb`.
The developer guide describes all seven callbacks, native execution, ownership,
exact output receipts, safe cancellation and the remaining runtime boundary.
