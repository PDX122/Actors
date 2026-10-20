package pdx.dev.fbactors.fx;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import pdx.dev.fbactors.compat.Compat;
import pdx.dev.fbactors.take.ActorDeath;

public final class DeathFx {

    private DeathFx() {
    }

    public static void play(ServerLevel level, Player clone, ActorDeath.Kind kind) {
        if (level == null || clone == null || kind == null) {
            return;
        }
        double x = clone.getX();
        double y = clone.getY();
        double z = clone.getZ();
        switch (kind) {
            case LIGHTNING -> {
                Compat.spawnLightning(level, x, y, z);
                Compat.sound(level, FxKinds.Ambient.THUNDER, x, y, z);
            }
            case ANVIL -> {
                Compat.spawnAnvil(level, x, y + 9.0, z);
                Compat.burst(level, FxKinds.Burst.POOF, x, y + 0.2, z, 10, 0.25);
                Compat.sound(level, FxKinds.Ambient.HURT, x, y, z);
            }
            case TNT -> {
                Compat.burst(level, FxKinds.Burst.EXPLODE, x, y + 0.6, z, 18, 0.6);
                Compat.sound(level, FxKinds.Ambient.EXPLODE, x, y, z);
            }
            case LAVA -> {
                Compat.burst(level, FxKinds.Burst.LAVA, x, y + 0.3, z, 18, 0.35);
                Compat.sound(level, FxKinds.Ambient.FIRE, x, y, z);
            }
            case WITHER -> {
                Compat.burst(level, FxKinds.Burst.WITHER, x, y + 0.8, z, 26, 0.45);
                Compat.sound(level, FxKinds.Ambient.WITHER, x, y, z);
            }
            case ARROW -> {
                Compat.burst(level, FxKinds.Burst.HIT, x, y + 1.0, z, 12, 0.5);
                Compat.sound(level, FxKinds.Ambient.HURT, x, y, z);
            }
            case DROWN -> {
                Compat.burst(level, FxKinds.Burst.BUBBLE, x, y + 0.8, z, 22, 0.4);
                Compat.sound(level, FxKinds.Ambient.DROWN, x, y, z);
            }
            case POISON -> {
                Compat.burst(level, FxKinds.Burst.INSTANT, x, y + 0.8, z, 16, 0.3);
                Compat.sound(level, FxKinds.Ambient.POISON, x, y, z);
            }
            case VOID -> {
                Compat.burst(level, FxKinds.Burst.ENDROD, x, y + 0.6, z, 22, 0.5);
                Compat.sound(level, FxKinds.Ambient.TELEPORT, x, y, z);
            }
            case METEOR -> {
                Compat.spawnLightning(level, x, y, z);
                Compat.spawnAnvil(level, x, y + 9.0, z);
                Compat.burst(level, FxKinds.Burst.EXPLODE, x, y + 0.6, z, 22, 0.6);
                Compat.sound(level, FxKinds.Ambient.THUNDER, x, y, z);
                Compat.sound(level, FxKinds.Ambient.EXPLODE, x, y, z);
            }
            default -> {
            }
        }
    }
}

