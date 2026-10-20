package pdx.dev.fbactors.morph;

import org.jetbrains.annotations.Nullable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;


public final class MorphRegistry {


    private static final Logger LOGGER = LoggerFactory.getLogger("fbactors");


    private static final List<MorphDefinition> BUILT_IN_LIST = buildBuiltIns();

    private final Map<String, MorphDefinition> byId = new LinkedHashMap<>();


    public MorphRegistry() {
        reset();
    }


@Nullable
public MorphDefinition byId(@Nullable String id) {
    return id == null ? null : this.byId.get(id);
}


public boolean has(@Nullable String id) {
    return id != null && this.byId.containsKey(id);
}


public List<MorphDefinition> all() {
    return List.copyOf(this.byId.values());
}


public List<String> ids() {
    return List.copyOf(this.byId.keySet());
}


public int size() {
    return this.byId.size();
}


@Nullable
public MorphDefinition register(MorphDefinition morph) {
    if (morph == null || morph.id() == null || morph.id().isBlank()) {

        throw new IllegalArgumentException("Cannot register a morph with no id");
    }
    return this.byId.put(morph.id(), morph);
}


@Nullable
public MorphDefinition remove(@Nullable String id) {
    return id == null ? null : this.byId.remove(id);
}


public void reset() {
    this.byId.clear();
    for (MorphDefinition morph : BUILT_IN_LIST) {
        this.byId.put(morph.id(), morph);
    }
}


public static List<MorphDefinition> builtIns() {
    return BUILT_IN_LIST;
}


public static boolean isBuiltIn(@Nullable String id) {
    if (id == null) {
        return false;
    }
    for (MorphDefinition morph : BUILT_IN_LIST) {
        if (morph.id().equals(id)) {
            return true;
        }
    }
    return false;
}


    private static final Map<String, String> ENTITY_ALIASES = Map.of(
        "snowman", "snow_golem",
        "dog", "wolf",
        "snake", "silverfish");


