# Export video

Use Flashback's video export after arranging your actors, camera clips and effects. Custom camera modes contribute to the export range and are evaluated during the real render.

## Prepare the timeline

1. Put the actors and their timing where you want them.
2. Add and tune [camera clips](camera-modes.md). For **Two subjects**, choose the second subject and keep both in the same dimension.
3. Scrub across clip boundaries and check framing.
4. Open Flashback's export dialog, choose the range and output settings, then export.

## Camera timing

Camera clips use replay ticks. Exports starting at a nonzero tick include that range offset, so the camera follows the same portion of the take you previewed.

Camera-only clips extend the inferred export range. The final clip end is exclusive: a one-tick clip covers one output tick rather than an empty interval. Camera smoothing resets between preview and export.

## Check the output

Inspect the encoded video as well as the editor preview. Confirm the camera changes where expected and the selected morphs, poses and effects appear. The release harness exports fourteen camera clips; its boundary regression decodes 56 frames across all modes.

## Import a ReplayMod recording

Conversion is same-version only. Format 14 conversion separates login, configuration and play packets, restores the local player and translates supported relative movement before writing a Flashback replay.

Unsupported signed chat, relative teleports, custom payloads or movement schemas stop conversion with an explanation instead of writing a known-bad replay. Full runtime conversion coverage currently comes from a real 26.2 fixture. Keep the original recording.

See the [test log](test-log/) for the converted fixture's playback and export results.
