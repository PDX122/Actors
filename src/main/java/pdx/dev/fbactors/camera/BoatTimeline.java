package pdx.dev.fbactors.camera;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class BoatTimeline {
    public boolean enabled = true;
    public boolean locked;
    public List<Clip> clips = new ArrayList<>();
    public static final class Clip {
        public UUID id = UUID.randomUUID();
        public int start, end;
        public boolean enabled = true;
        public UUID target, rival;
        public BoatCam.Settings settings;
        public Clip copy() {
            var copy = new Clip(); copy.id=id; copy.start=start; copy.end=end;
            copy.enabled=enabled; copy.target=target; copy.rival=rival; copy.settings=settings;
            return copy;
        }
    }
    public Clip find(UUID id) { return clips.stream().filter(c -> c.id.equals(id)).findFirst().orElse(null); }
    public BoatTimeline copy() {
        var copy = new BoatTimeline(); copy.enabled=enabled; copy.locked=locked;
        clips.forEach(c -> copy.clips.add(c.copy()));
        return copy;
    }
    public Clip duplicate(UUID id) {
        var clip = find(id);
        if (locked || clip == null || clips.size() >= 256) return null;
        var copy = clip.copy(); copy.id=UUID.randomUUID();
        int length=clip.end-clip.start, next=clip.end;
        for (var other : clips.stream().sorted(java.util.Comparator.comparingInt(c -> c.start)).toList()) {
            if ((long)next+length>other.start && next<other.end) next=other.end;
        }
        copy.start=Math.min(100_000_000-length,next); copy.end=copy.start+length;
        clips.add(copy); return copy;
    }
    public void normalize() {
        if (clips == null) clips = new ArrayList<>();
        clips = new ArrayList<>(clips.stream().filter(c -> c != null && c.settings != null && c.target != null).limit(256).toList());
        var ids = new java.util.HashSet<UUID>();
        for (var c : clips) {
            if (c.id == null || !ids.add(c.id)) { c.id = UUID.randomUUID(); ids.add(c.id); }
            c.start = Math.max(0, Math.min(100_000_000 - 1, c.start));
            c.end = Math.max(c.start + 1, Math.min(100_000_000, c.end));
        }
    }
    public Clip active(double tick) {
        if (!enabled || !Double.isFinite(tick)) return null;
        Clip result = null;
        for (var c : clips) if (c.enabled && c.target != null && c.settings != null
            && tick >= c.start && tick < c.end && (result == null || c.start >= result.start)) result = c;
        return result;
    }
}
