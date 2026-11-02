# Actor chunk loading

An actor clone is a registered server player with a silent connection. Its take
updates its position directly. Vanilla normally advances player chunk-loading
tickets when it handles movement packets, so the clone's tickets could remain
at its spawn position while the actor moved away.

Silent clones also receive default client information with a requested view
distance of two chunks. They never send a client settings packet to replace it.

`ActorChunkLoading` runs after actor movement each server tick. When a clone
crosses a section boundary, it moves the clone through the native chunk map,
updating the distance manager and releasing the old loading area. It also
refreshes the clone's chunk tracking view. `ActorClonePlayer` requests the
largest view distance among replay viewers, subject to the server's normal cap.
There are no permanent forced-chunk tickets.

Each actor anchors its own surrounding chunks. The replay viewer can remain
elsewhere, and following an actor can use the actor's loaded surroundings.
Removal and replay seeks use the normal player and chunk lifecycle. This does
not create terrain that is absent from the recording.

The opt-in boat visibility probe also exercises chunk loading: spawn an actor
near a stationary viewer, move its recorded pose 64 chunks away, then verify
its last section, tracking center, requested distance and an available neighbor
five chunks from the actor, beyond Flashback's small entity-loading radius.
