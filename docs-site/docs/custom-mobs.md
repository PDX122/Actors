# Custom mobs and animations

Open an actor's **Morphs** picker and press **Import model**. Choose a model
name, a Blockbench/GeckoLib `.geo.json`, an optional animation JSON and its PNG
texture. The files are copied into `flashback/fbactors/morphs/models/`. Select
the imported entry under **Custom**, inspect the preview, then Apply.

This initial importer supports:

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

The preview's pose list contains the imported animation names. The playback
selector below Apply lets you choose a named animation for the actor or use
**Auto: idle / walk**. Auto chooses clips whose names contain `idle` and `walk`;
otherwise it uses the first clip. Named playback selections persist per take.
Animation time comes from the replay tick, so pausing and scrubbing produce
the same pose at the same time. Preview playback has its own clock.

Installed mods' living entities appear under **Modded** after entering a level
when they can be created and have a renderer. Their source mod must be
installed when opening the replay or exporting. Some mod entities need extra
state supplied by their own mod and may not work as a morph; failed discovery
is skipped and logged. Imported file models are visual actor bodies, not new
server mobs with custom AI.
