package pdx.dev.fbactors.convert;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;


public final class McprReader {


    public record Packet(int timestampMs, byte[] payload) {
    }


    public record Recording(
        JsonObject meta,
        int durationMs,
        String mcVersion,
        String serverName,
        TreeMap<Integer, List<byte[]>> ticks,
        int totalTicks
    ) {
    }

    private McprReader() {
    }


    public static Recording readMeta(Path mcpr) throws IOException {
        JsonObject meta;
        try (ZipFile zip = new ZipFile(mcpr.toFile())) {
            meta = readJson(zip, "metaData.json");
        }
        return new Recording(meta, durationMs(meta), mcVersion(meta), serverName(meta),
            new TreeMap<>(), ticksFor(durationMs(meta)));
    }

    private static int durationMs(JsonObject meta) {
        return meta.has("duration") ? meta.get("duration").getAsInt() : 0;
    }

    private static String mcVersion(JsonObject meta) {
        return meta.has("mcversion") ? meta.get("mcversion").getAsString() : "";
    }

    private static String serverName(JsonObject meta) {
        return meta.has("customServerName") ? meta.get("customServerName").getAsString()
            : "mcpr-import";
    }

    private static int ticksFor(int durationMs) {
        return Math.max(1, (durationMs + 49) / 50);
    }


    public static Recording read(Path mcpr) throws IOException {
        JsonObject meta;
        byte[] stream;
        try (ZipFile zip = new ZipFile(mcpr.toFile())) {
            meta = readJson(zip, "metaData.json");
            stream = readAll(zip, "recording.tmcpr");
        }

        int durationMs = durationMs(meta);
        String mcVersion = mcVersion(meta);
        String serverName = serverName(meta);

        TreeMap<Integer, List<byte[]>> ticks = new TreeMap<>();
        int maxTick = 0;
        int pos = 0;
        while (pos < stream.length) {
            if (pos + 8 > stream.length) {
                throw new IOException("Truncated .tmcpr record header at byte " + pos
                    + " of " + stream.length);
            }
            int timestamp = getInt(stream, pos);
            int length = getInt(stream, pos + 4);
            if (timestamp < 0 || length < 0 || pos + 8 + length > stream.length) {
                throw new IOException("Corrupt .tmcpr record at byte " + pos
                    + " (timestamp " + timestamp + ", length " + length + ")");
            }
            byte[] payload = new byte[length];
            System.arraycopy(stream, pos + 8, payload, 0, length);
            int tick = Math.max(0, timestamp / 50);
            ticks.computeIfAbsent(tick, k -> new ArrayList<>()).add(payload);
            maxTick = Math.max(maxTick, tick);
            pos += 8 + length;
        }

        return new Recording(meta, durationMs, mcVersion, serverName, ticks,
            Math.max(1, maxTick + 1));
    }

    private static int getInt(byte[] data, int pos) {
        return ((data[pos] & 0xFF) << 24) | ((data[pos + 1] & 0xFF) << 16)
            | ((data[pos + 2] & 0xFF) << 8) | (data[pos + 3] & 0xFF);
    }

    private static JsonObject readJson(ZipFile zip, String name) throws IOException {
        return JsonParser.parseString(new String(readAll(zip, name), StandardCharsets.UTF_8))
            .getAsJsonObject();
    }

    private static byte[] readAll(ZipFile zip, String name) throws IOException {
        ZipEntry entry = zip.getEntry(name);
        if (entry == null) {
            throw new IOException(".mcpr is missing " + name);
        }
        try (InputStream in = zip.getInputStream(entry);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[65536];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }


    public static List<String> entries(Path zip) throws IOException {
        List<String> names = new ArrayList<>();
        try (ZipFile file = new ZipFile(zip.toFile())) {
            Enumeration<? extends ZipEntry> entries = file.entries();
            while (entries.hasMoreElements()) {
                names.add(entries.nextElement().getName());
            }
        }
        return names;
    }


    public static String entryText(Path zip, String name) throws IOException {
        try (ZipFile file = new ZipFile(zip.toFile())) {
            return new String(readAll(file, name), StandardCharsets.UTF_8);
        }
    }
}

