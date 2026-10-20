package pdx.dev.fbactors.gear;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;

import org.jetbrains.annotations.Nullable;

import pdx.dev.fbactors.FlashbackActors;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;


public final class GearStore {


    public static final int VERSION = 1;

    private static final String FILE_NAME = "gear.json";

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();


    private final Map<UUID, Map<GearSlot, GearOverride>> overrides =
        new java.util.concurrent.ConcurrentHashMap<>();


    private final Map<UUID, String> presets = new java.util.concurrent.ConcurrentHashMap<>();


    private final Map<UUID, String> names = new java.util.concurrent.ConcurrentHashMap<>();


    private volatile boolean dirty;


    public Set<UUID> actors() {
        Set<UUID> all = new LinkedHashSet<>(this.overrides.keySet());
        all.addAll(this.presets.keySet());
        return Collections.unmodifiableSet(all);
    }


    public Map<GearSlot, GearOverride> overridesFor(@Nullable UUID takeId) {
        if (takeId == null) {
            return Map.of();
        }
        Map<GearSlot, GearOverride> found = this.overrides.get(takeId);
        return found == null ? Map.of() : Collections.unmodifiableMap(found);
    }


    public void set(@Nullable UUID takeId, @Nullable GearOverride override) {
        if (takeId == null || override == null) {
            return;
        }
        Map<GearSlot, GearOverride> next = new EnumMap<>(GearSlot.class);
        next.putAll(this.overridesFor(takeId));
        if (override.isNoOp()) {
            next.remove(override.slot());
        } else {
            next.put(override.slot(), override);
        }
        if (next.isEmpty()) {
            this.overrides.remove(takeId);
        } else {
            this.overrides.put(takeId, Collections.unmodifiableMap(next));
        }
        this.dirty = true;
    }


    public void clear(@Nullable UUID takeId) {
        if (takeId == null) {
            return;
        }
        this.overrides.remove(takeId);
        this.presets.remove(takeId);
        this.dirty = true;
    }


    public void clearSlot(@Nullable UUID takeId, @Nullable GearSlot slot) {
        if (takeId == null || slot == null) {
            return;
        }
        Map<GearSlot, GearOverride> current = this.overrides.get(takeId);
        if (current == null || !current.containsKey(slot)) {
            return;
        }
        Map<GearSlot, GearOverride> next = new EnumMap<>(current);
        next.remove(slot);
        if (next.isEmpty()) {
            this.overrides.remove(takeId);
        } else {
            this.overrides.put(takeId, Collections.unmodifiableMap(next));
        }
        this.dirty = true;
    }


    public boolean isOverridden(@Nullable UUID takeId) {
        if (takeId == null) {
            return false;
        }
        Map<GearSlot, GearOverride> tweaks = this.overrides.get(takeId);
        if (tweaks != null && !tweaks.isEmpty()) {
            return true;
        }
        String preset = this.presets.get(takeId);
        return preset != null && GearPresets.byId(preset) != null;
    }


    @Nullable
    public String presetFor(@Nullable UUID takeId) {
        return takeId == null ? null : this.presets.get(takeId);
    }


    public GearLoadout loadoutFor(@Nullable UUID takeId) {
        if (takeId == null) {
            return GearLoadout.empty();
        }
        GearLoadout base = GearPresets.byId(this.presets.get(takeId));
        Map<GearSlot, GearOverride> tweaks = this.overrides.get(takeId);
        GearLoadout top = tweaks == null || tweaks.isEmpty()
            ? GearLoadout.empty()
            : new GearLoadout(
                "actor:" + takeId,
                nameOrDefault(takeId, "Custom"),
                tweaks);


        return GearLoadout.merge(base, top);
    }


    public void setPreset(@Nullable UUID takeId, @Nullable String presetId) {
        if (takeId == null) {
            return;
        }
        String trimmed = presetId == null ? null : presetId.trim();
        if (trimmed == null || trimmed.isEmpty()) {
            this.presets.remove(takeId);
        } else {
            this.presets.put(takeId, trimmed);
        }
        this.overrides.remove(takeId);
        this.dirty = true;
    }


    public void setName(@Nullable UUID takeId, @Nullable String name) {
        if (takeId == null) {
            return;
        }
        if (name == null || name.isBlank()) {
            this.names.remove(takeId);
        } else {
            this.names.put(takeId, name.trim());
        }
        this.dirty = true;
    }


