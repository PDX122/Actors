package pdx.dev.fbactors.morph;

import java.util.List;
import java.util.UUID;

public final class MorphLibraryPresets {
    public static final String PAYLOAD="ACTORS_MORPH_BODY";
    public record Preset(String body,String pose) {
        public String payload() { return body+"/"+pose; }
        public String label() { return MorphPoses.label(pose); }
    }
    private MorphLibraryPresets() {}
    public static List<Preset> entries(String body,MorphRegistry registry) {
        return registry.has(body)?MorphPoses.choices(body).stream().map(p -> new Preset(body,p)).toList():List.of();
    }
    public static Preset decode(String value,MorphRegistry registry) {
        if(value==null || value.length()>512) return null;
        int split=value.lastIndexOf('/');
        if(split<=0) return null;
        String body=value.substring(0,split),pose=value.substring(split+1);
        if(!registry.has(body) || !MorphPoses.choices(body).contains(pose)) return null;
        return new Preset(body,pose);
    }
    public static MorphTimeline.Clip create(String payload,UUID actor,int tick,int actorStart,int actorEnd,boolean locked,MorphRegistry registry) {
        var preset=decode(payload,registry);
        actorStart=Math.max(0,actorStart); actorEnd=Math.min(100_000_000,actorEnd);
        if(preset==null || actor==null || locked || actorEnd<=actorStart) return null;
        int start=Math.max(actorStart,Math.min(actorEnd-1,tick));
        var clip=new MorphTimeline.Clip();
        clip.actor=actor; clip.morph=preset.body(); clip.pose=preset.pose(); clip.start=start;
        clip.end=(int)Math.min(actorEnd,(long)start+100);
        return clip;
    }
}
