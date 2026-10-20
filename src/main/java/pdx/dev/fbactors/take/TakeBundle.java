package pdx.dev.fbactors.take;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.moulberry.flashback.Flashback;
import pdx.dev.fbactors.FlashbackActors;
import pdx.dev.fbactors.emote.Emotes;
import pdx.dev.fbactors.gear.Gear;
import pdx.dev.fbactors.gear.GearStore;
import pdx.dev.fbactors.morph.Morph;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;


public final class TakeBundle {


    public static final String EXTENSION = ".fbactors.zip";

    private static final int BUNDLE_VERSION = 2;
    private static final int MAX_BUNDLE_ENTRIES = 8192;
    private static final int MAX_BUNDLE_ENTRY_BYTES = 256 * 1024 * 1024;
    private static final long MAX_BUNDLE_TOTAL_BYTES = 512L * 1024L * 1024L;

    private TakeBundle() {
    }


    public record Bundle(UUID replayId, String replayName, List<ActorTake> takes,
                         JsonObject gearActors, JsonObject emoteTracks,
                         JsonObject morphActors) {
    }


    public record Exported(Path file, int takes) {
    }


    public record Summary(int takes, int gearActors, int emoteClips, int morphActors) {
    }


    public static Exported exportBundle(Path outFile, UUID replayId, String replayName,
                                        List<ActorTake> takes, JsonObject gearActors,
                                        JsonObject emoteTracks) throws IOException {
        return exportBundle(outFile, replayId, replayName, takes, gearActors, emoteTracks,
            new JsonObject());
    }


