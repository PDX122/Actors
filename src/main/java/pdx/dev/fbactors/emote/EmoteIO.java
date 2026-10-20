package pdx.dev.fbactors.emote;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;


public final class EmoteIO {


    public static final int VERSION = 1;


    public static final int MIN_SUPPORTED_VERSION = 1;

    private EmoteIO() {
    }


    static com.google.gson.JsonObject encodeClip(EmoteTrack.Clip clip) {
        com.google.gson.JsonObject obj = new com.google.gson.JsonObject();
        obj.addProperty("emote", clip.emoteId());
        obj.addProperty("start", clip.startTick());
        obj.addProperty("duration", clip.durationTicks());
        obj.addProperty("blendIn", clip.blendInTicks());
        obj.addProperty("blendOut", clip.blendOutTicks());
        obj.addProperty("loop", clip.loop());
        obj.addProperty("speed", clip.speed());
        return obj;
    }


    public static void write(Path path, Emote emote) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (BufferedWriter out = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            out.write(toJson(emote).toString());
            out.write('\n');
        }
    }


    public static Emote read(Path path) throws IOException {
        String text;
        try (BufferedReader in = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                sb.append(buf, 0, n);
            }
            text = sb.toString();
        }
        try {
            JsonElement root = JsonParser.parseString(text);


            if (!root.isJsonObject()) {
                throw new IOException("Emote file " + path.getFileName()
                    + " must contain a single emote object, but its top level is "
                    + (root.isJsonArray() ? "an array" : "a " + root.getClass().getSimpleName()));
            }
            return fromJson(root.getAsJsonObject(), path.getFileName().toString());
        } catch (JsonSyntaxException e) {
            throw new IOException("Emote file " + path.getFileName() + " is not valid JSON", e);
        } catch (IllegalArgumentException e) {
            throw new IOException("Emote file " + path.getFileName() + " is not a valid emote: "
                + e.getMessage(), e);
        }
    }


    public static void writeAll(Path path, List<Emote> emotes) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("version", VERSION);
        JsonArray array = new JsonArray();


        List<Emote> sorted = new ArrayList<>(emotes);
        sorted.sort(java.util.Comparator.comparing(Emote::id));
        for (Emote emote : sorted) {
            array.add(toJson(emote));
        }
        root.add("emotes", array);

        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (BufferedWriter out = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            out.write(root.toString());
            out.write('\n');
        }
    }


    public static List<Emote> readAll(Path path) throws IOException {
        JsonElement root;
        try (BufferedReader in = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            root = JsonParser.parseReader(in);
        } catch (JsonSyntaxException e) {
            throw new IOException("Emote file " + path.getFileName() + " is not valid JSON", e);
        }
        try {
            JsonArray array;
            if (root.isJsonArray()) {
                array = root.getAsJsonArray();
            } else {
                JsonObject obj = root.getAsJsonObject();
                int version = (int) number(obj, "version", VERSION);
                if (version > VERSION) {
                    throw new IOException("Emote file " + path.getFileName()
                        + " is version " + version + ", but this build only understands version "
                        + VERSION + ". Update fbacks to read it, or re-export the library from the"
                        + " version that wrote it. It has been left untouched.");
                }
                JsonElement emotes = obj.get("emotes");
                array = emotes != null && emotes.isJsonArray()
                    ? emotes.getAsJsonArray() : new JsonArray();
            }
            List<Emote> out = new ArrayList<>(array.size());
            for (JsonElement element : array) {
                if (element != null && element.isJsonObject()) {
                    out.add(fromJson(element.getAsJsonObject(),
                        path.getFileName().toString()));
                }
            }
            return out;
        } catch (IllegalArgumentException e) {
            throw new IOException("Emote file " + path.getFileName() + " is not a valid library: "
                + e.getMessage(), e);
        }
    }


    @Nullable
    static EmoteTrack.Clip decodeClip(com.google.gson.JsonObject obj) {
        com.google.gson.JsonElement emote = obj.get("emote");
        if (emote == null || !emote.isJsonPrimitive()
            || emote.getAsString().isBlank()) {
            return null;
        }
        try {
            return new EmoteTrack.Clip(
                clipNumber(obj, "start", 0),
                clipNumber(obj, "duration", 20),
                emote.getAsString(),
                (float) clipDecimal(obj, "blendIn", 4.0),
                (float) clipDecimal(obj, "blendOut", 4.0),
                clipBool(obj, "loop", false),
                (float) clipDecimal(obj, "speed", 1.0));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static int clipNumber(com.google.gson.JsonObject obj, String key, int fallback) {
        try {
            com.google.gson.JsonElement element = obj.get(key);
            if (element != null && element.isJsonPrimitive()) {
                return element.getAsInt();
            }
        } catch (RuntimeException ignored) {
        }
        return fallback;
    }

    private static double clipDecimal(com.google.gson.JsonObject obj, String key, double fallback) {
        try {
            com.google.gson.JsonElement element = obj.get(key);
            if (element != null && element.isJsonPrimitive()) {
                return element.getAsDouble();
            }
        } catch (RuntimeException ignored) {
        }
        return fallback;
    }

    private static boolean clipBool(com.google.gson.JsonObject obj, String key, boolean fallback) {
        try {
            com.google.gson.JsonElement element = obj.get(key);
            if (element != null && element.isJsonPrimitive()
                && element.getAsJsonPrimitive().isBoolean()) {
                return element.getAsBoolean();
            }
        } catch (RuntimeException ignored) {
        }
        return fallback;
    }


    public static JsonObject toJson(Emote emote) {
        JsonObject obj = new JsonObject();
        obj.addProperty("version", VERSION);
        obj.addProperty("id", emote.id());
        obj.addProperty("name", emote.name());
        obj.addProperty("loop", emote.loop());
        obj.addProperty("durationTicks", emote.durationTicks());

        JsonArray keys = new JsonArray();
        for (EmoteKeyframe key : emote.keyframes()) {
            keys.add(keyframeToJson(key));
        }
        obj.add("keyframes", keys);
        return obj;
    }


    public static Emote fromJson(JsonObject obj, String fileName) {
        String id = text(obj, "id", fileName);
        if (id == null || id.isBlank()) {
            id = fileName;
        }


        if (id.endsWith(".json")) {
            id = id.substring(0, id.length() - ".json".length());
        }
        String name = text(obj, "name", id);
        boolean loop = bool(obj, "loop", false);
        int duration = (int) number(obj, "durationTicks", 0);

        JsonElement keysElement = obj.get("keyframes");
        List<EmoteKeyframe> keys = new ArrayList<>();
        if (keysElement != null && keysElement.isJsonArray()) {
            JsonArray array = keysElement.getAsJsonArray();
            for (int i = 0; i < array.size(); i++) {
                JsonElement element = array.get(i);
                if (element == null || !element.isJsonObject()) {
                    continue;
                }
                try {
                    keys.add(keyframeFromJson(element.getAsJsonObject()));
                } catch (IllegalArgumentException e) {


                    throw new IllegalArgumentException(
                        "keyframe " + i + " of '" + id + "': " + e.getMessage(), e);
                }
            }
        }

        if (keys.isEmpty()) {
            throw new IllegalArgumentException(
                "'" + id + "' has no keyframes at all; an emote with no keys has no pose");
        }
        if (duration <= 0) {


            duration = Math.max(1, keys.get(keys.size() - 1).tick() + 1);
        }

        return new Emote(id, name == null ? id : name, loop, duration, keys);
    }

    private static JsonObject keyframeToJson(EmoteKeyframe key) {
        JsonObject obj = new JsonObject();
        obj.addProperty("tick", key.tick());
        obj.addProperty("x", key.xOffset());
        obj.addProperty("y", key.yOffset());
        obj.addProperty("z", key.zOffset());


        obj.addProperty("bodyYaw", key.bodyYaw());
        obj.addProperty("bodyPitch", key.bodyPitch());
        obj.addProperty("headYaw", key.headYaw());
        obj.addProperty("headPitch", key.headPitch());


        obj.add("leftArm", triple(key.leftArmPitch(), key.leftArmYaw(), key.leftArmRoll()));
        obj.add("rightArm", triple(key.rightArmPitch(), key.rightArmYaw(), key.rightArmRoll()));
        obj.add("leftLeg", triple(key.leftLegPitch(), key.leftLegYaw(), key.leftLegRoll()));
        obj.add("rightLeg", triple(key.rightLegPitch(), key.rightLegYaw(), key.rightLegRoll()));

        obj.addProperty("interpolation", key.interpolation());


        if (key.stance() != null) {
            obj.addProperty("stance", key.stance());
        }
        return obj;
    }


    private static JsonArray triple(float pitch, float yaw, float roll) {
        JsonArray array = new JsonArray(3);
        array.add(pitch);
        array.add(yaw);
        array.add(roll);
        return array;
    }

    private static EmoteKeyframe keyframeFromJson(JsonObject obj) {
        int tick = (int) number(obj, "tick", -1);
        if (tick < 0) {


            throw new IllegalArgumentException("no 'tick' (or a negative one)");
        }
        double x = number(obj, "x", 0.0);
        double y = number(obj, "y", 0.0);
        double z = number(obj, "z", 0.0);

        float bodyYaw = (float) number(obj, "bodyYaw", 0.0);
        float bodyPitch = (float) number(obj, "bodyPitch", 0.0);
        float headYaw = (float) number(obj, "headYaw", 0.0);
        float headPitch = (float) number(obj, "headPitch", 0.0);

        float[] leftArm = limb(obj, "leftArm");
        float[] rightArm = limb(obj, "rightArm");
        float[] leftLeg = limb(obj, "leftLeg");
        float[] rightLeg = limb(obj, "rightLeg");


        String mode = text(obj, "interpolation", EmoteKeyframe.SMOOTH);


        String stance = text(obj, "stance", null);

        return new EmoteKeyframe(tick, x, y, z,
            bodyYaw, bodyPitch, headYaw, headPitch,
            leftArm[0], leftArm[1], leftArm[2],
            rightArm[0], rightArm[1], rightArm[2],
            leftLeg[0], leftLeg[1], leftLeg[2],
            rightLeg[0], rightLeg[1], rightLeg[2],
            mode, stance);
    }


    private static float[] limb(JsonObject obj, String key) {
        JsonElement element = obj.get(key);
        if (element == null || element.isJsonNull()) {
            return new float[] {0.0f, 0.0f, 0.0f};
        }
        if (element.isJsonObject()) {
            JsonObject limb = element.getAsJsonObject();
            return new float[] {
                (float) number(limb, "pitch", 0.0),
                (float) number(limb, "yaw", 0.0),
                (float) number(limb, "roll", 0.0)};
        }
        if (!element.isJsonArray()) {
            throw new IllegalArgumentException(
                "'" + key + "' must be a [pitch, yaw, roll] array");
        }
        JsonArray array = element.getAsJsonArray();
        return new float[] {
            (float) number(array, 0, 0.0),
            (float) number(array, 1, 0.0),
            (float) number(array, 2, 0.0)};
    }


    private static double number(JsonObject obj, String key, double fallback) {
        return number(obj.get(key), fallback);
    }

    private static double number(JsonArray array, int index, double fallback) {
        return number(array.size() > index ? array.get(index) : null, fallback);
    }

    private static double number(JsonElement element, double fallback) {
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        try {
            if (element.isJsonPrimitive()) {
                JsonPrimitive primitive = element.getAsJsonPrimitive();
                if (primitive.isNumber()) {
                    return primitive.getAsDouble();
                }
                if (primitive.isBoolean()) {
                    return primitive.getAsBoolean() ? 1.0 : 0.0;
                }
                if (primitive.isString()) {
                    return Double.parseDouble(primitive.getAsString().trim());
                }
            }
        } catch (NumberFormatException ignored) {


        }
        return fallback;
    }


    private static String text(JsonObject obj, String key, String fallback) {
        JsonElement element = obj.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        if (element.isJsonPrimitive()) {
            return element.getAsString();
        }
        return fallback;
    }


    private static boolean bool(JsonObject obj, String key, boolean fallback) {
        JsonElement element = obj.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        if (element.isJsonPrimitive()) {
            JsonPrimitive primitive = element.getAsJsonPrimitive();
            if (primitive.isBoolean()) {
                return primitive.getAsBoolean();
            }
            if (primitive.isString()) {
                String text = primitive.getAsString().trim();
                if (text.equalsIgnoreCase("true")) {
                    return true;
                }
                if (text.equalsIgnoreCase("false")) {
                    return false;
                }
            }
            if (primitive.isNumber()) {
                return primitive.getAsInt() != 0;
            }
        }
        return fallback;
    }
}


