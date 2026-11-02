# Release testing

Run from the repository root on Windows with the configured Java toolchains.

To browse the saved test history, start the preview:

```powershell
py -3.13 tools/serve_qa.py
```

Open `http://127.0.0.1:8765`. The test log lists saved client runs, build failures,
visual flags, verified fixes and every individual result in the latest JUnit XML.
Search or filter by Minecraft version, and expand a record for its error context.
Earlier failures remain visible when later runs pass. Older per-test XML was
overwritten by later runs, so the individual test inventory reflects the latest run.

Export portable snapshots for GitHub Pages and GitHub Wiki with:

```powershell
py -3.13 tools/export_qa_site.py
```

Commit `docs-site/test-log/` with the site changes. The Documentation workflow
runs `docs-site/build.py`, which integrates the snapshot into the shared docs
layout at `test-log/`. All asset URLs are relative, including under `/Actors/test-log/`.
The export needs the local evidence; hosting the snapshot needs no Python, game
clients, private Flashback JARs or build output. Local user paths in failure
excerpts are replaced with placeholders. Restart the preview after rebuilding
the evidence index. `--qa-root` selects another saved final matrix.
The same command writes `wiki-export/`: the migrated feature guides plus pages
for every saved run, failures, fixes, images and individual tests, with their
referenced image files.
Those files work in the separate `Actors.wiki.git` repository without JavaScript.
See [wiki publishing](wiki-publishing.md) for the later publishing step.
The site vendors Lucide SVGs and Inter font files with their licenses, so the
interface does not depend on a font or icon CDN. Generated text replaces em
dashes before either snapshot is written.
The site requires Pillow; the optional browser check requires Playwright Chromium:

```powershell
py -3.13 tools/qa_site/check_browser.py
```

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File tools/release_qa.ps1
# Or select targets and a concurrency limit directly:
py -3.13 tools/release_batch.py --versions 1.21.1 26.2 26.3 --jobs 3
```

The harness boots actual clients, waits for resource loading, opens an isolated
copy of a same-version replay, and drives the mod through its internal APIs.
It uses a fresh replay UUID to protect existing actor and timeline sidecars.
It never records microphone input. Clients run on the target's own Java version.

The batch runner builds and tests all targets, verifies mixin signatures and runs
the OpenGL and native GPU suites before starting independent clients concurrently.
Do not run a separate Gradle build against the same checkout during a batch;
concurrent compilers can replace class files while another compiler reads them.
Automatic
parallelism reserves RAM for the desktop and limits the number of clients to four.
Each client has an isolated native library cache and output directory. A failed
client does not stop the remaining targets. Use `--jobs` or `-Parallelism` to
override the limit, and `--output` to select an evidence directory.

Evidence lives in `build/release-batches/<timestamp>/<target>/`: launcher logs, replay self-test,
boat and chunk-loading probes, recording and sharing roundtrips, a real H.264
camera export and camera transform telemetry, plus editor framebuffer captures.
The report generator decodes the MP4, extracts each camera mode, and assembles
UI and video contact sheets. Framebuffer capture works without desktop control.
The batch also checks for missing or blank captures, empty morph previews and
unchanging walking/running previews. Its `review/index.html` groups each feature
across all versions for one visual review. These checks flag obvious failures;
the comparison gallery still needs human review.
Use `py -3.13 tools/release_batch.py --review-only --output <batch-directory>`
to rebuild reports and comparison sheets without launching any clients.

```powershell
# Keep one client open after its verdict.
powershell -ExecutionPolicy Bypass -File tools/harness.ps1 -Version 1.21.11 -KeepOpen

# Supplemental checks with captions, voice setup and studio tabs enabled.
powershell -ExecutionPolicy Bypass -File tools/harness.ps1 -Version 26.3 -VisualFocus

# Convert a real ReplayMod recording with the current code, then test its playback.
powershell -ExecutionPolicy Bypass -File tools/harness.ps1 -Version 26.2 -ConvertInput C:\fixtures\example.mcpr
```

`-VisualFocus` and conversion runs use separate evidence directories. A failed
or timed-out client is not a pass. Empty or disabled feature windows do not
establish visual coverage. Review all contact sheets and retain the individual
frames for inspection. Walking and running previews have paired captures.

The automated suites cover the underlying editing, undo, storage, simulation,
filter and library handlers. Physical microphones, audible playback, authenticated
online skin lookup, arbitrary third-party mods and arbitrary user models/shaders
need separate environment-specific checks. Synthetic handler calls do not prove
mouse drag and keyboard interactions end to end. Record any remaining gap in the
release report rather than claiming complete production sign-off.
