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
feature is absent. ACO advertises `HOST_ATOMIC_SNAPSHOT` and
`EXPLICIT_HOST_REGISTRATION` only after their owning implementation and lifecycle tests are
complete. Receipt journaling, live transaction proof, and other reserved features remain disabled
until their owning implementations are merged.

The feature enum reserves contracts for atomic host snapshots, explicit host registration, receipt
slot reservation, live transaction proof, revision wakeup, quarantined thread state, and exact
storage journals.

## Exact storage amounts

Storage add-ons that keep counts beyond signed `long` may implement
`ExactStorageAmountProvider`. ACO copies the returned `Map<AEKey, BigInteger>` and accepts it only
when every key exposed by the normal AE2 `MEStorage` facade is present with a positive exact amount.
Counts and key cardinality are validated with `ExactCountLimits`; invalid or incomplete providers
fall back to an incomplete long facade and are never treated as authoritative exact inventory.

Add-ons can negotiate this boundary through
`SupportedFeature.EXACT_STORAGE_AMOUNT_PROVIDER`. The older
`ExtendedAePlusBigIntegerCellInventoryAccess` remains an internal compatibility adapter and must not
be implemented by new integrations.

## Big Crafting Host lifecycle

`BigCraftingHostRegistry.register(owner, runtime)` returns a
`BigCraftingHostRegistration`. The handle carries the owner identity, runtime UUID, and
monotonically increasing generation. `close()` is idempotent; an old handle cannot remove a newer
registration for the same owner. `unregister`, server stop, and registry clear close the runtime
and release the strong owner reference.

The existing AQE bridge must call `unregister` on cluster destroy, break, reform, or unload. A
controller must cancel pending futures, return only work that has not been physically accepted, and
quarantine uncertain ownership before it is released. `BigCraftingHostRuntime.close()` therefore
stops new admission but does not delete durable jobs or physical ownership.

`BigCraftingHostRegistration.snapshot(revision, backendState)` obtains one monitor-consistent
`BigCraftingHostSnapshot`. Its `available` field is derived from `physicalCapacity - reserved` and
is clamped to zero when `overcommitted` is true.

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
