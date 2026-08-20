# AE2 Crafting Optimizer 1.5.22

## Fixed

- Exact BigInteger inventory snapshots are now created only inside ACO's
  authoritative crafting planner.
- Normal AE2 terminal insertion, extraction, serial allocation, storage buses,
  import/export buses, and storage watchers no longer receive ACO sidecars.
- Exact `10^64` test-cell amounts remain available to BigInteger planning
  without replacing AE2's normal network inventory.
- ExtendedAE Plus cache refreshes are limited to cells directly modified by
  ACO's exact ledger.

## Compatibility

- NeoForge 1.21.1 with the supported AE2 19.2.x profile.
- This release does not transfer CPU execution ownership to ACO. AQE and other
  optional CPU add-ons continue to own submission, execution, and progress.
