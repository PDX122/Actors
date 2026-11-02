package pdx.dev.fbactors.morph;

import java.nio.file.*;
import java.util.*;
import java.io.IOException;

public final class CustomMobs {
    public record Asset(String id,String name,GeoModel model,List<GeoAnimations.Clip> animations,Path texture) {}
    private static final Map<String,Asset> ASSETS=new LinkedHashMap<>();
    private static final Map<UUID,String> ENTITIES=new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<UUID,String> PREVIEW_ANIMATION=new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<UUID,Float> PREVIEW_TIME=new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<UUID,UUID> ACTORS=new java.util.concurrent.ConcurrentHashMap<>();
    public static void bind(net.minecraft.world.entity.Entity entity,String id,UUID actor){bind(entity,id);ACTORS.put(entity.getUUID(),actor);}
    private static final Map<UUID,String> SELECTED_ANIMATION=new java.util.concurrent.ConcurrentHashMap<>();
    public static String status="";
    private CustomMobs() {}
    public static Path directory(){return Morph.directory().resolve("models");}
    public static Asset asset(String id){return ASSETS.get(id);}
    public static String entity(net.minecraft.world.entity.Entity entity){return ENTITIES.get(entity.getUUID());}
    public static void release(net.minecraft.world.entity.Entity entity){UUID id=entity.getUUID();ENTITIES.remove(id);ACTORS.remove(id);PREVIEW_ANIMATION.remove(id);PREVIEW_TIME.remove(id);}
    public static void clearInstances(){ENTITIES.clear();ACTORS.clear();PREVIEW_ANIMATION.clear();PREVIEW_TIME.clear();}
    public static void bind(net.minecraft.world.entity.Entity entity,String id){if(asset(id)!=null)ENTITIES.put(entity.getUUID(),id);}
    public static List<String> animations(String id){var asset=asset(id);return asset==null?List.of():asset.animations().stream().map(GeoAnimations.Clip::name).toList();}
    public static void preview(net.minecraft.world.entity.Entity entity,int stage,float time) {
        String id=entity(entity);var names=animations(id);
        if(!names.isEmpty()){PREVIEW_ANIMATION.put(entity.getUUID(),names.get(Math.max(0,Math.min(stage,names.size()-1))));PREVIEW_TIME.put(entity.getUUID(),time/20f);}
    }
    public static String animation(UUID take){return SELECTED_ANIMATION.getOrDefault(take,"");}
    public static void animation(UUID take,String name) throws IOException {
        String before=SELECTED_ANIMATION.get(take);
        if(name==null||name.isBlank())SELECTED_ANIMATION.remove(take);else SELECTED_ANIMATION.put(take,name);
        var gson=new com.google.gson.GsonBuilder().setPrettyPrinting().create();
        if(!pdx.dev.fbactors.util.AtomicSave.writeJson(directory(),"animation-selections.json",writer->gson.toJson(SELECTED_ANIMATION,writer))){
            if(before==null)SELECTED_ANIMATION.remove(take);else SELECTED_ANIMATION.put(take,before);
            throw new IOException("Could not save animation selection.");
        }
    }
    public static GeoAnimations.Clip clip(String id,UUID entity,float speed) {
        var asset=asset(id);if(asset==null)return null;
        UUID actor=ACTORS.get(entity);
        String name=PREVIEW_ANIMATION.get(entity);
        if(name==null) {
            name=actor==null?null:SELECTED_ANIMATION.get(actor);
        }
        if(name!=null)for(var clip:asset.animations())if(clip.name().equals(name))return clip;
        if(actor!=null) {
            var performance=pdx.dev.fbactors.FlashbackActors.ACTORS.byUuid(actor);
            if(performance!=null) {
                int tick=performance.takeTickFor(pdx.dev.fbactors.FlashbackActors.replayTick());var a=performance.take.frameAt(tick);var b=performance.take.frameAt(tick-1);
                if(a!=null&&b!=null)speed=(float)Math.hypot(a.x()-b.x(),a.z()-b.z());
            }
        }
        String action=speed>.03f?"walk":"idle";
        return asset.animations().stream().filter(clip->clip.name().toLowerCase(java.util.Locale.ROOT).contains(action)).findFirst()
            .orElse(asset.animations().isEmpty()?null:asset.animations().getFirst());
    }
    public static float time(UUID entity,float fallback){
        Float preview=PREVIEW_TIME.get(entity);if(preview!=null)return preview;
        UUID actor=ACTORS.get(entity);
        if(actor!=null){var performance=pdx.dev.fbactors.FlashbackActors.ACTORS.byUuid(actor);
            if(performance!=null)return Math.max(0,performance.takeTickFor(pdx.dev.fbactors.FlashbackActors.replayTick())-performance.take.startTick)/20f;
        }
        return fallback;
    }
    public static void install() {
        ASSETS.clear();
        try {
            Files.createDirectories(directory());
            try(var paths=Files.list(directory())) {
                for(var folder:paths.filter(Files::isDirectory).limit(128).toList())try{load(folder);}catch(Exception bad){pdx.dev.fbactors.FlashbackActors.LOGGER.warn("Could not load custom model {}",folder,bad);}
            }
            Path selections=directory().resolve("animation-selections.json");
            if(Files.isRegularFile(selections)&&Files.size(selections)<65536) {
                var data=com.google.gson.JsonParser.parseString(Files.readString(selections)).getAsJsonObject();
                for(var entry:data.entrySet())try{SELECTED_ANIMATION.put(UUID.fromString(entry.getKey()),entry.getValue().getAsString());}catch(RuntimeException ignored){}
            }
        }catch(Exception error){status="Could not load custom models.";pdx.dev.fbactors.FlashbackActors.LOGGER.warn(status,error);}
    }
    private static String read(Path path) throws IOException {
        if(!Files.isRegularFile(path)||Files.size(path)>2_000_000)throw new IOException("Choose a JSON file smaller than 2 MB.");
        return Files.readString(path);
    }
    public static Asset importFiles(Path geometry,Path animations,Path texture,String name) throws IOException {
        if(name==null||name.isBlank()||name.length()>64||name.contains("##")||name.chars().anyMatch(Character::isISOControl))throw new IllegalArgumentException("Use a model name of 1 to 64 characters.");
        String geo=read(geometry),anim=animations==null?"":read(animations);var model=GeoModel.parse(geo);var clips=GeoAnimations.parse(anim,model);
        if(!Files.isRegularFile(texture)||Files.size(texture)>16_000_000)throw new IOException("Choose a PNG smaller than 16 MB.");
        byte[] png=Files.readAllBytes(texture);validateTexture(png,model);
        String id="custom_"+UUID.randomUUID().toString().replace("-","");Path folder=directory().resolve(id);Files.createDirectories(folder);
        try {
            Files.writeString(folder.resolve("model.geo.json"),geo);Files.writeString(folder.resolve("animations.json"),anim);
            Files.write(folder.resolve("texture.png"),png);Files.writeString(folder.resolve("name.txt"),name.strip());
            return load(folder);
        }catch(Exception error) {
            for(String file:List.of("model.geo.json","animations.json","texture.png","name.txt"))Files.deleteIfExists(folder.resolve(file));
            Files.deleteIfExists(folder);throw error;
        }
    }
    static void validateTexture(byte[] png,GeoModel model) throws IOException {
        if(png.length<24||png[0]!=(byte)137||png[1]!=80||png[2]!=78||png[3]!=71)throw new IOException("The texture must be a PNG.");
        var bytes=java.nio.ByteBuffer.wrap(png).order(java.nio.ByteOrder.BIG_ENDIAN);
        int w=bytes.getInt(16),h=bytes.getInt(20);
        if(w!=model.textureWidth()||h!=model.textureHeight())throw new IOException("PNG dimensions must match the geometry texture width and height.");
        if(javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(png))==null)throw new IOException("The PNG could not be decoded.");
    }
    private static Asset load(Path folder) throws IOException {
        String id=folder.getFileName().toString();if(!id.matches("custom_[a-f0-9]{32}"))throw new IOException("Invalid custom model folder.");
        String name=read(folder.resolve("name.txt")).strip();var model=GeoModel.parse(read(folder.resolve("model.geo.json")));
        var asset=new Asset(id,name,model,GeoAnimations.parse(read(folder.resolve("animations.json")),model),folder.resolve("texture.png"));
        if(Files.size(asset.texture())>16_000_000)throw new IOException("Custom texture is too large.");
        validateTexture(Files.readAllBytes(asset.texture()),model);ASSETS.put(id,asset);
        Morph.registry().register(new MorphDefinition(id,name,"custom",1,model.width(),model.height(),model.height()*.85,0,MorphStance.HUMANOID,false,false,false));
        return asset;
    }
}
