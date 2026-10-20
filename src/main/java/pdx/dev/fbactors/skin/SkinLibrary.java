package pdx.dev.fbactors.skin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.moulberry.flashback.Flashback;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import pdx.dev.fbactors.FlashbackActors;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;


public final class SkinLibrary {


    public static final String FILE_NAME = "library.json";


    public static final int VERSION = 1;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();


    private final Map<String, SavedSkin> byName = new TreeMap<>();

    private static final int MAX_LIBRARY_ENTRIES = 4096;

    private final Path directory;


    public SkinLibrary(Path directory) {
        this.directory = directory;
    }


    public Path directory() {
        return this.directory;
    }


    public Path file() {
        return this.directory.resolve(FILE_NAME);
    }


    public static Path defaultDirectory() {
        return Flashback.getDataDirectory().resolve("fbactors").resolve("skins");
    }


    public static boolean isValidName(@Nullable String name) {
        return SavedSkin.isValidName(name) && !isReserved(name);
    }


    public static boolean isReserved(@Nullable String name) {
        if (name == null) {
            return true;
        }
        for (String slug : SkinAliases.BUILT_IN_SLUGS) {
            if (slug.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }


    @Nullable
    public static Component nameProblem(@Nullable String name) {
        if (SavedSkin.isValidName(name) && isReserved(name)) {
            return Component.translatable("fbactors.skin.name_reserved",
                Component.literal(String.valueOf(name)));
        }
        return SavedSkin.nameProblem(name);
    }

    public SavedSkin save(SavedSkin skin) {
        if (!isValidName(skin.name())) {
            throw new IllegalArgumentException(
                "Refusing to save '" + skin.name() + "': "
                    + (isReserved(skin.name()) ? "name is reserved by a built-in skin"
                        : "invalid skin name"));
        }
        this.byName.put(skin.name(), skin);
        SkinThumbnails.invalidate(skin.name());
        return skin;
    }


    public SavedSkin save(String name, SkinId source) {
        return save(new SavedSkin(name, source, name, "", System.currentTimeMillis()));
    }


    @Nullable
    public SavedSkin remove(String name) {
        SavedSkin removed = this.byName.remove(name);
        if (removed != null) {
            SkinThumbnails.invalidate(name);
        }
        return removed;
    }


    @Nullable
    public SavedSkin rename(String from, String to) {
        SavedSkin existing = this.byName.get(from);
        if (existing == null) {
            return null;
        }
        if (!isValidName(to)) {
            throw new IllegalArgumentException("Refusing to rename to '" + to + "': "
                + (isReserved(to) ? "name is reserved by a built-in skin"
                    : "invalid skin name"));
        }
        if (this.byName.containsKey(to)) {
            throw new IllegalArgumentException(
                "Refusing to rename '" + from + "' to '" + to + "': that name is already in use");
        }

        SavedSkin renamed = existing.withName(to);


        this.byName.remove(from);
        this.byName.put(to, renamed);
        SkinThumbnails.invalidate(from);
        SkinThumbnails.invalidate(to);
        return renamed;
    }


    @Nullable
    public SavedSkin byName(@Nullable String name) {
        return name == null ? null : this.byName.get(name);
    }


    public List<SavedSkin> all() {
        return new ArrayList<>(this.byName.values());
    }


    public List<String> names() {
        return new ArrayList<>(this.byName.keySet());
    }


    public boolean contains(@Nullable String name) {
        return name != null && this.byName.containsKey(name);
    }


    public int size() {
        return this.byName.size();
    }


    public boolean isEmpty() {
        return this.byName.isEmpty();
    }


    public void clear() {
        this.byName.clear();
        SkinThumbnails.invalidate();
    }


    public void replaceAll(Collection<SavedSkin> skins) {
        this.byName.clear();
        for (SavedSkin skin : skins) {
            this.byName.put(skin.name(), skin);
        }
        SkinThumbnails.invalidate();
    }


    public void adoptFrom(SkinLibrary other) {
        this.byName.clear();
        this.byName.putAll(other.byName);
        this.readOnly = other.readOnly;
        SkinThumbnails.invalidate();
    }

    public static SkinLibrary load(Path dir) {
        SkinLibrary library = new SkinLibrary(dir);
        Path file = dir.resolve(FILE_NAME);

        if (!Files.isRegularFile(file)) {
            return library;
        }

        JsonObject root;
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (parsed == null || !parsed.isJsonObject()) {
                FlashbackActors.LOGGER.warn(
                    "Skin library at {} is not a JSON object; starting empty", file);
                return library;
            }
            root = parsed.getAsJsonObject();
        } catch (Exception e) {


            FlashbackActors.LOGGER.warn(
                "Could not read the skin library at {}; starting empty. The file has been "
                    + "left in place - move it aside to make room for a new one", file, e);
            return library;
        }

        int version = root.has("version") && root.get("version").isJsonPrimitive()
            ? root.get("version").getAsInt() : VERSION;
        if (version > VERSION) {
            library.readOnly = true;
            FlashbackActors.LOGGER.warn(
                "Skin library at {} is version {}, but this build understands version {}. "
                    + "It will not be written to - saving would drop whatever the newer "
                    + "build added", file, version, VERSION);
            return library;
        }

        int skipped = readSkins(root, library);
        if (skipped > 0) {
            FlashbackActors.LOGGER.warn("Skipped {} unreadable entr(ies) in the skin library at {}",
                skipped, file);
        }
        if (!library.isEmpty()) {
            FlashbackActors.LOGGER.info("Loaded {} saved skin(s) from {}", library.size(), file);
        }
        return library;
    }

    private static int readSkins(JsonObject root, SkinLibrary library) {
        JsonElement skins = root.get("skins");
        if (skins == null || skins.isJsonNull()) {
            return 0;
        }

        List<JsonObject> objects = new ArrayList<>();
        if (skins.isJsonArray()) {
            JsonArray array = skins.getAsJsonArray();
            for (JsonElement element : array) {
                if (element.isJsonObject()) {
                    objects.add(element.getAsJsonObject());
                }
            }
        } else if (skins.isJsonObject()) {
            objects.add(skins.getAsJsonObject());
        } else {
            return 0;
        }

        int skipped = 0;
        for (JsonObject object : objects) {
            try {
                if (library.byName.size() >= MAX_LIBRARY_ENTRIES) {
                    skipped++;
                    continue;
                }
                SavedSkin skin = readSkin(object);
                library.byName.put(skin.name(), skin);
            } catch (RuntimeException e) {

                skipped++;
            }
        }
        return skipped;
    }


    private static SavedSkin readSkin(JsonObject object) {
        String name = text(object, "name", null);
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("entry has no name");
        }

        String source = text(object, "source", null);
        if (source == null) {
            throw new IllegalArgumentException("skin '" + name + "' has no source");
        }

        long savedAt = object.has("savedAt") && object.get("savedAt").isJsonPrimitive()
            ? object.get("savedAt").getAsLong() : 0L;

        return new SavedSkin(name, SkinId.parse(source), text(object, "displayName", name),
            text(object, "note", ""), savedAt);
    }


