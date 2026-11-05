# Events library

Open **Effects > Events library**, or the **Events** tab in Actors Studio.
Events are visual additions to the replay image. They do not create game
entities, damage actors, change blocks, or affect world simulation.

Browse **ICBM**, **Nuclear bomb**, **Meteor impact** and **Meteor airburst**. Search or filter by category, then select a
3D thumbnail to open its preview and settings in the inspector. Drag a card
directly into the world, or use **Place**. Missile and bomb models also have
turntables; natural events have independent, scrubbable 3D sequence previews.

## Nuclear bomb

Drag onto one impact block, or press **Place Nuclear bomb** and click it.
The supplied textured bomb drops from above that point. Adjust **Drop height**
to control its starting altitude. A flash and expanding pressure wave lead into
a large mushroom cloud, heat-haze lens distortion, floating embers, a fading
afterimage and lingering atmospheric smoke. These remain visual effects; they
do not alter the ground.

The default event lasts 28 seconds, including its aftermath. The bomb model is
**Atomic Bomb - Easter Freebie** by **Valentin Winkelmann**, CC BY 4.0. Source and
license credits are available in **About** and bundled with the model.

## Meteors

Each uses one world block. **Meteor impact** lasts 28 seconds: an irregular,
tumbling rock enters behind a turbulent fire-and-smoke train and breaks into
burning fragments. Its default event camera watches from the ground to retain
the sense of scale. **Meteor airburst** lasts 28 seconds and detonates above the
chosen block with an expanding vapour shell and airborne debris.
Use **Event size** to scale the visual effect; Event cam also offers an overhead view.

Carpet bombing, Lightning strike, Superbolt, Cloud crawler, Thunderstorm,
Meteor shower and Volcanic eruption are retired. They are hidden from the gallery
and timeline; saved clips remain in replay files but do not play.

## Sound and cancellation

Only **ICBM** currently plays sound, using the supplied `icbm_sound_pack.zip`
and newer `icbm_audio_download.zip`. The newer launch, incoming, staging and
explosion clips replace matching roles. Silo-mechanism movement, missile launch
sustain and explosion replace the older hatch, engine and distant-blast roles. Only the earlier alert remains
because the new siren is missing. Plane audio is excluded.
The Ogg files are imported unchanged. Alert/door cues fade as launch takes over;
stage deployment, clank and cap cues follow separation, and incoming follows
re-entry. Detonation uses distance-based delay and selects the close or distant
clip with restrained bass, crackle and wind layers.
Nuclear bomb and both meteor events stay silent.

Use **Hear launch**, **Re-entry**, **Detonation**, and **Stop sound** in the preview
panel to audition the supplied audio while paused. Auditions stop when the panel
closes and are excluded from exports. Use **Event sounds** and **Sound volume**
on each ICBM clip. Audio stops on pause, seeking, muting, deletion or replay changes.
Unpausing midway through a powered stage restarts its supplied sound; tiny live
playhead corrections do not reset playback. Sounds use Minecraft's mixer.

**Escape** cancels placement before Minecraft can open its pause menu.
For the ICBM, right click returns to the launch step. For single-point events,
right click cancels placement.

The library shows the actual missile in a lit **3D model** turntable. Choose
**Play sequence** to preview the complete animation, with pause, replay and
scrubbing controls. Preview playback is independent of the replay playhead.

## Place an ICBM

1. Pause at the tick where the event should begin.
2. Drag the **ICBM** card onto a block in the scene. The hovered block highlights.
   Release to choose the launch pad. **Place ICBM** also lets you click both blocks.
3. Click a different block for impact. The launch block stays marked while you choose.
4. Unpause to watch ignition, accelerating boost, pitch-over, stage separation,
   upper-stage burn, fairing release, coast, re-entry,
   and impact, followed by a fireball, shockwave, ejecta and mushroom cloud.

Right click goes back one step. Escape or **Cancel placement** abandons placement.
Nothing is added until both locations are selected.

Each event creates its own row under **Events**. Drag the clip to move it in time,
or drag either edge to stretch the complete sequence. Small phase ticks mark
stage separation, re-entry and impact. Double click for duration, missile size,
explosion size, and location controls. Events can be muted, locked, duplicated,
deleted, and undone or redone independently.

New ICBM events default to 60 seconds, with impact at about 43 seconds and the
remaining time for the explosion. Existing event durations are preserved.
Select **Use cinematic timing (60 s)** to apply the new pace to an existing clip.

Playback, scrubbing and video export use the same replay clock. Event settings
save per replay in `flashback/fbactors/<replay-uuid>/events.json`.

### Event cam and the nuclear cloud

