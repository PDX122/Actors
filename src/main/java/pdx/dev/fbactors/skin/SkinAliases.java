package pdx.dev.fbactors.skin;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;


public final class SkinAliases {


    public static final String FILE_NAME = "aliases.json";


    public static final int VERSION = 1;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();


    public static final List<String> BUILT_IN_SLUGS = List.of(
        "Built-in", "Steve", "Alex", "Default", "Vanilla");


    private final Map<String, SkinId> byLabel = new TreeMap<>();

    private final Path directory;


    public SkinAliases(Path directory) {
        this.directory = directory;
    }


    public Path directory() {
        return this.directory;
    }


    public Path file() {
        return this.directory.resolve(FILE_NAME);
    }


    public static Path defaultDirectory() {
        return SkinLibrary.defaultDirectory();
    }


    private static String normalise(String label) {
        return label.trim().toLowerCase(Locale.ROOT);
    }

    public SkinId put(String label, SkinId target) {
        String key = normalise(label);
        if (key.isEmpty()) {
            throw new IllegalArgumentException("Cannot alias a blank label");
        }
        if (target == null) {
            throw new IllegalArgumentException("Cannot alias '" + key + "' to nothing");
        }
        this.byLabel.put(key, target);
        return target;
    }


    public SkinId put(String label, String target) {
        return put(label, SkinId.parse(target));
    }


    public boolean remove(String label) {
        return this.byLabel.remove(normalise(label)) != null;
    }


    @Nullable
    public SkinId resolve(@Nullable SkinId id) {
        if (id == null) {
            return null;
        }

        SkinId current = id;
        for (int depth = 0; depth < 8 && current.isUsername(); depth++) {
            SkinId next = this.byLabel.get(current.value());
            if (next == null || next.equals(current)) {
                return current;
            }
            current = next;
        }
        return current;
    }

    @Nullable
    public SkinId byLabel(@Nullable String label) {
        return label == null ? null : this.byLabel.get(normalise(label));
    }


    public boolean contains(@Nullable String label) {
        return label != null && this.byLabel.containsKey(normalise(label));
    }


    public Map<String, SkinId> all() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(this.byLabel));
    }


    public List<String> labels() {
        return new ArrayList<>(this.byLabel.keySet());
    }


    public int size() {
        return this.byLabel.size();
    }


    public boolean isEmpty() {
        return this.byLabel.isEmpty();
    }


    public void clear() {
        this.byLabel.clear();
    }


    public static List<String> builtInSlugs() {
        List<String> slugs = new ArrayList<>(BUILT_IN_SLUGS.size());
        for (String slug : BUILT_IN_SLUGS) {
            slugs.add("/" + slug);
        }
        return slugs;
    }


    private static final Map<String, String> BUILT_IN_SYNONYMS = Map.of(
        "default", "Built-in",
        "vanilla", "Built-in");


    @Nullable
    public static SkinId resolveBuiltIn(@Nullable SkinId id) {
        if (id == null || !id.isBuiltin()) {
            return null;
        }

        String slug = id.value().substring(1).toLowerCase(Locale.ROOT);

        String synonym = BUILT_IN_SYNONYMS.get(slug);
        if (synonym != null) {
            return SkinId.parse("/" + synonym);
        }


        return SkinId.parse("/" + id.value().substring(1));
    }


    public static boolean isValidLabel(@Nullable String label) {
        return SavedSkin.isValidName(label);
    }


    @Nullable
    public static Component labelProblem(@Nullable String label) {
        return SavedSkin.nameProblem(label);
    }

    public static SkinAliases load(Path dir) {
        SkinAliases aliases = new SkinAliases(dir);
        Path file = dir.resolve(FILE_NAME);

        if (!Files.isRegularFile(file)) {
            return aliases;
        }

        JsonObject root;
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            if (parsed == null || !parsed.isJsonObject()) {
                FlashbackActors.LOGGER.warn(
                    "Skin alias table at {} is not a JSON object; starting empty", file);
                return aliases;
            }
            root = parsed.getAsJsonObject();
        } catch (Exception e) {
            FlashbackActors.LOGGER.warn(
                "Could not read the skin alias table at {}; starting empty", file, e);
            return aliases;
        }

        int version = root.has("version") && root.get("version").isJsonPrimitive()
            ? root.get("version").getAsInt() : VERSION;
        if (version > VERSION) {
            aliases.readOnly = true;
            FlashbackActors.LOGGER.warn(
                "Skin alias table at {} is version {}, but this build understands version {}; "
                    + "loading it read-only", file, version, VERSION);
        }

        JsonElement entries = root.get("aliases");
        int skipped = 0;
        if (entries != null && entries.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry
                    : entries.getAsJsonObject().entrySet()) {
                try {
                    JsonElement value = entry.getValue();
                    if (!value.isJsonPrimitive()) {
                        throw new IllegalArgumentException("not a string");
                    }


                    aliases.byLabel.put(normalise(entry.getKey()),
                        SkinId.parse(value.getAsString()));
                } catch (RuntimeException e) {
                    skipped++;
                }
            }
        }

        if (skipped > 0) {
            FlashbackActors.LOGGER.warn("Skipped {} unreadable alias(es) in {}", skipped, file);
        }
        if (!aliases.isEmpty()) {
            FlashbackActors.LOGGER.info("Loaded {} skin alias(es) from {}",
                aliases.size(), file);
        }
        return aliases;
    }


    public boolean save() {
        Path file = file();
        if (this.readOnly) {
            FlashbackActors.LOGGER.warn("Not saving the skin alias table to {}: it was written "
                + "by a newer version of this mod", file);
            return false;
        }

        JsonObject root = new JsonObject();
        root.addProperty("version", VERSION);

        JsonObject entries = new JsonObject();
        for (Map.Entry<String, SkinId> entry : this.byLabel.entrySet()) {
            entries.addProperty(entry.getKey(), entry.getValue().value());
        }
        root.add("aliases", entries);

        try {
            Files.createDirectories(this.directory);
            Path temp = this.directory.resolve(FILE_NAME + ".tmp");
            try (BufferedWriter writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
            try {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            FlashbackActors.LOGGER.error("Could not save the skin alias table to {}", file, e);
            return false;
        }
    }


    public boolean isReadOnly() {
        return this.readOnly;
    }

    private boolean readOnly;


    @Override
    public String toString() {
        return "SkinAliases[" + this.byLabel.size() + " alias(es) at " + file() + "]";
    }
}
