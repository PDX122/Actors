package pdx.dev.fbactors.actor;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import org.jetbrains.annotations.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;


public final class SkinFileAssignments {


    private static final Logger LOGGER = LoggerFactory.getLogger("fbactors");


    public static final int VERSION = 1;


    public record Entry(@Nullable String skin, @Nullable String cape,
                        @Nullable Boolean slim) {
    }

    private SkinFileAssignments() {
    }


    public static JsonObject encode(Map<UUID, Entry> files) {
        JsonObject actors = new JsonObject();
        List<UUID> ids = new ArrayList<>(files.keySet());
        ids.sort(Comparator.comparing(UUID::toString));
        for (UUID takeId : ids) {
            Entry entry = files.get(takeId);


            if (entry == null || (entry.skin() == null && entry.cape() == null)) {
                continue;
            }
            JsonObject value = new JsonObject();
            if (entry.skin() != null) {
                value.addProperty("skin", entry.skin());
            }
            if (entry.cape() != null) {
                value.addProperty("cape", entry.cape());
            }
            if (entry.slim() != null) {
                value.addProperty("slim", entry.slim());
            }
            actors.add(takeId.toString(), value);
        }
        JsonObject root = new JsonObject();
        root.addProperty("version", VERSION);
        root.add("actors", actors);
        return root;
    }


    public static Map<UUID, Entry> parse(String text, Predicate<Path> fileExists) {
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(text);
        } catch (JsonSyntaxException e) {
            LOGGER.warn("File-skin assignments are not valid JSON", e);
            return Map.of();
        }
        if (!parsed.isJsonObject()) {
            LOGGER.warn("File-skin assignments must contain an object");
            return Map.of();
        }
        JsonObject root = parsed.getAsJsonObject();
        JsonObject actors = root.has("actors") && root.get("actors").isJsonObject()
            ? root.getAsJsonObject("actors") : null;
        if (actors == null) {
            return Map.of();
        }
        return decode(actors, fileExists);
    }


    static Map<UUID, Entry> decode(JsonObject actors, Predicate<Path> fileExists) {
        Map<UUID, Entry> kept = new LinkedHashMap<>();
        for (Map.Entry<String, JsonElement> entry : actors.entrySet()) {
            UUID takeId;
            try {
                takeId = UUID.fromString(entry.getKey());
            } catch (IllegalArgumentException bad) {
                continue;
            }
            if (entry.getValue() == null || !entry.getValue().isJsonObject()) {
                continue;
            }
            JsonObject value = entry.getValue().getAsJsonObject();
            String skin = value.has("skin") && value.get("skin").isJsonPrimitive()
                ? value.get("skin").getAsString() : null;


            if (skin != null && (skin.isBlank() || !fileExists.test(Path.of(skin)))) {
                LOGGER.warn("Dropping the saved skin for take {} (file gone: {})",
                    takeId, skin);
                continue;
            }
            String cape = value.has("cape") && value.get("cape").isJsonPrimitive()
                ? value.get("cape").getAsString() : null;
            if (cape != null && (cape.isBlank() || !fileExists.test(Path.of(cape)))) {
                cape = null;
            }
            if ((skin == null || skin.isBlank()) && cape == null) {
                continue;
            }
            if (skin != null && skin.isBlank()) {
                skin = null;
            }
            Boolean slim = null;
            if (value.has("slim") && value.get("slim").isJsonPrimitive()
                && value.getAsJsonPrimitive("slim").isBoolean()) {
                slim = value.get("slim").getAsBoolean();
            }
            kept.put(takeId, new Entry(skin, cape, slim));
        }
        return kept;
    }
}

