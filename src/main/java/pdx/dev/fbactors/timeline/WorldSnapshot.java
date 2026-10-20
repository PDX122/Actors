package pdx.dev.fbactors.timeline;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.record.Recorder;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import pdx.dev.fbactors.FlashbackActors;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

public final class WorldSnapshot {

    private static final int MIN_WAIT_TICKS = 20;
    private static final int MAX_WAIT_TICKS = 200;

    private static boolean running;
    private static boolean registered;

    private static Consumer<Result> pending;
    private static int waited;
    private static int stable;

    private WorldSnapshot() {
    }

    public static boolean isRunning() {
        return running;
    }

    public static void capture(Consumer<Result> done) {
        if (running) {
            done.accept(Result.failure("A snapshot is already being taken"));
            return;
        }
        if (Flashback.isInReplay()) {
            done.accept(Result.failure("Already inside a replay"));
            return;
        }
        if (Minecraft.getInstance().level == null) {
            done.accept(Result.failure("No world loaded"));
            return;
        }

        List<String> incompatible = Flashback.getRecordingIncompatibleMods();
        if (incompatible != null && !incompatible.isEmpty()) {
            done.accept(Result.failure("These mods block recording: "
                + String.join(", ", incompatible)));
            return;
        }

        running = true;
        try {
            Flashback.startRecordingReplay();
        } catch (Exception e) {
            running = false;
            FlashbackActors.LOGGER.error("Could not start a recording for the snapshot", e);
            done.accept(Result.failure("Could not start recording, see the log"));
            return;
        }

        FlashbackActors.LOGGER.info("Snapshot: recording the world");
        pending = done;
        waited = 0;
        stable = 0;
        registerOnce();
    }

    private static void registerOnce() {
        if (registered) {
            return;
        }
        registered = true;
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
    }

    private static void tick() {
        Consumer<Result> done = pending;
        if (done == null) {
            return;
        }

        pending = null;

        waited++;
        Recorder recorder = Flashback.RECORDER;

        boolean ready = recorder != null && recorder.readyToWrite();
        stable = ready ? stable + 1 : 0;

        boolean outOfTime = waited >= MAX_WAIT_TICKS;
        boolean settled = ready && (stable >= MIN_WAIT_TICKS || waited >= MAX_WAIT_TICKS);
        if (!settled && !outOfTime) {

            pending = done;
            return;
        }

        if (recorder != null) {
            recorder.setPaused(false);
        }
        try {
            Flashback.finishRecordingReplay();
        } catch (Exception e) {
            running = false;
            FlashbackActors.LOGGER.error("Could not finish the snapshot recording", e);
            done.accept(Result.failure("Could not finish recording, see the log"));
            return;
        }

        Path source = locate();
        running = false;
        if (source == null) {
            FlashbackActors.LOGGER.warn("Snapshot: recording finished but no replay file appeared");
            done.accept(Result.failure("Recording finished but no replay was written"));
            return;
        }

        FlashbackActors.LOGGER.info("Snapshot: captured the world to {}", source);
        done.accept(Result.success(source));
    }

    private static Path locate() {
        Path newest = null;
        for (Path folder : List.of(
            Flashback.getReplayFolder(),
            Minecraft.getInstance().gameDirectory.toPath().resolve("flashback").resolve("replays"),
            Minecraft.getInstance().gameDirectory.toPath().resolve("flashback").resolve("recordings"))) {
            if (folder == null || !Files.isDirectory(folder)) {
                continue;
            }
            try (var files = Files.list(folder)) {
                for (Path candidate : files.filter(p -> p.toString().endsWith(".zip")).toList()) {
                    if (newest == null
                        || Files.getLastModifiedTime(candidate).compareTo(
                            Files.getLastModifiedTime(newest)) > 0) {
                        newest = candidate;
                    }
                }
            } catch (Exception e) {
                FlashbackActors.LOGGER.debug("Could not list {}: {}", folder, e.toString());
            }
        }
        return newest;
    }

    public record Result(Path replay, String failure) {

        static Result success(Path replay) {
            return new Result(replay, null);
        }

        static Result failure(String reason) {
            return new Result(null, reason);
        }

        public boolean ok() {
            return replay != null;
        }
    }
}

