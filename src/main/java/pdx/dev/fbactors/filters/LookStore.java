package pdx.dev.fbactors.filters;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import pdx.dev.fbactors.util.AtomicSave;

public final class LookStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private LookStore() {}

    public static LookSettings load(Path file) {
        try {
            if (Files.isRegularFile(file) && Files.size(file) <= 2_000_000) {
                LookSettings result = GSON.fromJson(Files.readString(file), LookSettings.class);
                if (result != null) {
                    result.normalize();
                    return result;
                }
            }
        } catch (IOException | RuntimeException ignored) {}
        return new LookSettings();
    }

    public static boolean save(Path file, LookSettings settings) {
        settings.normalize();
        return AtomicSave.writeJson(file.getParent(), file.getFileName().toString(), out -> GSON.toJson(settings, out));
    }

    public static Map<String, LookSettings> presets(Path file) {
        Map<String, LookSettings> clean = new LinkedHashMap<>();
        try {
            if (!Files.isRegularFile(file) || Files.size(file) > 2_000_000) return clean;
            Map<String, LookSettings> saved = GSON.fromJson(Files.readString(file),
                new TypeToken<LinkedHashMap<String, LookSettings>>() {}.getType());
            if (saved != null) saved.forEach((name, look) -> {
                if (name != null && !name.isBlank() && name.length() <= 64 && look != null && clean.size() < 100) {
                    look.normalize();
                    clean.put(name, look);
                }
            });
        } catch (IOException | RuntimeException ignored) {}
        return clean;
    }

    public static boolean presets(Path file, Map<String, LookSettings> presets) {
        return AtomicSave.writeJson(file.getParent(), file.getFileName().toString(), out -> GSON.toJson(presets, out));
    }
}
