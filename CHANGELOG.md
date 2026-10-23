# Changelog

Take-format versions are the compatibility contract: a take file records the
version that wrote it, newer builds read older files (migrating where noted),
and older builds refuse newer files at the version check.

## 0.10.11

- First release built entirely in public CI (Modrinth-fetched dependencies,
  all nine targets, both test families). No code changes over 0.10.10.

## Unreleased

- Take format v30: spawn track. Performer-placed boats, minecarts, stands,
  paintings, frames and crystals record registry id, measured pos/rot and
  opaque NBT; playback re-spawns them with scrub-backwards prune and despawn
  cleanup. Capture via addFreshEntity hook correlated with recent uses.
- Take format v29: discrete uses. Right-click BLOCK/ENTITY/AIR records pos,
  target UUID and hand via packet-hook mixins (attack-skipped); playback
  swings the correct arm.
- Take format v28: sound cues. Performer-caused `playSound` broadcasts are
  recorded (id, position, volume, pitch, source) and rebroadcast on playback;
  block breaks replay the vanilla destroy event for particles plus sound.
- Take format v27: ride transitions. Mounts and dismounts record the vehicle
  identity; clones re-board the same entity.
- Container recording enabled: slot, open, and close capture mixins registered;
  block-entity pre-break capture retargeted to `Level`.
- Chest playback: correct menu type per block entity, lid animation, open and
  close sounds across all nine targets.
- `Watermark` licence notice, logged once at startup.
- Public CI (static checks) and a self-hosted release workflow.
