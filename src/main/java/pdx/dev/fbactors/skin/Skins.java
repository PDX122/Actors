package pdx.dev.fbactors.skin;

import com.moulberry.flashback.Flashback;
import org.jetbrains.annotations.Nullable;
import pdx.dev.fbactors.FlashbackActors;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;


public final class Skins {

    private Skins() {
    }


    private static boolean installed = false;


    private static final SkinLibrary LIBRARY = new SkinLibrary(directory());


    private static final SkinAliases ALIASES = new SkinAliases(directory());


    public static synchronized void install() {
        if (installed) {
            return;
        }
        installed = true;

        Path dir = directory();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            FlashbackActors.LOGGER.error(
                "Could not create the skin directory {}; saved skins will not load or save",
                dir, e);
        }


        LIBRARY.adoptFrom(SkinLibrary.load(dir));
        for (Map.Entry<String, SkinId> alias : SkinAliases.load(dir).all().entrySet()) {
            ALIASES.put(alias.getKey(), alias.getValue());
        }


        SkinThumbnails.invalidate();


        pdx.dev.fbactors.actor.ActorSkins.loadFiles();

        FlashbackActors.LOGGER.info("Skins ready: {} saved skin(s), {} alias(es) in {}",
            LIBRARY.size(), ALIASES.size(), dir);
    }


    public static boolean installed() {
        return installed;
    }


    public static Path directory() {
        return Flashback.getDataDirectory().resolve("fbactors").resolve("skins");
    }


    public static SkinLibrary library() {
        return LIBRARY;
    }


    public static SkinAliases aliases() {
        return ALIASES;
    }

    public static SkinHistory.Fetcher historyFetcher() {
        return SkinHistory.fetcher();
    }


    public static void setHistoryFetcher(@Nullable SkinHistory.Fetcher fetcher) {
        SkinHistory.setFetcher(fetcher);
    }


    public static List<String> builtInSlugs() {
        return List.copyOf(SkinAliases.builtInSlugs());
    }


    public static void save() {
        SkinThumbnails.invalidate();
        LIBRARY.save();
        ALIASES.save();
    }


    public static synchronized void reload() {
        Path dir = directory();
        LIBRARY.adoptFrom(SkinLibrary.load(dir));
        SkinAliases reloaded = SkinAliases.load(dir);
        ALIASES.clear();
        for (Map.Entry<String, SkinId> alias : reloaded.all().entrySet()) {
            ALIASES.put(alias.getKey(), alias.getValue());
        }
        SkinThumbnails.invalidate();
    }


    @Nullable
    public static SkinId resolve(@Nullable SkinId id) {
        if (id == null) {
            return null;
        }
        if (id.isBuiltin()) {
            SkinId builtIn = SkinAliases.resolveBuiltIn(id);
            return builtIn == null ? id : builtIn;
        }
        return ALIASES.resolve(id);
    }
}