Select an event and enable **Event cam**. The attached camera shows the complete
falling bomb, then cuts to the blast. Choose Director cuts, Distant witness, or
Overhead. Director cuts frame nearby existing actors when available; their replay
motion is unchanged. Disable Event cam to restore the previous camera and FOV.
Scrubbing and export use the same time-sampled shots.

The nuclear bomb is now 10 metres tall at size 1. Its cloud has a much larger
rising column, rolling cap, ground-level dust and condensation skirts. The cloud
is an art-directed procedural effect inspired by film and cloud-rise imagery,
not a scientifically validated nuclear-effects simulator. Nothing damages or
paints the world.

The model uses 7,000 triangles instead of 40,272. Nuclear clouds render at the scene's full resolution, with a 96-step maximum,
early opacity exit, and a tiny filtered 3D-noise lookup.
Library cards remain isolated 3D previews.

Sound combines an existing CC0 vintage cinematic recording with pressure rumble
synthesized at playback. Distance delays the arrival of the blast and filters the
procedural layer. Minecraft owns mixing, volume, pause/seek cleanup and export.
See THIRD_PARTY_NOTICES.md for provenance and licenses.

### Supplied three-stage ICBM and space view

The ICBM now uses the geometry and livery from the supplied `icbm-model.zip`:
white body, fins, stage collars, split nose shroud, engine bells and post-boost
section. The imported model has 4,528 triangles across six animated parts.

New ICBM events default to 60 seconds, with impact at about 43 seconds. Custom and locked clip lengths remain editable and are retained. The path climbs several thousand
blocks above the scene with a continuous turn, unpowered coast and terminal dive.
Enable **Event cam** for the atmospheric-to-space transition, curved horizon,
stars and close post-boost camera. Preview and export sample the same flight.
This is a deliberately compressed cinematic sequence, not actual spaceflight physics.

Falling models always render at full resolution. Complete material mip chains and
extra silhouette samples reduce distant shimmer and jagged edges. All explosion
clouds also render at full resolution.
The cloud's rolling flow is continuous in 3D and no longer has a polar spoke seam.

### Earth appearance

Event cam's space view uses NASA Blue Marble Next Generation surface imagery and
Solar System Scope cloud, night-light and ocean-specular maps (CC BY 4.0). The
8192x4096 surface and weather maps have complete mip chains. Clouds are shaded on an elevated
shell, oceans reflect the sun, the night side shows city lights, and an eight-step
Rayleigh/Mie approximation supplies the thin blue atmosphere. Bright exposure
keeps most stars faint. Geographic placement is fixed, with compressed cinematic
altitude; this is not a 1:1 mapping of Minecraft terrain or a live-weather globe.
See bundled credits and THIRD_PARTY_NOTICES.md.

### Slower pacing and selectable endings

New ICBMs use a 60-second sequence. Earlier unlocked clips that still have the
original 12- or 18-second default are upgraded once; custom and locked durations
are retained. Use **Use cinematic timing (60 s)** on any selected ICBM to apply
the new timing. When an event outlasts the footage, the last recorded world frame
holds until the event finishes. You can watch from your own camera or enable
**Event cam** for cinematic shots. A custom export range needs to include the
impact and aftermath ticks to capture them.

**Director cuts** now include a low launch shot, side tracking, upper-stage
close-up, a slow orbital coast dolly and terminal tracking. Choose **Continuous
follow** for an uninterrupted shot. **Cinematic letterbox** can be toggled.

Choose **Ending** on a selected event: **World blast** keeps the explosion in the
scene; **Cut to black** hides the final impact and leaves its sound offscreen.
Map endings have been removed for now. Saved map selections fall back to World blast.

Earth uses original-resolution 8K cloud imagery, sharper directional filtering,
and independent-channel mipmaps. The ocean/cloud mask channels are never treated
as image transparency during filtering, preserving surface detail at distance.

### Preview stability and distant visibility

Event cam and the world effect use one time sample for each rendered frame.
Small live-clock prediction reversals cannot cross a stage cut backwards; paused
seeks and exports retain their exact time. Camera control applies only to the main
world camera, so other 3D previews cannot move it.

Distant event rendering bypasses cloud-layer depth and the unloaded-terrain/far
boundary. Nearby walls and terrain continue to occlude the event. This keeps the
blast visible from high or distant shots even when Minecraft cannot draw the
terrain beneath it. No world blocks or cloud settings are changed.

Explosion clouds now use native-resolution integration, four scales of rolling
noise, directional shading, cool cap shadows and a hotter underside. Noise is
sampled from a deterministic 512x512 lookup after impact, using the existing
material binding; it adds no model vertices or runtime asset dependency.
