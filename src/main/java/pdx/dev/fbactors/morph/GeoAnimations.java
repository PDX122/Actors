package pdx.dev.fbactors.morph;

import com.google.gson.*;
import java.util.*;
import static pdx.dev.fbactors.morph.GeoModel.*;

public final class GeoAnimations {
    public record Key(float time,Vec before,Vec after,boolean step) {}
    public record Channel(List<Key> keys) {
        public Vec sample(float time) {
            if(time<=keys.getFirst().time())return keys.getFirst().before();
            for(int i=1;i<keys.size();i++) {
                var a=keys.get(i-1);var b=keys.get(i);
                if(time<=b.time())return a.step()?a.after():a.after().lerp(b.before(),(time-a.time())/(b.time()-a.time()));
            }
            return keys.getLast().after();
        }
    }
    public record Motion(Channel position,Channel rotation,Channel scale) {}
    public record Clip(String name,float length,boolean loop,Map<String,Motion> bones) {}
    private GeoAnimations() {}
    public static List<Clip> parse(String json,GeoModel model) {
        if(json==null||json.isBlank())return List.of();
        var root=JsonParser.parseString(json).getAsJsonObject();var data=root.getAsJsonObject("animations");
        if(data==null||data.size()>128)throw new IllegalArgumentException("Use an animation JSON with at most 128 clips.");
        var names=model.bones().stream().map(Bone::name).collect(java.util.stream.Collectors.toSet());
        var clips=new ArrayList<Clip>();
        for(var entry:data.entrySet()) {
            var source=entry.getValue().getAsJsonObject();
            for(String unsupported:List.of("sound_effects","particle_effects","timeline"))
                if(source.has(unsupported))throw new IllegalArgumentException("Animation event tracks are not supported yet: "+unsupported);
            float length=number(source,"animation_length",0);var bones=new LinkedHashMap<String,Motion>();
            if(source.has("bones"))for(var item:source.getAsJsonObject("bones").entrySet()) {
                if(!names.contains(item.getKey()))throw new IllegalArgumentException("Animation refers to unknown bone: "+item.getKey());
                var value=item.getValue().getAsJsonObject();Channel p=channel(value.get("position")),r=channel(value.get("rotation")),s=channel(value.get("scale"));
                bones.put(item.getKey(),new Motion(p,r,s));
                for(var channel:new Channel[]{p,r,s})if(channel!=null)length=Math.max(length,channel.keys().getLast().time());
            }
            boolean loop=source.has("loop")&&source.get("loop").isJsonPrimitive()&&"true".equals(source.get("loop").getAsString());
            clips.add(new Clip(entry.getKey(),Math.max(.05f,length),loop,Map.copyOf(bones)));
        }
        return List.copyOf(clips);
    }
    private static Channel channel(JsonElement data) {
        if(data==null)return null;
        if(data.isJsonArray()||data.isJsonPrimitive()) {var v=vector(data,null);return new Channel(List.of(new Key(0,v,v,false)));}
        var object=data.getAsJsonObject();var keys=new ArrayList<Key>();
        if(object.size()>4096)throw new IllegalArgumentException("A channel can contain at most 4096 keyframes.");
        for(var entry:object.entrySet()) {
            float time=finite(Float.parseFloat(entry.getKey()));if(time<0)throw new IllegalArgumentException("Animation key times must be nonnegative.");
            var value=entry.getValue();Vec before,after;boolean step=false;
            if(value.isJsonObject()) {
                var frame=value.getAsJsonObject();String mode=frame.has("lerp_mode")?frame.get("lerp_mode").getAsString():"linear";
                if(!mode.equals("linear")&&!mode.equals("step"))throw new IllegalArgumentException("Use linear or step animation interpolation.");
                step=mode.equals("step");before=vector(frame.has("pre")?frame.get("pre"):frame.get("post"),null);after=vector(frame.has("post")?frame.get("post"):frame.get("pre"),null);
            }else {before=after=vector(value,null);}
            keys.add(new Key(time,before,after,step));
        }
        keys.sort(Comparator.comparingDouble(Key::time));
        if(keys.isEmpty())throw new IllegalArgumentException("Animation channels cannot be empty.");
        for(int i=1;i<keys.size();i++)if(keys.get(i).time()==keys.get(i-1).time())throw new IllegalArgumentException("Duplicate keyframe time.");
        return new Channel(List.copyOf(keys));
    }
}
