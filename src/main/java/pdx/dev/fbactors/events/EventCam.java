package pdx.dev.fbactors.events;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.keyframe.handler.MinecraftKeyframeHandler;
import com.moulberry.flashback.state.EditorStateManager;
import net.minecraft.client.Minecraft;
import org.joml.Vector3d;
import pdx.dev.fbactors.actor.ActorMode;
import pdx.dev.fbactors.camera.BoatCam;
import pdx.dev.fbactors.camera.BoatCameraModel.Shot;
import pdx.dev.fbactors.camera.BoatSubjects;

/** Camera-only ownership. Event geometry and replay actors remain untouched. */
public final class EventCam {
    private static Shot rendered,saved;
    private static Object session;
    private static boolean applying,savedFov,savedRoll;
    private static float fov,roll;
    private static double frameTick=Double.NaN,lastTick=Double.NaN;
    private static int lastServerTick;
    private EventCam() {}
    public static boolean applying() {return applying;}
    private static EventClip active(double tick) {
        var mc=Minecraft.getInstance();
        if(Flashback.getReplayServer()==null || mc.level==null || mc.player==null || ActorMode.isActive() || EventPlacement.active())return null;
        var timeline=Events.timeline();if(!timeline.enabled)return null;
        String dimension=mc.level.dimension().toString();
        return timeline.clips.stream().filter(c->c.eventCam && c.active(tick) && c.valid() && (c.dimension.isEmpty() || c.dimension.equals(dimension)))
            .max(java.util.Comparator.comparingInt((EventClip c)->c.start).thenComparing(c->c.id)).orElse(null);
    }
    public static boolean ownsCamera() {return !applying && active(renderedShot()!=null?frameTick:BoatSubjects.clock())!=null;}
    public static double clock() {return renderedShot()!=null?frameTick:BoatSubjects.clock();}
    public static Shot renderedShot() {return session==Flashback.getReplayServer() && !ActorMode.isActive()?rendered:null;}
    public static boolean renderFrame() {
        var replay=Flashback.getReplayServer();
        if(session!=null && session!=replay)stop();
        int serverTick=replay==null?0:replay.getReplayTick();
        double tick=EventFrameClock.sample(lastTick,lastServerTick,BoatSubjects.clock(),serverTick,replay==null || replay.replayPaused,Flashback.isExporting());
        rendered=null;var c=active(tick);var mc=Minecraft.getInstance();var state=EditorStateManager.getCurrent();
        if(c==null || state==null) {stop();return false;}
        if(saved==null) {
            session=Flashback.getReplayServer();
            saved=new Shot(mc.player.getX(),mc.player.getY(),mc.player.getZ(),mc.player.getYRot(),mc.player.getXRot(),0,70);
            savedFov=state.replayVisuals.overrideFov;fov=state.replayVisuals.overrideFovAmount;
            savedRoll=state.replayVisuals.overrideRoll;roll=state.replayVisuals.overrideRollAmount;
        }
        EventClip.Point witness=null;double nearest=Double.MAX_VALUE;
        for(var actor:pdx.dev.fbactors.FlashbackActors.ACTORS.actors()) {
            var p=actor.livePosition();if(!actor.enabled() || p==null)continue;
            var point=new EventClip.Point(p.x,p.y,p.z);double distance=point.distance(c.impact);
            if(distance>32 && distance<700*c.blast && distance<nearest) {nearest=distance;witness=point;}
        }
        for(var player:mc.level.players().stream().sorted(java.util.Comparator.comparing(p->p.getUUID())).toList()) {
            if(player==mc.player)continue;
            var point=new EventClip.Point(player.getX(),player.getY(),player.getZ());double distance=point.distance(c.impact);
            if(distance>32 && distance<700*c.blast && distance<nearest) {nearest=distance;witness=point;}
        }
        frameTick=lastTick=tick;lastServerTick=serverTick;
        rendered=EventCameraModel.shot(c,tick,BoatCam.aspect(),witness);
        // Keep witness shots above loaded ground without forcing chunk loads.
        var pos=new net.minecraft.core.BlockPos((int)Math.floor(rendered.x()),(int)Math.floor(rendered.y()),(int)Math.floor(rendered.z()));
        if(mc.level.hasChunkAt(pos)) {
            double floor=mc.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,pos.getX(),pos.getZ())+2;
            if(rendered.y()<floor)rendered=new Shot(rendered.x(),floor,rendered.z(),rendered.yaw(),rendered.pitch(),rendered.roll(),rendered.fov());
        }
        applying=true;
        try {
            var handler=new MinecraftKeyframeHandler(mc);
            handler.applyCameraPosition(new Vector3d(rendered.x(),rendered.y()-mc.player.getEyeHeight(),rendered.z()),rendered.yaw(),rendered.pitch(),rendered.roll());
            handler.applyFov(rendered.fov());
        } finally {applying=false;}
        return true;
    }
    public static void stop() {
        var mc=Minecraft.getInstance();var state=EditorStateManager.getCurrent();
        if(saved!=null && session==Flashback.getReplayServer()) {
            if(mc.player!=null && !ActorMode.isActive()) {
                pdx.dev.fbactors.compat.Compat.snapTo(mc.player,saved.x(),saved.y(),saved.z(),(float)saved.yaw(),(float)saved.pitch());
                pdx.dev.fbactors.compat.Compat.cancelInterpolation(mc.player);
            }
            if(state!=null) {state.replayVisuals.overrideFov=savedFov;state.replayVisuals.overrideFovAmount=fov;state.replayVisuals.overrideRoll=savedRoll;state.replayVisuals.overrideRollAmount=roll;}
        }
        saved=rendered=null;session=null;frameTick=lastTick=Double.NaN;lastServerTick=0;
    }
}
