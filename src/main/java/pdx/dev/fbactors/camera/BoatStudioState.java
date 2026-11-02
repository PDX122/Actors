package pdx.dev.fbactors.camera;

import com.moulberry.flashback.Flashback;
import pdx.dev.fbactors.FlashbackActors;
import pdx.dev.fbactors.take.TakeStore;
import java.nio.file.Files;
import java.util.UUID;

public final class BoatStudioState {
    private static Object session;
    private static UUID replayId;
    private static BoatStudioState current = new BoatStudioState();
    private static boolean dirty;
    private static final pdx.dev.fbactors.util.RetryingJsonSaves<BoatStudioState> SAVES = new pdx.dev.fbactors.util.RetryingJsonSaves<>("boat-studio.json");
    private static long lastSave;
    public UUID target, rival;
    public BoatTimeline timeline = new BoatTimeline();

    public static BoatStudioState get() {
        var replay = Flashback.getReplayServer();
        if (replay != session) {
            flush(true);
            session = replay;
            replayId = replay == null ? null : replay.getMetadata().replayIdentifier;
            dirty = false;
            current = new BoatStudioState();
            var pending = SAVES.pending(replayId);
            if (pending != null) current = pending;
            else if (replayId != null) {
                var file = TakeStore.rootFor(replayId).resolve("boat-studio.json");
                if (Files.isRegularFile(file)) try {
                    var loaded = pdx.dev.fbactors.util.JsonFiles.read(file, BoatStudioState.class, 4_000_000);
                    if (loaded != null) {
                        if (loaded.timeline == null) loaded.timeline = new BoatTimeline();
                        loaded.timeline.normalize();
                        current = loaded;
                    }
                } catch (Exception e) { FlashbackActors.LOGGER.warn("Could not load boat studio settings; using defaults", e); }
            }
        }
        return current;
    }
    public static void changed() { dirty = true; }
    public static boolean waitingToSave() { return !SAVES.isEmpty(); }
    public static void flush(boolean now) {
        if (dirty && replayId != null) { SAVES.put(replayId, current); dirty = false; }
        if (SAVES.isEmpty() || !now && System.currentTimeMillis() - lastSave < 750) return;
        lastSave = System.currentTimeMillis();
        SAVES.flush(TakeStore::rootFor);
    }
}
