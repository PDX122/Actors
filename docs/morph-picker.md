# Morph picker

Open Change morph from an actor's right-click menu. The full scrolling mob library
has search and category filtering. Clicking an entry
shows a three-dimensional preview of that mob using the game's inventory entity
renderer and current textures. The current body is selected when the menu opens,
or Zombie for an unmodified player. Click Apply to use the selected mob.
Browsing leaves the actor unchanged.

The selected model now animates, with Play/Pause, Cycle poses and an animation
selector. Only the selected model updates, capped at 30 preview frames per second.
Each cached preview retains its entity and renderer instead of allocating them
each frame. The inspector stays beside the library in wide windows and stacks
below it in narrow windows. Both panes scroll independently.

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

Size and appearance in the inspector contains size, opacity and Hide name for the applied morph.
Restore player clears the applied morph. Locked actors can be browsed and
previewed; applying, tuning and restoring respect their track lock.

The preview demonstrates default mob appearances independently of an actor's
custom size or opacity. Existing morph files and selected actor settings retain
their format. Adapter compilation and headless cache/framing tests validate the
implementation; in-game visual review is still required.

### Actor poses

Choose a cat, wolf or parrot in the body library, then use the **Pose** buttons
above its preview and press **Apply**. Cats support **Auto**, **Standing**,
**Sitting** and **Relaxing**. Wolves and parrots support **Auto**, **Standing**
and **Sitting**. The preview updates to show the chosen pose.

These native poses affect the actor in the world and exports, and are saved
with morph selections and take bundles. **Auto** follows recorded movement and
maps crouching to sitting for these mobs. **Standing** stays upright even while
the recorded actor crouches. **Relaxing** settles a cat into its native lying
pose, including while playback is paused or after scrubbing. Changing bodies
resets the pose. Sitting and relaxing keep their legs still but retain the recorded
movement path; use a stationary take when the animal should stay in place.

Use the **Morph timeline** page to switch poses during a shot, such as standing,
then sitting, then relaxing. Gaps return to the actor's saved default pose.
Preview animation controls are independent demonstrations; a forced pose takes
priority over them.

### Morph timeline

The body library keeps **Apply** above the optional preview so a body can be
applied while paused, even if its preview is unavailable. Apply sets the actor's
default body; use **Morph timeline** for timed changes. Choose the body, pose and
start/end in seconds, then add a clip. A purple **Morph** lane appears beneath
that actor. Gaps use the default body, and **Player** clips temporarily restore
the player body. The end time is exclusive; overlaps use the latest starting
clip (later-added clips win ties). Supported bodies include imported custom models.

Drag a clip to move it or its edge to trim; double-click or right-click to edit.
The clip menu also offers mute, duplicate and delete. Actor locks protect morph
clips, and edits support editor undo/redo. Tracks save per replay in
`morph-track.json` and run during playback, seeking and export. They are separate
from take bundles. In **Auto** pose, crouching maps to sitting for cats, wolves
(including the Dog body) and parrots. Other mobs keep their native crouch behavior.

The morph picker now uses larger text and a persistent action bar. Choose a body
from the library, choose its pose on the right, then press **Apply** at the bottom.
**Use player body** resets the default. The preview is upright and visible without
expanding a section. Extra preview animation and appearance controls are grouped
below it. **Schedule a body change** opens the timeline tab.

The picker uses a compact 560 by 460 default layout with centered headings and body names,
a smaller preview and a fixed action bar. The window fits smaller screens while
keeping Apply visible. Dropdown labels sit above their fields to avoid clipping.

The default is also capped at 55 percent of viewport width and height. Drag a
window edge to resize it. Font enlargement applies only once at the window level.

### Draggable morph library

Open **Actors > Morph library...** or **Browse morph library** in a morph's
inspector. Browse bodies as cards, search by name and filter by category. The
library includes registered vanilla bodies, loaded custom models and discovered
modded mobs. Pages keep the preview cache bounded while browsing larger libraries.

Select a morph card to inspect its body and choose a supported pose. Drag the
card or inspector preview onto an actor row or its Morph lane. A selected card
carries its chosen pose; other cards start in Auto. A drop creates a five-second
clip at the mouse position, shortened to stay within the actor's take. Dropping
on the actor label uses the current playhead. Move or trim the clip afterward.
Locked actors cannot receive drops, and Undo removes a drop.

**Apply to actor** changes the displayed actor's default body and pose.
**Add clip at playhead** uses the same rules as a drop. Clips return to the saved
default when they end. Bodies without native pose overrides use recorded movement.
Unavailable thumbnails do not prevent applying or dragging a registered morph.

### Animation and pose actions

Choose **Animation / pose** in the Morph library inspector. Idle holds the legs
still, Walk and Run force a locomotion cycle, and Auto follows recorded movement.
Supported humanoids, including zombie and skeleton variants and piglins, also
have Crouch and Sitting. Humanoid Sitting uses Minecraft's seated riding posture;
position the actor appropriately for chair shots. Cats, wolves and parrots use
their native sitting poses, and cats also support Relaxing.

**Play preview** plays or pauses the inspector animation. Card thumbnails and
drag previews use the same upright texture orientation as the main preview.
Dragging a selected card or inspector preview includes its current action.
Actions save with default morph selections, take bundles and Morph timeline
clips. They change the rendered animation, not the recorded movement path.
Imported custom models retain their existing named-animation controls in the
morph editor rather than receiving unsupported humanoid poses.
