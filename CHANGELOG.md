# Changelog

Take-format versions are the compatibility contract: a take file records the
version that wrote it, newer builds read older files (migrating where noted),
and older builds refuse newer files at the version check.

## 0.11.2 (release candidate)

- Improved the /start countdown with bold colored numbers, rising final-three
  tones and a green ACTION! cue. Every second of the configured delay plays a
  sound directly to the recording player. Removed the duplicate start cue.

- Expanded Camera modes from 14 to 24 shots, adding Ice chase, Ice lead,
  Ice trackside, Mounted chase, Gallop profile, Rider shoulder, Route scout,
  Arc reveal, Rise to overhead and Hero orbit. Existing saved mode IDs remain
  unchanged. Travel-based shots follow slides and mounted framing uses rider height.
- Added shot groups, search, wider previews and scenario previews with boats on
  ice and horses on trails. Small cards reduce geometry detail to keep the larger
  library within ImGui's vertex limit. Controls now match each shot's behavior.
- Orbit and cinematic moves now start from their camera clip's local clock.
  Live follow starts its motion clock when activated, and selecting a shot or
  reopening the library restarts the inspector preview.

- Removed the legacy Choose morph window and its Appearance menu entries
  (Choose morph, Morph timeline and Reset morph). Use Morph library to apply
  bodies and poses, restore Player, or add clips. Timed clips remain available
  in the Morph lane with drag, trim, mute, duplicate, delete and undo support.

## 0.11.1 (in development)

- Changed 26.3 Vulkan morph previews to render after the ImGui draw is submitted.
  Preview textures stay alive until a GPU fence confirms the menu has finished using them.

- Fixed world flashing while the Morph library is open on 26.2 and 26.3.
  Preview rendering now owns its feature renderer and GPU buffers.

- Fixed empty private morph-preview renderer registries on 1.21.10, 1.21.11
  and 26.1. The Morph library now opens on the page containing the selected body.
- Fixed ReplayMod conversion login selection and player entity ID encoding:
  identify the Login packet from the game protocol and preserve its fixed-width
  entity ID instead of shifting subsequent packet fields.
- Added protocol-aware ReplayMod format 14 conversion, including initial
  configuration, the recorded local player and absolute entity movement.
  Connection housekeeping and live-only UI packets are filtered before writing.
  Unsupported signed chat, relative teleports, custom payloads and movement
  formats stop conversion with an explanation instead of writing a crashing replay.
- Fixed camera-only exports ending one tick early, including empty exports for single-tick clips.
- Fixed blank legacy morph previews, OpenGL state leaks in 26.2 morph previews,
  and a timeline crash when replay ticks change the actor list during rendering.
  Morph libraries now omit bodies unavailable in the running Minecraft version.
- Added a concurrent release harness with isolated native caches, full-window
  captures, animation comparisons and a gallery grouped by feature and version.

- Added animation and pose actions to the Morph library: Idle, Walk and Run,
  plus Crouch and Sitting for supported humanoids. Cats, wolves and parrots
  retain their native sitting options, and cats retain Relaxing. Selected
  actions travel with dragged cards and timed clips, with a playing inspector
  preview. Fixed upside-down morph card and drag-preview thumbnails.

- Expanded the Pose library into a Morph library. Browse registered bodies as
  draggable cards with search and category filters, including loaded custom and
  modded morphs. Choose a supported pose in the selected morph's inspector and
  drag that body/pose combination onto an actor or Morph lane.

- Added a draggable Pose library with cached 3D cards for cats, wolves and
  parrots. Drop a body/pose card onto an actor or Morph lane to create a timed
  clip at the drop point. Clips respect actor locks and take bounds, support
  undo, and can be moved or trimmed. Apply a default pose or add at the playhead
  from the library. Open it from Actors or the morph inspector.

- Made native morph poses visible in the picker with Auto, Standing and Sitting
  controls, plus Relaxing for cats. The preview now uses the selected pose.
  Cat relaxing sets native pose blends directly, including paused frames and
  scrubbing. Pose choices remain saved with morph selections and timed clips.

- Fixed custom camera clips missing from video exports. Export time now includes
  the selected range's start tick, and Flashback's accurate camera transforms use
  the active shot instead of stale viewer data. Camera clips contribute to the
  inferred export range. Camera smoothing resets between preview and export.

- Added a PDX startup banner with mod, Minecraft and build versions.