    private static String text(JsonObject object, String key, @Nullable String fallback) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        if (element.isJsonPrimitive()) {
            return element.getAsString();
        }
        return fallback;
    }

    public boolean save() {
        Path file = file();


        if (this.readOnly) {
            FlashbackActors.LOGGER.warn(
                "Not saving the skin library to {}: it was written by a newer version of "
                    + "this mod and is loaded read-only", file);
            return false;
        }

        JsonObject root = new JsonObject();
        root.addProperty("version", VERSION);

        JsonArray skins = new JsonArray();


        for (SavedSkin skin : this.byName.values()) {
            JsonObject object = new JsonObject();
            object.addProperty("name", skin.name());
            object.addProperty("source", skin.source().value());
            object.addProperty("displayName", skin.displayName());
            object.addProperty("note", skin.note());
            object.addProperty("savedAt", skin.savedAtMillis());
            skins.add(object);
        }
        root.add("skins", skins);

        boolean ok = pdx.dev.fbactors.util.AtomicSave.writeJson(
            this.directory, FILE_NAME, writer -> GSON.toJson(root, writer));
        if (!ok) {
            FlashbackActors.LOGGER.error("Could not save the skin library to {}",
                this.directory.resolve(FILE_NAME));
            return false;
        }
        return true;
    }


    private boolean readOnly;


    public boolean isReadOnly() {
        return this.readOnly;
    }


    @Override
    public String toString() {
        return "SkinLibrary[" + this.byName.size() + " saved skin(s) at " + file() + "]";
    }
}
