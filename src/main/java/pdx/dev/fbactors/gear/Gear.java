package pdx.dev.fbactors.gear;

import com.moulberry.flashback.Flashback;

import pdx.dev.fbactors.FlashbackActors;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;


public final class Gear {


    public static final String DIRECTORY_NAME = "gear";


    public static GearStore STORE = new GearStore();

    private static boolean loaded;

    private Gear() {
    }


    public static void install() {
        Path dir = directory();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            FlashbackActors.LOGGER.warn(
                "Could not create gear directory {}; gear will not persist", dir, e);
        }

        int actors = STORE.load(dir);
        loaded = true;


        FlashbackActors.LOGGER.info("Gear ready: {} preset(s), {} saved actor(s)",
                GearPresets.all().size(), actors);
    }


    public static Path directory() {
        return Flashback.getDataDirectory().resolve("fbactors").resolve(DIRECTORY_NAME);
    }


    public static GearStore store() {
        return STORE;
    }


    public static boolean isLoaded() {
        return loaded;
    }


    public static void save() {
        if (!STORE.isDirty()) {
            return;
        }
        try {
            int written = STORE.saveAll(directory());
            if (written > 0) {
                FlashbackActors.LOGGER.debug("Saved gear for {} actor(s) to {}",
                    written, directory());
            }
        } catch (RuntimeException e) {
            FlashbackActors.LOGGER.error("Failed to save gear overrides", e);
        }
    }


    public static GearLoadout loadoutFor(UUID takeId) {
        return STORE.loadoutFor(takeId);
    }
}

