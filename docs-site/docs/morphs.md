# Morphs and poses

Open **Actors > Morph library** to browse bodies as draggable cards. Search by name or filter the library, select a body, then choose an animation or pose in its inspector.

![Cat selected in the Morph library with the Sitting pose](assets/screenshots/morph-cat-sitting.png)

In 0.11.2, Morph library replaces the legacy Choose morph window. The actor Appearance menu no longer includes Choose morph, Morph timeline or Reset morph. Select the Player card and apply it to restore the recorded player body.

## Apply a body or make a clip

Choose a default body/pose for an actor, or drag a card onto an actor or its **Morph** lane to make a timed clip. Cards carry the selected body and pose together. Move or trim the resulting clip on the timeline.

Locked actor tracks cannot be changed. Clips respect take bounds and support editor undo. The library opens on the page containing the selected body.

## Choose an animation or pose

| Choice | Behavior |
|---|---|
| Auto | Follows recorded movement and native idle animation |
| Idle | Keeps the legs still while native idle motion continues |
| Standing | Holds an upright body even if the recorded performer crouches |
| Walk | Plays a walking cycle without changing the recorded path |
| Run | Plays a running cycle without changing the recorded path |
| Crouch | Holds a crouched humanoid pose where supported |
| Sitting | Uses the supported animal sit, or a seated humanoid pose |
| Relaxing | Uses the cat's native lying pose |

Only choices supported by the selected body appear. Cats, wolves and parrots have native sitting options. Cats also support Relaxing. Humanoid sitting places the legs as if seated on a chair; it does not create a chair or change the actor's movement path.

![Zombie using the seated humanoid pose](assets/screenshots/morph-zombie-sitting.png)

## Preview playback

The inspector previews the selected body and action. Use **Play preview** to run or pause it. The preview has its own clock and does not move the actor in the replay.

Recorded playback, scrubbing and export resolve the saved selection at the replay tick. Use [custom models](custom-mobs.md) for imported animation clips, or [skins](skins.md) to keep a player body with another appearance.

## Version differences

The library omits built-in bodies unavailable in the running Minecraft version. A modded body also needs its source mod and renderer installed. Reopen the library after a preview failure to retry it, and check [error recovery](troubleshooting.md).
