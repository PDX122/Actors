# Record takes

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

## What a take remembers

Body and held items, combat (measured strikes, health, effects, arrows),
world edits (blocks with drops and sounds, mining overlays, block-entity data,
containers, vehicle rides, performer-caused sounds), and optional voice.

## Sharing takes

`/actor bundle export` writes every take plus gear and emote slices into one
`<replay>.fbactors.zip` beside the replay file; `/actor bundle import` (or just
opening a replay whose bundle sits beside it) brings the cast back.

## Take format versions

The format is append-only: newer builds read older takes, older builds refuse
newer ones at the version check. Current version: **v28** (sound cues), v27
added rides. Full history lives in [CHANGELOG.md](https://github.com/PDX122/Actors/blob/main/CHANGELOG.md).
