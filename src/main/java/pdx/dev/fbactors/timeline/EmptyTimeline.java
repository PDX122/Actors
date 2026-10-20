package pdx.dev.fbactors.timeline;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.io.ReplayWriter;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.record.FlashbackChunkMeta;
import com.moulberry.flashback.record.FlashbackMeta;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import pdx.dev.fbactors.FlashbackActors;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public final class EmptyTimeline {

    public static final int MIN_SECONDS = 5;
    public static final int MAX_SECONDS = 600;

    private static final String METADATA = "metadata.json";
    private static final String CHUNK = "c0.flashback";
    private static final String WORLD_CACHE_PREFIX = "level_chunk_caches/";
    private static final int TICKS_PER_SECOND = 20;

    private EmptyTimeline() {
    }

    public static int clampSeconds(int seconds) {
        return Math.max(MIN_SECONDS, Math.min(MAX_SECONDS, seconds));
    }

    public record Result(boolean ok, String message) {

        public static Result failure(String message) {
            return new Result(false, message);
        }
    }

    public static Result generateAndOpen(String requestedName, int seconds) {
        Path path;
        try {
            path = generate(requestedName, seconds);
        } catch (NoReplayOpenException e) {
            FlashbackActors.LOGGER.warn("Timeline needs an open replay: {}", e.getMessage());
            return Result.failure("Open a replay first, so there is a map to copy");
        } catch (Exception e) {
            FlashbackActors.LOGGER.error("Could not create an empty timeline", e);
            return Result.failure("Could not create the timeline, see the log");
        }

        try {
            Flashback.openReplayWorld(path);
        } catch (Exception e) {
            FlashbackActors.LOGGER.error("Wrote the timeline but could not open it", e);
            return Result.failure("Wrote it, but Flashback would not open it, see the log");
        }

        return new Result(true, "Created " + path.getFileName());
    }

    public static Result snapshotAndOpen(String requestedName, int seconds, WorldSnapshot.Result source) {
        if (source == null || !source.ok()) {
            return Result.failure(source == null ? "No world snapshot" : source.failure());
        }
        try {
            return fromSource(requestedName, seconds, source.replay());
        } catch (Exception e) {
            FlashbackActors.LOGGER.error("Could not build a timeline from the snapshot", e);
            return Result.failure("Could not build the timeline, see the log");
        }
    }

    private static Result fromSource(String requestedName, int seconds, Path source) {
        Path path;
        try {
            path = generate(requestedName, seconds, source);
        } catch (Exception e) {
            FlashbackActors.LOGGER.error("Could not write the timeline", e);
            return Result.failure("Could not write the timeline, see the log");
        }
        try {
            Flashback.openReplayWorld(path);
        } catch (Exception e) {
            FlashbackActors.LOGGER.error("Wrote the timeline but could not open it", e);
            return Result.failure("Wrote it, but Flashback would not open it, see the log");
        }
        return new Result(true, "Created " + path.getFileName());
    }

    public static final class NoReplayOpenException extends IOException {
        public NoReplayOpenException(String message) {
            super(message);
        }
    }

    static Path generate(String requestedName, int seconds) throws IOException {
        ReplayServer replay = Flashback.getReplayServer();
        if (replay == null) {
            throw new NoReplayOpenException("open a replay first, so there is a world to copy");
        }
        Path source = findSource(replay);
        if (source == null) {
            throw new IOException("Could not find the replay file for " + replay.getMetadata().name);
        }
        return generate(requestedName, seconds, source);
    }

    static Path generate(String requestedName, int seconds, Path source) throws IOException {

        int ticks = clampSeconds(seconds) * TICKS_PER_SECOND;
        Path folder = Flashback.getReplayFolder();
        Files.createDirectories(folder);
        String name = requestedName == null || requestedName.isBlank()
            ? "Timeline " + clampSeconds(seconds) + "s"
            : requestedName.trim();
        Path target = uniquePath(folder, name);

        RegistryAccess registries = currentRegistries();

        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(source));
             ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(target))) {

            boolean sawMetadata = false;
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                String name0 = entry.getName();

                if (METADATA.equals(name0)) {
                    write(out, METADATA, rewriteMetadata(readAll(in), ticks, name).getBytes(StandardCharsets.UTF_8));
                    sawMetadata = true;
                } else if (CHUNK.equals(name0)) {

                    continue;
                } else if (name0.equals("icon.png") || name0.startsWith(WORLD_CACHE_PREFIX)) {

                    write(out, name0, in.readAllBytes());
                }
            }

            if (!sawMetadata) {
                throw new IOException("Source replay has no " + METADATA);
            }

            out.putNextEntry(new ZipEntry(CHUNK));
            out.write(emptyChunk(registries));
            out.closeEntry();
        }

        FlashbackActors.LOGGER.info("Created empty timeline '{}' ({} ticks) at {}",
            name, ticks, target);
        return target;
    }

    private static byte[] emptyChunk(RegistryAccess registries) {
        ReplayWriter writer = new ReplayWriter(registries);
        writer.startSnapshot();
        writer.endSnapshot();
        return writer.popBytes();
    }

    private static String rewriteMetadata(String json, int ticks, String name) {
        JsonObject parsed = JsonParser.parseString(json).getAsJsonObject();
        FlashbackMeta meta = FlashbackMeta.fromJson(parsed);

        meta.replayIdentifier = UUID.randomUUID();
        meta.name = name;
        meta.totalTicks = ticks;
        meta.worldName = null;
        meta.bobbyWorldName = null;

        LinkedHashMap<String, FlashbackChunkMeta> chunks = new LinkedHashMap<>();
        FlashbackChunkMeta chunk = new FlashbackChunkMeta();
        chunk.duration = ticks;
        chunk.forcePlaySnapshot = true;
        chunks.put(CHUNK, chunk);
        meta.chunks = chunks;

        return meta.toJson().toString();
    }

    private static RegistryAccess currentRegistries() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            throw new IllegalStateException("no level to read registries from");
        }
        return mc.level.registryAccess();
    }

    private static Path findSource(ReplayServer replay) throws IOException {
        UUID wanted = replay.getMetadata().replayIdentifier;
        Path folder = Flashback.getReplayFolder();
        if (folder == null || !Files.isDirectory(folder)) {
            return null;
        }
        try (var files = Files.list(folder)) {
            for (Path candidate : files.filter(p -> p.getFileName().toString().endsWith(".zip")).toList()) {
                if (belongsTo(candidate, wanted)) {
                    return candidate;
                }
            }
        }
        return null;
    }

    private static boolean belongsTo(Path zip, UUID wanted) {
        try (ZipInputStream in = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if (METADATA.equals(entry.getName())) {
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
    private static Path uniquePath(Path folder, String name) {
        String stamp = name.replaceAll("[^A-Za-z0-9_.-]", "_");
        Path candidate = folder.resolve(stamp + ".zip");
        int n = 2;
        while (Files.exists(candidate)) {
            candidate = folder.resolve(stamp + " " + n + ".zip");
            n++;
        }
        return candidate;
    }

    private static String readAll(InputStream in) throws IOException {
        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }

    private static void write(ZipOutputStream out, String name, byte[] data) throws IOException {
        out.putNextEntry(new ZipEntry(name));
        out.write(data);
        out.closeEntry();
    }
}