- Added Shoulder, Low angle, Crane reveal, Push in, Pull back and Dolly zoom
  camera modes. Reveals ease over eight clip-local seconds and hold their final
  framing, with matching previews and replay/export timing. Existing mode IDs
  remain unchanged.
- Added four cinematic Looks: 35mm, Noir, Desert Chrome and Moonlight, bringing
  the original shader library to 60. Each has adjustable strength; 35mm also has
  replay-timed film grain. Updated gallery thumbnails and CPU material grading.

- Added an in-game changelog under Actors > Changelog, with release selection,
  search and bundled notes that work offline.
- Added an error report window for recoverable failures in guarded editor panels,
  shortcuts and actor updates. It identifies the affected feature, keeps its stack
  trace and offers Copy log, Retry affected feature and Dismiss. Failed features
  stop repeating; actor playback update failures pause the replay. The Random
  button deliberately triggers a recoverable test failure. Native process aborts
  and fatal JVM errors still require prevention or a restart.
- Reorganized Settings into Recording, Playback, Actors, World, Camera & boats,
  Audio, Captions, Interface and Shortcuts. Related controls have clear sections,
  labels sit above full-width sliders, and narrow windows use a page picker.
  Boat handling is available directly in Settings. Camera tuning groups framing,
  motion and obstacles, with controls appropriate to the selected mode.
- Removed the old scene Theme panel. Actors, Studio and Interface settings now
  open UI themes, with editor presets, custom theme codes and a default reset.
- Improved camera previews with grounded walking, travel-aligned facing,
  directional lighting, soft ground shadows and distance haze. The main preview
  uses the signed-in Minecraft account's skin and matching arm model.
- Ground textures now repeat once per world block, preserving the player's
  apparent height when terrain geometry uses fewer polygons.
- Fixed camera previews exceeding ImGui's 16-bit vertex limit. Previews share a
  draw-list safety margin, use explicit geometry budgets and reduce terrain
  detail before sacrificing the actor. Adaptive subdivision reduces perspective
  stretching within those limits.

- Camera previews now show full skinned players with walking limbs on Minecraft
  grass and stone textures. Thumbnail geometry is cached and animated previews
  update at up to 60 FPS without taking the replay camera.
- Improved camera framing for player height, a true side view, a steady orbit,
  eye-level first person and north-up overhead, with recommended tuning per mode.

- Fixed a Vulkan UI crash when scrolled preview cards produce zero-height clip
  rectangles. Hidden previews no longer draw; the 26.3 ImGui renderer skips empty
  scissors and their draw commands while resuming the next visible command.

- Renamed Boat Cam to Camera modes with a draggable preset library and a clean
  animated composition preview. Drop a mode onto an actor to create a following
  camera clip for its take. Existing clips and saved camera settings remain valid.

- Hardened boat and morph state loading against oversized files, invalid poses,
  duplicate clip IDs, excessive clip counts and non-finite camera settings.
- Failed preview renderers are released and disabled until the picker reopens.
  Renderer cleanup failures no longer interrupt other cached previews.
- Save failures preserve the last complete file and retry pending boat/morph
  changes under their original replay IDs. Concurrent saves use separate temporary
  files and serialized replacement on Windows.

- Fixed upside-down morph previews by using the same texture orientation as skins.
- Rebuilt the morph picker with larger text, taller body rows, an always-visible
  Apply action, a larger preview and simpler pose/timeline controls. Child panels,
  dropdowns and disabled scopes now close on exceptions to prevent ImGui assertions.

- Added an About window under Actors with community credits for videos, ideas
  and bug fixes, including a link to BlueTomato on YouTube.

- Boat camera tracks now match Misc clip styling and interactions, with selection,
  snapping, right-click edit/mute/duplicate/delete, track mute/lock and undo/redo.
- Reworked the morph picker with visible Apply controls, optional previews and
  immediate paused body changes. Auto pose maps cat/wolf/parrot crouching to sitting.
- Added per-actor Morph timeline lanes for timed body and pose changes, including
  custom models, with drag/trim, context menus, locks, undo and per-replay saves.

- Removed Particle studio, its preview, VFX timeline and particle rendering support.

- Removed actor Ctrl shortcuts and their Controls entries.
- Actor container playback keeps lid animation and sound without opening or
  closing the viewer's GUI, including during recording and rendering.
- Repeated loads of the same take reuse its actor entry. Clone cleanup also
  removes stale player registrations and explicitly retires the client entity
  when seeking or respawning.

