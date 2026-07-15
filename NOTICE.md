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

ACO is not a port or fork of AE2-UEL. No AE2 or AE2-UEL source files, textures, models, translations, or binaries are vendored in this repository. ACO contains independently implemented compatibility and optimization code for Forge 1.20.1 and falls back to the installed mods' original behavior when its optional fast paths cannot validate a candidate.

All product names and project names remain the property of their respective owners. Third-party projects retain their own licenses and copyright notices.
