# Timeline editing

Actors are arranged as takes on the replay timeline. Record another pass while existing actors play, then adjust timing in the editor.

## Arrange a take

Use the actor's timing and trim controls to place its performance. A take resolves its saved state at the current tick, so scrubbing and video export can revisit the same moment.

Actor controls include visibility, solo, locks, timing, speed and hide ranges. Lock a finished track to protect it from accidental changes.

## Add timed changes

| Lane | What it controls |
|---|---|
| Camera modes | Which actor the shot follows and the shot's framing |
| Morph | A body and selected pose over part of a take |
| Looks | Actor effects, or whole-scene effects on Misc |
| Emotes | Animation clips resolved at the playhead |

Drag a card from a library onto the intended track. Move or trim the clip to change its interval. Camera and morph edits respect track locks and support undo.

## Keep changes separate

A pose animation changes how the body looks; it does not rewrite the recorded movement. A skin or gear override changes the actor's appearance. Whole-scene Looks belong on **Misc**, while an actor-specific Look belongs on that actor's lane.

Use [sharing and storage](sharing.md) to move a cast with its stored appearance data.
