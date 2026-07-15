# Publishing

This project is self-contained and ready to be placed in a GitHub repository as source code.

## Recommended Repository

Use one repository for this mod:

```text
ae2-crafting-optimizer
```

Do not commit generated Gradle output, built jars, or local runtime folders.

Ignored by `.gitignore`:

- `.gradle/`
- `build/`
- `run/`
- `run-server/`
- IDE metadata

## Reproducible Build

The build does not read a Prism Launcher instance or a local `mods` directory.

Dependencies are resolved from public repositories:

- Forge: Forge Maven
- Applied Energistics 2 `15.4.10`: ModMaven (`appeng:appliedenergistics2-forge:15.4.10`)

GitHub Actions executes `./gradlew clean build` on Java 17 and uploads the generated jar as a workflow artifact.

## First Push

From this project directory:

```bat
git init
git add .
git commit -m "Initial source import"
git branch -M main
git remote add origin https://github.com/syarukasu/ae2-crafting-optimizer.git
git push -u origin main
```

## Release Checklist

1. Run `gradlew.bat clean build` on Java 17.
2. Complete the checks in `docs/TESTING.md` on the pinned AE2 version.
3. Confirm `git status --short` contains no generated output or local config.
4. Confirm the jar metadata reports the intended version.
5. Confirm `mods.toml`, `README.md`, and `LICENSE` all report `LGPL-3.0-only` / LGPL v3.
6. Confirm the server and client use the exact same jar hash.
7. Tag the commit, for example `v1.0.0`.
8. Attach only `build/libs/ae2-crafting-optimizer-<version>.jar` to the GitHub release.
9. Use `docs/RELEASE_NOTES_1.0.0.md` as the release description.

Do not publish `.gradle`, `build`, `run`, world configs, logs, crash reports, or jars copied from dependency mods.

Do not report issues caused by this optimizer directly to AE2 without reproducing them without this mod first.
