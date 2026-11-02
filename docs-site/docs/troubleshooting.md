# Troubleshooting and error recovery



Open **Actors > Error report...** for diagnostics. Recoverable failures in guarded
editor panels, shortcuts and actor tick callbacks temporarily stop the affected
feature and open this window. Actor playback update failures also pause the replay.
**Copy log** copies the captured stack traces, version details and up to 64 KB from
`logs/latest.log`. **Retry affected feature** enables another attempt; dismissing
the window leaves the feature stopped. Reports remain available for the session.
The **Random** button deliberately throws a test exception through the same handler.

Native process aborts, JVM memory exhaustion and stack exhaustion cannot safely
recover through this window. Geometry and other native preconditions still need
their own preventive checks.


![Recoverable error window with Copy log and Retry controls](assets/screenshots/error-recovery.png)

## Start with the matching version

Confirm Minecraft, Flashback, Fabric API and Actors belong to the same supported version family. Use Java 21 for the 1.21.x builds and Java 25 for 26.x. The [install guide](install.md) lists the target families.

## A preview is empty

Check that the selected body exists in this Minecraft version. Modded morphs require their source mod. An imported asset must use a supported geometry and animation format. See [custom models](custom-mobs.md).

A failed preview can be disabled until the picker reopens. Reopen the window, then use **Copy log** if the error repeats.

## A change will not apply

Check whether the actor track or the relevant effect lane is locked. Confirm you are editing the intended actor and replay. Moving a camera, look or morph card onto a locked track is refused.

## Report an issue

Include the Minecraft version, Actors version, Flashback version, what you were doing and the copied error report or relevant log. Attach a screenshot when the problem is visual. Open the [issue tracker](https://github.com/PDX122/Actors/issues).

The [test log](test-log/) retains known failed attempts, later passes and coverage limits. A previous pass does not guarantee every hardware or third-party-mod setup.
