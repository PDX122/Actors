# Gear and emotes

Appearance overrides are stored beside the take and resolved at the current tick. Changing gear or an emote does not require re-recording the actor's movement.

## Gear

Use per-slot replacements or a loadout preset to change an actor's equipment. Gear supports enchantments and full-stack snapshots. `/actor gear copy` captures equipment data from the current context for reuse.

Check both held items and armor when scrubbing. Keep the intended actor selected and unlock its track before making changes.

## Emotes

Built-in and supported custom keyframed emotes can be placed as clips on an actor's track. Clips can blend into and out of a performance. Their timing follows the replay playhead.

Use [Morphs and poses](morphs.md) for native mob sitting or relaxing. Imported model animation clips are documented under [Custom models](custom-mobs.md).
