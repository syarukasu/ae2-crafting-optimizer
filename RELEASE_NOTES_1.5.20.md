# AE2 Crafting Optimizer 1.5.20

## Added

- Added the public `ExactStorageAmountProvider` contract for add-ons that expose exact BigInteger inventory amounts.

## Fixed

- Small crafting requests that fit in one configured wave are no longer reduced to the cold-start probe size.
- Preserved AE2 Pattern Provider face round-robin behavior for normal-sized requests.
- External BigInteger consumers can receive exact non-simulation BigCapacity plans whose total bytes exceed `long`.
- Unrelated Pattern Provider updates no longer invalidate exact plans when all referenced patterns still exist unchanged.
- Reworked BigInteger sidecar copying to avoid the Java 25 C2 crash path while preserving exact visible amounts.
- Provider generation changes are now published after AE2 completes provider
  refresh, add, and remove mutations.
- Lazy root-program and strict-topology compilation verifies the provider and
  recipe generations before accepting its result.
- Failed root-program compilation is no longer negatively cached. A temporary
  `canEmitFor` mismatch can therefore recover on the next attempt.
- `NO_COMPILED_PROGRAM` is recorded as its own planner diagnostic instead of
  being folded into `AMBIGUOUS_PRODUCER`.
- A wide plan that lacks exact BigInteger execution backing now returns
  `INCOMPLETE_PLAN`, records `SUBMISSION_BACKING_MISSING`, and emits a one-time
  warning. It is no longer misreported as the capacity error `CPU_TOO_SMALL`.

## Compatibility

- NeoForge 1.21.1 / Java 21 / AE2 19.2.17.
- Forge 1.20.1 compatibility is released from the matching maintenance branch.
- No recipe, storage-content, or external CPU execution behavior is replaced by this update.
