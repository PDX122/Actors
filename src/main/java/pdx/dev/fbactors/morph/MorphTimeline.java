package pdx.dev.fbactors.morph;

import java.util.*;

public final class MorphTimeline {
    public List<Clip> clips = new java.util.concurrent.CopyOnWriteArrayList<>();
    public static final class Clip {
        public UUID id=UUID.randomUUID(), actor;
        public int start,end;
        public String morph="cat",pose="auto";
        public boolean enabled=true;
        public Clip copy() {
            var c=new Clip(); c.id=id; c.actor=actor; c.start=start; c.end=end;
            c.morph=morph; c.pose=pose; c.enabled=enabled; return c;
        }
    }
    public void normalize() {
        if(clips==null) clips=new java.util.concurrent.CopyOnWriteArrayList<>();
        else if(!(clips instanceof java.util.concurrent.CopyOnWriteArrayList<?>)) clips=new java.util.concurrent.CopyOnWriteArrayList<>(clips);
        clips = new java.util.concurrent.CopyOnWriteArrayList<>(clips.stream().filter(c -> c!=null && c.actor!=null && c.morph!=null && !c.morph.isBlank()).limit(256).toList());
        var ids=new HashSet<UUID>();
        for(var c:clips) {
            if(c.id==null || !ids.add(c.id)) { c.id=UUID.randomUUID(); ids.add(c.id); }
            c.start=Math.max(0,Math.min(99_999_999,c.start));
            c.end=Math.max(c.start+1,Math.min(100_000_000,c.end));
            c.pose=MorphPoses.normalize(c.morph,c.pose);
        }
    }
    public MorphTimeline copy() {
        var t=new MorphTimeline(); clips.forEach(c -> t.clips.add(c.copy())); return t;
    }
    public Clip find(UUID id) { return clips.stream().filter(c -> c.id.equals(id)).findFirst().orElse(null); }
    public Clip active(UUID actor,double tick) {
        if(actor==null || !Double.isFinite(tick)) return null;
        Clip found=null;
        for(var c:clips) if(actor.equals(c.actor) && c.enabled && tick>=c.start && tick<c.end
            && (found==null || c.start>=found.start)) found=c;
        return found;
    }
    public MorphSelection evaluate(UUID actor,double tick,MorphSelection fallback) {
        var c=active(actor,tick);
        if(c==null) return fallback;
        if("player".equals(c.morph)) return MorphSelection.none(actor);
        var base=fallback==null?MorphSelection.none(actor):fallback;
        return base.withMorph(c.morph).withPose(c.pose);
    }
}
