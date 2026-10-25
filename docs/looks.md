# Looks

The Looks tab changes the replay image before the interface is drawn. It does
not alter actor data, world lighting or replay files. The effects are disabled
until you enable a look or add a filter.

## Looks library and targets

Open **Actors Studio > Looks** or choose **Looks** from the editor menu.
The library uses a compact search and category row above a resizable thumbnail
grid. Descriptions appear on hover. The library shows searchable, labeled photo thumbnails. Drag a thumbnail or
its name directly onto an actor track or the **Misc** track. The thumbnails use a bundled real landscape photo, with each look baked once
into a PNG atlas at 256 pixels wide. Browsing does not render shaders, capture
the replay or update the gallery during playback.

Dropping onto an actor creates a look clip covering that actor's take, with
no default fade. Another drop adds to the actor clip's stack, up to three looks.
The actor gets an indented Looks lane for timing edits. Looks and Misc use the
same slim bars and row spacing as actor tracks, with a small sliders icon. Its body, armor and
other living-entity material textures use private graded copies; source
textures, other actors and the background remain unchanged. Most actor effects run
in material texture space before normal lighting. Mosaic uses a separate screen-space pass. Held item atlases remain
unchanged. Spatial looks on Misc run in screen space over the final scene.

Dropping onto Misc creates a five second scene look at the drop position, or
at the playhead when dropped on the label. Both targets start at full strength.
Drag a clip to move it, drag an edge to trim, and double click to edit its
stack, grade, intensity, fades or name. The inspector always identifies the
target. Actor clips honor their actor track's lock. Misc mute and lock apply
only to scene looks. Timing, content, duplication and deletion support undo.

The **Edit Looks** window separates controls into **Look**, **Timing**,
**Grade** and **Presets** tabs. Look contains intensity, the three-filter stack
and filter tuning. Its preview stays collapsed until opened. Timing contains
name, clip enable, start, end, fades, duplicate and delete. Grade holds color
controls, while Presets holds saved looks and advanced reset/reload options.
**Browse looks** opens the library without embedding another gallery in the
editor. The photo previews demonstrate each look at its default settings; the
viewport shows the result on the selected actor or scene.

Effect bars use compact outlined blocks, an accent edge and hover/selection trim
handles. Right click an effect for **Edit effect**, **Duplicate effect**,
**Delete effect**, or **Delete effect track**. The last action removes all effects
for that actor or Misc, with undo support. Removing an actor's last effect removes
its Looks lane. Misc remains available as the scene drop target. Locked tracks
cannot be deleted. The actor row no longer has an Actor options button; actor
commands remain available by right clicking the actor name or timeline clip.

Clips save per replay in `flashback/fbactors/<replay-uuid>/effects.json`.
Older untargeted clips become Misc clips. Old replay grades migrate once into
visible Misc clips, with no hidden global grade left behind. Different targets
have independent timing. Overlaps on the same target use the latest start;
muted clips are ignored. Fades blend that clip's look with the original input.
Outside Misc clips the scene is ungraded. Export evaluates the same targets
and timing. The take bundle does not include look clips.

## Mosaic

Mosaic replaces the former Tessera glass tiles while retaining the saved shader
ID. It averages the final lit scene into flat square blocks, without dark
seams, bevels or a skin-UV grid. **Block size** ranges from 6 to 80 at a 1080p
reference height, with a default of 28. Misc scales with render height. Actor
Mosaic additionally scales with the actor mask height, keeping smaller blocks
on distant actors. The grid follows the actor mask rather than the screen
origin. GPU reductions measure the mask without reading pixels back to the CPU.
The minimum block width is two rendered pixels. Old Tile size tuning is replaced by the new default.

Actor Mosaic captures a depth-only copy of the actor geometry, including the
materials already supported by actor looks. Final scene depth rejects hidden
parts and later foreground occluders. Sampling uses only visible actor pixels
within each block. Background pixels are left untouched, and the original
material textures remain unchanged. Normal depth and the reversed depth in
26.2 and 26.3 are supported. Up to 32 visible actors can have individual Mosaic
masks. Compare and clip fades also apply to Mosaic.

