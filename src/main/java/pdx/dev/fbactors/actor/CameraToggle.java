package pdx.dev.fbactors.actor;

import pdx.dev.fbactors.compat.Compat;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;


public final class CameraToggle {


    private static volatile boolean thirdPerson = false;

    private CameraToggle() {
    }


    public static void toggle() {
        thirdPerson = !thirdPerson;
    }

    public static boolean isThirdPerson() {
        return thirdPerson;
    }

    public static void applyCamera(ServerPlayer player, ServerLevel level) {
        if (thirdPerson) {
            Compat.setCamera(player, player);
        } else {
            Compat.setCamera(player, null);
        }
    }
}

