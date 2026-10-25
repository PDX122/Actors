# Changelog

Take-format versions are the compatibility contract: a take file records the
version that wrote it, newer builds read older files, and older builds refuse
newer files at the version check.

## 0.11.0 (in development)

- Mosaic replaces Tessera's seamed glass tiles with flat averaged blocks.
  Actor Mosaic samples the final lit image through a geometry depth mask,
  preserving the background and rejecting foreground occluders. The Block size
  control scales with render height. Normal and reversed depth are supported.

- Looks and Misc use the same slim clip height and row spacing as actor tracks,
  with a compact sliders icon and no tinted header strip.

- Looks library supports dragging thumbnails onto actor tracks or Misc.
  Actor looks grade private skin, armor and living-entity material copies;
  Misc looks grade the whole scene. Per-target clips retain fades, trimming,
  duplication and undo. Existing replay grades migrate into visible Misc clips.
- Replay thumbnails replace the test pattern and render at 256 pixels wide.
  The inspector identifies the target, and Misc mute does not mute actor looks.

- Looks tab with 32 original shaders, live scene thumbnails and a larger
  selected preview. Three-filter stacks, optical controls, grading,
  original-view comparison and saved presets. Settings save per replay.
  The imported shader collection is removed. No upscaling is included.
- Actor server movement packets checked every tick, with one native sender
  for movement and head rotation. Settings > Recording can restore normal cadence.
- Replay browser with live search, refresh, readable durations and adaptive rows.
- Studio sidebar with collapsible scene controls, full-width buttons and
  selection-aware timeline actions.
- Actor options grouped by Appearance, Timing, Smooth movement and Camera.
- Settings grouped into Recording, Audio, Interface and Advanced tabs.
- Undoable movement smoothing with separate look and horizontal path modes.
  Use the actor menu or `/actor smooth <look|path|all> <actor> <radius> <strength>`.
- Microphone picker, continuous voice threshold and resizable setup window.
  Closing setup cancels actor mode; closing settings saves preferences.
- Trim boundaries cannot cross, and extreme values no longer overflow.
- Bounded camera curves, fractional sampling and predictable duplicate keys.

## Earlier unreleased changes

- Take format v28: sound cues. Performer-caused `playSound` broadcasts are
  recorded (id, position, volume, pitch, source) and rebroadcast on playback;
  block breaks replay the vanilla destroy event for particles plus sound.
- Take format v27: ride transitions. Mounts and dismounts record the vehicle
  identity; clones re-board the same entity.
- Container recording enabled: slot, open, and close capture mixins registered;
  block-entity pre-break capture retargeted to `Level`.
- Chest playback: correct menu type per block entity, lid animation, open and
  close sounds across all nine targets.
- `Watermark` licence notice, logged once at startup.
- Public CI (static checks) and a self-hosted release workflow.

Full history: [CHANGELOG.md](https://github.com/PDX122/Actors/blob/main/CHANGELOG.md).