On 26.3, player skins render through OIT transparency targets. Mosaic recognizes
these world draws by their shared scene depth attachment as well as the main
color attachment, so they contribute to the actor mask too.
RenderPearl also renames linked sampler uniforms; actor material lookup uses
the active 2D samplers when the original `Sampler0` name is unavailable.

On Misc, Mosaic pixelates the whole scene. Other actor color looks continue
to use material copies. The new render path still needs in-game visual review.

## Original collection

All 44 looks use original Actors shader source. The imported collection and
lens designer have been removed. Existing saved settings discard removed
filter IDs while preserving grading and supported original filters.

| Look | Category | Character |
|---|---|---|
| Signal | Optics | A clean analog monitor with subtle scanlines and edge color separation. |
| Bleach | Cinematic | Desaturated silver contrast with controlled highlights. |
| Silver Screen | Mono | Luminance-based monochrome with local contrast and a soft shoulder. |
| Opal | Cinematic | Soft pastel color with lifted shadows and pearl highlights. |
| Ember | Cinematic | Warm cinematic highlights, slate shadows and soft highlight glow. |
| Aurora | Cinematic | Cool shadows, warm highlights and restrained luminous color. |
| Daybreak | Cinematic | Peach light, open shadows and a soft highlight shoulder. |
| Blue Hour | Cinematic | Cobalt shadows with protected amber lights. |
| Velvet | Cinematic | Plum shadows, muted greens and smooth dense blacks. |
| Copper | Cinematic | Copper highlights against restrained petrol shadows. |
| Evergreen | Cinematic | Forest green shadows and quiet cream highlights. |
| Glacier | Cinematic | Clear blue whites and crisp cool contrast. |
| Sunprint | Film | Faded golden paper, subdued blue and a gentle toe. |
| Roseglass | Film | Rose tinted midtones with pearly highlights. |
| Midnight | Film | Deep indigo blacks and soft luminous highlights. |
| Chalk | Mono | High key monochrome with paper white highlights. |
| Carbon | Mono | Dense neutral black and white with a smooth S curve. |
| Blueprint | Mono | Architectural cyan ink on deep navy with bright edges. |
| Halation | Optics | Warm light spill around bright details. Adjustable highlight threshold. |
| Diffusion | Optics | A soft optical veil that retains a clear central image. |
| Prism | Optics | Radial color refraction that grows toward the frame edges. |
| Ink Wash | Illustration | Luminance contour ink over softly quantized pigment. |
| Mosaic | Illustration | Flat averaged blocks on the visible actor image, with no tile seams. |
| Heatmap | Illustration | A continuous midnight, violet, coral and gold luminance palette. |
| Pearl Bloom | Optics | Luminous pearlescent highlights with a controllable bloom veil. |
| Contour Light | Illustration | Colored outlines built from local luminance gradients. |
| Chromatic Ink | Illustration | A dark print with cyan, magenta and gold channel separations. |
| Luma Relief | Illustration | A directional embossed silver surface from scene edges. |
| Honey | Film | Golden amber midtones with soft cream whites. |
| Lavender | Film | Muted violet shadows with neutral pale highlights. |
| Celestial | Cinematic | Electric blue and turquoise shadow color with warm stars. |
| Candle | Cinematic | A rich low light amber grade with warm highlight rolloff. |
| Cel Shade | Illustration | Stepped comic colors with bold ink contours. Tune color bands and ink weight. |
| Halftone | Print | Cyan, magenta and yellow dot screens on warm paper, with adjustable dot spacing. |
| Crosshatch | Print | Layered diagonal pen strokes that build up in dark areas, with crisp ink edges. |
| Risograph | Print | Offset indigo and vermilion inks, textured cream paper and a coarse print screen. |
| Night Vision | Stylized | Bright phosphor green, lifted dark detail and a restrained surveillance scan pattern. |
| Infrared | Stylized | False-color foliage in hot coral and pink against cyan skies and pale highlights. |
| Duotone | Print | Deep indigo shadows and electric coral highlights in a graphic two-ink print. |
| Solarize | Stylized | Partial photographic tone reversal with metallic cyan and warm inverted highlights. |
| Arcade | Pixel | Four-color handheld palette with an ordered pixel dither and adjustable pixel size. |
| Red Accent | Stylized | Keeps vivid reds and warm orange accents against a dense silver monochrome image. |
| Neon Wire | Illustration | Electric cyan and magenta contours against a dark violet image, with adjustable glow. |
| Anamorphic | Optics | Long blue light streaks from bright details, with adjustable reach and strength. |