- Actors move their native player chunk-loading tickets as they move, including
  while boating. Their surrounding chunks stay loaded independently of the
  replay viewer, and their previous loading area is released normally. Clones
  use the replay viewers' requested distance instead of the silent player's
  default of two chunks.

- Actor-created boats remain visible in replays whose server resource pack hides
  the vanilla hull, including BoatLabs. Fully transparent hull textures fall
  back to the installed game's vanilla texture for authored boats only.

- Escape finishes actor recording only after the final replay tick. Inventories
  and other screens keep normal Escape behavior, including in remapped jars.

- Boat studio now uses the custom Looks-style menu, with Camera, Timeline
  and Handling pages. Added a two-driver battle camera with automatic framing.
- Replaced boat baking with a per-replay Boat camera timeline. Move and trim
  clips, save shot settings and drivers per clip, and activate them automatically
  during playback and export.
- Added saved native morph poses: cats can sit or relax; wolves and parrots can
  sit. Pose selections persist in morph saves and take bundles.

- Give authored replay spawns their own entity IDs so boats and other placed
  entities cannot collide with ordinary recorded entity IDs.
- Added Front chase, Overhead and Drift chase Boat Cam modes on every target.
- Cross-version build checks now verify native boat hooks and identical handling
  adapters across all nine targets. Boat camera and authority hooks require at
  least one matching injection, preventing silent loss of support after renames.
- Sound capture uses exact overload selectors and accepts legacy Player and newer
  Entity arguments, fixing a 1.21.10 startup crash caused by remapping an absent
  legacy overload onto a different method.

- Added Boat Cam under Actors with player/actor targets, chase, drift-side,
  orbit and hood shots. Camera controls include follow lag, look ahead,
  speed FOV, turn banking and obstacle avoidance in preview and exports.
- Dedicated Boats settings now expose actor boat propulsion, steering,
  lateral grip and wall restitution, with vanilla defaults and a reset.
- Advance nearby native redstone, fluids and block entities during actor
  playback, including sulfur geysers on supported versions. Bubble columns,
  piston motion and geyser impulses release actors into native movement.
  Simulated elytra starts and attached firework boosts now run on the actor clock.

- Added Playback and World edits settings for paddle animation, smoothing,
  older-take animation, sliding momentum, saved viewport edits and terrain
  reactions. Recording settings now include Escape, camera return and hints.
- Removed the scrapped timeline redesign option and its alternate layouts.
- Actor playback now advances a simulated boat once per take tick instead of
  relying on frozen replay-world ticks. Riders stay seated and their head and
  body follow the live view, fixing stopped boats and twisted heads after
  terrain reactions.

- Fix mounted actor baselines moving boats back to stale player boarding
  coordinates before terrain checks. The vehicle track now owns mounted
  position and the rider follows its vanilla seat, so viewport ice removal
  is tested at the boat's actual location.

- Viewport block edits made outside an actor run are saved per replay and
  reapplied after scrubbing. Removing ice or placing obstacles releases a
  nearby actor or boat from its recorded path into world physics, retaining
  momentum and recorded controls. Unaffected actors keep their original path.

- Smooth boat paddles using the fractional replay clock and adjacent saved
  poses, including paused playback, exports and delayed server updates.
  Actor-driven boats now use the same per-tick movement packet cadence as
  actor players. Stopping a paddle returns by the short arc instead of
  unwinding accumulated strokes.

- Take format 35 captures independent boat paddle strokes and actual player
  and vehicle momentum. Paddle animation follows the recorded tick, including
  pauses and seeks; older boat takes animate from their recorded controls.
  Actor and boat playback retain post-friction momentum for sliding and
  coasting on ice, packed ice and blue ice, including a handoff to simulation.

- Removed the outdated Director menus, windows and automatic camera generator.
  Existing take files retain their legacy camera data for compatibility.
- Escape ends the current actor run. The recording HUD and end-of-footage
  prompt now say "Click Escape to end run". Ending a run dismounts the viewer
  and restores the editor camera position.
- Take format 34 records vehicle positions and rotations on every mounted tick.
  Playback restores missing vehicles and riders when seeking, keeps vehicle
  poses through clip edits, and prevents a second physics step from moving
  recorded boats. Older takes still load with their existing ride data.

- Selected actors now show X/Y/Z move handles while the replay is paused.
  Dragging moves the recorded path, saves on release, and supports undo/redo;
  locked tracks and active recording prevent position edits.
