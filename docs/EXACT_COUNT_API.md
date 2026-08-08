# Exact Count API

ACO exposes the `com.syaru.ae2craftingoptimizer.api.contract` package as the only boundary that
future AQE and AAC integrations should use for exact-count data.

## One finite contract

`ExactCountLimits.defaults()` is the shared boundary:

| Field | Default |
| --- | ---: |
| maximum count bits | 1,048,576 |
| maximum canonical count bytes | 131,072 |
| maximum keys per payload | 65,536 |
| maximum encoded payload bytes | 16 MiB |
| maximum identifier length | 256 UTF-8 bytes |
| maximum digest length | 128 bytes |

The count and canonical-byte limits are deliberately matched: the largest accepted count can be
encoded and saved without narrowing at a later stage. `longValue()` is never used as an implicit
conversion. Callers must use `toLongExact` or `toIntExact` when a legacy narrow boundary is
explicitly required.

## Canonical persistence

`CanonicalBigIntegerCodec` stores non-negative quantities as a minimal unsigned magnitude:

- zero is exactly one byte, `00`;
- positive values contain no leading zero byte;
- negative, non-canonical, oversized, truncated, and unknown-schema values are rejected;
- NBT schema `1` stores `<key>_schema` and `<key>_bytes`;
- the old decimal string at `<key>` is read only as a validated migration source and is converted
  to canonical fields on the next write.

`ExactCountPayloadCodec` uses the same limits and deterministic ordering for all five owners:
`REQUEST`, `VECTOR_PLAN`, `HOST`, `JOURNAL`, and `RECEIPT`. Payload keys are sorted lexicographically,
duplicate keys and trailing bytes are rejected, and the payload is re-encoded after decoding to
prove canonical equality.

## Integration handshake

`IntegrationCapabilitiesRegistry.initializeOnce` publishes one immutable startup snapshot. Optional
mods read it with `snapshot()` or `peek()` and must fail closed when the required API version or
feature is absent. ACO currently advertises no unimplemented feature as supported; later issues
will enable bits only when their owning implementation and persistence tests are complete.

The feature enum reserves contracts for atomic host snapshots, explicit host registration, receipt
slot reservation, live transaction proof, revision wakeup, quarantined thread state, and exact
storage journals.

## Receipts, proofs, and revisions

`LiveTransactionProof.UNKNOWN` is never an orphan proof. Only `ABSENT_CONFIRMED`, after the
authoritative registries were checked at one revision, can pass `ReceiptOrphanPolicy.mayForget`.

`ReceiptReservationProtocol` defines idempotent `reserve`, `commitRunning`, `markOutputReady`,
`cancelReservation`, `acknowledge`, `forget`, and `quarantine` transitions. Reusing a transaction
ID with a different digest is rejected.

`RevisionWakeupApi` is only a wakeup optimization. Registrations return an `AutoCloseable` handle,
listener references are weak, and durable receipts remain the completion proof. The
`CraftingTableBatchSnapshot` output map is immutable, carries an explicit state, and is accepted
by `SnapshotRevisionTracker` only in monotonic revision order.

No ACO issue in this change alters physical crafting order, machine energy, storage ownership, or
AE2's final crafting result. The actual AQE capacity ledger and AAC terminal ledger remain future
issues.