    public static Exported exportBundle(Path outFile, UUID replayId, String replayName,
                                        List<ActorTake> takes, JsonObject gearActors,
                                        JsonObject emoteTracks, JsonObject morphActors)
            throws IOException {
        List<ActorTake> safe = takes == null ? List.of() : takes;
        Path parent = outFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = parent == null
            ? Files.createTempFile("fbactors-bundle", ".zip")
            : Files.createTempFile(parent, "fbactors-bundle", ".zip");

        JsonArray ids = new JsonArray();
        JsonObject gear = gearActors == null ? new JsonObject() : gearActors;
        JsonObject emotes = emoteTracks == null ? new JsonObject() : emoteTracks;
        JsonObject morphs = morphActors == null ? new JsonObject() : morphActors;
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(temp))) {
            Path takeTemp = Files.createTempDirectory("fbactors-takes");
            try {
                for (ActorTake take : safe) {
                    if (take == null) {
                        continue;
                    }

                    Path takeFile = takeTemp.resolve(take.id + ".take");
                    TakeIO.write(takeFile, take);
                    writeEntry(out, "takes/" + take.id + ".take", Files.readAllBytes(takeFile));
                    ids.add(take.id.toString());
                }
            } finally {
                deleteTree(takeTemp);
            }
            writeEntry(out, "gear.json", gearJson(gear).getBytes(StandardCharsets.UTF_8));
            writeEntry(out, "emotes.json", emotesJson(emotes).getBytes(StandardCharsets.UTF_8));
            writeEntry(out, "morphs.json", morphsJson(morphs).getBytes(StandardCharsets.UTF_8));
            writeEntry(out, "bundle.json",
                manifest(replayId, replayName, ids).getBytes(StandardCharsets.UTF_8));
        }
        try {
            Files.move(temp, outFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(temp, outFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        return new Exported(outFile, ids.size());
    }


    public static Bundle readBundle(Path bundleFile) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        long total = 0L;
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(bundleFile))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (!entry.isDirectory()) {
                    if (entries.size() >= MAX_BUNDLE_ENTRIES) {
                        throw new IOException("Take bundle declares too many entries: "
                            + bundleFile.getFileName());
                    }
                    byte[] data = in.readAllBytes();
                    if (data.length > MAX_BUNDLE_ENTRY_BYTES) {
                        throw new IOException("Take bundle entry too large: " + entry.getName());
                    }
                    total += data.length;
                    if (total > MAX_BUNDLE_TOTAL_BYTES) {
                        throw new IOException("Take bundle too large: "
                            + bundleFile.getFileName());
                    }
                    entries.put(entry.getName(), data);
                }
            }
        } catch (IOException e) {
            throw new IOException("Not a take bundle: " + bundleFile.getFileName(), e);
        }
        byte[] manifest = entries.get("bundle.json");
        if (manifest == null) {
            throw new IOException("Not a take bundle (no bundle.json): "
                + bundleFile.getFileName());
        }
        JsonObject root = JsonParser.parseString(
            new String(manifest, StandardCharsets.UTF_8)).getAsJsonObject();
        UUID replayId = null;
        try {
            if (root.has("replayId")) {
                replayId = UUID.fromString(root.get("replayId").getAsString());
            }
        } catch (IllegalArgumentException e) {
            throw new IOException("Take bundle has a malformed replay id", e);
        }
        String replayName = root.has("replayName") ? root.get("replayName").getAsString() : "";

        Path takeTemp = Files.createTempDirectory("fbactors-import");
        List<ActorTake> takes = new ArrayList<>();
        try {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                String name = entry.getKey();
                if (!name.startsWith("takes/") || !name.endsWith(".take")) {
                    continue;
                }
                Path takeFile = takeTemp.resolve(
                    name.substring("takes/".length()).replace('/', '_'));
                Files.write(takeFile, entry.getValue());

                takes.add(TakeIO.read(takeFile));
            }
        } finally {
            deleteTree(takeTemp);
        }

        JsonObject gearActors = new JsonObject();
        byte[] gear = entries.get("gear.json");
        if (gear != null) {
            JsonObject parsed = JsonParser.parseString(
                new String(gear, StandardCharsets.UTF_8)).getAsJsonObject();
            if (parsed.has("actors") && parsed.get("actors").isJsonObject()) {
                gearActors = parsed.getAsJsonObject("actors");
            }
        }
        JsonObject emoteTracks = new JsonObject();
        byte[] emotes = entries.get("emotes.json");
        if (emotes != null) {
            JsonObject parsed = JsonParser.parseString(
                new String(emotes, StandardCharsets.UTF_8)).getAsJsonObject();
            if (parsed.has("tracks") && parsed.get("tracks").isJsonObject()) {
                emoteTracks = parsed.getAsJsonObject("tracks");
            }
        }
        JsonObject morphActors = new JsonObject();
        byte[] morphs = entries.get("morphs.json");
        if (morphs != null) {
            JsonObject parsed = JsonParser.parseString(
                new String(morphs, StandardCharsets.UTF_8)).getAsJsonObject();
            if (parsed.has("actors") && parsed.get("actors").isJsonObject()) {
                morphActors = parsed.getAsJsonObject("actors");
            }
        }
        return new Bundle(replayId, replayName, takes, gearActors, emoteTracks, morphActors);
    }


    public static Summary importInto(Bundle bundle, UUID replayId) {
        int takes = 0;
        for (ActorTake take : bundle.takes()) {
            ActorTake toSave = take;
            if (take != null && Files.isRegularFile(TakeStore.pathFor(replayId, take.id))) {
                toSave = take.copyAs(UUID.randomUUID(), take.name);
            }
            TakeStore.save(replayId, toSave);
            FlashbackActors.ACTORS.add(toSave);
            takes++;
        }
        int gearActors = 0;
        for (Map.Entry<String, com.google.gson.JsonElement> entry
                : bundle.gearActors().entrySet()) {
            if (Gear.store().importActor(entry.getKey(), entry.getValue())) {
                gearActors++;
            }
        }
        if (gearActors > 0) {
            Gear.save();
        }
        int emoteClips = 0;
        for (Map.Entry<String, com.google.gson.JsonElement> entry
                : bundle.emoteTracks().entrySet()) {
            emoteClips += Emotes.importClips(entry.getKey(), entry.getValue());
        }
        Emotes.save();
        int morphActors = 0;
        for (Map.Entry<String, com.google.gson.JsonElement> entry
                : bundle.morphActors().entrySet()) {
            if (Morph.store().importSelection(entry.getKey(), entry.getValue())) {
                morphActors++;
            }
        }
        if (morphActors > 0) {
            Morph.save();
        }
        return new Summary(takes, gearActors, emoteClips, morphActors);
    }


    public static Path findBundleFor(Path replayFile) {
        if (replayFile == null) {
            return null;
        }
        String name = replayFile.getFileName().toString();
        Path parent = replayFile.getParent();
        if (parent == null) {
            return null;
        }
        Path candidate;
        if (name.endsWith(".zip")) {
            candidate = parent.resolve(name.substring(0, name.length() - 4) + EXTENSION);
        } else {
            candidate = parent.resolve(name + EXTENSION);
        }
        return Files.isRegularFile(candidate) ? candidate : null;
    }


    public static Path findReplaySource(UUID replayId) {
        if (replayId == null) {
            return null;
        }
        Path folder = Flashback.getReplayFolder();
        if (folder == null || !Files.isDirectory(folder)) {
            return null;
        }
        try (Stream<Path> files = Files.list(folder)) {
            for (Path candidate : files
                    .filter(p -> p.getFileName().toString().endsWith(".zip")).toList()) {
                if (belongsTo(candidate, replayId)) {
                    return candidate;
                }
            }
        } catch (IOException e) {
            FlashbackActors.LOGGER.debug("Could not scan the replay folder for a bundle", e);
        }
        return null;
    }

    private static boolean belongsTo(Path zip, UUID wanted) {
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if ("metadata.json".equals(entry.getName())) {
                    JsonObject parsed = JsonParser.parseString(
                        new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                    String uuid = parsed.has("uuid") ? parsed.get("uuid").getAsString() : null;
                    return uuid != null && uuid.equals(wanted.toString());
                }
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    private static String manifest(UUID replayId, String replayName, JsonArray ids) {
        JsonObject root = new JsonObject();
        root.addProperty("version", BUNDLE_VERSION);
        root.addProperty("replayId", replayId == null ? "" : replayId.toString());
        root.addProperty("replayName", replayName == null ? "" : replayName);
        root.add("takes", ids);
        return root.toString();
    }

    private static String gearJson(JsonObject actors) {
        JsonObject root = new JsonObject();
        root.addProperty("version", GearStore.VERSION);
        root.add("actors", actors);
        return root.toString();
    }

    private static String emotesJson(JsonObject tracks) {
        JsonObject root = new JsonObject();
        root.add("tracks", tracks);
        return root.toString();
    }

    private static String morphsJson(JsonObject actors) {
        JsonObject root = new JsonObject();
        root.addProperty("version", pdx.dev.fbactors.morph.MorphStore.VERSION);
        root.add("actors", actors);
        return root.toString();
    }

    private static void writeEntry(ZipOutputStream out, String name, byte[] data)
            throws IOException {
        out.putNextEntry(new ZipEntry(name));
        out.write(data);
        out.closeEntry();
    }

    private static void deleteTree(Path dir) {
        try (Stream<Path> files = Files.walk(dir)) {
            for (Path file : files.sorted(java.util.Comparator.reverseOrder()).toList()) {
                try {
                    Files.deleteIfExists(file);
                } catch (IOException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
    }

}

