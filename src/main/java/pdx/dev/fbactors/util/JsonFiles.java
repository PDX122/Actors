package pdx.dev.fbactors.util;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class JsonFiles {
    private static final Gson GSON = new Gson();
    private JsonFiles() {}
    public static <T> T read(Path file, Class<T> type, int maxBytes) throws IOException {
        if (maxBytes < 1 || maxBytes == Integer.MAX_VALUE) throw new IllegalArgumentException("Invalid JSON size limit");
        if (Files.size(file) > maxBytes) throw new IOException("JSON file exceeds " + maxBytes + " bytes: " + file);
        byte[] bytes;
        try (var input = Files.newInputStream(file)) { bytes = input.readNBytes(maxBytes + 1); }
        if (bytes.length > maxBytes) throw new IOException("JSON file grew past its size limit: " + file);
        try { return GSON.fromJson(new String(bytes, StandardCharsets.UTF_8), type); }
        catch (RuntimeException | StackOverflowError error) { throw new IOException("Invalid JSON: " + file, error); }
    }
}
