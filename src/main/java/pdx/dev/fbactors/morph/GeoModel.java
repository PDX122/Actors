package pdx.dev.fbactors.morph;

import com.google.gson.*;
import java.util.*;

public record GeoModel(int textureWidth,int textureHeight,List<Bone> bones) {
    public float width() {
        float radius=0;
        for(var bone:bones)for(var cube:bone.cubes())radius=Math.max(radius,Math.max(Math.max(Math.abs(cube.origin.x),Math.abs(cube.origin.x+cube.size.x)),Math.max(Math.abs(cube.origin.z),Math.abs(cube.origin.z+cube.size.z))));
        return Math.max(.1f,radius/8f);
    }
    public float height() {
        float max=0;
        for(var bone:bones)for(var cube:bone.cubes())max=Math.max(max,cube.origin.y+cube.size.y);
        return Math.max(.1f,max/16f);
    }
    public record Vec(float x,float y,float z) {
        static Vec zero(){return new Vec(0,0,0);}
        static Vec one(){return new Vec(1,1,1);}
        Vec lerp(Vec b,float t){return new Vec(x+(b.x-x)*t,y+(b.y-y)*t,z+(b.z-z)*t);}
    }
    public record Cube(Vec origin,Vec size,Vec pivot,Vec rotation,int u,int v,float inflate,boolean mirror) {}
    public record Bone(String name,String parent,Vec pivot,Vec rotation,List<Cube> cubes) {}
    public static GeoModel parse(String json) {
        var root=JsonParser.parseString(json).getAsJsonObject();
        var geometry=root.getAsJsonArray("minecraft:geometry");
        if(geometry==null||geometry.size()!=1)throw new IllegalArgumentException("Export exactly one Bedrock/GeckoLib geometry per file.");
        var model=geometry.get(0).getAsJsonObject();var desc=model.getAsJsonObject("description");
        int tw=desc.get("texture_width").getAsInt(),th=desc.get("texture_height").getAsInt();
        if(tw<1||th<1||tw>4096||th>4096)throw new IllegalArgumentException("Model texture dimensions must be 1 to 4096.");
        var source=model.getAsJsonArray("bones");
        if(source==null||source.size()==0||source.size()>256)throw new IllegalArgumentException("Use between 1 and 256 bones.");
        var bones=new ArrayList<Bone>();var names=new HashSet<String>();int count=0;
        for(var value:source) {
            var bone=value.getAsJsonObject();String name=bone.get("name").getAsString();
            if(name.isBlank()||name.length()>128||!names.add(name))throw new IllegalArgumentException("Bone names must be nonempty and unique.");
            String parent=bone.has("parent")?bone.get("parent").getAsString():null;
            var pivot=vector(bone.get("pivot"),Vec.zero());var cubes=new ArrayList<Cube>();
            if(bone.has("cubes"))for(var item:bone.getAsJsonArray("cubes")) {
                if(++count>2048)throw new IllegalArgumentException("A custom model can contain at most 2048 cubes.");
                var cube=item.getAsJsonObject();var uv=cube.get("uv");
                if(uv==null||!uv.isJsonArray()||uv.getAsJsonArray().size()!=2)throw new IllegalArgumentException("Use Box UV in Blockbench. Per-face UV models are not supported yet.");
                var size=vector(cube.get("size"),null);
                if(size.x<0||size.y<0||size.z<0)throw new IllegalArgumentException("Cube sizes cannot be negative.");
                cubes.add(new Cube(vector(cube.get("origin"),null),size,vector(cube.get("pivot"),pivot),vector(cube.get("rotation"),Vec.zero()),
                    uv.getAsJsonArray().get(0).getAsInt(),uv.getAsJsonArray().get(1).getAsInt(),number(cube,"inflate",0),
                    cube.has("mirror")?cube.get("mirror").getAsBoolean():bone.has("mirror")&&bone.get("mirror").getAsBoolean()));
            }
            bones.add(new Bone(name,parent,pivot,vector(bone.get("rotation"),Vec.zero()),List.copyOf(cubes)));
        }
        var byName=new HashMap<String,Bone>();bones.forEach(b->byName.put(b.name,b));
        for(var bone:bones) {
            var seen=new HashSet<String>();Bone next=bone;
            while(next!=null) {
                if(!seen.add(next.name))throw new IllegalArgumentException("The bone hierarchy contains a cycle.");
                if(next.parent!=null&&!byName.containsKey(next.parent))throw new IllegalArgumentException("Unknown parent bone: "+next.parent);
                next=next.parent==null?null:byName.get(next.parent);
            }
        }
        return new GeoModel(tw,th,List.copyOf(bones));
    }
    static Vec vector(JsonElement value,Vec fallback) {
        if(value==null){if(fallback!=null)return fallback;throw new IllegalArgumentException("Missing model vector.");}
        if(value.isJsonPrimitive()&&value.getAsJsonPrimitive().isNumber()){float n=finite(value.getAsFloat());return new Vec(n,n,n);}
        if(!value.isJsonArray()||value.getAsJsonArray().size()!=3)throw new IllegalArgumentException("Use numeric three-component vectors. Molang expressions are not supported yet.");
        var a=value.getAsJsonArray();
        try{return new Vec(finite(a.get(0).getAsFloat()),finite(a.get(1).getAsFloat()),finite(a.get(2).getAsFloat()));}
        catch(RuntimeException error){throw new IllegalArgumentException("Use numeric animation vectors; Molang expressions are not supported yet.");}
    }
    static float number(JsonObject obj,String key,float fallback){return obj.has(key)?finite(obj.get(key).getAsFloat()):fallback;}
    static float finite(float n){if(!Float.isFinite(n)||Math.abs(n)>10000)throw new IllegalArgumentException("Model values must be finite and within 10000 units.");return n;}
}
