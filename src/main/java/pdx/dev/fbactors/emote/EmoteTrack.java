package pdx.dev.fbactors.emote;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;


public final class EmoteTrack {


    public record Clip(
        int startTick,
        int durationTicks,
        String emoteId,
        float blendInTicks,
        float blendOutTicks,
        boolean loop,
        float speed
    ) {

        public static final float NORMAL_SPEED = 1.0f;


        public Clip {
            durationTicks = Math.max(1, durationTicks);
            emoteId = emoteId == null ? "" : emoteId;
            blendInTicks = Math.max(0.0f, blendInTicks);
            blendOutTicks = Math.max(0.0f, blendOutTicks);


            speed = speed <= 0.0f ? NORMAL_SPEED : Math.min(speed, 16.0f);
        }


        public int endTick() {
            return this.startTick + this.durationTicks;
        }


        public boolean covers(int takeTick) {
            return takeTick >= this.startTick && takeTick < endTick();
        }


        public Clip atTick(int newStartTick) {
            return new Clip(newStartTick, this.durationTicks, this.emoteId,
                this.blendInTicks, this.blendOutTicks, this.loop, this.speed);
        }


        public Clip resized(int newDurationTicks) {
            return new Clip(this.startTick, newDurationTicks, this.emoteId,
                this.blendInTicks, this.blendOutTicks, this.loop, this.speed);
        }
    }

    private final EmoteLibrary library;
    private final UUID takeId;


    private volatile List<Clip> clips = List.of();

    public EmoteTrack(UUID takeId, EmoteLibrary library) {
        this.takeId = takeId;
        this.library = library;
    }


    public UUID takeId() {
        return this.takeId;
    }


    public List<Clip> clips() {
        return this.clips;
    }


    public boolean isEmpty() {
        return this.clips.isEmpty();
    }


    public int size() {
        return this.clips.size();
    }


    public void add(Clip clip) {
        List<Clip> next = new ArrayList<>(this.clips);
        next.add(clip);
        sort(next);
        this.clips = List.copyOf(next);
    }


    public void setAll(List<Clip> clips) {
        List<Clip> next = new ArrayList<>(clips == null ? List.of() : clips);
        sort(next);
        this.clips = List.copyOf(next);
    }


    public Clip remove(int index) {
        List<Clip> current = this.clips;
        if (index < 0 || index >= current.size()) {
            return null;
        }
        List<Clip> next = new ArrayList<>(current);
        Clip removed = next.remove(index);
        this.clips = List.copyOf(next);
        return removed;
    }


    public boolean move(int index, int newStartTick) {
        List<Clip> current = this.clips;
        if (index < 0 || index >= current.size()) {
            return false;
        }
        List<Clip> next = new ArrayList<>(current);
        next.set(index, current.get(index).atTick(newStartTick));
        sort(next);
        this.clips = List.copyOf(next);
        return true;
    }


    public boolean resize(int index, int newDurationTicks) {
        List<Clip> current = this.clips;
        if (index < 0 || index >= current.size()) {
            return false;
        }
        List<Clip> next = new ArrayList<>(current);
        next.set(index, current.get(index).resized(newDurationTicks));
        this.clips = List.copyOf(next);
        return true;
    }


    private static void sort(List<Clip> list) {
        list.sort(Comparator.comparingInt(Clip::startTick));
    }


    public Clip clipAt(int takeTick) {
        List<Clip> current = this.clips;
        if (current.isEmpty()) {
            return null;
        }
        int lo = 0;
        int hi = current.size() - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (current.get(mid).startTick() <= takeTick) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        Clip candidate = current.get(lo);
        return candidate.covers(takeTick) ? candidate : null;
    }


    public EmotePose poseAt(int takeTick) {
        List<Clip> current = this.clips;
        if (current.isEmpty()) {
            return EmotePose.identity();
        }

        int index = coveringIndex(current, takeTick);
        if (index < 0) {
            return EmotePose.identity();
        }
        Clip clip = current.get(index);
        Emote emote = this.library.byId(clip.emoteId());
        if (emote == null) {


            return EmotePose.identity();
        }

        EmotePose pose = emote.poseAt(localTick(clip, emote, takeTick));


        float inTicks = clip.blendInTicks();
        if (inTicks > 0.0f && index > 0) {
            float elapsed = takeTick - clip.startTick();
            if (elapsed < inTicks) {
                Clip previous = current.get(index - 1);
                EmotePose from = poseOf(previous, takeTick);
                float t = EmoteMath.smoothstep(elapsed / inTicks);
                pose = from.blend(pose, t);
            }
        }


        float outTicks = clip.blendOutTicks();
        if (outTicks > 0.0f) {
            float remaining = clip.endTick() - takeTick;
            if (remaining < outTicks) {
                float t = EmoteMath.smoothstep(remaining / outTicks);
                pose = pose.blend(EmotePose.identity(), t);
            }
        }
        return pose;
    }


    private static int coveringIndex(List<Clip> current, int takeTick) {
        int lo = 0;
        int hi = current.size() - 1;
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            if (current.get(mid).startTick() <= takeTick) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return current.get(lo).covers(takeTick) ? lo : -1;
    }


    private static int localTick(Clip clip, Emote emote, int takeTick) {
        int elapsed = takeTick - clip.startTick();
        float scaled = elapsed * clip.speed();


        int local = (int) Math.floor(scaled);
        if (clip.loop()) {
            return EmoteMath.wrapTick(local, emote.durationTicks());
        }
        return local;
    }


    private EmotePose poseOf(Clip clip, int takeTick) {
        Emote emote = this.library.byId(clip.emoteId());
        if (emote == null) {
            return EmotePose.identity();
        }
        return emote.poseAt(localTick(clip, emote, takeTick));
    }


    public List<String> referencedEmotes() {
        java.util.TreeSet<String> ids = new java.util.TreeSet<>();
        for (Clip clip : this.clips) {
            if (!clip.emoteId().isEmpty()) {
                ids.add(clip.emoteId());
            }
        }
        return List.copyOf(ids);
    }
}


