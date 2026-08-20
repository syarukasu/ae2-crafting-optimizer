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

- Forge 1.20.1 with AE2 15.4.x or the audited AE2-UELM profile.
- This release does not transfer CPU execution ownership to ACO. AQE and other
  optional CPU add-ons continue to own submission, execution, and progress.
