# Morph library

As of 0.11.2, Morph library replaces the legacy Choose morph window. The
Appearance menu no longer includes Choose morph, Morph timeline or Reset morph.
Choose the Player card and Apply to restore an actor's recorded player body.

Open **Actors > Morph library...** or **Morph library...** in an actor's
right-click popup. Browse bodies as cards, search by name and filter by category. The
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
Imported custom models use their supported native animation behavior.


Drag a clip to move it or drag its edge to trim it in the purple Morph lane.
Double-clicking a clip opens its body in Morph library. Right-click a clip to
mute, duplicate or delete it, or browse the library. Actor locks protect clips,
and edits support undo/redo. Gaps use the actor's default body; Player clips
temporarily restore the recorded player body. Tracks save per replay in
`morph-track.json` and run during playback, seeking and export.
