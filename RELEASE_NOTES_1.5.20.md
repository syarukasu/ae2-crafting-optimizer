# AE2 Crafting Optimizer 1.5.20

## Added

- Added the public `ExactStorageAmountProvider` contract for add-ons that expose exact BigInteger inventory amounts.
- Added the same `api.contract` capability negotiation API to the Forge 1.20.1 build.

## Fixed

- Small crafting requests that fit in one configured wave are no longer reduced to the cold-start probe size.
- Preserved AE2 Pattern Provider face round-robin behavior for normal-sized requests.
- External BigInteger consumers can receive exact non-simulation BigCapacity plans whose total bytes exceed `long`.
- Unrelated Pattern Provider updates no longer invalidate exact plans when all referenced patterns still exist unchanged.
- Reworked BigInteger sidecar copying to avoid the Java 25 C2 crash path while preserving exact visible amounts.
- Missing wide-plan execution backing is now reported as `INCOMPLETE_PLAN`, not the misleading capacity error `CPU_TOO_SMALL`.

## Compatibility

- Forge 1.20.1 / Java 17 or newer / AE2 15.4.x.
- No recipe, storage-content, or external CPU execution behavior is replaced by this update.
