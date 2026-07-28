# Feature Ownership

## ACO Owns

- Provider/recipe generation tracking
- Compiled deterministic Pattern graphs
- `long` and `BigInteger` planning
- Exact boundary-input and final-output formulas
- Parent CPU capacity reservation and job progress
- Transaction-local crafting escrow
- Exact ME storage before/after reconciliation
- Cancellation, recovery, quarantine, and fallback decisions
- Per-CPU and per-grid TPS budgets
- BigInteger NBT and status protocol

ACO never asks AAC to decide whether a parent job is complete.

## AAC Owns

- AAC block and multiblock registration
- Neo ECO structure integration
- Pattern Bus to Worker routing
- One real `assemble` proof for each accepted crafting-table step
- Neo ECO Thread progress and power
- Worker-local live ownership
- Durable terminal output receipts
- Physical Thread cancellation before output is complete

AAC does not compile the crafting tree, reserve ME storage, complete the AQE
parent job, or generate the tree's final output.

## AE2 Owns

- Encoded Pattern identity
- Normal crafting jobs and recipe eligibility
- Standard CPU inventories and links
- Normal Provider routing and machine work
- Standard insertion/extraction when no exact sidecar path is active

## Neo ECO Owns

- Pattern Bus, Worker, and Thread lifecycle
- Structure formation and cluster lists
- Physical progress and power consumption
- Physical Thread NBT

AAC subclasses and narrowly extends these components. It does not replace the
Neo ECO cluster implementation.

## AQE and Advanced AE

AQE is an optional BigInteger host integration for ACO. Advanced AE continues
to own its Quantum Computer structure and active CPU implementation.

AAC does not require AQE at runtime for its execution code. AQE-dependent AAC
progression recipes are Forge-conditional and load only when AQE is installed.

## Fallback Boundary

Before boundary input ownership moves, an unsupported or unavailable physical
path may return to AE2's normal execution.

After ownership moves, fallback is forbidden. The transaction must:

1. resume from its receipts;
2. cancel and return exact escrow; or
3. quarantine when ownership cannot be proven.

This boundary prevents a normal fallback from executing the same work a second
time.
