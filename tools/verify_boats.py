"""Check boat hooks and shared behavior against every installed game target."""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent


def verify(live=True):
    import verify_mixins as signatures
    failures = []
    registered = json.loads((ROOT / 'src/main/resources/fbactors.mixins.json').read_text())['client']
    for name in ('MixinActorBoat', 'MixinActorBoatAuthority', 'MixinBoatCamera',
                 'MixinActorEntitySpawn', 'MixinActorPacketTracker',
                 'MixinActorBoatTexture', 'ChunkMapActorAccess'):
        if name not in registered:
            failures.append('Boat support mixin missing: ' + name)
    javap = signatures.find_javap() if live else None
    if live and not javap:
        return failures + ['javap required for boat parity verification']
    baseline = None
    for folder in sorted((ROOT / 'versions').iterdir()):
        props_file = folder / 'gradle.properties'
        if not props_file.exists():
            continue
        props = signatures.read_props(props_file)
        source = (ROOT / 'compat' / props['compat'] / 'java/pdx/dev/fbactors/mixin/MixinActorBoat.java').read_text()
        # Only the Minecraft boat class/package may vary across targets.
        normalized = re.sub(r'import net.minecraft.world.entity.vehicle[\w.]*;', '', source)
        normalized = re.sub(r'@Mixin\((?:AbstractBoat|Boat)\.class\)', '@Mixin(BOAT.class)', normalized)
        normalized = re.sub(r'\s+', '', normalized)
        if baseline is None:
            baseline = normalized
        elif normalized != baseline:
            failures.append(folder.name + ': boat behavior differs from other targets')
        if not live:
            continue
        cls = re.search(r'import (net.minecraft.world.entity.vehicle[\w.]*);', source).group(1)
        text = signatures.live_shadow_text(props['mc_version'], javap, cls)
        if text is None:
            failures.append(folder.name + ': missing mapped boat class')
            continue
        for method, result, params in (
            ('controlBoat', 'void', ''), ('floatBoat', 'void', ''), ('tick', 'void', ''),
            ('setInput', 'void', 'boolean, boolean, boolean, boolean'),
            ('getRowingTime', 'float', 'int, float'), ('getPaddleState', 'boolean', 'int'),
            ('setPaddleState', 'void', 'boolean, boolean'),
            ('push', 'void', 'net.minecraft.world.entity.Entity')):
            declaration = f'{result} {method}({params});'
            if declaration not in text:
                failures.append(f'{folder.name}: missing boat hook {declaration}')
        if 'float deltaRotation;' not in text:
            failures.append(folder.name + ': missing boat steering field')
        entity = signatures.live_shadow_text(props['mc_version'], javap, 'net.minecraft.world.entity.Entity') or ''
        if not any(f'boolean {name}();' in entity for name in ('isControlledByLocalInstance', 'isLocalInstanceAuthoritative')):
            failures.append(folder.name + ': no boat authority hook matches Entity')
        camera = signatures.live_shadow_text(props['mc_version'], javap, 'net.minecraft.client.Camera') or ''
        if not re.search(r'\bvoid (?:setup|update)\(', camera):
            failures.append(folder.name + ': no Boat Cam render hook matches Camera')
        chunk_map = signatures.live_shadow_text(props['mc_version'], javap, 'net.minecraft.server.level.ChunkMap') or ''
        if 'void updateChunkTracking(net.minecraft.server.level.ServerPlayer);' not in chunk_map:
            failures.append(folder.name + ': missing replay chunk-tracking invoker target')
        texture_source = (ROOT / 'compat' / props['compat'] / 'java/pdx/dev/fbactors/mixin/MixinActorBoatTexture.java').read_text()
        renderer_class = re.search(r'@org.spongepowered.asm.mixin.Mixin\(([\w.]+)\.class\)', texture_source).group(1)
        renderer = signatures.live_shadow_text(props['mc_version'], javap, renderer_class) or ''
        wrapped = re.search(r'@com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod\(method = "(\w+)\(', texture_source).group(1)
        if not re.search(r'\bvoid ' + wrapped + r'\(', renderer):
            failures.append(folder.name + ': missing authored boat texture render hook')
        print(f'ok: {folder.name} boat hooks and shared handling/camera', flush=True)
    return failures


if __name__ == '__main__':
    failures = verify('--no-live' not in sys.argv)
    for failure in failures:
        print('FAIL: ' + failure)
    sys.exit(bool(failures))
