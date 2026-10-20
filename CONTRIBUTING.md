# Contributing to Flashback Actors

## Getting set up

You need JDK 21 and JDK 25, plus the local Flashback jars: `libs/` for
compiling and one per target in `versions/<mc>/run/mods/` for game tests.
Those jars are built from your own Flashback install and are proprietary, so
they never enter git. Without them you can still run the static gate below,
but not compilation or tests.

```bash
./gradlew verifyMixinSignatures   # fast static gate, no game boot
./gradlew :versions:1.21.11:test --offline   # headless unit tests
```

## Rules

- One file, one responsibility; keep files clean. Public APIs on shared types
  stay frozen within a change: split logic out, delegate from the old name.
- Shared source (`src/main/java`) stays version-blind and must compile against
  all nine targets. No version-specific tokens; `var` where id types renamed;
  `Compat` shims where signatures moved. Per-target differences go in
  `compat/`, with all nine implementations kept behavior-identical.
- Prose rules live in `docs/prose-style.md`: explain the why once, vary the
  cadence, numbers over adjectives, no em dashes.
- Every feature lands with headless tests (`src/test/java`).
- The take format is append-only: new sections go last in `TakeIO` with a
  version bump, gated reads, and defaults so older files load. Update
  `CHANGELOG.md` in the same change.

## Adding a Minecraft version

1. Add `versions/<mc>/` (copy the nearest target's layout) with its
   `gradle.properties` (`mc_version`, `compat`, `flashback_jar`).
2. Add the compat dir under `compat/` if its APIs differ, or reuse an existing
   one via the `compat` property.
3. Add rows to the versions table in `README.md` and to `EXPECTED` in
   `tools/verify_mixins.py`.
4. Run `./gradlew verifyMixinSignatures` and the unit tests before opening the PR.

## Game testing

```bash
powershell -ExecutionPolicy Bypass -File tools/smoke_all.ps1 -JoinWorld
```

Boots every dev client with Flashback, joins a world, verdicts each from its
game log, writes `build/smoke-report.md`. Scope with `-Versions`, budget with
`-TimeoutSec`. For manual review there is a matching `smoke_open.ps1` /
`smoke_close.ps1` pair (see README).

## Pull requests

CI runs the static checks (mixin tables, JSON validity, tooling syntax). Full
builds and game tests run on release hardware. Describe the behavior change,
not the diff. Issues and ideas belong in the
[tracker](https://github.com/PDX122/Actors/issues).
