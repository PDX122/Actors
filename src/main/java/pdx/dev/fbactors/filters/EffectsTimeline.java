package pdx.dev.fbactors.filters;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

public final class EffectsTimeline {
    public boolean enabled = true;
    public boolean locked;
    public boolean replayGradeImported;
    public List<EffectClip> clips = new ArrayList<>();

    public void normalize() {
        if (clips == null) clips = new ArrayList<>();
        var ids = new HashSet<UUID>();
        clips = new ArrayList<>(clips.stream().filter(clip -> clip != null).limit(256).toList());
        for (var clip : clips) {
            clip.normalize();
            if (!ids.add(clip.id)) { clip.id = UUID.randomUUID(); ids.add(clip.id); }
        }
        clips.sort(Comparator.comparingInt(clip -> clip.start));
    }

    public EffectsTimeline copy() {
        EffectsTimeline copy = new EffectsTimeline();
        copy.enabled = enabled;
        copy.locked = locked;
        copy.replayGradeImported = replayGradeImported;
        clips.forEach(clip -> copy.clips.add(clip.copy()));
        return copy;
    }

    public boolean importReplayGrade(LookSettings look, int duration) {
        if (replayGradeImported) return false;
        replayGradeImported = true;
        if (look.enabled) {
            var clip = new EffectClip();
            clip.name = "Replay look";
            clip.start = 0;
            clip.end = Math.max(1, duration);
            clip.fadeIn = clip.fadeOut = 0;
            clip.look = look.copy();
            clips.add(0, clip);
        }
        normalize();
        return true;
    }

    public EffectClip find(UUID id) {
        return clips.stream().filter(clip -> clip.id.equals(id)).findFirst().orElse(null);
    }

    public boolean removeTrack(UUID actor) {
        if (actor == null && locked) return false;
        return clips.removeIf(clip -> java.util.Objects.equals(clip.actor, actor));
    }

    public EffectClip active(float tick) { return active(tick, null); }

    public EffectClip active(float tick, UUID actor) {
        if (actor == null && !enabled || !Float.isFinite(tick)) return null;
        EffectClip active = null;
        for (var clip : clips) {
            if (java.util.Objects.equals(clip.actor, actor) && clip.enabled && clip.look.enabled && tick >= clip.start && tick < clip.end
                && (active == null || clip.start >= active.start)) active = clip;
        }
        return active;
    }

    public LookSettings evaluate(float tick, LookSettings base) {
        EffectClip clip = active(tick);
        if (clip == null) return base;
        LookSettings result = clip.look.copy();
        result.intensity *= clip.weight(tick);
        return result;
    }

    public LookSettings actorLook(float tick, UUID actor) {
        if (actor == null) return null;
        EffectClip clip = active(tick, actor);
        if (clip == null) return null;
        LookSettings result = clip.look.copy();
        result.intensity *= clip.weight(tick);
        return result;
    }

    public int nextStart(int requested, int duration) { return nextStart(requested, duration, null); }

    public int nextStart(int requested, int duration, UUID actor) {
        int start = Math.max(0, requested);
        for (var clip : clips.stream().filter(item -> java.util.Objects.equals(item.actor, actor)).sorted(Comparator.comparingInt(clip -> clip.start)).toList()) {
            if ((long) start + duration > clip.start && start < clip.end) start = clip.end;
        }
        return Math.min(100_000_000 - duration, start);
    }
}
