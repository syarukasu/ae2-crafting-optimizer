# AE2 Crafting Optimizer 1.5.21

## Fixed

- Restored the normal AE2 crafting boundary after the optional BigInteger API
  integration began affecting ordinary crafting calculations.
- Ordinary long-range jobs now remain on AE2's standard planner unless the
  experimental crafting engine is explicitly enabled.
- External BigInteger consumer registration no longer changes standard AE2 CPU
  submission. External CPU add-ons retain ownership of capacity checks,
  submission, execution, progress, and cancellation.
- Removed the ME terminal inventory snapshot redirect so normal insertion and
  extraction use AE2's original menu path.
- The optimizer master switch now disables the BigInteger runtime backend too.

## Compatibility

- Forge 1.20.1
- NeoForge 1.21.1
- Existing public BigInteger planning API remains available to AQE and optional
  external CPU add-ons.

