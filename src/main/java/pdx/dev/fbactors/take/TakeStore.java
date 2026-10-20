package pdx.dev.fbactors.take;

import com.moulberry.flashback.Flashback;
import pdx.dev.fbactors.FlashbackActors;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

public final class TakeStore {

    private static final long SAVE_INTERVAL_NANOS = 2_000_000_000L;

    private static final java.util.Map<UUID, Long> lastSaveNanos = new java.util.HashMap<>();
    private static final java.util.Set<String> dirty = new java.util.HashSet<>();

    private static String dirtyKey(UUID replayId, UUID takeId) {
        return replayId + "/" + takeId;
    }

    private TakeStore() {
    }

    public static Path rootFor(UUID replayId) {
        return Flashback.getDataDirectory().resolve("fbactors").resolve(replayId.toString());
    }

    public static Path pathFor(UUID replayId, UUID takeId) {
        return rootFor(replayId).resolve(takeId + ".take");
    }

    public static void save(UUID replayId, ActorTake take) {
        try {
            TakeIO.write(pathFor(replayId, take.id), take);
            lastSaveNanos.put(take.id, System.nanoTime());
        } catch (IOException e) {
            FlashbackActors.LOGGER.error("Failed to save take {}", take.id, e);
        }
    }


    public static void saveThrottled(UUID replayId, ActorTake take) {
        Long last = lastSaveNanos.get(take.id);
        long now = System.nanoTime();
        if (last != null && now - last < SAVE_INTERVAL_NANOS) {
            dirty.add(dirtyKey(replayId, take.id));
            return;
        }
        save(replayId, take);
    }


    public static void flushDirty(UUID replayId, java.util.function.Function<UUID, ActorTake> lookup) {
        if (dirty.isEmpty()) {
            return;
        }
        String prefix = replayId + "/";
        for (String key : new ArrayList<>(dirty)) {
            if (!key.startsWith(prefix)) {
                continue;
            }
            dirty.remove(key);
            UUID id;
            try {
                id = UUID.fromString(key.substring(prefix.length()));
            } catch (IllegalArgumentException bad) {
                continue;
            }
            ActorTake take = lookup.apply(id);
            if (take != null) {
                save(replayId, take);
            }
        }
    }

    public static void clearDirty(UUID takeId) {
        dirty.removeIf(key -> key.endsWith("/" + takeId));
    }

    public static List<ActorTake> loadAll(UUID replayId) {
        List<ActorTake> takes = new ArrayList<>();
        Path root = rootFor(replayId);
        if (!Files.isDirectory(root)) {
            return takes;
        }
        try (Stream<Path> files = Files.list(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".take")).toList()) {
                try {
                    ActorTake take = TakeIO.read(file);
                    takes.add(take);

                    if (TakeIO.isLegacy(file)) {
                        backUpOnce(file);
                        try {
                            TakeIO.write(file, take);
                            FlashbackActors.LOGGER.info(
                                "Re-saved migrated take {} in the current format", file.getFileName());
                        } catch (IOException e) {
                            FlashbackActors.LOGGER.warn(
                                "Migrated take {} plays this session but was not re-saved",
                                file.getFileName(), e);
                        }
                    }
                } catch (IOException e) {
                    FlashbackActors.LOGGER.warn("Skipping unreadable take {}", file.getFileName(), e);
                }
            }
        } catch (IOException e) {
            FlashbackActors.LOGGER.error("Failed to list takes for replay {}", replayId, e);
        }
        takes.sort((a, b) -> Integer.compare(a.startTick, b.startTick));
        return takes;
    }


    private static void backUpOnce(Path file) {
        try {
            int version = TakeIO.peekVersion(file);
            Path backup = file.resolveSibling(file.getFileName() + ".v" + version + ".bak");
            if (!Files.isRegularFile(backup)) {
                Files.copy(file, backup);
            }
        } catch (IOException | RuntimeException e) {
            FlashbackActors.LOGGER.warn("Could not back up take {} before re-saving",
                file.getFileName(), e);
        }
    }

    public static void delete(UUID replayId, UUID takeId) {
        try {
            Files.deleteIfExists(pathFor(replayId, takeId));
        } catch (IOException e) {
            FlashbackActors.LOGGER.error("Failed to delete take {}", takeId, e);
        }
    }
}

