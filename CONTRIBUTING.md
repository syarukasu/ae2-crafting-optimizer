# Contributing

## Before Opening an Issue

1. Reproduce on Minecraft 1.20.1, Forge 47.4.18+, and AE2 15.4.10.
2. Confirm the server and every client use the same ACO jar.
3. Reproduce once with the normal config and once with `enableOptimizer = false`.
4. Remove unrelated client-only visual mods when the failure is a menu or packet problem.
5. Do not report the issue upstream to AE2 or an integration mod until it also reproduces without ACO.

Attach `latest.log`, the relevant crash report, `ae2_crafting_optimizer-server.toml`, the exact mod list, and concise reproduction steps. Remove tokens, addresses, player IPs, and unrelated personal information before uploading logs.

## Building

Use Java 17:

```bat
gradlew.bat clean build
```

The build must succeed from a fresh clone without a local Minecraft instance. Dependencies come from public Maven repositories.

## Pull Requests

- Keep AE2 as the source of truth for recipes, craft validity, job submission, and storage mutation.
- Add a config switch for behavior that changes timing, ordering, or batching.
- Preserve an unmodified AE2 fallback when a fast path cannot prove that it is valid.
- Bound every cache and document its invalidation conditions.
- Avoid hard class references to optional mods from common initialization paths.
- Add or update manual checks in `docs/TESTING.md`.
- Include before/after timing or allocation evidence for performance changes.

Do not include dependency jars, decompiled third-party source, world files, runtime configs, or generated build output.
