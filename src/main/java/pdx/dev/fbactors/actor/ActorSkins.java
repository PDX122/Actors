package pdx.dev.fbactors.actor;

import com.mojang.authlib.GameProfile;
import com.moulberry.flashback.FilePlayerSkin;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import org.jetbrains.annotations.Nullable;
import pdx.dev.fbactors.FlashbackActors;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import pdx.dev.fbactors.compat.Compat;

public final class ActorSkins {

    private static final String DEFAULT_SKIN_FILE = "default_skin.png";


    private record SkinFiles(@Nullable String skin, @Nullable String cape,
                             @Nullable Boolean slim) {
    }


    private static final Map<UUID, SkinFiles> FILES = new ConcurrentHashMap<>();


    private static volatile boolean filesLoaded;


    private static Path filesPath() {
        return Flashback.getDataDirectory().resolve("fbactors").resolve("skins")
            .resolve("files.json");
    }

    private ActorSkins() {
    }

    public static Path defaultSkinPath() {
        return Flashback.getDataDirectory().resolve("fbactors").resolve(DEFAULT_SKIN_FILE);
    }

    public static void applyDefault(Actor actor) {
        Path skin = defaultSkinPath();
        if (!Files.isRegularFile(skin)) {
            return;
        }

        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState == null) {
            return;
        }

        if (editorState.skinOverrideFromFile.containsKey(actor.take.id)
            || editorState.skinOverride.containsKey(actor.take.id)) {
            return;
        }

