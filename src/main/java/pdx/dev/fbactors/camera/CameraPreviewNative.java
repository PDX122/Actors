package pdx.dev.fbactors.camera;

import net.minecraft.client.Minecraft;
import pdx.dev.fbactors.compat.Compat;
import pdx.dev.fbactors.morph.*;
import java.util.*;

/** Isolated native entity renderers keep resource-pack animation state out of the replay. */
public final class CameraPreviewNative {
    public record Subject(CameraPreviewCapture.Mesh mesh,long fallbackTexture,CameraModePreviewScene.Point origin,double yaw) {}
    public record Frame(List<Subject> subjects,long revision,double seconds) {}
    private static final MorphPreviewCache<Source> CACHE=new MorphPreviewCache<>(18,2);
    private static Object level,resources;
    private static int frame=-1;
    private static long revisions;
    private static final Map<String,Set<Integer>> EVIDENCE=new LinkedHashMap<>();
    private static final Map<String,Integer> COUNTS=new HashMap<>();
    private static final Map<String,Set<String>> MATERIALS=new HashMap<>();
    private static double packGliding,packWalk;
    private static boolean packStateFound;
    private static final Set<Float> ROWING=new HashSet<>();
    public static void rowingEvidence(float pitch) { if("1".equals(System.getenv("FBACTORS_CAMERA_PREVIEW_QA"))) ROWING.add(pitch); }
    private static final class Source implements AutoCloseable {
        final MorphPreviewModel player,horse;
        final CameraPreviewBoat boat;
        final java.util.UUID profile;
        CameraPreviewCapture.Mesh playerMesh,horseMesh,boatMesh;
        final CameraPreviewPose playerPose=new CameraPreviewPose(),horsePose=new CameraPreviewPose();
        long revision,renderedAt;
        double renderedTime=Double.NaN;
        Source(int mode) {
            var mc=Minecraft.getInstance();var identity=mc.player.getGameProfile();profile=Compat.profileId(identity);
            CameraPreviewAssets.skin(profile);
            player=MorphPreviewBridge.openSkin(identity);
            if(player==null) throw new IllegalStateException("Could not create native player preview");
            player.cameraAction(CameraModePreviewScene.ice(mode)||CameraModePreviewScene.horse(mode)?"ride":CameraModesCatalog.flight(mode)?"glide":"walk");
            if(CameraModesCatalog.flight(mode)) player.cameraEntity().setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST,new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.ELYTRA));
            player.capture(mesh->{
                if("1".equals(System.getenv("FBACTORS_CAMERA_PREVIEW_QA")) && CameraModesCatalog.flight(mode)) {
                    var entity=player.cameraEntity();
                    if(!entity.isFallFlying() || Math.abs(entity.walkAnimation.speed(1))>1e-5)
                        throw new IllegalStateException("Elytra must glide without a walk cycle");
                    packState(entity);
                }
                playerPose.accept(mesh,renderedTime);evidence(mode,"player",mesh);
                if(!mesh.equals(playerMesh)) { playerMesh=mesh;revision=++revisions; }
            });
            boat=CameraModePreviewScene.ice(mode)?new CameraPreviewBoat():null;
            if(boat!=null) player.cameraMount(boat.entity);
            horse=CameraModePreviewScene.horse(mode)?MorphPreviewBridge.open("horse"):null;
            if(horse!=null) {
                var mount=horse.cameraEntity();
                var saddle=new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.SADDLE);
                var saddleSlot=Arrays.stream(net.minecraft.world.entity.EquipmentSlot.values()).filter(slot->slot.name().equals("SADDLE")).findFirst();
                if(saddleSlot.isPresent()) mount.setItemSlot(saddleSlot.get(),saddle);
                else mount.getSlot(400).set(saddle);
                horse.cameraAction("run");
                player.cameraMount(mount);
                horse.capture(mesh->{ horsePose.accept(mesh,renderedTime);evidence(mode,"horse",mesh);if(!mesh.equals(horseMesh)) { horseMesh=mesh;revision=++revisions; } });
            }
        }
        void sample(double seconds,boolean moving) {
            long now=System.nanoTime();
            if(playerMesh!=null && renderedTime==seconds) return;
            if(now-renderedAt<16_666_667L) return;
            renderedAt=now;renderedTime=seconds;
            var sample=new MorphPreviewPlayback.Frame(0,(float)(seconds*20),(float)(seconds*20),0);
            if(boat!=null) { var mesh=boat.sample(seconds);evidence(14,"boat",mesh);if(!mesh.equals(boatMesh)) { boatMesh=mesh;revision=++revisions; } }
            if(horse!=null) MorphPreviews.renderIsolated(()->horse.render(sample));
            MorphPreviews.renderIsolated(()->player.render(sample));
        }
        @Override public void close() {
            player.close();if(horse!=null)horse.close();
        }
    }
    private CameraPreviewNative() {}
    private static void packState(net.minecraft.world.entity.LivingEntity entity) {
        if(!net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("entity_model_features")) return;
        try {
            var variables=(Map<?,?>)entity.getClass().getMethod("emf$getVariableMap").invoke(entity);
            for(var entry:variables.entrySet()) if(entry.getValue() instanceof Number value) {
                String key=String.valueOf(entry.getKey());
                if(key.equals("var.gliding") || key.equals("gliding")) { packStateFound=true;packGliding=Math.max(packGliding,value.doubleValue()); }
                if(key.equals("var.walk") || key.equals("walk")) packWalk=Math.max(packWalk,value.doubleValue());
            }
        } catch(ReflectiveOperationException error) { throw new IllegalStateException("Could not verify the pack's gliding variables",error); }
    }
    private static void evidence(int mode,String kind,CameraPreviewCapture.Mesh mesh) {
        if(!"1".equals(System.getenv("FBACTORS_CAMERA_PREVIEW_QA"))) return;
        var hashes=EVIDENCE.computeIfAbsent(mode+":"+kind,key->new LinkedHashSet<>());
        COUNTS.merge(mode+":"+kind,mesh.vertices().size(),Math::max);
        var materials=MATERIALS.computeIfAbsent(mode+":"+kind,key->new LinkedHashSet<>());
        for(var v:mesh.vertices()) if(v.texture()!=null) materials.add(v.texture());
        if(hashes.size()>=2 || !hashes.add(mesh.vertices().hashCode())) return;
        if(!mesh.nativeTransform()) throw new IllegalStateException("Native entity transform missing: "+mode+":"+kind);
        pdx.dev.fbactors.FlashbackActors.LOGGER.info("Camera animation evidence: mode={} kind={} vertices={} renderer={} frames={} nativeTransform={} materials={}",mode,kind,mesh.vertices().size(),mesh.renderer(),hashes.size(),mesh.nativeTransform(),materials);
    }
    public static void verifyEvidence() {
        for(String key:List.of("14:player","17:player","17:horse","30:player"))
            if(EVIDENCE.getOrDefault(key,Set.of()).size()<2 || COUNTS.getOrDefault(key,0)<144) throw new IllegalStateException("Missing native animation motion: "+key);
        if(COUNTS.getOrDefault("14:boat",0)<=120 || EVIDENCE.getOrDefault("14:boat",Set.of()).size()<2)
            throw new IllegalStateException("Boat paddles must be visible and animate");
        if(ROWING.size()<2) throw new IllegalStateException("Boat rider arms must paddle");
        pdx.dev.fbactors.FlashbackActors.LOGGER.info("Rider paddling evidence: {} distinct arm poses",ROWING.size());
        for(String key:List.of("17:horse","30:player")) if(MATERIALS.getOrDefault(key,Set.of()).size()<2)
            throw new IllegalStateException("Body and equipment materials were not captured separately: "+key);
        if(net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("entity_model_features")) {
            pdx.dev.fbactors.FlashbackActors.LOGGER.info("Pack gliding evidence: found={} gliding={} walk={}",packStateFound,packGliding,packWalk);
            if(!packStateFound || packGliding<.8 || packWalk>1e-5) throw new IllegalStateException("Pack gliding must activate without walking");
        }
    }
    public static void close() { CACHE.close();frame=-1; }
    public static void beginFrame() {
        var mc=Minecraft.getInstance();
        if(mc.level!=level || mc.getResourceManager()!=resources) {
            close();level=mc.level;resources=mc.getResourceManager();
        }
        int current=imgui.moulberry90.ImGui.getFrameCount();
        if(current!=frame) { frame=current;CACHE.beginFrame(); }
    }
    public static Frame get(int mode,double seconds,boolean moving) {
        return get(mode,seconds,moving,true);
    }
    public static void prepare(int mode) {
        beginFrame();var mc=Minecraft.getInstance();
        if(!CameraModesCatalog.available(mode) || mc.player==null || mc.level==null || CameraModesCatalog.pov(mode)
            || pdx.dev.fbactors.debug.ErrorRecovery.FAILURES.stopped("Camera preview")) return;
        try { CACHE.get(mode+":inspector",key->new Source(mode)); }
        catch(RuntimeException | LinkageError | AssertionError error) { pdx.dev.fbactors.debug.ErrorRecovery.report("Camera preview",error); }
    }
    public static Frame get(int mode,double seconds,boolean moving,boolean inspector) {
        beginFrame();var mc=Minecraft.getInstance();
        if(!CameraModesCatalog.available(mode) || mc.player==null || mc.level==null || CameraModesCatalog.pov(mode) || CameraModesCatalog.fpv(mode)) return new Frame(List.of(),0,seconds);
        var source=CACHE.get(mode+":"+(inspector?"inspector":moving?"hover":"card"),key->new Source(mode));
        if(source==null) return new Frame(List.of(),0,seconds);
        source.sample(seconds,moving);
        if(source.playerMesh==null) return new Frame(List.of(),source.revision,seconds);
        double shown=moving?Math.max(0,seconds-1.0/30):seconds;
        var rider=source.playerPose.at(shown);var mount=source.horsePose.at(shown);
        var p=CameraModePreviewScene.subject(shown,mode);
        double yaw=CameraModePreviewScene.bodyHeading(shown,mode),angle=Math.toRadians(yaw);
        var vehicle=source.boat!=null?source.boat.entity:source.horse==null?null:source.horse.cameraEntity();
        var offset=vehicle!=null?CameraPreviewRiding.offset(source.player.cameraEntity(),vehicle):new CameraModePreviewScene.Point(0,0,0);
        var origin=new CameraModePreviewScene.Point(p.x()+offset.x()*Math.cos(angle)-offset.z()*Math.sin(angle),p.y()+offset.y(),p.z()+offset.x()*Math.sin(angle)+offset.z()*Math.cos(angle));
        long skin=CameraPreviewAssets.skin(source.profile);
        var subjects=new ArrayList<Subject>();
        subjects.add(new Subject(rider,skin,origin,yaw));
        if(mode==7) subjects.add(new Subject(rider,skin,CameraModePreviewScene.partner(shown),CameraModePreviewScene.heading(shown)));
        if(mount!=null) subjects.add(new Subject(mount,0,p,yaw));
        if(source.boatMesh!=null) subjects.add(new Subject(source.boatMesh,0,p,yaw));
        return new Frame(List.copyOf(subjects),source.revision,shown);
    }
    public static String animationLabel() {
        var mc=Minecraft.getInstance();
        boolean emf=net.fabricmc.loader.api.FabricLoader.getInstance().isModLoaded("entity_model_features");
        boolean player=mc.getResourceManager().getResource(Compat.boatTextureId("minecraft:emf/cem/player.jem")).isPresent();
        int mode=pdx.dev.fbactors.settings.FbSettings.settings().boatCamMode;
        String action=CameraModesCatalog.flight(mode)?"Elytra gliding":CameraModePreviewScene.ice(mode)?"boat riding":CameraModePreviewScene.horse(mode)?"mounted riding":"walking";
        return (emf && player?"Fresh Animations / ":"Native Minecraft / ")+action;
    }
}
