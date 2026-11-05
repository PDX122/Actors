# Camera modes

Open **Actors > Camera modes**. Boat Cam is now a general camera mode library
for actors on foot or in vehicles. Existing saved boat camera clips keep working.

![Camera mode library and animated Shoulder preview](assets/screenshots/camera-8.png)

## Drag a mode onto an actor

1. Pick a mode card in the **Modes** tab.
2. Drag its thumbnail, name or large preview onto an actor's timeline row.
3. A camera clip is created in **Camera modes**, following that actor for its
   take, including its offset and trim range.

You can also select an actor and choose **Add to selected actor**. Both the actor
track and the Camera modes track must be unlocked. Adding, moving, trimming,
muting, duplicating and deleting clips support editor undo/redo.

Use the group filter to browse **Tracking**, **Ice boating**, **Horse riding**
and **Cinematic** shots, or search by name and purpose. **Elytra flight** and **UAV**
are also available. The library has 38 modes. FPV and Drone dive remain disabled;
their saved clip data is retained, but these clips do not activate or extend exports.

The preview places native Minecraft characters on a clean, open perspective grid
with faded scale markings, soft contact shadows and studio lighting. It captures
the geometry and animation produced by Minecraft's renderer, so enabled EMF/ETF
packs such as Fresh Animations and the player add-on animate the preview too.
The packs stay installed as resource packs; their animation files are not bundled
into the mod. Captured poses interpolate between animation frames for smooth
playback. Pack animations use the preview clock and keep playing while the replay
is paused. Mounted shots use a riding player attached to the displayed horse,
with a saddle and Minecraft's native passenger and vehicle attachment offsets.
The preview preserves the renderer's actual player scale and body rotation.
Ice shots use Minecraft's baked oak boat hull and native paddle stroke motion,
with synchronized rider arm strokes over the pack's seated pose.
The rider uses the boat's seat attachment. A simple ice strip and hull
drift relative to travel direction make the slide readable.
Elytra shots equip wings and use the native gliding state, so the pack supplies
its gliding and wing animation, with walking motion disabled. Flight framing keeps the pilot and wings readable,
and native pitch follows the demonstration's climbs and descents. UAV shots use ordinary walking subjects; the
packs contain no UAV or drone animation.

Hover over a card to play it. The larger view has pause, replay and a time slider,
plus a top-down camera path. Playback holds at the end until replayed. Hovering
a card does not change the main preview's animation clock. Lens, height, distance and smoothing use the current
camera settings. This demonstration does not move the replay camera or use the
replay's location as its background.

## Modes

- **Follow** follows the subject from behind.
- **Side follow** tracks beside the subject.
- **Orbit** circles the subject using replay time, independently of its body turns.
- **First person** looks forward from the subject's eye level.
- **Front follow** looks back from ahead of the subject.
- **Overhead** gives a stable north-up view of its path.
- **Drift follow** follows travel direction through turns and slides.
- **Two subjects** frames a pair. Choose a different **Second subject** before
  dropping this mode onto the first actor. Both must share a dimension.

Adjust distance, height, FOV and smoothing under **Camera settings**. **Advanced motion**
contains look ahead, side offset, orbit speed, speed zoom and banking. Obstacle
avoidance can pull the camera closer, which may crop one of two subjects.
**Live follow** can temporarily follow the chosen subject outside a camera clip.
Closing the library does not stop an active live follow camera.

## Ice boating and horse riding

- **Ice chase** stays behind travel direction even when the hull rotates into a slide.
- **Ice lead** looks back from ahead of the boat, keeping corners readable.
- **Ice trackside** follows low beside the hull to emphasize lateral drift.
- **Mounted chase** frames the horse and rider from a raised rear quarter.
- **Gallop profile** shows the full mount and stride from the side.
- **Rider shoulder** looks down the trail past the rider, adapting to mounted eye height.
- **Route scout**, in Tracking, gives a high chase view with forward look ahead.

Select the player or actor riding the vehicle. The camera samples the mount's
position and the passenger's eye height. These are framing presets, so they can
also follow subjects outside their suggested use. They do not spawn a mount or
change vehicle handling. Travel-based shots fall back to body yaw at rest.

## Cinematic shots

- **Shoulder** follows close behind with a sideways offset for intimate framing.
- **Low angle** tracks near the ground and looks up at the subject.
- **Crane reveal** rises and widens to expose the surrounding scene.
- **Push in** eases closer to the subject.
- **Pull back** eases away and rises to reveal the surroundings.
- **Dolly zoom** moves back while narrowing the field of view to keep the
  subject approximately the same size as the background perspective changes.

- **Arc reveal** sweeps from the rear quarter toward the front.
- **Rise to overhead** lifts into a top-down composition.
- **Hero orbit** rises over eight seconds and keeps circling at the chosen orbit speed.

Crane reveal, Push in, Pull back, Dolly zoom, Arc reveal and Rise to overhead move over the first eight seconds
of each camera clip, then hold. Shorter clips use the opening part of the move.
Distance and height set their framing range. Scrubbing and export use the same
clip-local time, including Orbit and Hero orbit. Live follow starts its shot clock
when following begins; reopening or selecting a shot restarts the inspector preview. Obstacle avoidance can change the
composition, including the size compensation of a dolly zoom.

## Timeline and export

Camera clips contribute to Flashback's inferred export range, including projects
without native camera keys. Explicit export In / Out values still take precedence.
Trimmed exports use absolute replay time so camera clips and actor sampling stay
aligned, and Flashback's accurate camera transforms consume the active shot.

Camera clips activate as the playhead enters them and release control at their
exclusive end tick. Preview playback and export use the same fractional clock.
Drag a clip to move it, drag its edges to trim it, or double-click to edit.
Right-click a clip for edit, mute, duplicate or delete; right-click the row for
track mute, lock and delete. The clip with the latest start wins overlaps;
later-added clips win ties. Normal Flashback camera tracks resume outside clips.

The **Timeline** tab also accepts exact times and the export In / Out range.
Each clip saves its mode, tuning and subjects per replay in `boat-studio.json`.
This legacy file name stays unchanged to preserve existing projects. No baking
or native Camera/FOV key generation is needed.

## Boat handling

**Boat handling** contains the actor boat settings. The same controls are available in
**Actors > Settings > Camera & boats**:

- Propulsion scales thrust from recorded controls, preserving external impulses.
- Steering changes turning response.
- Lateral grip below 1 preserves sideways drift through native friction;
  above 1 adds sideways damping while grounded.
- Wall bounciness ranges from 0 (vanilla) to 1 (reflection without added speed).
- Paddle animation, stroke smoothing, older-take fallback and collision reactions
  can each be toggled.

Handling applies to simulated actor boats. A non-default handling setting releases
an actor boat from its recorded positions into physics using its saved controls.
Seek back to the take's beginning after changing handling. Reset vanilla handling
restores propulsion, steering and grip to 1 and bounciness to 0.

World settings control local native redstone/fluid/block entity simulation
and environmental movement reactions. Sulfur geysers use the installed game's
native implementation, so they are available only in versions containing them.
Elytra and attached firework simulation can be toggled under Playback.


Each mode starts with its own recommended distance, height, FOV and smoothing.
Use **Reset recommended framing** under Camera settings to restore those settings.
