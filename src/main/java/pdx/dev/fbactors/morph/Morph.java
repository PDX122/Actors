package pdx.dev.fbactors.morph;

import com.moulberry.flashback.Flashback;

import org.jetbrains.annotations.Nullable;

import pdx.dev.fbactors.FlashbackActors;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;


public final class Morph {


    private static final String RENDER_PIPELINE = "puppet1+veto1+posedrift1+swapdiag1+walkdrive1+sizemath1";


    public static final String DIRECTORY_NAME = "morphs";


    public static final MorphRegistry REGISTRY = new MorphRegistry();


    public static final MorphStore STORE = new MorphStore();

    private Morph() {
    }

    public static synchronized void install() {
        Path dir = directory();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            FlashbackActors.LOGGER.error(
                "Could not create the morph directory {}; custom morphs will not load "
                    + "or save", dir, e);
        }

        int definitions = REGISTRY.load(dir);
        if (definitions > 0) {
            FlashbackActors.LOGGER.info("Loaded {} custom morph(s) from {}", definitions, dir);
        }

        int selections = STORE.load(dir);
        if (selections > 0) {
            FlashbackActors.LOGGER.info("Loaded {} morph selection(s) from {}", selections, dir);
        }


        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK
            .register(client -> pdx.dev.fbactors.compat.Compat.syncPuppets());


        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK
            .register(client -> pdx.dev.fbactors.actor.ExportSkinProbe.clientTick());


        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK
            .register(client -> pdx.dev.fbactors.actor.ActorSkins.ensureLiveIds());


        FlashbackActors.LOGGER.info(
            "Morph ready: {} morph(s), {} render exactly as themselves (the rest need the "
                + "renderer accessor) [pipeline {}]", REGISTRY.size(),
            MorphCompatibility.available(REGISTRY).size(), RENDER_PIPELINE);
    }


    public static Path directory() {
        return Flashback.getDataDirectory().resolve("fbactors").resolve(DIRECTORY_NAME);
    }


    public static MorphRegistry registry() {
        return REGISTRY;
    }


    public static MorphStore store() {
        return STORE;
    }

    public static MorphDefinition definitionFor(@Nullable UUID takeId) {
        MorphSelection selection = STORE.selectionFor(takeId);
        if (selection == null || !selection.isMorphed()) {
            return MorphDefinition.player();
        }
        MorphDefinition found = REGISTRY.byId(selection.morphId());
        if (found == null) {
            FlashbackActors.LOGGER.warn(
                "Take {} is morph-ed as '{}', which is not in the catalogue; drawing a player",
                takeId, selection.morphId());
            return MorphDefinition.player();
        }
        return found;
    }


    public static MorphSelection selectionFor(@Nullable UUID takeId) {
        return STORE.selectionFor(takeId);
    }


    public static boolean assign(@Nullable UUID takeId, @Nullable String morphId) {
        if (takeId == null || morphId == null || morphId.isBlank()) {
            return false;
        }
        MorphDefinition found = REGISTRY.byId(morphId.trim());
        if (found == null) {


            return false;
        }
        STORE.set(takeId, MorphSelection.of(takeId, found.id()));
        return true;
    }


    public static void save() {
        if (!STORE.isDirty()) {
            return;
        }
        try {
            int written = STORE.saveAll(directory());
            FlashbackActors.LOGGER.debug("Saved morph selections for {} actor(s) to {}",
                written, directory());
        } catch (IOException | RuntimeException e) {
            FlashbackActors.LOGGER.error("Failed to save morph selections", e);
        }
    }
}