The expanded collection adds Print, Pixel and Stylized categories. Each new look
has its own tuning controls in Edit Looks > Look. Opal, Daybreak, Blue Hour,
Velvet, Sunprint, Roseglass, Lavender and Celestial now use stronger default
palettes and tonal separation. Their saved IDs remain compatible; use Intensity
to soften a look when needed. Actor texture-space behavior and scene targets
remain the same.

## Photo previews

Each tile is a fixed PNG preview of **Lake Mountain Landscape**, a real photo
by Bonnie Moreland released under CC0. The source and license are recorded in
`assets/fbactors/filters/DEMO-LICENSE.txt`. The bundled atlas is generated offline
through the actual shaders at default tuning and a fixed animation time.
Opening the library uploads that image once. It does not compile preview shaders,
copy the scene or run a recurring preview pass. The editor uses the same atlas.
Browsing and dragging previews leave saved settings unchanged until a drop is
accepted. The Look tab has an optional collapsed preview at full strength.

The main viewport shows the actual assigned clip, stack, tuning, grading and
intensity. The static photo previews do not depend on replay time or export size. GPU tests still use a synthetic pattern for reproducible checks.

## Controls

1. Drag a look onto an actor or Misc. Double click the clip to edit a stack of up to three looks.
2. Select a filter in the stack to show its tuning controls, when available.
3. Move filters up or down to change their processing order.
4. Use Intensity to blend the full effect with the original image.
5. Open the Grade tab for exposure, contrast, saturation, temperature, tint,
   vignette and grain. Reset grade preserves the filter stack.
6. Toggle Show original to compare. This is preview-only; export uses the
   enabled look even while comparison is on.

Changes save after a short pause. Each replay gets its own targeted look clips.
New replays start without look clips. The old `looks.json` file is migration input.
The Presets tab saves reusable looks at `flashback/fbactors/look-presets.json`.
Saving the same name replaces that preset; removing a preset leaves the current
replay look in place. The take bundle does not currently include looks.

Presets > Advanced > Reset this look clears the selected clip look. Reload shaders
recompiles on the next use and can retry a failed effect. Rendering failures
disable the look and show a message instead of stopping the replay.

## Rendering

Filters use the actual render target size, including export sizes. No upscaler,
supersampling or capture tiling was imported. Animated effects follow replay
ticks, so pausing holds the effect and export uses the same timeline time.
Compiled shader programs are cached; adjusting sliders does not rebuild them.

The renderer supports OpenGL 3.3 or newer. Other graphics backends leave the scene unchanged
and display a status message. Diffusion and halation operate on image color
rather than scene depth.

The shader GPU test is available with `:versions:1.21.11:gpuTest`. It creates a
hidden OpenGL context and renders all 44 filters and their thumbnail atlas without launching Minecraft.
Normal builds run the headless settings, catalog and persistence tests.

To rebuild the bundled previews after shader changes, run
`./gradlew :versions:1.21.11:gpuTest` and copy `build/demo-atlas.png` to
`src/main/resources/assets/fbactors/filters/demo-atlas.png`. Keep
`demo-atlas.json` in the same order as `catalog.json`.
