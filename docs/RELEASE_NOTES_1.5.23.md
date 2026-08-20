# AE2 Crafting Optimizer 1.5.23

## NeoForge 1.21.1

- Added exact BigInteger physical execution for jobs accepted by the standard AE2
  `CraftingCPUCluster`.
- Standard AE2 clusters now reuse ACO's quantity-independent
  `PhysicalCraftingTreeTransaction` instead of requiring repeated `Long.MAX_VALUE`
  execution windows.
- Preserved AE2 ownership of CPU capacity checks, busy state, crafting links, job
  creation, and normal long crafting.
- Added exact task, waiting-output, final-output, receipt, cancellation, and NBT
  recovery accounting to the same AE2 `ExecutingCraftingJob`.
- Added regression guards that prevent this path from depending on Advanced AE or
  InsaneAE implementation classes.

This release does not add or modify CPU blocks, structures, recipes, models, or
textures. The new path only activates for an ACO exact plan that the target AE2 CPU
has already accepted.
