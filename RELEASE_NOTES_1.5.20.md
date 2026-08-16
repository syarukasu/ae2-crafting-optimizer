# AE2 Crafting Optimizer 1.5.20

This hotfix resolves issue #103, where asynchronous planning could observe a
provider generation that had already advanced while AE2's crafting-provider
index was still on the previous state.

## Fixed

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
