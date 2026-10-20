package pdx.dev.fbactors.morph;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import org.jetbrains.annotations.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;


public final class MorphStore {


    private static final Logger LOGGER = LoggerFactory.getLogger("fbactors");


    public static final int VERSION = 1;


    static final String FILE_NAME = "selections.json";


    private final Map<UUID, MorphSelection> selections = new java.util.concurrent.ConcurrentHashMap<>();


    private volatile boolean dirty;


    public MorphSelection selectionFor(@Nullable UUID takeId) {
        if (takeId == null) {


            return null;
        }
        MorphSelection found = this.selections.get(takeId);
        return found == null ? MorphSelection.none(takeId) : found;
    }


    public void set(@Nullable UUID takeId, @Nullable MorphSelection selection) {
        if (takeId == null || selection == null) {
            return;
        }
        if (!selection.isMorphed()) {
            clear(takeId);
            return;
        }


        this.selections.put(takeId, new MorphSelection(takeId, selection.morphId(),
            selection.scale(), selection.alpha(), selection.hideName()));
        this.dirty = true;
    }


    public boolean clear(@Nullable UUID takeId) {
        if (takeId == null) {
            return false;
        }


        boolean removed = this.selections.remove(takeId) != null;
        this.dirty |= removed;
        return removed;
    }