    @Nullable
    public static String entityIdFor(@Nullable String morphId) {
        if (morphId == null) {
            return null;
        }
        return ENTITY_ALIASES.getOrDefault(morphId, morphId);
    }


public int load(java.nio.file.Path dir) {
    if (dir == null || !java.nio.file.Files.isDirectory(dir)) {

        return 0;
    }


    for (MorphDefinition morph : new ArrayList<>(this.byId.values())) {
        if (!isBuiltIn(morph.id())) {
            this.byId.remove(morph.id());
        }
    }

    int loaded = 0;
    try (java.util.stream.Stream<java.nio.file.Path> files = java.nio.file.Files.list(dir)) {
        for (java.nio.file.Path file : files
                .filter(p -> p.getFileName().toString().endsWith(".json"))

                .filter(p -> !p.getFileName().toString().equals(MorphStore.FILE_NAME))
                .sorted()
                .toList()) {
            try {
                MorphDefinition morph = readDefinition(file);
                if (morph != null) {
                    register(morph);
                    loaded++;
                }
            } catch (IOException e) {

                LOGGER.warn(
                    "Skipping unreadable morph file {}", file.getFileName(), e);
            } catch (RuntimeException e) {

                LOGGER.warn(
                    "Skipping invalid morph file {}", file.getFileName(), e);
            }
        }
    } catch (IOException e) {
        LOGGER.error(
            "Failed to list morph files in {}", dir, e);
    }
    return loaded;
}


public int saveAll(java.nio.file.Path dir) throws IOException {
    java.nio.file.Files.createDirectories(dir);
    int written = 0;
    for (MorphDefinition morph : this.byId.values()) {
        if (isBuiltIn(morph.id())) {
            continue;
        }
        writeDefinition(dir.resolve(morph.id() + ".json"), morph);
        written++;
    }
    return written;
}


static final int FILE_VERSION = 1;


private static void writeDefinition(java.nio.file.Path path, MorphDefinition morph)
        throws IOException {
    com.google.gson.JsonObject root = new com.google.gson.JsonObject();
    root.addProperty("version", FILE_VERSION);
    root.addProperty("id", morph.id());
    root.addProperty("displayName", morph.displayName());
    root.addProperty("kind", morph.kind());
    root.addProperty("scale", morph.scale());
    root.addProperty("hitboxWidth", morph.hitboxWidth());
    root.addProperty("hitboxHeight", morph.hitboxHeight());
    root.addProperty("eyeHeight", morph.eyeHeight());
    root.addProperty("rideOffset", morph.rideOffset());
    root.addProperty("stance", morph.stance().name());
    root.addProperty("elytraCapable", morph.elytraCapable());
    root.addProperty("swimmer", morph.swimmer());
    root.addProperty("quadruped", morph.quadruped());

    java.nio.file.Path parent = path.getParent();
    if (parent != null) {
        java.nio.file.Files.createDirectories(parent);
    }
    try (java.io.BufferedWriter out = java.nio.file.Files.newBufferedWriter(
            path, java.nio.charset.StandardCharsets.UTF_8)) {
        out.write(root.toString());
        out.write('\n');
    }
}


@Nullable
private static MorphDefinition readDefinition(java.nio.file.Path path) throws IOException {
    String text;
    try (java.io.BufferedReader in = java.nio.file.Files.newBufferedReader(
            path, java.nio.charset.StandardCharsets.UTF_8)) {
        StringBuilder sb = new StringBuilder();
        char[] buf = new char[8192];
        int n;
        while ((n = in.read(buf)) > 0) {
            sb.append(buf, 0, n);
        }
        text = sb.toString();
    }

    com.google.gson.JsonElement parsed;
    try {
        parsed = com.google.gson.JsonParser.parseString(text);
    } catch (com.google.gson.JsonSyntaxException e) {
        throw new IOException("not valid JSON", e);
    }

    if (!parsed.isJsonObject()) {
        throw new IOException("must contain a single morph object, but its top level is "
                + (parsed.isJsonArray() ? "an array"
                : "a " + parsed.getClass().getSimpleName()));
    }
    com.google.gson.JsonObject root = parsed.getAsJsonObject();

    int version = (int) number(root, "version", FILE_VERSION);
    if (version > FILE_VERSION) {

        throw new IOException("is version " + version
                + ", but this build only understands up to " + FILE_VERSION);
    }

    String id = text(root, "id");
    if (id == null || id.isBlank()) {
        throw new IOException("has no 'id', so there is nothing to register it under");
    }

    String stanceName = text(root, "stance");
    MorphStance stance = MorphStance.byName(stanceName);
    if (stance == null) {
        throw new IllegalArgumentException("has stance '" + stanceName
                + "', which this build does not know");
    }

float scale = (float) number(root, "scale", 1.0);
    if (!Float.isFinite(scale) || scale <= 0.0) {
        throw new IllegalArgumentException("has scale " + scale
                + ", which is not a positive finite number");
    }

    double width = number(root, "hitboxWidth", 0.6);
    float height = (float) number(root, "hitboxHeight", 1.8);
    if (!Double.isFinite(width) || width <= 0.0 || !Float.isFinite(height) || height <= 0.0) {
        throw new IllegalArgumentException("has a non-positive or non-finite hitbox");
    }

    double eye = number(root, "eyeHeight", 1.62);
    double ride = number(root, "rideOffset", 0.0);
    if (!Double.isFinite(eye) || !Double.isFinite(ride)) {
        throw new IllegalArgumentException("has a non-finite eyeHeight or rideOffset");
    }

    return new MorphDefinition(
        id,

        text(root, "displayName", id),
        text(root, "kind", "monster"),
        scale, width, height, eye, ride, stance,
        flag(root, "elytraCapable", false),
        flag(root, "swimmer", false),
        flag(root, "quadruped", false));
}


@Nullable
private static String text(com.google.gson.JsonObject root, String key) {
    return text(root, key, null);
}


private static String text(com.google.gson.JsonObject root, String key, String fallback) {
    com.google.gson.JsonElement element = root.get(key);
    if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
        return fallback;
    }
    return element.getAsString();
}


private static double number(com.google.gson.JsonObject root, String key, double fallback) {
    com.google.gson.JsonElement element = root.get(key);
    if (element == null || !element.isJsonPrimitive()) {
        return fallback;
    }
    try {
        double value = element.getAsDouble();
        return Double.isFinite(value) ? value : fallback;
    } catch (NumberFormatException e) {
        return fallback;
    }
}


