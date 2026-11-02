# Release QA evidence

**Batch evidence collected. Review visual-quality.json and the comparison gallery before release sign-off.**

Physical audio and authenticated skin-service checks require an environment with those services available. Conversion evidence is recorded separately from native replay results.

Generated from saved harness verdicts, JUnit XML, framebuffer captures and decoded video metadata.

A client PASS requires fresh recording and export evidence as well as completed screenshots. Visual approval is recorded separately.

| Minecraft target | Unit tests | Replay harness | Recording | Camera MP4 | UI captures | Visual issues |
|---|---:|---|---|---|---:|---:|
| 1.21.1 | 516, 0 failures | PASS | PASS | PASS | 117 | 0 |
| 1.21.4 | 516, 0 failures | PASS | PASS | PASS | 117 | 0 |
| 1.21.5 | 516, 0 failures | PASS | PASS | PASS | 117 | 0 |
| 1.21.8 | 516, 0 failures | PASS | PASS | PASS | 117 | 0 |
| 1.21.10 | 516, 0 failures | PASS | PASS | PASS | 118 | 0 |
| 1.21.11 | 516, 0 failures | PASS | PASS | PASS | 125 | 0 |
| 26.1 | 516, 0 failures | PASS | PASS | PASS | 125 | 0 |
| 26.2 | 516, 0 failures | PASS | PASS | PASS | 125 | 0 |
| 26.3 | 516, 0 failures | PASS | PASS | PASS | 125 | 0 |

## ReplayMod conversion fixture

26.2-converted: VERDICT=PASS. See conversion.txt, camera-export.txt and the visual captures in that folder.


## Export boundary regression

1.21.1-boundary: VERDICT=PASS. Expected 56 frames for all fourteen camera clips; decoded video evidence is saved with the supplemental run.


## Export boundary regression

26.2-boundary: VERDICT=PASS. Expected 56 frames for all fourteen camera clips; decoded video evidence is saved with the supplemental run.


## Export boundary regression

26.3-boundary: VERDICT=PASS. Expected 56 frames for all fourteen camera clips; decoded video evidence is saved with the supplemental run.


## Coverage

- Every target: unit tests across actor simulation, camera math and export timing, take editing and storage, undo, emotes, morphs, equipment, skin lookup validation, voice data, conversion, timeline, UI geometry and failure recovery.
- Real replay harness: standing/walking/flying actor playback, morph render state, gear override, emote resolution, clone identity and recorded path continuity.
- Real recorder: setup confirmation, countdown, scripted performer movement and crouch/sprint inputs, item-use capture, recording stop, take binary roundtrip and sharing bundle roundtrip.
- Real export: H.264 MP4, nonzero start tick, fourteen camera clips and accurate-position telemetry. ffprobe checks the encoded stream and ffmpeg extracts a frame for each mode.
- UI framebuffer captures: nine settings pages, cameras and timeline, boat handling, looks, effects, actor setup, skins, actor morph picker, body/action combinations, subtitles, themes, changelog, credits and deliberate recovery. Animated actions retain a second frame.
- Separate GPU suites: all look shaders through OpenGL and native Vulkan, alpha/state restoration, actor masking and preview geometry.

## Coverage limits

These checks do not prove physical microphone capture, audible output, live authenticated skin services, arbitrary third-party mod combinations, every custom user model/shader, or mouse drag and keyboard interaction end to end. Scripted calls and unit tests cover the underlying handlers; opening a window is not a successful interaction test.

A complete production sign-off requires visual review of the contact sheets and explicit disposition of these gaps. No automatic production-ready verdict is inferred from compilation or screenshots alone.