    public Set<UUID> morphedTakeIds() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(this.selections.keySet()));
    }


    public int size() {
        return this.selections.size();
    }


    public boolean isDirty() {
        return this.dirty;
    }


    public void markSaved() {
        this.dirty = false;
    }


    public void clearAll() {
        if (!this.selections.isEmpty()) {
            this.selections.clear();
            this.dirty = true;
        }
    }

    public int load(Path dir) {
        if (dir == null) {
            return 0;
        }
        Path file = dir.resolve(FILE_NAME);
        if (!Files.isRegularFile(file)) {


            return 0;
        }

        this.selections.clear();

        String text;
        try (BufferedReader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            if (Files.size(file) > 32L * 1024L * 1024L) {
                LOGGER.warn("{} too large; no morph selections loaded", file);
                return 0;
            }
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                sb.append(buf, 0, n);
            }
            text = sb.toString();
        } catch (IOException e) {


            LOGGER.warn("Could not read {}", file, e);
            return 0;
        }

        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(text);
        } catch (JsonSyntaxException e) {
            LOGGER.warn("{} is not valid JSON; no morph selections loaded",
                file, e);
            return 0;
        }


        if (!parsed.isJsonObject()) {
            LOGGER.warn("{} must contain an object; no morph selections loaded",
                file);
            return 0;
        }

        JsonObject root = parsed.getAsJsonObject();
        int version = intOf(root, "version", VERSION);
        if (version > VERSION) {


            LOGGER.warn(
                "{} is version {}, but this build only understands up to {}; "
                    + "no morph selections loaded", file, version, VERSION);
            return 0;
        }

        int loaded = 0;
        JsonArray array = root.getAsJsonArray("actors");
        if (array == null || !array.isJsonArray()) {


            this.dirty = false;
            return 0;
        }
        for (JsonElement element : array) {
            if (loaded >= 4096) {
                break;
            }
            MorphSelection selection = readSelection(element);
            if (selection != null) {
                this.selections.put(selection.takeId(), selection);
                loaded++;
            }
        }


        this.dirty = false;
        return loaded;
    }

    public int saveAll(Path dir) throws IOException {
        Files.createDirectories(dir);
        Path file = dir.resolve(FILE_NAME);

        JsonObject root = new JsonObject();
        root.addProperty("version", VERSION);
        JsonArray actors = new JsonArray();


        List<UUID> keys = new ArrayList<>(this.selections.keySet());
        keys.sort(Comparator.comparing(UUID::toString));
        for (UUID key : keys) {
            MorphSelection selection = this.selections.get(key);


            if (selection != null && selection.isMorphed()) {
                actors.add(writeSelection(selection));
            }
        }
        root.add("actors", actors);

        try (BufferedWriter out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            out.write(root.toString());
            out.write('\n');
        }
        this.dirty = false;
        return actors.size();
    }


    private static JsonObject writeSelection(MorphSelection selection) {
        JsonObject object = new JsonObject();
        object.addProperty("take", selection.takeId().toString());
        object.addProperty("morph", selection.morphId());
        object.addProperty("scale", selection.scale());
        object.addProperty("alpha", selection.alpha());
        object.addProperty("hideName", selection.hideName());
        return object;
    }


    @Nullable
    public JsonObject exportSelection(@Nullable UUID takeId) {
        if (takeId == null) {
            return null;
        }
        MorphSelection selection = this.selections.get(takeId);
        if (selection == null || !selection.isMorphed()) {
            return null;
        }
        JsonObject object = new JsonObject();
        object.addProperty("morph", selection.morphId());
        object.addProperty("scale", selection.scale());
        object.addProperty("alpha", selection.alpha());
        object.addProperty("hideName", selection.hideName());
        return object;
    }


    public boolean importSelection(@Nullable String key, @Nullable JsonElement value) {
        if (key == null || key.isBlank() || value == null || !value.isJsonObject()) {
            return false;
        }
        UUID takeId;
        try {
            takeId = UUID.fromString(key.trim());
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Skipping a morph bundle entry whose take id '{}' is not a UUID", key);
            return false;
        }
        JsonObject object = value.getAsJsonObject();
        String morphId = textOf(object, "morph", null);
        if (morphId == null || morphId.isBlank()) {
            return false;
        }


        float scale = (float) numberOf(object, "scale", MorphSelection.DEFAULT_SCALE);
        float alpha = (float) numberOf(object, "alpha", MorphSelection.DEFAULT_ALPHA);
        boolean hideName = boolOf(object, "hideName", false);
        this.selections.put(takeId,
            new MorphSelection(takeId, morphId.trim(), scale, alpha, hideName));
        this.dirty = true;
        return true;
    }

    @Nullable
    private static MorphSelection readSelection(JsonElement element) {
        if (element == null || !element.isJsonObject()) {
            return null;
        }
        JsonObject object = element.getAsJsonObject();

        String takeText = textOf(object, "take", null);
        if (takeText == null || takeText.isBlank()) {
            LOGGER.warn("Skipping a morph entry with no 'take' id");
            return null;
        }
        UUID takeId;
        try {
            takeId = UUID.fromString(takeText.trim());
        } catch (IllegalArgumentException e) {


            LOGGER.warn(
                "Skipping a morph entry whose take id '{}' is not a UUID", takeText);
            return null;
        }

        String morphId = textOf(object, "morph", null);
        if (morphId == null || morphId.isBlank()) {


            return null;
        }


        float scale = (float) numberOf(object, "scale", MorphSelection.DEFAULT_SCALE);
        float alpha = (float) numberOf(object, "alpha", MorphSelection.DEFAULT_ALPHA);
        boolean hideName = boolOf(object, "hideName", false);

        return new MorphSelection(takeId, morphId.trim(), scale, alpha, hideName);
    }


    @Nullable
    private static String textOf(JsonObject object, String key, @Nullable String fallback) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return fallback;
        }
        return element.getAsString();
    }


    private static double numberOf(JsonObject object, String key, double fallback) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return fallback;
        }
        try {
            double value = element.getAsDouble();
            return Double.isFinite(value) ? value : fallback;
        } catch (NumberFormatException e) {
            return fallback;
        }
    }


    private static int intOf(JsonObject object, String key, int fallback) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return fallback;
        }
        try {
            return element.getAsInt();
        } catch (NumberFormatException e) {
            return fallback;
        }
    }


    private static boolean boolOf(JsonObject object, String key, boolean fallback) {
        if (object == null) {
            return fallback;
        }
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return fallback;
        }
        if (element.getAsJsonPrimitive().isBoolean()) {
            return element.getAsBoolean();
        }
        if (element.getAsJsonPrimitive().isNumber()) {
            return element.getAsInt() != 0;
        }
        String value = element.getAsString().trim();
        if (value.equalsIgnoreCase("true")) {
            return true;
        }
        if (value.equalsIgnoreCase("false")) {
            return false;
        }
        return fallback;
    }
}
