package pdx.dev.fbactors.director;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import pdx.dev.fbactors.FlashbackActors;


public final class Director {

    private static boolean installed;

    private Director() {
    }


    public static void install() {
        if (installed) {
            return;
        }
        installed = true;
        FlashbackActors.LOGGER.info("Director ready");
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            DirectorMenu.flushPending();
            CameraPlayback.tick();
        });
    }


    public static void openMenu() {
        DirectorMenu.open();
    }
}

