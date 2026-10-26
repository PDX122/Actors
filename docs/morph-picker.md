# Morph picker

Open Change morph from an actor's right-click menu. Browse is a compact mob list
with search, category filtering and twelve entries per page. Clicking an entry
shows a three-dimensional preview of that mob using the game's inventory entity
renderer and current textures. Opening the menu alone does not render any mob.
Click Apply morph to use the selected mob. Browsing leaves the actor unchanged.

The selected model now animates, with Play/Pause, Cycle poses and an animation
selector. Only the selected model updates, capped at 30 preview frames per second.
Each cached preview retains its entity and renderer instead of allocating them
each frame. Opening the picker without a selection renders nothing.

Automatic cycling demonstrates idle, walking, running, attacking, hurt and death.
Crouch, swimming and flight poses are included where appropriate. Available growth
stages include baby idle, baby walking and adult. Iron golems demonstrate four
health/crack stages. Sheep include all sixteen wool colors and sheared appearance;
slimes and magma cubes include three sizes; tameable mobs include sitting and
standing. Framing stays fixed so baby and size stages can be compared.

The selector also includes native AnimationState channels exposed by the mob's
class, including private fields. Names are readable where runtime mappings retain
them; otherwise numbered Mob animation labels identify them. These channels run
through the real renderer. Pose demos cycle every 3.5 seconds; native sequences
have twelve seconds before looping so longer clips can finish. Manual selection
disables automatic pose cycling, and Pause freezes the selected pose.

The preview does not run world AI or tick the entity in the replay world. Behavior
requiring a live AI goal, world interaction, an animation system other than
AnimationState or a custom renderer's extra state is not automatically enumerated.
The cache holds eight models and releases them after closing or changing levels.
Reopen the picker after changing resource packs to refresh previews. Missing mobs,
nonliving projectiles and unsupported custom models display Model unavailable.

Morphed actors now keep independent stride histories. Previous/current speed values
remain separate for render interpolation, movement starts and stops ease, and the
walk phase stays continuous through turns. Teleports and invalid samples reset the
stride instead of producing a lunge. Stationary packet jitter does not advance it.

The Tuning tab contains size, opacity and Hide name for the applied morph.
Restore player clears the applied morph. Locked actors can be browsed and
previewed; applying, tuning and restoring respect their track lock.

The preview demonstrates default mob appearances independently of an actor's
custom size or opacity. Existing morph files and selected actor settings retain
their format. Adapter compilation and headless cache/framing tests validate the
implementation; in-game visual review is still required.
