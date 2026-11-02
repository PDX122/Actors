# Movement and PvP validation

Vanilla behavior is version-specific. This pass checks the mapped Minecraft
classes shipped in the local Loom cache (`Player`, `ServerPlayer`, `LocalPlayer`,
`LivingEntity`, `Entity` and `SwingAnimation`), plus Flashback's replay overrides.
Damage, armor, enchantments, blocking and knockback continue through vanilla's
attack/damage methods.

Primary references:

- [Java 1.21: mace and smash attacks](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21)
- [Java 1.21.5: weapon and blocks_attacks components](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-5)
- [Java 1.21.11: spear, custom attack range and use effects](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-11)
- [Java 26.3 changes](https://www.minecraft.net/en-us/article/minecraft-java-edition-26-3)

## Changes to exercise

| Area | Implementation change | Manual comparison |
| --- | --- | --- |
| Preparation | Capture building and placed entities before countdown; carry them into the take | Build an ice lane and place a boat before recording, then rewind |
| Movement input | Publish client-computed axes and jump state to the replay server; compensate once for vanilla's input drag | Walk, diagonal strafe, sneak, sprint jump, jump while blocking, then get knocked off the path |
| Sprint | Preserve the resolved sprint state in the recording | Sprint jump, sprint reset between hits, sprint swim |
| Riding | Unfreeze ridden vehicles on client/server; preserve UUID, saved data and passenger seat offset; spawn before mount | Board a newly placed boat, drive on ice, turn, dismount, rewind |
| Swing animation | Remove the extra swing tick; capture swing requests rather than just false-to-true transitions | Single swing, rapid repeated swings, mining, offhand interactions |
| 26.3 swings | Save animation type and duration | Compare spear jab with normal sword swing |
| Attack charge | Capture charge at attack time; retain fractional weapon recharge delay | Compare spam hits and fully charged sword/axe hits |
| Criticals and mace | Capture actual fall distance and sprint state at attack time | Compare rising/falling hits and mace attacks from different heights |
| Reach | Use the target version's vanilla attack-range API | Test sword and spear reach, corners, walls and target hitbox edges |
| Damage | Survival clones; vanilla performer damage while recording; latch damage only after an accepted hit | Shield front/back, shield disable, armor, absorption, repeated hits inside immunity window |
| Knockback | Restore vanilla hurt/motion signaling and retain recorded impulses | Standing/sprint hits, knockback enchantments, air hits, ledges |
| Arrows | Unfreeze performer/actor projectiles; capture arrows when successfully added | Short/long range, partial/full draw, moving shooter, hit while recording |
| Actor interactions | Prevent playback attacks from being captured as performer attacks | Record a second actor while the first fights, then replay several times |

## Test procedure

Record a **new** take for these checks. Older takes cannot recover input, swing
requests or attack-time conditions that were never saved. Format 32 stores
attack-time charge/fall distance/sprint state and 26.3 swing type/duration;
older supported takes still load with their existing fallback behavior. Format 33 also preserves the concrete arrow type and saved projectile state (including ammo and weapon/enchantment data).

For each case compare live recording, forward playback, a backward seek followed
by forward playback, and export. Confirm positions, timing, health, knockback and
arm motion. Repeat attacks on the same timeline to check that damage is stable.

Compilation and file-format tests do **not** establish gameplay parity. The
manual comparisons above remain pending until exercised in the client. Special
arrow payloads/enchantment fidelity, long-range seeks through projectile flight,
vehicle steering orientation, hunger history and all environmental PvP tools
(crystals, anchors, explosives, liquids, webs, pearls and wind charges) need
separate live comparisons before claiming complete Minecraft parity.

Game clients opened for manual review stay open until the user closes them.

## Physical interaction follow-up

- Vanilla's player knockback reset is bypassed only for actor clones. Their
  velocity must remain on the server because they have no controlling client.
- Knockback, pushes and boat collisions release the recorded movement baseline.
  A displaced builder no longer applies timed block-state changes. Block uses
  require the current eye ray to hit the recorded support within vanilla reach;
  vanilla then validates the placement. Container data and placed-entity tracks
  also stop forcing recorded results onto a displaced actor.
- Actor-driven boats run native buoyancy, ice friction, steering and collision
  code on the server. Passenger reseating does not tick the clone twice.
- Spawn eggs and placed vehicles can be captured during preparation or recording.
- Required live scenario: build on a supporting actor, strike the support off
  the tower, and verify that building stops when the support is no longer in
  reach/aim. Repeat using a wind charge; then test boat-to-boat bumps on ice,
  tipped/spectral arrows, a mace drop, and spear jab/charge attacks.

## Combat controller replacement and actor positioning

Combat bookkeeping now lives in `ActorCombat`, separate from the unchanged
movement input/travel driver. Accepted damage is captured at the damage call;
final impulses are captured at the server-tick boundary. The old next-tick
health-diff recorder and duplicate impulse subtraction/suppression code were
removed. Playback delivers every receipt at its integration boundary, and
observed combat states are cached for seek recovery. Unknown seek destinations
still require live comparison; compilation alone does not establish fidelity.

Pause the replay and left-click an actor in the viewport to show X/Y/Z move handles. Right-click keeps the context menu; its **Move actor** action closes the menu and activates the same handles. Gizmo selection persists independently of the popup.
Drag a handle to translate the actor's complete recorded path. Track locks and
recording mode prevent edits. Releasing saves a single undoable edit; undo/redo
restores the path. Position edits preserve the recorded displacements, timing,
rotations and poses. World edits remain at their authored locations.

Manual checks: select each actor; drag all three axes; undo and redo; scrub and
reopen to confirm persistence. Compare a sword hit, sprint hit, rising/falling
hit, wind-charge push and rapid repeated hits before and after a backward seek.
