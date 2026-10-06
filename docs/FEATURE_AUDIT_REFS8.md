# refs.8 feature audit (2026-10-06)

Static call-path audit of the Forge 1.20.1 snapshot. Presence of a class or config
key is not evidence that its Mixin runs, or that production performance improved.
NeoForge rc.5 is an older implementation and must not inherit this feature list.

| Previously described feature | refs.8 status | Source/qualification |
| --- | --- | --- |
| Deduplicate identical running plans | Inactive in bundled VM route | `AcoMixinPlugin.NATIVE_VM_EXCLUDED` disables `CraftingServiceCalculationDeduplicationMixin`; NativeVm creates independent order tasks. |
| Reuse Pattern search by generation | Not the normal VM planning policy | Old generation-cache/memo classes remain, but VM uses order-local producer/input observations. Transactional execution metadata has its own generation cache. |
| Compile deterministic Pattern graph for AQE | Replaced/qualified | The VM compiles demanded patterns and eligible shared demand coefficients, including exact-result integration. This is not the old AQE-only global graph planner. |
| Calculate in long, promote on overflow | Not the VM arithmetic policy | VM quantities are BigInteger throughout. Legacy overflow-promoting classes do not describe the active calculator. Exact long conversion is an eligible result-interface boundary. |
| Limit normal CPU/Grid dispatch each tick | Removed | `CraftingExecutionBudget` is pass-through for ordinary execution; native VM excludes the CPU budget Mixin. Exact-owned transactional step budgets remain separate. |
| Prevent every giant CPU from occupying a tick | Not guaranteed | Ordinary AE2/add-on execution owns throughput. ACO-owned exact job budgets do not establish a universal tick-time bound. |
| Leave CPU capacity/displayed coprocessors unchanged | Preserved ownership | ACO does not resize external CPUs or their displayed coprocessors. Plan byte calculation is still ACO/VM work and has correctness fixes. |
| GTCEu/Mekanism recipe intent | Implemented, restricted | GTCEu unique candidates with native validation/fallback; Mekanism item-to-item electric-machine recipes only. This does not bypass power/time or cover every machine. |
| Advanced AE Reaction Chamber recipe reuse | Implemented hook | `AdvancedAeReactionChamberRecipeCacheMixin` seeds native `cachedTask` after lookup; incompatible class layout disables this optimization. |
| ExtendedAE Circuit Cutter / Assembler Matrix | Implemented hooks | Validated recipe reuse, thread-count cache and status-update coalescing. Optional target signatures/configuration govern activation. |
| AE2 Overclock reflection/card counts | Implemented hooks | Reflection/member/MethodHandle reuse and card count caches target the matching Overclock runtime classes. |
| Heavy calculation diagnostics | Active VM diagnostics | NativeVm/NativeVmDiagnostics provide bounded summaries and `/aco vm recent`; old calculation diagnostics Mixin is disabled with native VM. |
| Pattern Access Terminal Page Up/Down guard | Not found in current runtime | No matching client Mixin or event handler is registered in refs.8. Do not advertise it. |

The public BigInteger API and physical execution ownership are retained. Neither
this static audit nor local unit/build results prove live lifecycle acceptance.
