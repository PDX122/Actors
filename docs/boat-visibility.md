# Invisible actor boats in BoatLabs replays

Investigated October 27, 2026 on Minecraft 1.21.10 and Flashback 0.39.10.

The BoatLabs replay loads a server resource pack. In the tested pack,
`assets/minecraft/textures/entity/boat/oak.png` is fully transparent: every pixel
has zero alpha. A boat created in actor mode reaches the client and can be
mounted normally, but its hull uses that transparent texture. The private-world
replay does not load that pack, so the same vanilla boat is visible there.

BoatLabs offers custom boat cosmetics, as described in the maintainer's
[TimingSystem README](https://github.com/Makkuusen/TimingSystem). Replacing the
vanilla hull with server-provided cosmetics is consistent with the pack contents;
the exact server plugin implementation was not available for verification.

The local profile contains Fabric API, Flashback and Actors, without
OpenBoatUtils. Although [BoatLabs supports track-specific OpenBoatUtils settings](https://boatlabs-wiki.github.io/track-making.html),
OBU is not needed to explain this disappearance.

## Fix

Track boats spawned by Actors separately from the replay's recorded entities.
During rendering of an authored boat, inspect the active hull texture. If every
pixel is transparent, load the matching hull from the installed vanilla resource
pack and register a separate texture. Preserve partially transparent and opaque
textures. Recorded server boats continue using their original cosmetics.

The fallback is cached by resource-pack identity and texture registration, so
changing packs or reloading textures invalidates it. Vanilla assets are read at
runtime; the mod does not bundle copies of Minecraft textures.

## Reproduction check

The opt-in `BoatVisibilitySelfTest` opens actor prep in a replay, creates and
mounts a boat, waits for the server pack to load, and checks the client boat,
mount, transparent source, renderer-created fallback, visible fallback pixels,
and restoration of the original texture outside the authored render scope.
It then creates an actor near a stationary viewer and moves that actor far
outside the viewer's loading area. The check verifies the actor's tracking
center and server chunk availability beyond Flashback's three-chunk entity
ticket radius, while the viewer stays in place.

Set `FBACTORS_HARNESS_REPLAY` to the replay ZIP and
`FBACTORS_BOAT_VISIBILITY_TEST` to an output verdict path before launching the
matching dev client. Use a disposable dev profile: the probe enters actor mode
and changes its camera perspective. It is inactive without that environment
variable. Headless tests cover alpha detection, including preservation of a
texture with just one visible pixel.
