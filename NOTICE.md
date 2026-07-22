# Notices and Design References

AE2 Crafting Optimizer (ACO)

Copyright (c) 2026 syaru

SPDX-License-Identifier: LGPL-3.0-only

## Applied Energistics 2

ACO is an independent Forge add-on that compiles against and interacts with Applied Energistics 2. AE2 remains a separately installed dependency and is not redistributed by this repository.

- Project: Applied Energistics 2
- Source: https://github.com/AppliedEnergistics/Applied-Energistics-2
- Referenced branch: `forge/1.20.1`
- License: GNU Lesser General Public License v3.0

## AE2-UEL / GTNH Design Research

ACO's design research used public behavior, issue discussions, and optimization ideas from the 1.12.x AE2-UEL/GTNH ecosystem, including invalidation-driven caching and intent-oriented automation concepts.

- Project: AE2-UEL Applied Energistics 2
- Source: https://github.com/AE2-UEL/Applied-Energistics-2
- Referenced branch: `rv6-1.12`
- License: GNU Lesser General Public License v3.0

Specific public design references include:

- AE2 1.20.1 crafting CPU guide: https://guide.appliedenergistics.org/1.20.1/items-blocks-machines/crafting_cpu_multiblock
- AE2 1.20.1 autocrafting guide: https://guide.appliedenergistics.org/1.20.1/ae2-mechanics/autocrafting
- AE2 performance issue `#7884`: https://github.com/AppliedEnergistics/Applied-Energistics-2/issues/7884
- AE2 terminal synchronization issue `#2363`: https://github.com/AppliedEnergistics/Applied-Energistics-2/issues/2363
- GTNH AE2 feature proposal `#11349`: https://github.com/GTNewHorizons/GT-New-Horizons-Modpack/issues/11349
- AE2 Fluid Crafting Rework: https://github.com/AE2-UEL/AE2FluidCraft-Rework

ACO is not a port or fork of AE2-UEL. No AE2 or AE2-UEL source files, textures, models, translations, or binaries are vendored in this repository. ACO contains independently implemented compatibility and optimization code for Forge 1.20.1 and falls back to the installed mods' original behavior when its optional fast paths cannot validate a candidate.

## AppliedE Compatibility Research

ACO 1.4.1 contains an independently implemented optional compatibility boundary
for AppliedE's dynamic EMC crafting patterns. No AppliedE source, asset, or
binary is copied or bundled.

- Original project: https://github.com/62832/AppliedE
- Source-reviewed release: `0.14.3`
- Performance fork: https://github.com/BOLTMAGIC/AppliedE
- Source-reviewed fork release: `0.14.7-fix2`
- Repository license at time of review: GNU Lesser General Public License v3.0

Both implementations retain ownership of EMC accounting, player knowledge,
temporary transmutation-pattern creation, output insertion, and lifecycle
cleanup. ACO only recognizes their shared public class boundary and returns
those patterns to AE2's original crafting planner.

## Neo ECO AE Extension

ACO 1.1.1 includes an independently implemented optional compatibility Mixin for Neo ECO AE Extension 20.3.x. The published 20.3.0 artifact is used only as a `compileOnly` target to validate class and method signatures. It is not bundled or redistributed by ACO.

- Project: Neo ECO AE Extension
- Source: https://github.com/DancingSnow0517/NeoECOAEExtension
- Referenced release: `20.3.0`
- CurseForge project/file: `1460639` / `8438819`
- License: GNU General Public License v3.0

No Neo ECO source file, binary, texture, model, or translation is copied into this repository. ACO calls no Neo ECO implementation method itself; its optional Mixin only bounds existing tick-limit return values and measures the existing execution method.

All product names and project names remain the property of their respective owners. Third-party projects retain their own licenses and copyright notices.
