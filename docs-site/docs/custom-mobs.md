# Custom mobs and animations

In 0.11.2, models already installed in `flashback/fbactors/morphs/models/`
appear under **Custom** in **Actors > Morph library**. Select a body to preview,
apply it to an actor, or drag it into the Morph lane.

The legacy Morphs picker, its import dialog and named-animation controls have
been removed. Existing model files and saved animation selections still load.

The custom model format supports:


- A single Bedrock/GeckoLib geometry with Box UV, nested bones, cube pivots,
  cube rotations, inflate and mirror.
- Numeric position, rotation and scale channels, including timestamped
  keyframes, pre/post values, linear and step interpolation, and looping clips.
- PNG textures matching the geometry's declared texture dimensions, up to
  4096 by 4096 pixels and 16 MB. JSON files are limited to 2 MB, models to
  256 bones and 2048 cubes, and animation files to 128 clips.

Export with **Box UV** enabled. Per-face UV, Molang expressions, animation
controllers, sound/particle event tracks and custom interpolation modes are
not supported in this first version. Unsupported files produce an import
error instead of silently substituting a vanilla body. GeckoLib itself is not
required for these imported assets: the addon converts their bones into
Minecraft model parts and uses the native entity renderer.

Automatic playback chooses clips whose names contain `idle` and `walk`;
otherwise it uses the first clip. Previously saved named-animation selections
persist per take. Animation time comes from the replay tick, so pausing and
scrubbing produce the same pose at the same time.

Installed mods' living entities appear under **Modded** after entering a level
when they can be created and have a renderer. Their source mod must be
installed when opening the replay or exporting. Some mod entities need extra
state supplied by their own mod and may not work as a morph; failed discovery
is skipped and logged. Imported file models are visual actor bodies, not new
server mobs with custom AI.