    @Nullable
    public String nameFor(@Nullable UUID takeId) {
        return takeId == null ? null : this.names.get(takeId);
    }


    private String nameOrDefault(UUID takeId, String fallback) {
        String name = this.names.get(takeId);
        return name == null || name.isBlank() ? fallback : name;
    }


    public boolean isDirty() {
        return this.dirty;
    }


    public void markSaved() {
        this.dirty = false;
    }


    public void clearAll() {
        this.overrides.clear();
        this.presets.clear();
        this.names.clear();
        this.dirty = true;
    }


    public int load(Path dir) {
        this.overrides.clear();
        this.presets.clear();
        this.names.clear();

        if (dir == null) {
            return 0;
        }
        Path file = dir.resolve(FILE_NAME);
        if (!Files.isRegularFile(file)) {
            this.dirty = false;
            return 0;
        }

        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement root = JsonParser.parseReader(reader);
            if (!root.isJsonObject()) {
                FlashbackActors.LOGGER.warn("Gear file {} is not a JSON object; ignoring", file);
                this.dirty = false;
                return 0;
            }
            JsonObject object = root.getAsJsonObject();

            int version = intOf(object, "version", 0);
            if (version > VERSION) {


                FlashbackActors.LOGGER.warn("Gear file {} is version {} but this build "
                    + "understands up to {}; leaving it untouched",
                    file, version, VERSION);
                this.dirty = false;
                return 0;
            }

            int loaded = 0;
            JsonElement actors = object.get("actors");
            if (actors != null && actors.isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry
                        : actors.getAsJsonObject().entrySet()) {
                    if (readActor(entry.getKey(), entry.getValue())) {
                        loaded++;
                    }
                }
            }
            this.dirty = false;
            return loaded;
        } catch (IOException | JsonSyntaxException e) {
            FlashbackActors.LOGGER.warn("Could not read gear overrides from {}", file, e);
            this.dirty = false;
            return 0;
        }
    }


    private boolean readActor(String key, JsonElement value) {
        UUID takeId;
        try {
            takeId = UUID.fromString(key.trim());
        } catch (IllegalArgumentException e) {
            FlashbackActors.LOGGER.warn("Skipping gear entry with malformed id '{}'", key);
            return false;
        }
        if (!value.isJsonObject()) {
            return false;
        }
        JsonObject actor = value.getAsJsonObject();

        String name = text(actor, "name", null);
        if (name != null) {
            this.names.put(takeId, name);
        }

        String preset = text(actor, "preset", null);
        if (preset != null && !preset.isBlank()) {
            if (GearPresets.byId(preset) == null) {


                FlashbackActors.LOGGER.debug("Gear preset '{}' for {} is unknown to this "
                    + "build; keeping the reference", preset, takeId);
            }
            this.presets.put(takeId, preset.trim());
        }

        Map<GearSlot, GearOverride> tweaks = new EnumMap<>(GearSlot.class);
        JsonElement slots = actor.get("overrides");
        if (slots != null && slots.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry
                    : slots.getAsJsonObject().entrySet()) {
                GearSlot slot = GearSlot.fromName(entry.getKey());
                if (slot == null) {
                    FlashbackActors.LOGGER.warn("Skipping unknown gear slot '{}' for {}",
                        entry.getKey(), takeId);
                    continue;
                }
                GearOverride override = readOverride(slot, entry.getValue());
                if (override != null && !override.isNoOp()) {
                    tweaks.put(slot, override);
                }
            }
        }
        if (!tweaks.isEmpty()) {
            this.overrides.put(takeId, Collections.unmodifiableMap(tweaks));
        }
        return true;
    }


    @Nullable
    private GearOverride readOverride(GearSlot slot, JsonElement value) {
        if (value == null || !value.isJsonObject()) {
            return null;
        }
        JsonObject object = value.getAsJsonObject();


        int count = intOf(object, "count", 1);

        Map<String, Integer> enchantments = new LinkedHashMap<>();
        JsonElement enchants = object.get("enchantments");
        if (enchants != null && enchants.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry
                    : enchants.getAsJsonObject().entrySet()) {
                int level = intOf(entry.getValue(), 0);
                if (level > 0) {
                    enchantments.put(entry.getKey(), level);
                }
            }
        }

        return new GearOverride(slot, text(object, "item", ""), count, enchantments,
            text(object, "nbt", null), boolOf(object, "enabled", true));
    }


    public int saveAll(Path dir) {
        if (dir == null) {
            return 0;
        }
        JsonObject root = new JsonObject();
        root.addProperty("version", VERSION);

        JsonObject actors = new JsonObject();
        int written = 0;


        List<UUID> ids = new ArrayList<>(this.actors());
        ids.sort(null);
        for (UUID takeId : ids) {
            JsonObject actor = writeActor(takeId);
            if (actor != null) {
                actors.add(takeId.toString(), actor);
                written++;
            }
        }
        root.add("actors", actors);

        boolean ok = pdx.dev.fbactors.util.AtomicSave.writeJson(
            dir, FILE_NAME, writer -> GSON.toJson(root, writer));
        if (!ok) {
            FlashbackActors.LOGGER.warn("Could not write gear overrides to {}", dir);
            return 0;
        }
        this.dirty = false;
        return written;
    }


    @Nullable
    public JsonObject exportActor(UUID takeId) {
        return writeActor(takeId);
    }


    public boolean importActor(String key, JsonElement value) {
        boolean had = readActor(key, value);
        if (had) {
            this.dirty = true;
        }
        return had;
    }


    @Nullable
    private JsonObject writeActor(UUID takeId) {
        Map<GearSlot, GearOverride> tweaks = this.overrides.get(takeId);
        String preset = this.presets.get(takeId);
        String name = this.names.get(takeId);
        boolean hasTweaks = tweaks != null && !tweaks.isEmpty();

        if (!hasTweaks && preset == null && name == null) {
            return null;
        }

        JsonObject actor = new JsonObject();
        if (name != null && !name.isBlank()) {
            actor.addProperty("name", name);
        }
        if (preset != null && !preset.isBlank()) {
            actor.addProperty("preset", preset);
        }
        if (hasTweaks) {
            JsonObject slots = new JsonObject();


            for (GearSlot slot : GearSlot.values()) {
                GearOverride override = tweaks.get(slot);
                if (override != null && !override.isNoOp()) {
                    slots.add(slot.name(), writeOverride(override));
                }
            }
            actor.add("overrides", slots);
        }
        return actor;
    }


    private JsonObject writeOverride(GearOverride override) {
        JsonObject object = new JsonObject();


        object.addProperty("item", override.itemId());
        object.addProperty("count", override.count());
        object.addProperty("enabled", override.enabled());
        if (override.nbt() != null && !override.nbt().isBlank()) {
            object.addProperty("nbt", override.nbt());
        }
        if (!override.enchantments().isEmpty()) {
            JsonObject enchants = new JsonObject();
            for (Map.Entry<String, Integer> entry : override.enchantments().entrySet()) {
                enchants.addProperty(entry.getKey(), entry.getValue());
            }
            object.add("enchantments", enchants);
        }
        return object;
    }


    @Nullable
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


    private static int intOf(JsonObject object, String key, int fallback) {
        return intOf(object.get(key), fallback);
    }

    private static int intOf(@Nullable JsonElement element, int fallback) {
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return fallback;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isNumber()) {
            return primitive.getAsInt();
        }
        if (primitive.isBoolean()) {
            return primitive.getAsBoolean() ? 1 : 0;
        }
        if (primitive.isString()) {
            try {
                return Integer.parseInt(primitive.getAsString().trim());
            } catch (NumberFormatException ignored) {


            }
        }
        return fallback;
    }


    private static boolean boolOf(JsonObject object, String key, boolean fallback) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
            return fallback;
        }
        JsonPrimitive primitive = element.getAsJsonPrimitive();
        if (primitive.isBoolean()) {
            return primitive.getAsBoolean();
        }
        if (primitive.isNumber()) {
            return primitive.getAsInt() != 0;
        }
        if (primitive.isString()) {
            String value = primitive.getAsString().trim();
            if (value.equalsIgnoreCase("true")) {
                return true;
            }
            if (value.equalsIgnoreCase("false")) {
                return false;
            }
        }
        return fallback;
    }
}