private static boolean flag(com.google.gson.JsonObject root, String key, boolean fallback) {
    com.google.gson.JsonElement element = root.get(key);
    if (element == null || element.isJsonNull() || !element.isJsonPrimitive()) {
        return fallback;
    }
    return element.getAsBoolean();
}


public List<MorphDefinition> search(@Nullable String query) {
    return MorphSearch.search(this, query);
}


private static MorphDefinition morph(String id, String displayName, String kind,
                                     float scale, double hitboxWidth, float hitboxHeight,
                                     double eyeHeight, MorphStance stance,
                                     boolean elytraCapable, boolean swimmer,
                                     boolean quadruped) {
    return new MorphDefinition(id, displayName, kind, scale, hitboxWidth, hitboxHeight,
        eyeHeight, 0.0, stance, elytraCapable, swimmer, quadruped);
}


private static MorphDefinition rideable(String id, String displayName, String kind,
                                        float scale, double hitboxWidth,
                                        float hitboxHeight, double eyeHeight,
                                        double rideOffset) {
    return new MorphDefinition(id, displayName, kind, scale, hitboxWidth, hitboxHeight,
        eyeHeight, rideOffset, MorphStance.QUADRUPED, false, false, true);
}


private static List<MorphDefinition> buildBuiltIns() {
    List<MorphDefinition> morphs = new ArrayList<>(90);


    morphs.add(morph("zombie", "Zombie", "monster", 1.08f, 0.6f, 1.95f, 1.74f,
        MorphStance.HUMANOID, false, false, false));
    morphs.add(morph("husk", "Husk", "monster", 1.08f, 0.6f, 1.95f, 1.74f,
        MorphStance.HUMANOID, false, false, false));

    morphs.add(morph("parched", "Parched", "monster", 1.08f, 0.6f, 1.95f, 1.74f,
        MorphStance.HUMANOID, false, false, false));
    morphs.add(morph("drowned", "Drowned", "monster", 1.08f, 0.6f, 1.95f, 1.74f,
        MorphStance.HUMANOID, false, true, false));
    morphs.add(morph("skeleton", "Skeleton", "monster", 1.11f, 0.6f, 1.99f, 1.79f,
        MorphStance.HUMANOID, false, false, false));
    morphs.add(morph("stray", "Stray", "monster", 1.11f, 0.6f, 1.99f, 1.79f,
        MorphStance.HUMANOID, false, false, false));

    morphs.add(morph("bogged", "Bogged", "monster", 1.11f, 0.6f, 1.99f, 1.79f,
        MorphStance.HUMANOID, false, false, false));
    morphs.add(morph("zombie_villager", "Zombie Villager", "monster", 1.08f, 0.6f, 1.95f,
        1.74f, MorphStance.HUMANOID, false, false, false));
    morphs.add(morph("wither_skeleton", "Wither Skeleton", "monster", 1.33f, 0.7f, 2.4f,
        2.1f, MorphStance.HUMANOID, false, false, false));


    morphs.add(morph("villager", "Villager", "humanoid", 1.08f, 0.6f, 1.95f, 1.62f,
        MorphStance.HUMANOID, false, false, false));
    morphs.add(morph("witch", "Witch", "monster", 1.08f, 0.6f, 1.95f, 1.62f,
        MorphStance.HUMANOID, false, false, false));
    morphs.add(morph("pillager", "Pillager", "monster", 1.08f, 0.6f, 1.95f, 1.74f,
        MorphStance.HUMANOID, false, false, false));
    morphs.add(morph("vindicator", "Vindicator", "monster", 1.08f, 0.6f, 1.95f, 1.74f,
        MorphStance.HUMANOID, false, false, false));
    morphs.add(morph("evoker", "Evoker", "monster", 1.08f, 0.6f, 1.95f, 1.74f,
        MorphStance.HUMANOID, false, false, false));
    morphs.add(morph("illusioner", "Illusioner", "monster", 1.08f, 0.6f, 1.95f, 1.66f,
        MorphStance.HUMANOID, false, false, false));
    morphs.add(morph("piglin", "Piglin", "monster", 1.03f, 0.6f, 1.85f, 1.62f,
        MorphStance.HUMANOID, false, false, false));
    morphs.add(morph("piglin_brute", "Piglin Brute", "monster", 1.11f, 0.7f, 2.0f, 1.74f,
        MorphStance.HUMANOID, false, false, false));
    morphs.add(morph("zombified_piglin", "Zombified Piglin", "monster", 1.08f, 0.6f, 1.95f,
        1.79f, MorphStance.HUMANOID, false, false, false));

    morphs.add(morph("iron_golem", "Iron Golem", "humanoid", 1.5f, 0.9f, 2.7f, 2.0f,
        MorphStance.HUNCHED, false, false, false));
    morphs.add(morph("snow_golem", "Snow Golem", "humanoid", 1.06f, 0.7f, 1.9f, 1.62f,
        MorphStance.HUMANOID, false, false, false));

    morphs.add(morph("snowman", "Snowman", "humanoid", 1.06f, 0.7f, 1.9f, 1.62f,
        MorphStance.HUMANOID, false, false, false));
    morphs.add(morph("copper_golem", "Copper Golem", "humanoid", 0.54f, 0.49f, 0.98f,
        0.81f, MorphStance.HUMANOID, false, false, false));


    morphs.add(morph("enderman", "Enderman", "monster", 1.61f, 0.6f, 2.9f, 2.45f,
        MorphStance.UPRIGHT_TALL, false, false, false));

    morphs.add(morph("breeze", "Breeze", "monster", 0.67f, 0.4f, 1.2f, 1.0f,
        MorphStance.HUMANOID, false, false, false));


    morphs.add(morph("creeper", "Creeper", "monster", 0.94f, 0.6f, 1.7f, 1.4f,
        MorphStance.CRAWLING, false, false, false));
    morphs.add(morph("slime", "Slime", "monster", 1.13f, 2.04f, 2.04f, 1.1f,
        MorphStance.BLOB, false, false, false));

    morphs.add(morph("magma_cube", "Magma Cube", "monster", 1.13f, 2.04f, 2.04f, 1.1f,
        MorphStance.BLOB, false, false, false));
    morphs.add(morph("blaze", "Blaze", "monster", 1.0f, 0.6f, 1.8f, 1.62f,
        MorphStance.HUMANOID, false, false, false));

    morphs.add(morph("ghast", "Ghast", "monster", 2.22f, 4.0f, 4.0f, 2.4f,
        MorphStance.FLYING, false, false, false));

    morphs.add(morph("happy_ghast", "Happy Ghast", "animal", 2.22f, 4.0f, 4.0f, 2.4f,
        MorphStance.FLYING, false, false, false));
    morphs.add(morph("endermite", "Endermite", "monster", 0.39f, 0.4f, 0.7f, 0.4f,
        MorphStance.SNAKING, false, false, false));

    morphs.add(morph("zoglin", "Zoglin", "monster", 0.78f, 0.9f, 1.4f, 1.05f,
        MorphStance.QUADRUPED, false, false, true));
    morphs.add(morph("hoglin", "Hoglin", "monster", 1.22f, 1.4f, 2.2f, 1.65f,
        MorphStance.QUADRUPED, false, false, true));

    morphs.add(morph("guardian", "Guardian", "monster", 0.47f, 0.85f, 0.85f, 0.6f,
        MorphStance.FLYING, false, true, false));

    morphs.add(morph("elder_guardian", "Elder Guardian", "monster", 1.65f, 1.997f, 2.97f,
        2.5f, MorphStance.FLYING, false, true, false));

    morphs.add(morph("warden", "Warden", "monster", 1.61f, 0.9f, 2.9f, 2.4f,
        MorphStance.UPRIGHT_TALL, false, false, false));
    morphs.add(morph("creaking", "Creaking", "monster", 1.5f, 0.9f, 2.7f, 2.3f,
        MorphStance.HUMANOID, false, false, false));
    morphs.add(morph("ravager", "Ravager", "monster", 1.22f, 1.95f, 2.2f, 1.87f,
        MorphStance.QUADRUPED, false, false, true));
    morphs.add(morph("shulker", "Shulker", "monster", 0.69f, 1.235f, 1.2375f, 0.5f,
        MorphStance.BLOB, false, false, false));


    morphs.add(rideable("cow", "Cow", "animal", 0.78f, 0.9f, 1.4f, 1.3f, 1.05f));
    morphs.add(rideable("mooshroom", "Mooshroom", "animal", 0.78f, 0.9f, 1.4f, 1.3f, 1.05f));

    morphs.add(rideable("pig", "Pig", "animal", 0.5f, 0.9f, 0.9f, 0.85f, 0.75f));
    morphs.add(rideable("sheep", "Sheep", "animal", 0.72f, 0.9f, 1.3f, 1.1f, 1.0f));
    morphs.add(rideable("goat", "Goat", "animal", 0.72f, 0.9f, 1.3f, 1.1f, 1.0f));

    morphs.add(rideable("chicken", "Chicken", "animal", 0.39f, 0.4f, 0.7f, 0.7f, 0.6f));

    morphs.add(rideable("rabbit", "Rabbit", "animal", 0.28f, 0.4f, 0.5f, 0.45f, 0.4f));
    morphs.add(rideable("fox", "Fox", "animal", 0.39f, 0.6f, 0.7f, 0.6f, 0.5f));
    morphs.add(rideable("cat", "Cat", "animal", 0.39f, 0.6f, 0.7f, 0.59f, 0.5f));
    morphs.add(rideable("ocelot", "Ocelot", "animal", 0.39f, 0.6f, 0.7f, 0.6f, 0.5f));
    morphs.add(rideable("wolf", "Wolf", "animal", 0.47f, 0.6f, 0.85f, 0.85f, 0.7f));

    morphs.add(rideable("dog", "Dog", "animal", 0.47f, 0.6f, 0.85f, 0.85f, 0.7f));

    morphs.add(rideable("polar_bear", "Polar Bear", "animal", 0.78f, 1.4f, 1.4f, 1.4f, 1.1f));
    morphs.add(rideable("panda", "Panda", "animal", 0.69f, 1.3f, 1.25f, 1.15f, 1.0f));
    morphs.add(rideable("llama", "Llama", "animal", 1.22f, 1.1f, 2.2f, 2.0f, 1.75f));
    morphs.add(rideable("trader_llama", "Trader Llama", "animal", 1.22f, 1.1f, 2.2f, 2.0f,
        1.75f));

    morphs.add(rideable("camel", "Camel", "animal", 1.32f, 1.8f, 2.375f, 2.0f, 1.75f));
    morphs.add(rideable("camel_husk", "Camel Husk", "animal", 1.32f, 1.7f, 2.375f, 2.275f,
        1.75f));

    morphs.add(rideable("sniffer", "Sniffer", "animal", 0.97f, 1.9f, 1.75f, 1.35f, 1.2f));
    morphs.add(morph("armadillo", "Armadillo", "animal", 0.36f, 0.7f, 0.65f, 0.26f,
        MorphStance.QUADRUPED, false, false, true));
    morphs.add(rideable("horse", "Horse", "animal", 0.89f, 1.4f, 1.6f, 1.6f, 1.25f));
    morphs.add(rideable("donkey", "Donkey", "animal", 0.89f, 1.4f, 1.6f, 1.5f, 1.25f));
    morphs.add(rideable("mule", "Mule", "animal", 0.89f, 1.4f, 1.6f, 1.5f, 1.25f));
    morphs.add(rideable("skeleton_horse", "Skeleton Horse", "animal", 0.89f, 1.4f, 1.6f,
        1.52f, 1.25f));
    morphs.add(rideable("zombie_horse", "Zombie Horse", "animal", 0.89f, 1.4f, 1.6f,
        1.52f, 1.25f));

    morphs.add(rideable("strider", "Strider", "animal", 1.11f, 0.9f, 2.0f, 1.0f, 1.6f));


    morphs.add(morph("frog", "Frog", "animal", 0.33f, 0.8f, 0.6f, 0.5f,
        MorphStance.SNAKING, false, false, false));
    morphs.add(morph("axolotl", "Axolotl", "animal", 0.23f, 0.75f, 0.42f, 0.36f,
        MorphStance.SWIMMING, false, true, false));

    morphs.add(morph("turtle", "Turtle", "animal", 0.22f, 1.2f, 0.4f, 0.4f,
        MorphStance.SNAKING, false, false, false));
    morphs.add(morph("dolphin", "Dolphin", "animal", 0.33f, 0.9f, 0.6f, 0.55f,
        MorphStance.SWIMMING, false, true, false));
    morphs.add(morph("squid", "Squid", "animal", 0.44f, 0.8f, 0.8f, 0.8f,
        MorphStance.SWIMMING, false, true, false));
    morphs.add(morph("glow_squid", "Glow Squid", "animal", 0.44f, 0.8f, 0.8f, 0.8f,
        MorphStance.SWIMMING, false, true, false));
    morphs.add(morph("cod", "Cod", "animal", 0.17f, 0.5f, 0.3f, 0.2f,
        MorphStance.SWIMMING, false, true, false));
    morphs.add(morph("salmon", "Salmon", "animal", 0.22f, 0.7f, 0.4f, 0.26f,
        MorphStance.SWIMMING, false, true, false));
    morphs.add(morph("tropical_fish", "Tropical Fish", "animal", 0.22f, 0.5f, 0.4f, 0.26f,
        MorphStance.SWIMMING, false, true, false));
    morphs.add(morph("pufferfish", "Pufferfish", "animal", 0.39f, 0.7f, 0.7f, 0.46f,
        MorphStance.SWIMMING, false, true, false));
    morphs.add(morph("tadpole", "Tadpole", "animal", 0.17f, 0.4f, 0.3f, 0.2f,
        MorphStance.SNAKING, false, true, false));
    morphs.add(morph("snake", "Snake", "animal", 0.28f, 0.5f, 0.5f, 0.5f,
        MorphStance.SNAKING, false, false, false));

    morphs.add(morph("silverfish", "Silverfish", "animal", 0.17f, 0.4f, 0.3f, 0.2f,
        MorphStance.SNAKING, false, false, false));


    morphs.add(morph("bat", "Bat", "monster", 0.5f, 0.5f, 0.9f, 0.65f,
        MorphStance.FLYING, true, false, false));
    morphs.add(morph("bee", "Bee", "animal", 0.33f, 0.7f, 0.6f, 0.5f,
        MorphStance.FLYING, true, false, false));

    morphs.add(morph("phantom", "Phantom", "monster", 0.28f, 0.9f, 0.5f, 0.4f,
        MorphStance.FLYING, true, false, false));
    morphs.add(morph("vex", "Vex", "monster", 0.44f, 0.4f, 0.8f, 0.52f,
        MorphStance.FLYING, true, false, false));
    morphs.add(morph("parrot", "Parrot", "animal", 0.5f, 0.5f, 0.9f, 0.85f,
        MorphStance.FLYING, true, false, false));


    morphs.add(morph("spider", "Spider", "monster", 0.5f, 1.4f, 0.9f, 0.95f,
        MorphStance.QUADRUPED, false, false, true));
    morphs.add(morph("cave_spider", "Cave Spider", "monster", 0.28f, 0.7f, 0.5f, 0.5f,
        MorphStance.QUADRUPED, false, false, true));


    morphs.add(morph("wandering_trader", "Wandering Trader", "humanoid", 1.08f, 0.6f,
        1.95f, 1.62f, MorphStance.HUMANOID, false, false, false));

    morphs.add(morph("allay", "Allay", "humanoid", 0.33f, 0.35f, 0.6f, 0.55f,
        MorphStance.HUMANOID, false, false, false));

    return List.copyOf(morphs);
}


public List<String> categories() {
    Collection<String> seen = new java.util.LinkedHashSet<>();
    for (MorphDefinition morph : this.byId.values()) {
        String kind = MorphSearch.categoryOf(morph);
        if (kind != null && !kind.isBlank()) {
            seen.add(kind.toLowerCase(Locale.ROOT));
        }
    }
    return List.copyOf(seen);
}
}

