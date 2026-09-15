# Industrial recipe diagnostics (Issue #190)

This is opt-in **test tooling**, not a mod runtime dependency. It does not encode
patterns into a live ME network. Never install capture.js on a production server.
Use an isolated fresh world; the script deliberately pauses that server while
serializing recipes and stops it after a successful capture.

## Prepare

Run prepare-capture.ps1 with explicit ServerRoot and a NEW Destination outside the
production directory. It copies mods, scripts, config, serverconfig and datapacks,
but not world chunks, inventories or players. Bukkit plugins are excluded to avoid
external integrations. This is a recorded difference from production, not a claim
of total environment identity. It binds localhost:25585 with an empty enforced
whitelist and disables query/RCON. Verify the port is free first.

The current launcher filename in the preparation script targets this pack's Arclight
1.20.1. Run the private copy only, with a suitable installed JDK and a bounded heap.
Do not use the production startup script for this copy, or change production files.
Use an interactive console so a failed capture can be stopped cleanly.

## Capture

- KubeJS 2001.6.5 build 26 and GTCEu 7.5.3 were inspected and used on Forge 1.20.1.
- The lowest-priority recipe listener retains RecipeJS references before KubeJS
  clears its collections. The first server tick after startup captures RecipeManager
  membership. Later runtime reloads require a new capture, not reuse of old files.
- GTCEu exports use the actual runtime CODEC with RegistryOps. Other recipe JSON
  is explicitly labeled provenance, not proof of dynamic behavior.
- Resolved item/fluid amounts are decimal strings, with NBT and ingredient classes.
  KubeJS ItemStackSet order is NOT ME input/producer priority.
- GT conditions, chances, tick inputs, outputs and nonconsumable catalysts remain
  in the captured data. Unsupported capabilities are never assumed deterministic.
- Output is sharded under industrial-capture; summary.json is the completion record.
  An in-progress marker and per-chunk capture ID prevent mixed-generation reads.
- A failed capture does not automatically halt the server. Correct the diagnostic
  script, reload in the private instance, then use
  `kubejs custom_command aco_industrial_capture`. Keep error logs.

## Audit and actual-core probe

```powershell
node --test tools/industrial-benchmark/audit.test.mjs
node tools/industrial-benchmark/audit.mjs <private-server>/industrial-capture
```

IndustrialCompileProbe.java runs against the built ACO engine classes and Gson,
using Java source-file mode. Arguments: normalized-recipes.json and an output JSON.
Classpath: build/classes/java/main plus the pack's Gson 2.10 JAR.

It separately records:

1. Actual CompiledCraftingGraph/CompiledRootProgram attempts on eligible captured
   candidates, one explicit root manufacturing route at a time. Unsupported
   recipes remain enumerated as omissions, not invented raw materials.
2. Twelve **one-machine-only** exact quantity checks: two captured creative circuit
   routes, amounts 1, 100, 9220000000000000000, Long.MAX_VALUE, Long.MAX_VALUE+1,
   and 10^64. This validates their captured quantities, NOT the full recipe tree.

The report deliberately says NOT_END_TO_END_ACCEPTED. Whole recipe coverage,
resolved producer choices, full dependency consumption, submit-ready CPU bytes,
concurrent reservations, tick costs, cancellation and restart remain separate gates.
Do not quote one-machine or graph-index timings as full crafting-plan latency.
Empty resolved vanilla ingredients are unsupported until vacant slots and empty
tags can be distinguished. Recipe-specific returned containers and damage changes
also require verification before any normalized row can become an executable plan.

## Privacy and evidence

Keep the private server, input manifests, full recipes and logs OUT of Git.
The manifest can contain local paths and pack-specific data. No archive, credentials,
world, inventory or third-party model assets belong in an Issue or PR.
Only publish sanitized counts, hashes, failure reasons and minimal regression cases.
Recompute the diagnostic script hash after edits; the preparation hash is not the
hash of a later edited script.
