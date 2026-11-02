# Verified fixes

## Camera exports keep the final tick

**FIXED** · 2026-11-02T04:15:34.492505+00:00 · `export-boundary-final`

Camera-only export ranges now use the exclusive clip end. The regression runs decoded 56 frames for fourteen clips on 1.21.1, 26.2 and 26.3. A single-tick clip is also covered by a unit test.


Verification: Export boundary check / 1.21.1, Export boundary check / 26.2, Export boundary check / 26.3

## ReplayMod conversion completed the full harness

**FIXED** · 2026-11-02T04:06:06.780039+00:00 · `conversion-final-2`

The 26.2 fixture now separates login, configuration and play traffic, filters connection packets, restores the local player, and translates relative entity movement. Unsupported payloads and movement formats stop before writing a replay.


Verification: ReplayMod import / 26.2

## Timeline mutation and preview state fixes verified

**FIXED** · 2026-11-02T03:45:01.668209+00:00 · `render-repair`

The actor list can be read safely while the replay thread updates it. The reruns also verify morph previews after repairing the 26.2 OpenGL state cache.


Verification: Client run / 1.21.8, Client run / 26.2

## Parallel client isolation repaired

**FIXED** · 2026-11-02T03:40:18.726938+00:00 · `production-check-3`

Each client gets its own FFmpeg native cache. Process cleanup now matches an exact version directory, so 1.21.1 does not match 1.21.10 or 1.21.11. Legacy preview matrices and unavailable morph entries were also corrected.


Verification: Client run / 1.21.1, Client run / 1.21.10, Client run / 1.21.11, Client run / 1.21.4, Client run / 1.21.5, Client run / 26.1.2, Client run / 26.3

