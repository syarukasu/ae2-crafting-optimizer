# AE2-VM exact integration probe (#208)

This is **not a production integration or an exact crafting implementation**.
The real upstream compiler and interpreter are compiled in an isolated source
set. Nothing under vmProbe is packaged in ACO or installed into Minecraft.
The logging/bootstrap shims are headless-only; they do not mock the compiler,
arithmetic or interpreter. No upstream build scripts, deployment tasks or mixin
configuration are executed.

Reference: https://github.com/TaoLe-si/AE2-VM/tree/f9e083732ee654a99a1679bcdee7f864eb90be94
Branch: 1.20.1-forge. License: upstream LGPL-3.0; retain LICENSE and source
provenance before distributing any derived VM artifact. Current source is read
from a separate checkout, checked against normalized SHA-256 hashes, then copied
into build/vm-probe-upstream. The original checkout is never edited by the task.

The upstream license is preserved verbatim in LICENSE.upstream. The isolated
arithmetic patch was authored for ACO on 2026-09-22 and changes the upstream
CraftingVM source only; the upstream project and authors retain their credits.
The patch is provided under the same LGPL-3.0 terms as that source.

Run with Java 17:

```powershell
.\gradlew.bat vmProbeTest -Pae2VmSource=<checkout> --no-daemon
```

To reproduce unmodified upstream failures, add
`-PvmPatchExactStack=false -PvmRequireExact=true`. The initial two exact arithmetic
assertions fail. Omitting vmRequireExact in baseline mode tests that those known
defects reproduce, **not** that exact arithmetic works.

The local patch fixes arithmetic stack operations and rejects lossy legacy
conversions. It does not implement exact stock simulation, aggregation rollback,
byte accounting, caches or ICraftingPlan replacement. Those remain blockers to
production adoption and Jar-in-Jar activation. A strict successful probe proves
only its named arithmetic cases, not the creative-control-circuit stock matrix.
