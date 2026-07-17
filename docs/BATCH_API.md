# Transactional Pattern Batch API

ACO 1.2.0 exposes an adapter registry for processing-pattern batching without redefining AE2's crafting result.

## Contract

`PatternBatchAdapter.commit(context, budget)` returns a `PatternBatchResult`.

- `acceptedExecutions == 0` means the adapter changed nothing and retained no input.
- `acceptedExecutions == N` means the adapter durably accepted exactly N complete executions.
- N must not exceed `maximumExecutions`.
- Inventory insertion simulation, partial insertion, or temporary capacity is not a commit receipt.
- The adapter does not mutate AE2 task progress, expected outputs, or energy. ACO applies those from the validated receipt.
- An adapter must not throw after transferring ownership. If an external system cannot provide that guarantee, it must use the conservative adapter behavior.
- `limitExecutions` may narrow ACO's offer before extraction. Use it for inexpensive queue or implementation limits so unused aggregate inputs are not extracted and returned.
- `PatternBatchBudget` carries the maximum execution count and the CPU call's hard deadline. Iterative adapters must check it between machine-facing operations.

ACO offers only exact processing patterns to the API: one concrete key per input, no substitution, no returned container, no provider lock, and an allowed target namespace.

## Registration

```java
PatternBatchApi.register(new MyNativeBatchAdapter());
```

Adapter IDs must be unique. Higher `priority()` values are considered first. Native adapters default to deterministic single-target contexts; override `supportsMultipleProviderTargets()` only when the adapter preserves the provider's own routing semantics.

```java
public final class MyNativeBatchAdapter implements PatternBatchAdapter {
    @Override
    public ResourceLocation id() {
        return new ResourceLocation("example", "native_machine_batch");
    }

    @Override
    public int priority() {
        return 100;
    }

    @Override
    public boolean supports(PatternBatchContext context) {
        return context.target() instanceof MyMachineBlockEntity;
    }

    @Override
    public long limitExecutions(PatternBatchContext context, long offeredExecutions) {
        return Math.min(offeredExecutions, machine(context).availableBatchSlots());
    }

    @Override
    public PatternBatchResult commit(PatternBatchContext context, PatternBatchBudget budget) {
        long accepted = machine(context).durablyAcceptExecutions(
                context.pattern(),
                context.copyInputsPerExecution(),
                budget.maximumExecutions());
        return PatternBatchResult.accepted(accepted);
    }
}
```

The example machine method is intentionally a strong API boundary. It must enqueue complete execution records or start native parallel work atomically and return the exact accepted count. It must not infer acceptance from a Forge item/fluid/chemical insertion simulation alone.

## Built-in Adapter

`ae2_crafting_optimizer:sequential_pattern_provider` is always registered and is enabled through server config. It combines exact CPU input extraction and bookkeeping, then calls AE2's original `pushPattern` once per execution. It checks provider backpressure before every call and reports only successful calls.

When instant dispatch is enabled, ACO may execute multiple adapter transactions and visit multiple ready tasks in one CPU call. It stops at the CPU operation limit, transaction limit, or wall-clock deadline. This accelerates dispatch only; adjacent machines retain their normal duration, energy, recipe, and output behavior.

This adapter is the compatibility baseline. It does not claim O(1) machine submission. GTCEu or Mekanism native adapters should be enabled only after their machine-side queue or parallel recipe API satisfies the contract above.
