# Changelog

Take-format versions are the compatibility contract: a take file records the
version that wrote it, newer builds read older files (migrating where noted),
and older builds refuse newer files at the version check.

## 0.10.11

- First release built entirely in public CI (Modrinth-fetched dependencies,
  all nine targets, both test families). No code changes over 0.10.10.

## 0.11.0 (in development)

- Fixed actor Mosaic on 26.3 by recognizing RenderPearl's renamed texture
  samplers and world transparency targets sharing the scene depth buffer.

- Selected morph previews now animate with pause, pose selection and automatic
  cycling. Added native AnimationState channels, growth stages, golem damage,
  sheep wool/shearing, slime sizes and sitting poses where supported. Only the
  selected 128px model updates, capped at 30 fps with its renderer reused.
- Morphed walking now preserves previous/current stride interpolation and eases
  starts and stops. Per-actor phase stays continuous; teleports and invalid
  positions reset without a lunge.

- Morphs now uses a compact searchable list. Clicking a mob renders its actual
  3D inventory model in the preview pane; Apply changes the actor. Tuning has
  its own tab. Cached previews render only on selection and are released when
  the picker closes or its level changes. Unsupported models show a fallback.

- Expanded Looks from 32 to 44 original shaders. Added Cel Shade, Halftone,
  Crosshatch, Risograph, Night Vision, Infrared, Duotone, Solarize, Arcade,
  Red Accent, Neon Wire and Anamorphic, with individual tuning controls.
  Strengthened eight existing grades and rebuilt every static photo preview.

- Removed the Actor options button from actor rows. Effects use compact outlined
  bars with aligned labels and trim handles. Right click an effect to edit,
  duplicate, delete it or delete its entire effect track, with undo and lock checks.

- Looks uses a bundled real CC0 landscape photo with offline baked PNG previews.
  The gallery no longer captures the replay or repeatedly renders all shaders.
  Edit Looks separates Look, Timing, Grade and Presets into compact tabs.

- Actor Mosaic measures the mask on the GPU and scales its blocks with actor
  height on screen, retaining visible pixelation at a distance. Its grid follows
  the actor, and the background and foreground occluders remain untouched.

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

- Looks tab: 32 original Actors shaders replace the imported collection.
  Live scene thumbnails and a larger selected preview update twice per second.
  Browsing leaves the current look unchanged until a drop or inspector action.
  Stack three effects, adjust optics and grading, compare the original view,
  and save named presets. No upscaling is included.
- Looks save per replay. Animation follows replay time; comparison is preview
  only. Shaders compile once per selection and use the native render size.
  OpenGL backends are supported; other backends leave the scene unchanged.
- Shader GPU checks compile and render every filter, verify finite pixels,
  preserve alpha, and check render-state restoration and zero-intensity bypass.
- Actor movement uses Minecraft's server packet tracker every tick by default.
  Position and rotation keep the native relative/absolute encoding, teleport,
  velocity and passenger handling; the extra head sender is disabled in this
  mode. Other entities keep their normal cadence. A Recording setting restores
  the original cadence without restarting.
- Replay browser: live name search, refresh, adaptive row counts, readable
  durations and empty states. Unsupported conversion rows are disabled.
- Studio sidebar: collapsible scene sections, full-width actions, a paired
  undo/redo row and selection-aware timeline tools. Workspace options stay
  collapsed until needed.
- Settings split into Recording, Audio, Interface and Advanced tabs. The
  window keeps its resized dimensions and saves when closed from the title bar.
- Actor menu grouped into Appearance, Timing, Smooth movement and Camera.
  Locked tracks disable timing, smoothing, camera paste and deletion in the menu.
- Movement smoothing: separate look, horizontal path and combined modes with
  radius and strength controls, save support and undo/redo. Vertical movement,
  segment endpoints and interaction frames stay on their recorded values.
- Camera curves use time-weighted, bounded tangents to avoid positional
  overshoot between unevenly spaced keys. Fractional tick sampling is available.
  Adding a camera key at an occupied tick replaces it; collapsed keys keep the
  latest value, and replacing a camera track with itself keeps its keys.
- Actor setup uses a microphone picker and a continuous voice threshold slider.
  The window can be resized; closing it cancels setup and releases actor mode,
  including when the window is collapsed.
- Trim actions keep at least one frame when the other end is already trimmed;
  extreme trim counts and playhead values no longer overflow validation.
- Take format v30: spawn track. Performer-placed boats, minecarts, stands,
  paintings, frames and crystals record registry id, measured pos/rot and
  opaque NBT; playback re-spawns them with scrub-backwards prune and despawn
  cleanup. Capture via addFreshEntity hook correlated with recent uses.
- Take format v29: discrete uses. Right-click BLOCK/ENTITY/AIR records pos,
  target UUID and hand via packet-hook mixins (attack-skipped); playback
  swings the correct arm.
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