- Replaced the old cross-tick health-diff combat recorder and hit replay code
  with a separate combat controller using accepted damage receipts, one-time
  impulse delivery and cached combat-state seek recovery. Walking, jumping,
  strafing and look input/travel behavior are preserved.

- Keep knockback velocity on actor clones instead of applying vanilla's
  client-controlled-player velocity reset. Damage-free pushes also release
  the recorded movement baseline. Displaced actors use live item interactions
  with reach and aim checks rather than continuing timed tower placements.
- Actor boats now steer and move with vanilla server physics, ice friction and
  collisions; vehicle passenger ticking no longer ticks a clone twice.
- Capture spawn-egg entities during preparation/recording. Take format 33 adds
  concrete arrow type and full projectile data, preserving special ammunition
  and weapon effects during playback.

- Movement/PvP: preserve airborne sprinting, capture client movement and jump
  input, restore vanilla clone damage/motion behavior, and stop advancing arm
  swings twice. Capture repeated/offhand swing requests and 26.3 swing styles.
- Take format 32 saves attack-time charge, fall distance and sprint state, plus
  swing type/duration. Older takes retain their existing fallback behavior.
- Creative preparation now permits building and placing vehicles; scene edits
  carry into the take. Vehicle playback preserves identity/data, spawns before
  mount events, and accounts for passenger seat height. Performer projectiles
  and ridden vehicles can tick inside the replay; short-range arrows are
  captured on successful spawn.
- Added a [movement/PvP comparison checklist](docs/pvp-validation.md); live
  gameplay parity checks remain required.

- Rebuilt the Looks gallery with a shader grid, inspector, target selector,
  direct Add effect, imports and deletion. GLSL imports store a name,
  description and source, compile before saving, preview on the demo photo,
  and run as scene or masked actor effects on OpenGL and 26.3 native Vulkan.
  Bundled deletion warns that reinstalling is required; a replaced mod jar
  restores bundled entries while preserving custom imports.
- Settings now has Recording, Audio, Captions and Interface pages, current
  labels and help, and no broken self-test controls. Automatic self-tests are
  disabled.
- Added a custom model importer for Blockbench/GeckoLib box-UV geometry,
  PNG textures and numeric linear/step animation channels. Converted models
  render through vanilla model parts, preview imported animation clips and
  save per-actor playback choices. Replay time drives animation. Installed
  mods' renderable living entities are discovered under Modded.
- Hid preview player name tags without changing actors in the replay. The skin
  menu is now 600 by 510, with rounder controls, a borderless outer window and
  softer slate panel colors.
- Skin models render at 512 by 512 instead of 128 by 128, keeping the same
  framing while improving model edges at UI display sizes. The model sits
  slightly lower in its stage, and the fixed skin menu is now 640 by 540.
- Flipped the skin preview's texture coordinates upright and removed its drag
  hint. Skin actions use bundled Phosphor regular icons (MIT), retaining their
  text labels. The skin menu now has a fixed size, clamped to the viewport,
  with manual resizing disabled.
- Removed the skin picker's leading selection stripe and preview floor line.
  Source, view and action buttons now use rounded controls with consistent
  padding; Walk uses a toggle button instead of a checkbox.
- Initialized the private preview dispatcher's renderer registry before its
  first draw, fixing the remaining null player-renderer failure.
- Fixed 3D skin previews failing on 26.3: preview entities now receive a private
  numeric ID before render-state extraction. Player-list exclusions snapshot
  replay viewers and actors so server changes cannot close the picker with a
  concurrent-modification error.
- Skin picking now gives more space to the model, with portrait rows, a
  drag-to-turn stage, front/back controls and an Apply button below the panels.
  Restore default and the browser shortcut live under More.
- Actor effect materials retain the original skin sampler on native Vulkan
  and modern OpenGL. Older OpenGL targets use nearest filtering for generated
  actor materials, avoiding the soft pixel edges introduced by replacement
  texture defaults.
- Rebuilt the skin picker as Skin studio: one search field, a Players/Saved/Files
  library and a full-body 3D preview with rotation and walking controls. Narrow
  docks stack the list above the preview. Replay viewers and actor clones are
  excluded from the player list. Preview entities never enter the replay world;
  their downloaded appearance is loaded off the render thread. Cape and arm
  controls sit under the file tools, and applying a skin remains explicit.
- Actors Studio now renders after Flashback creates its dockspace, reusing the
  native Visuals panel's saved position. Drag its title to dock beside or below
  the timeline. Other addon windows use the same editor panel phase.
