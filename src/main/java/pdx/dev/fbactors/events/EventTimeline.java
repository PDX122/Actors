package pdx.dev.fbactors.events;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

public final class EventTimeline {
    public static final int MAX_CLIPS=64;
    public boolean enabled=true;
    public List<EventClip> clips=new ArrayList<>();
    public void normalize() {
        if(clips==null) clips=new ArrayList<>();
        clips=new ArrayList<>(clips.stream().filter(c->c!=null && c.valid()).limit(MAX_CLIPS).toList());
        var ids=new HashSet<UUID>();
        for(var c:clips) { c.normalize();if(!c.kind.available())c.enabled=false;if(!ids.add(c.id)) {c.id=UUID.randomUUID();ids.add(c.id);} }
    }
    public EventClip find(UUID id) { return clips.stream().filter(c->c.id.equals(id)).findFirst().orElse(null); }
    public EventTimeline copy() { var t=new EventTimeline();t.enabled=enabled;clips.forEach(c->t.clips.add(c.copy()));return t; }
    public int end(int footageEnd) {
        if (!enabled) return footageEnd;
        for (var clip : clips) {
            if (clip.enabled && clip.kind.available() && clip.valid()) {
                footageEnd = Math.max(footageEnd, clip.end);
            }
        }
        return footageEnd;
    }
    public boolean upgradeTiming() {
        boolean changed=false;
        for(var c:clips)if(c.timingVersion==0) {
            if(c.kind==EventKind.ICBM && !c.locked && (c.end-c.start==240 || c.end-c.start==360)) {
                c.end=Math.min(EventClip.MAX_TICK,c.start+MissileFlight.DEFAULT_TICKS);changed=true;
            }
            c.timingVersion=1;changed=true;
        }
        return changed;
    }
}
