# Regression history

Read the related record before changing a crafting optimization path. The
external-consumer contract is intentionally separate from AE2's normal
calculation and from an add-on's CPU execution loop.

| Record | Symptom | Affected loader | Fix status |
|---|---|---|---|
| [BigInteger external consumer](ISSUE-BIGINT-EXTERNAL-CONSUMER.md) | The real AE2 service path could lose the exact sidecar, and an external Quantum Bulk path could treat `maxPatterns=1` as its physical capacity. | NeoForge 1.21.1 | In this branch |

The record must be updated with the eventual GitHub issue number after the
issue is created. Do not replace exact accounting with a saturated `long`.
