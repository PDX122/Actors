# Sharing and storage

Actors data is stored beside the replay's working data, not baked into the original recording. Keep backups of the replay and its actor data together.

## Share a cast

`/actor bundle export` writes a `<replay>.fbactors.zip` bundle beside the replay. Import it with `/actor bundle import`, or open a replay with its bundle beside it.

The sharing path carries takes and supported appearance slices. Keep a backup before importing into an existing project. Verify imported actors and appearance libraries before rendering.

## Local files

| Data | Location |
|---|---|
| Per-replay actor data | `flashback/fbactors/<replay-uuid>/` |
| Imported custom model assets | `flashback/fbactors/morphs/models/` |
| Imported shader library | `flashback/fbactors/shaders/library.json` |

Modded morphs still require their source mod on the receiving installation. Keep custom asset dependencies available and confirm them after moving the project.

## Compatibility

Take formats are versioned. Newer builds can read supported older files; older builds refuse files written with newer formats. Use the [changelog](changelog.md) for format history.

Baking the cast into vanilla replay packets for viewers without Actors is not implemented. A bundle shares an editable cast; it is not a baked replay.
