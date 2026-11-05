# Third-party event assets

## Supplied ICBM sound pack

ICBM Ogg clips come from the user's `icbm_sound_pack.zip` and newer
`icbm_audio_download.zip`. The newer pack supplies launch, incoming, separation,
ignition, mechanism, explosion, bass, crackle and wind clips. The new silo-mechanism
loop replaces the old hatch sound, missile launch also supplies sustained boost,
and the new explosion replaces the old distant blast. Only the original alert
remains because the new siren is missing. Plane, control-panel, camera and
building audio is not imported. Files are copied byte-for-byte; only the Minecraft
namespace, gain, pitch, looping and playback timing are configured by the mod.
`tools/import_icbm_sounds.py` reproduces the merge;
the source manifest and SHA-256 hashes are bundled at
`assets/fbactors/sounds/events/icbm/source.json`.

The supplied archives do not include an author/license notice. These assets
are user-supplied; no third-party author, public-domain status or MIT license
is asserted for the audio. Only ICBM events currently schedule sound.

## Atomic bomb model

This work is based on
["Atomic Bomb - Easter Freebie"](https://sketchfab.com/3d-models/atomic-bomb-easter-freebie-a3e1493da19d46fca7ae38b376bf9210)
by [Valentin Winkelmann](https://sketchfab.com/valentinwinkelmann), licensed under
[CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).

The model was simplified from 40,272 to 7,000 triangles with attribute-aware
meshoptimizer QEM, retaining source texture coordinates and shading attributes.
It was rotated, scaled, and stored in a precomputed acceleration structure. Texture maps have been resized for the bundled asset.
The original license is included at
`assets/fbactors/events/atomic_bomb/license.txt` in the mod.

## Explosion recording

["Explosive.flac"](https://freesound.org/people/qubodup/sounds/189778/)
by qubodup / Iwan Gabovitch, [CC0 / public domain](https://creativecommons.org/publicdomain/zero/1.0/).
The source page identifies it as a United States Government recording.
The mod bundles edited impact samples and a slowed, filtered, reverberated
rumble derived from this recording. Asset notices are included at
`assets/fbactors/sounds/events/license.txt`.

The falling whistle and rushing-air effect were synthesized for this mod and
are covered by the source-code MIT license.

## Nuclear cinematic recording

["S28-35 Atomic bomb explosion. then rumble.wav"](https://freesound.org/people/craigsmith/sounds/674884/)
by craigsmith, [CC0](https://creativecommons.org/publicdomain/zero/1.0/).
The author identifies this as a vintage Hollywood sound effect preserved from
USC Cinema tape, not an authenticated nuclear test field recording. The bundled
sample has been high-pass filtered, limited, and encoded as Ogg Vorbis.
The additional pressure/rumble is synthesized at runtime by EventPressure from
bounded visual-size and listener-distance parameters, under the mod's MIT license.

## Supplied ICBM model

Geometry and canvas-painted livery were adapted from the user's
`icbm-model.zip` / `icbm-model.html`. Six model components were extracted,
uniformly scaled to 31.4 m, simplified from 12,576 to 4,528 triangles and baked
with their materials. Standalone UI, demo environment and flight code were not
imported. The authoring tools use Three.js 0.128 and @napi-rs/canvas; neither is
bundled as a mod runtime dependency. See `assets/fbactors/events/icbm/source.txt`.

## Earth view

Surface imagery: [NASA Blue Marble Next Generation, July 2004](https://science.nasa.gov/earth/earth-observatory/blue-marble-next-generation/base-map/),
Reto Stockli / NASA Earth Observatory. Resized to 8192x4096 from the 21600x10800 source.
Cloud, night-light and ocean specular maps: [Solar System Scope](https://play.solarsystemscope.com/textures/),
[CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). Resized to the 8192x4096 surface and weather dimensions,
packed into RGBA channels and filtered independently per data channel into mip chains. Source URLs and credits
are included at `assets/fbactors/events/earth/source.txt`. The archival maps are
not live weather. No endorsement is implied.
