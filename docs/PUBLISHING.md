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

ACO 1.6.0 targets NeoForge 1.21.1 and Java 21. Local builds read the exact
dependency JARs from `../../mods` by default. CI downloads the pinned public
artifacts into `.ci-mods` and passes that directory through
`acoLocalModsDir`, without committing or redistributing dependency JARs.

GitHub Actions executes `./gradlew clean build` on Java 21 and uploads the
generated JAR as a workflow artifact.

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

1. Run `gradlew.bat clean build --no-daemon` on Java 21.
2. Complete the checks in `docs/TESTING.md` on the pinned AE2 version.
3. Confirm `git status --short` contains no generated output or local config.
4. Confirm the jar metadata reports the intended version.
5. Confirm `neoforge.mods.toml`, `README.md`, and `LICENSE` all report `LGPL-3.0-only` / LGPL v3.
6. Confirm the server and client use the exact same jar hash.
7. Tag the commit, for example `v1.0.0`.
8. Attach only `build/libs/ae2-crafting-optimizer-<version>.jar` to the GitHub release.
9. Use the release-specific `RELEASE_NOTES_<version>.md` as the release description.

Do not publish `.gradle`, `build`, `run`, world configs, logs, crash reports, or jars copied from dependency mods.

Do not report issues caused by this optimizer directly to AE2 without reproducing them without this mod first.