- Change skin adds online username/UUID lookup, NameMC profile links and a
  browser shortcut, previews, explicit Apply, saved skins and replay players.
  Lookups use a bounded background pool, cancellation and an overall timeout.
  Invalid input, malformed profiles, unexpected texture URLs and failed loads
  show errors; closed pickers discard late results.
- Added Pixel Sort, Codec Smear, Signal Rot and Bitcrush, bringing the library
  to 56 effects. Pixel Sort uses brightness sorting, thresholded runs, both
  axes and replay-timed span movement. GPU checks verify its actual sorted
  output, threshold behavior and deterministic paused frames.
- Fixed actor effects showing through blocks by running effects at world-render
  completion before the hand/3D HUD pass clears world depth. Player occlusion
  stays active, and later hand draws cannot contaminate the next actor mask.
  Native GPU regression reproduces the old leak after a depth clear and checks
  the corrected ordering. World completion hooks are verified across all targets.

- Flashback's Hide [F1] control stays last. Actor category menus insert before
  it; static checks verify the insertion point in every compile and installed
  Flashback jar.
- Removed the latest twelve stylized/cosmic additions, restoring the 52-filter
  library and its previews.
- Fixed actor effects showing through foreground players. The screen pass now
  checks a separate nearest living-entity depth capture, including players and
  mobs without effects, instead of relying on world depth alone. This covers
  26.3 OIT player draws that do not update the world depth target. GPU regression
  checks cover both depth conventions, filtered and unfiltered foreground
  players, and one-ULP depth separation on native Vulkan.
- Added eight animated styles: VHS Damage, Digital Glitch, Hologram, ASCII
  Terminal, Sonar Pulse, Liquid Melt, Matrix Rain and Neon Spectrum. The library
  includes rebuilt photo previews and strength/speed controls.
- Minecraft 26.3 native Vulkan runs the original filter shaders on RenderPearl,
  including scene stacks, actor materials, grading and masked actor Mosaic.
  Look-library previews upload through the native texture API.
- Morphs uses a full scrolling library beside an animated model inspector,
  with pose selection, playback controls and inline size/appearance tuning.
  Narrow windows stack the two panes. Native previews avoid OpenGL state calls.
- Actor-owned projectiles can tick in the frozen replay world. Recording runs
  nearby scheduled block updates and piston events, and follows connected block
  changes beyond interaction reach. Sound capture includes direct seeded
  broadcasts, recorded uses and performer-owned projectile sources.

- Take format 31 appends item snapshots to recorded air uses. Playback starts
  vanilla item use with the recorded hand and stack, and releases charged items
  through vanilla. Bow and crossbow shots keep their measured arrow track.
  Playback projectiles are tracked for scrub and despawn cleanup. Older takes
  retain their existing use animations.

- Kept Flashback's vanilla controls and grouped addon tools under three short
  topbar menus: Actors (setup, subtitles, theme, settings), Camera (Director,
  Shots) and Effects (Looks, clip editing).
- Spatial actor effects now process the lit actor image through the geometry
  depth mask on OpenGL and native Vulkan. ASCII, the animated styles,
  Arcade, print patterns, Cel Shade and Neon Wire no longer collapse into skin
  texture patterns. Mixed stacks retain their saved order; grade and intensity
  apply once. Sampling and final compositing protect background and occluders.
- ASCII renders 5 by 7 terminal glyphs with character-size and glyph-color
  controls. GPU checks verify strokes and gaps, normal/reversed depth,
  foreground occlusion and mixed Mosaic stacks.

- Fixed actor Mosaic on 26.3 by recognizing RenderPearl's renamed texture
  samplers and world transparency targets sharing the scene depth buffer.

- Selected morph previews now animate with pause, pose selection and automatic
  cycling. Added native AnimationState channels, growth stages, golem damage,
  sheep wool/shearing, slime sizes and sitting poses where supported. Only the
  selected model updates, capped at 30 fps with its renderer reused.
- Morphed walking now preserves previous/current stride interpolation and eases
  starts and stops. Per-actor phase stays continuous; teleports and invalid
  positions reset without a lunge.

- Morphs renders the selected mob's actual 3D inventory model in the preview
  pane; Apply changes the actor. Cached previews are released when the picker
  closes or its level changes. Unsupported models show a fallback.

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
  OpenGL and Minecraft 26.3 native Vulkan backends are supported.
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

## 0.10.11

- First release built entirely in public CI (Modrinth-fetched dependencies,
  all nine targets, both test families). No code changes over 0.10.10.