        SkinFiles current = FILES.get(actor.take.id);
        FILES.put(actor.take.id,
            new SkinFiles(skin.toString(), current == null ? null : current.cape(),
                current == null ? null : current.slim()));
        storeSkinFiles(editorState, actor);
        saveFiles();
    }

    public static void applyProfile(Actor actor, GameProfile profile) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState == null) {
            return;
        }

        FlashbackActors.LOGGER.debug(
            "SKINDBG applyProfile actor={} take={} profileId={} profileName={} hasTextures={}",
            actor.displayName(), actor.take.id, Compat.profileId(profile),
            Compat.profileName(profile), Compat.profileHasTextures(profile));
        FILES.remove(actor.take.id);
        editorState.skinOverrideFromFile.remove(actor.take.id);
        editorState.skinOverride.put(actor.take.id, profile);
        actor.take.skinName = Compat.profileName(profile);
        editorState.markDirty();
    }


    public static void sweepOverrides() {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState == null) {
            return;
        }
        for (Map.Entry<UUID, GameProfile> entry : editorState.skinOverride.entrySet()) {
            GameProfile profile = entry.getValue();
            UUID account = profile == null ? null : Compat.profileId(profile);
            if (account == null
                || pdx.dev.fbactors.skin.SkinTextures.ready(account) != null
                || Compat.profileHasTextures(profile)) {
                continue;
            }
            pdx.dev.fbactors.skin.SkinTextures.ensureTextures(profile);
        }
    }

    public static void applyFile(Actor actor, String path) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState == null || path == null || path.isBlank()) {
            return;
        }

        FlashbackActors.LOGGER.debug("SKINDBG applyFile actor={} take={} path={}",
            actor.displayName(), actor.take.id, path.trim());
        SkinFiles current = FILES.get(actor.take.id);
        FILES.put(actor.take.id, new SkinFiles(path.trim(),
            current == null ? null : current.cape(),
            current == null ? null : current.slim()));
        storeSkinFiles(editorState, actor);
        actor.take.skinName = null;
        editorState.markDirty();
        saveFiles();
    }


    public static boolean setCape(Actor actor, @Nullable String capePath) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState == null) {
            return false;
        }
        String cape = capePath == null || capePath.isBlank() ? null : capePath.trim();
        SkinFiles current = FILES.get(actor.take.id);
        String skin = current == null ? null : current.skin();
        if (skin == null) {
            Path fallback = defaultSkinPath();
            skin = Files.isRegularFile(fallback) ? fallback.toString() : null;
        }

        FlashbackActors.LOGGER.debug("SKINDBG setCape actor={} take={} cape={}", actor.displayName(),
            actor.take.id, cape);
        if (skin != null) {
            Boolean slim = current == null ? null : current.slim();
            FILES.put(actor.take.id, new SkinFiles(skin, cape, slim));
            storeSkinFiles(editorState, actor);
        } else if (cape != null) {
            FilePlayerSkin caped = Compat.defaultSkinWithCape(actor.take.id, cape);
            if (caped == null) {
                return false;
            }
            FILES.put(actor.take.id, new SkinFiles(null, cape,
                current == null ? null : current.slim()));
            try {
                editorState.skinOverride.remove(actor.take.id);
                editorState.skinOverrideFromFile.put(actor.take.id, caped);
            } catch (Exception e) {
                FlashbackActors.LOGGER.warn("Could not apply actor cape from {}", cape, e);
                return false;
            }
        } else {
            FILES.remove(actor.take.id);
            editorState.skinOverrideFromFile.remove(actor.take.id);
        }
        editorState.markDirty();
        saveFiles();
        return true;
    }


    @Nullable
    public static String capeName(Actor actor) {
        if (actor == null) {
            return null;
        }
        SkinFiles files = FILES.get(actor.take.id);
        if (files == null || files.cape() == null) {
            return null;
        }
        String name = java.nio.file.Path.of(files.cape()).getFileName().toString();
        return name.isBlank() ? null : name;
    }


    private static void storeFiles(UUID takeId, SkinFiles files, EditorState editorState) {
        if (takeId == null || files == null || editorState == null) {
            return;
        }
        if (files.skin() != null) {
            try {
                editorState.skinOverride.remove(takeId);
                editorState.skinOverrideFromFile.put(takeId,
                    Compat.fileSkinWithLook(files.skin(), files.cape(), files.slim()));
            } catch (Exception e) {
                FlashbackActors.LOGGER.warn("Could not apply actor skin from {}", files.skin(), e);
            }
            return;
        }
        if (files.cape() != null) {
            try {
                com.moulberry.flashback.FilePlayerSkin caped =
                    Compat.defaultSkinWithCape(takeId, files.cape());
                if (caped != null) {
                    editorState.skinOverride.remove(takeId);
                    editorState.skinOverrideFromFile.put(takeId, caped);
                }
            } catch (Exception e) {
                FlashbackActors.LOGGER.warn("Could not apply actor cape from {}", files.cape(), e);
            }
        }
    }


    private static void storeSkinFiles(EditorState editorState, Actor actor) {
        SkinFiles files = FILES.get(actor.take.id);
        if (files == null || files.skin() == null) {
            editorState.skinOverrideFromFile.remove(actor.take.id);
            return;
        }
        storeFiles(actor.take.id, files, editorState);
    }


    public static boolean setModel(Actor actor, @Nullable Boolean slim) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState == null) {
            return false;
        }
        SkinFiles current = FILES.get(actor.take.id);
        String skin = current == null ? null : current.skin();
        if (skin == null) {
            Path fallback = defaultSkinPath();
            skin = Files.isRegularFile(fallback) ? fallback.toString() : null;
        }
        if (skin == null) {
            return false;
        }
        FlashbackActors.LOGGER.debug("SKINDBG setModel actor={} take={} slim={}",
            actor.displayName(), actor.take.id, slim);
        FILES.put(actor.take.id, new SkinFiles(skin,
            current == null ? null : current.cape(), slim));
        storeSkinFiles(editorState, actor);
        editorState.markDirty();
        saveFiles();
        return true;
    }


    @Nullable
    public static String modelName(Actor actor) {
        if (actor == null) {
            return null;
        }
        SkinFiles files = FILES.get(actor.take.id);
        if (files == null || files.slim() == null) {
            return null;
        }
        return files.slim() ? "Slim" : "Classic";
    }

    public static void clear(Actor actor) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState == null) {
            return;
        }

        FlashbackActors.LOGGER.debug("SKINDBG clear actor={} take={}", actor.displayName(),
            actor.take.id);
        FILES.remove(actor.take.id);
        editorState.skinOverride.remove(actor.take.id);
        editorState.skinOverrideFromFile.remove(actor.take.id);
        actor.take.skinName = null;
        editorState.markDirty();
        saveFiles();
    }

    public static void applyName(Actor actor, String name) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState == null) {
            return;
        }
        if (name == null || name.isBlank()) {
            editorState.nameOverride.remove(actor.take.id);
        } else {
            editorState.nameOverride.put(actor.take.id, name.trim());
        }
        editorState.markDirty();
    }


    static int fileSkinCount() {
        return FILES.size();
    }


    public static java.util.Set<UUID> fileSkinTakeIds() {
        return new java.util.HashSet<>(FILES.keySet());
    }


    public static void loadFiles() {
        if (filesLoaded) {
            return;
        }
        filesLoaded = true;
        Path file = filesPath();
        if (!Files.isRegularFile(file)) {
            return;
        }
        String text;
        try {
            text = Files.readString(file, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            FlashbackActors.LOGGER.warn("Could not read file-skin assignments from {}", file, e);
            return;
        }
        Map<UUID, SkinFileAssignments.Entry> restored =
            SkinFileAssignments.parse(text, Files::isRegularFile);
        int kept = 0;
        for (Map.Entry<UUID, SkinFileAssignments.Entry> entry : restored.entrySet()) {
            SkinFileAssignments.Entry value = entry.getValue();
            FILES.put(entry.getKey(),
                new SkinFiles(value.skin(), value.cape(), value.slim()));
            kept++;
        }
        if (kept > 0) {
            FlashbackActors.LOGGER.info("Restored file skins for {} actor(s)", kept);
        }
    }


    private static void saveFiles() {
        Map<UUID, SkinFileAssignments.Entry> files = new java.util.LinkedHashMap<>();
        for (Map.Entry<UUID, SkinFiles> entry : FILES.entrySet()) {
            SkinFiles value = entry.getValue();
            if (value == null) {
                continue;
            }
            files.put(entry.getKey(),
                new SkinFileAssignments.Entry(value.skin(), value.cape(), value.slim()));
        }
        com.google.gson.JsonObject root = SkinFileAssignments.encode(files);
        try {
            Path file = filesPath();
            Files.createDirectories(file.getParent());
            Files.writeString(file, root + "\n", java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            FlashbackActors.LOGGER.warn("Could not save file-skin assignments", e);
        }
    }


    static void ensureLive(Actor actor) {
        try {
            if (FILES.isEmpty()) {
                return;
            }
            if (actor == null || actor.take == null) {
                return;
            }
            EditorState editorState = EditorStateManager.getCurrent();
            if (editorState == null) {
                return;
            }
            if (editorState.skinOverride.containsKey(actor.take.id)
                || editorState.skinOverrideFromFile.containsKey(actor.take.id)) {
                return;
            }
            if (!FILES.containsKey(actor.take.id)) {
                return;
            }
            restoreFiles(actor);
            applyDefault(actor);
        } catch (Exception e) {
            FlashbackActors.LOGGER.warn("Could not heal live skin entries", e);
        }
    }


    public static void ensureLiveIds() {
        try {
            if (FILES.isEmpty()) {
                return;
            }
            EditorState editorState = EditorStateManager.getCurrent();
            if (editorState == null) {
                return;
            }
            for (Map.Entry<UUID, SkinFiles> entry : FILES.entrySet()) {
                try {
                    UUID takeId = entry.getKey();
                    SkinFiles files = entry.getValue();
                    if (takeId == null || files == null) {
                        continue;
                    }
                    if (editorState.skinOverride.containsKey(takeId)
                        || editorState.skinOverrideFromFile.containsKey(takeId)) {
                        continue;
                    }
                    storeFiles(takeId, files, editorState);
                } catch (Exception ignored) {


                }
            }
        } catch (Exception e) {
            FlashbackActors.LOGGER.warn("Could not heal live skin entries", e);
        }
    }


    public static void restoreFiles(Actor actor) {
        if (actor == null) {
            return;
        }
        SkinFiles files = FILES.get(actor.take.id);
        if (files == null) {
            return;
        }
        if (files.skin() != null && !Files.isRegularFile(Path.of(files.skin()))) {
            FILES.remove(actor.take.id);
            saveFiles();
            return;
        }
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState == null) {
            return;
        }
        storeFiles(actor.take.id, files, editorState);
    }
}

