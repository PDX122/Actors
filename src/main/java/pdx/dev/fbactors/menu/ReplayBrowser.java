package pdx.dev.fbactors.menu;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;
import pdx.dev.fbactors.convert.McprReader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;


public final class ReplayBrowser {


    public enum Source {

        FLASHBACK,

        REPLAY_MOD
    }


    public record Entry(Source source, Path path, String name, int ticks) {

        public int seconds() {
            return Math.max(1, (ticks + 19) / 20);
        }
    }

    private ReplayBrowser() {
    }


    public static List<Entry> scan(@Nullable Path flashbackFolder, @Nullable Path replayModFolder) {
        List<Entry> entries = new ArrayList<>();
        scanFlashback(flashbackFolder, entries);


        entries.sort(Comparator.comparingLong((Entry e) -> modified(e)).reversed()
            .thenComparing(e -> e.name().toLowerCase(java.util.Locale.ROOT)));
        return entries;
    }

    private static long modified(Entry entry) {
        try {
            return Files.getLastModifiedTime(entry.path()).toMillis();
        } catch (Exception e) {
            return 0L;
        }
    }

    private static void scanFlashback(@Nullable Path folder, List<Entry> entries) {
        if (folder == null || !Files.isDirectory(folder)) {
            return;
        }
        try (Stream<Path> files = Files.list(folder)) {
            for (Path file : files
                    .filter(p -> p.getFileName().toString().endsWith(".zip")).toList()) {
                Entry entry = readFlashback(file);
                if (entry != null) {
                    entries.add(entry);
                }
            }
        } catch (Exception e) {
            pdx.dev.fbactors.FlashbackActors.LOGGER.warn("Could not list Flashback replays in {}",
                folder, e);
        }
    }

    @Nullable
    private static Entry readFlashback(Path file) {
        try {
            String json = McprReader.entryText(file, "metadata.json");
            JsonObject meta = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            String name = meta.has("name") ? meta.get("name").getAsString()
                : file.getFileName().toString();
            int ticks = meta.has("total_ticks") ? meta.get("total_ticks").getAsInt()
                : (meta.has("totalTicks") ? meta.get("totalTicks").getAsInt() : 0);
            return new Entry(Source.FLASHBACK, file, name, Math.max(0, ticks));
        } catch (Exception e) {
            return null;
        }
    }

    private static void scanReplayMod(@Nullable Path folder, List<Entry> entries) {
        if (folder == null || !Files.isDirectory(folder)) {
            return;
        }
        try (Stream<Path> files = Files.list(folder)) {
            for (Path file : files
                    .filter(p -> p.getFileName().toString().endsWith(".mcpr")).toList()) {
                Entry entry = readReplayMod(file);
                if (entry != null) {
                    entries.add(entry);
                }
            }
        } catch (Exception e) {
            pdx.dev.fbactors.FlashbackActors.LOGGER.warn("Could not list ReplayMod recordings in {}",
                folder, e);
        }
    }

    @Nullable
    private static Entry readReplayMod(Path file) {
        try {
            McprReader.Recording recording = McprReader.readMeta(file);
            String name = file.getFileName().toString().replaceAll("\\.mcpr$", "");
            return new Entry(Source.REPLAY_MOD, file, name, recording.totalTicks());
        } catch (Exception e) {
            return null;
        }
    }


    @Nullable
    public static Path flashbackFolder() {
        try {
            return com.moulberry.flashback.Flashback.getReplayFolder();
        } catch (Exception e) {
            return null;
        }
    }


    @Nullable
    public static Path replayModFolder(Path gameDir) {
        try {
            Path folder = gameDir.resolve("replay_recordings");
            return Files.isDirectory(folder) ? folder : null;
        } catch (Exception e) {
            return null;
        }
    }
}

