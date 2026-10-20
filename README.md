# Flashback Actors

<p align="center">
  <img src="src/main/resources/assets/fbactors/logo.png" alt="Flashback Actors logo" width="128"/>
</p>

[![Minecraft 1.21 to 26.3](https://img.shields.io/badge/minecraft-1.21%E2%80%9326.3-3b8526)](https://github.com/PDX122/Actors)
[![Fabric](https://img.shields.io/badge/loader-fabric-dbd8c3)](https://fabricmc.net)
[![License MIT](https://img.shields.io/badge/license-MIT-lightgrey)](LICENSE)
[![Requires Flashback](https://img.shields.io/badge/requires-Flashback-orange)](https://modrinth.com/mod/flashback)
[![Docs](https://img.shields.io/badge/docs-site-blue)](https://pdx122.github.io/Actors/)

Full guides live on the [docs site](https://pdx122.github.io/Actors/): install
walkthrough, take recording, take format versions, and changelog.

A Fabric addon for [Flashback](https://modrinth.com/mod/flashback) that records
**actors** inside a replay. Enter actor mode, perform a take, stop: the performance
plays back on the timeline as a clone, and you act against it on the next take.
Chases and fights are just multiple passes. Requires Flashback 0.39+ (0.42+ on 26.x).

## Downloads

Prebuilt jars are on
[GitHub releases](https://github.com/PDX122/Actors/releases), one per Minecraft
family. Match the jar to your game version with the table below.

## Installation

1. Install [Fabric Loader](https://fabricmc.net/use/) 0.16+ and
   [Fabric API](https://modrinth.com/mod/fabric-api) for your game version.
2. Install [<img src="docs/assets/modrinth.svg" width="14" height="14" alt="Modrinth"> Flashback](https://modrinth.com/mod/flashback) (0.39+, 0.42+ on 26.x).
3. Drop the matching Flashback Actors jar into your `mods` folder.
4. Java 21 runs the 1.21.x jars; the 26.x jars need Java 25.

## Getting help

For setup trouble and crashes, open an issue with your game version, mod
version, and the relevant log lines:
[issue tracker](https://github.com/PDX122/Actors/issues).

## How it works

Inside a replay you are already an ordinary `ServerPlayer` on a real server, and
Flashback runs playback as a genuine `IntegratedServer` without intercepting
movement or interaction packets. So there is no puppet rig: actor mode samples
your own pose once per server tick.

Two constraints shape the design:

- **Scrubbing does not rewind.** Flashback jumps to the nearest snapshot and
  fast-forwards, clearing the level on the way. A take is a lookup table indexed
  by tick, never a simulation, and clones respawn constantly. Export scrubs too,
  which is why this matters for rendered output, not just the editor.
- **Recorded entities are packet-driven.** Real damage to a clone is overwritten
  on the next tick and lost on the next scrub. Hits are stored as events and
  replayed as hurt animation: authored, idempotent, correct at any timeline point.

## Record a take

1. Open a replay in Flashback and scrub to where the actor enters.
2. Press **R** (or the actor track's *New actor take* row). The editor hides
   itself, playback rolls after a 3-2-1 countdown, and you perform in survival.
3. Perform. Breaking and placing blocks is real; each edit is recorded against
   its tick. (Editing is refused during setup, when the level is still scrubbing.)
4. Press **R** to stop. You return to spectator and the playhead jumps back to
   where the take started.

Takes live per replay under `flashback/fbactors/<replay-uuid>/<take-uuid>.take`.
Nothing is written into the `.flashback` file, so replays open normally without
this mod.

## What actors remember

- **Body**: position, rotation, pose, sneak/sprint/ground/elytra, swings, held
  items, hotbar slot, item use (bow draw, shield, eating), attack charge.
- **Combat**: strikes with measured damage, direction, knockback impulse and
  crit flags; health and absorption; mob effects; arrows with measured velocity.
- **World**: block breaks and placements with drops and sounds, mining crack
  overlays, block-entity data (sign text, chest contents, banners), container
  open/close/slot changes, vehicle mounts and dismounts (boats, minecarts,
  horses), and every performer-caused sound, replayed at its position and volume.
- **Voice**: optional per-actor microphone capture with activity gating.

Earlier actors play back while you record the next one. Solo isolates one
performance; per-actor timing, trims, colour, speed, visibility, hide ranges,
dialogue colour and the Director shot list persist in the take file.

## Dressing the cast

Looks are stored beside the take and resolved per tick, so none of this
re-records anything:

- **Emotes**: 24 keyframed built-ins plus drop-in `.json` customs, with
  cross-fading clips on a per-actor track.
- **Gear**: per-slot replacements with 18 loadout presets, first-class
  enchantments, and full-stack SNBT snapshots via `/actor gear copy`.
- **Morphs**: 73 built-in bodies mapped onto vanilla poses, filtered to what the
  renderer can actually show.
- **Skins**: any player, UUID, or PNG on disk, with a named library and aliases.

## Directing

- A Director with one-click presets (orbit, follow, over-shoulder, drone, crane,
  push in), pinnable framing, recorded players as subjects, and a seed covering
  every choice so regeneration is exact.
- A Shots window: explicit shot list plus a five-feed monitor wall.
- Timeline ops: razor, camera keyframe copy/paste, ripple-delete,
  multi-select, full undo/redo, snapping, and per-track lock.
- A Station sidebar replacing the Visuals panel with Scene + Edit tabs.

## Sharing takes

`/actor bundle export` writes every take plus gear and emote slices into one
`<replay>.fbactors.zip` beside the replay file; `/actor bundle import` (or just
opening a replay whose bundle sits beside it) brings the cast back. This is how
takes travel to collaborators. Baking actors into the replay as vanilla packets
(no mod needed to view, frozen takes) is not built yet.

Block edits change the shared replay world, so what the original recorded
players stand next to can differ from the footage. That is inherent to editing a
world the recording shares.

## Versions

One jar per Minecraft family, covering every version Flashback supports:

| Minecraft | Flashback | Java | Build |
|---|---|---|---|
| 1.21, 1.21.1 | 0.39.7 | 21 | `:versions:1.21.1` |
| 1.21.4 | 0.39.7 | 21 | `:versions:1.21.4` |
| 1.21.5 | 0.39.7 | 21 | `:versions:1.21.5` |
| 1.21.6 to 1.21.8 | 0.39.7 | 21 | `:versions:1.21.8` |
| 1.21.9, 1.21.10 | 0.39.7 | 21 | `:versions:1.21.10` |
| 1.21.11 | 0.39.7 | 21 | `:versions:1.21.11` |
| 26.1 to 26.1.2 | 0.42.1 | 25 | `:versions:26.1` |
| 26.2 | 0.43.2 | 25 | `:versions:26.2` |
| 26.3 | 0.43.6 | 25 | `:versions:26.3` |

## Building

```bash
./gradlew buildAll        # verify mixins, build all nine, collect jars in build/dist/
./gradlew :versions:1.21.11:runClient   # game-test one version with Flashback present
```

Each `runClient` needs its matching Flashback jar in `versions/<mc>/run/mods/`.
Four 26.x gotchas: the Gradle daemon must run on JDK 25
(`org.gradle.java.home`), 26.x ships deobfuscated (identity intermediary, no
Mojang mappings), 26.x uses plain `implementation` instead of
`modImplementation`, and Flashback is compiled against a locally slimmed stub
jar in `libs/` that is not redistributable. Public CI fetches the same pinned
versions from Modrinth at build time instead of checking jars in. Everything
version-specific lives
in `compat/Compat.java` per target; shared source stays version-blind. See
[CONTRIBUTING.md](CONTRIBUTING.md) for the full rules.

## Related work

The idea of an actor in Flashback is not new: see
[Flashback Studio](https://modrinth.com/mod/flashback-studio) and
[Flashback Actor Replay](https://modrinth.com/mod/flashback-actor-replay).

## Licence

MIT, see [LICENSE](LICENSE). Flashback itself is proprietary ("all rights
reserved, do not redistribute"); this repo contains none of its code, and the
locally generated `libs/` stub jars must not be redistributed.
