package pdx.dev.fbactors.filters;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.moulberry.flashback.Flashback;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;
import pdx.dev.fbactors.FlashbackActors;
import pdx.dev.fbactors.undo.UndoStack;
import pdx.dev.fbactors.util.AtomicSave;

public final class Effects {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static UUID replay;
    private static EffectsTimeline timeline = new EffectsTimeline();
    private static EffectsTimeline checkpoint = new EffectsTimeline();
    private static EffectsTimeline pending;
    private static long saveAt;
    public static UUID selected;
    public static String status = "";
    private Effects() {}

    private static Path file() { return Flashback.getDataDirectory().resolve("fbactors").resolve(replay.toString()).resolve("effects.json"); }

    public static EffectsTimeline timeline() {
        UUID current = FlashbackActors.loadedReplayIdOrNull();
        if (!Objects.equals(replay, current)) {
            finishEdit();
            flush();
            replay = current;
            timeline = new EffectsTimeline();
            selected = null;
            status = "";
            if (replay != null) {
                try {
                    if (Files.isRegularFile(file()) && Files.size(file()) <= 4_000_000) {
                        EffectsTimeline loaded = GSON.fromJson(Files.readString(file()), EffectsTimeline.class);
                        if (loaded != null) timeline = loaded;
                    }
                } catch (Exception error) { status = "Could not read the effects track."; }
            }
            timeline.normalize();
            checkpoint = timeline.copy();
        }
        return timeline;
    }

    public static void importReplayGrade(LookSettings look) {
        var server = Flashback.getReplayServer();
        if (server != null && timeline().importReplayGrade(look, server.getTotalReplayTicks())) {
            checkpoint = timeline.copy();
            pending = null;
            saveAt = System.nanoTime();
            flush();
        }
    }

    public static EffectClip selected() { return timeline().find(selected); }

    public static void changed() {
        if (pending == null) pending = checkpoint.copy();
        timeline.normalize();
        saveAt = System.nanoTime() + 500_000_000L;
    }

    public static void finishEdit() {
        if (pending == null) return;
        EffectsTimeline before = pending;
        EffectsTimeline after = timeline.copy();
        UUID owner = replay;
        pending = null;
        checkpoint = after.copy();
        UndoStack.pushEdit(() -> restore(owner, before), () -> restore(owner, after));
    }

    private static void restore(UUID owner, EffectsTimeline value) {
        if (!Objects.equals(FlashbackActors.loadedReplayIdOrNull(), owner)) return;
        timeline = value.copy();
        checkpoint = value.copy();
        pending = null;
        saveAt = System.nanoTime();
        flush();
    }

    public static void add(String filter, int tick) { add(filter, tick, null); }

    public static void add(String filter, int tick, UUID actor) {
        var track = timeline();
        if (actor == null && track.locked || track.clips.size() >= 256 || FilterCatalog.find(filter) == null) return;
        pdx.dev.fbactors.debug.DragDiag.log("clip created: filter=%s tick=%d actor=%s",
            filter, tick, String.valueOf(actor));
        EffectClip clip = new EffectClip();
        clip.name = FilterCatalog.find(filter).name();
        clip.actor = actor;
        clip.start = Math.max(0, Math.min(99_999_900, tick));
        clip.fadeIn = clip.fadeOut = 0;
        clip.end = clip.start + 100;
        clip.look.add(filter);
        track.clips.add(clip);
        selected = clip.id;
        changed();
        finishEdit();
    }

    public static void attach(String filter, pdx.dev.fbactors.actor.Actor actor) {
        var track = timeline();
        if (actor.take.locked || FilterCatalog.find(filter) == null) return;
        pdx.dev.fbactors.debug.DragDiag.log("clip attached: filter=%s actor=%s",
            filter, actor.displayName());
        int start = Math.max(0, actor.take.firstTick() + actor.offset());
        int end = Math.max(start + 1, actor.take.lastTick() + actor.offset() + 1);
        EffectClip existing = track.active(Math.max(start, Math.min(end - 1, FlashbackActors.replayTick())), actor.take.id);
        if (existing != null && existing.look.filters.size() < LookSettings.MAX_FILTERS) {
            if (existing.look.add(filter)) { selected = existing.id; changed(); finishEdit(); }
            return;
        }
        if (track.clips.size() >= 256) return;
        var clip = new EffectClip();
        clip.actor = actor.take.id;
        clip.name = FilterCatalog.find(filter).name();
        clip.start = start;
        clip.end = end;
        clip.fadeIn = clip.fadeOut = 0;
        clip.look.add(filter);
        track.clips.add(clip);
        selected = clip.id;
        changed();
        finishEdit();
    }

    public static boolean locked(EffectClip clip) {
        if (clip == null || clip.actor == null) return timeline().locked;
        return FlashbackActors.ACTORS.actors().stream().filter(actor -> actor.take.id.equals(clip.actor))
            .anyMatch(actor -> actor.take.locked);
    }

    public static void remove() {
        var track = timeline();
        if (selected == null || locked(track.find(selected))) return;
        if (track.clips.removeIf(clip -> clip.id.equals(selected))) { changed(); finishEdit(); }
        selected = null;
    }

    public static void removeTrack(UUID actor) {
        var track = timeline();
        if (track.clips.stream().filter(clip -> Objects.equals(clip.actor, actor)).anyMatch(Effects::locked)) return;
        finishEdit();
        if (!track.removeTrack(actor)) return;
        if (track.find(selected) == null) selected = null;
        changed();
        finishEdit();
    }

    public static void duplicate() {
        var clip = selected();
        if (clip == null || locked(clip) || timeline.clips.size() >= 256) return;
        EffectClip copy = clip.copy();
        copy.id = UUID.randomUUID();
        int length = copy.end - copy.start;
        copy.start = timeline.nextStart(copy.end, length, copy.actor);
        copy.end = copy.start + length;
        timeline.clips.add(copy);
        selected = copy.id;
        changed();
        finishEdit();
    }

    public static void tick() {
        timeline();
        if (saveAt != 0 && System.nanoTime() >= saveAt) flush();
    }

    public static void flush() {
        if (saveAt == 0 || replay == null) return;
        Path target = file();
        if (!AtomicSave.writeJson(target.getParent(), target.getFileName().toString(), writer -> GSON.toJson(timeline, writer))) {
            status = "Could not save the effects track.";
        }
        saveAt = 0;
    }

    public static void close() { finishEdit(); flush(); }
}
