package pdx.dev.fbactors.events;

import com.moulberry.flashback.Flashback;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import org.joml.Vector3f;
import pdx.dev.fbactors.compat.Compat;
import pdx.dev.fbactors.filters.FilterGpuPipeline;
import pdx.dev.fbactors.filters.FilterRenderBridge;

public final class EventRenderer {
    private static FilterGpuPipeline pipeline;
    private static boolean failed;
    private EventRenderer() {}
    public static void render() {
        var timeline=Events.timeline();
        if(Flashback.getReplayServer()==null || Minecraft.getInstance().level==null || !timeline.enabled || failed)return;
        double tick=EventCam.clock();
        String dimension=Minecraft.getInstance().level.dimension().toString();
        var active=timeline.clips.stream().filter(c->c.active(tick) && (c.dimension.isEmpty() || c.dimension.equals(dimension))).toList();if(active.isEmpty())return;
        try {
            var mc=Minecraft.getInstance();var target=FilterRenderBridge.target(mc);
            var camera=Compat.eventCamera();
            pdx.dev.fbactors.selftest.EventCameraChecks.frame(camera,tick);
            for(var clip:active) {
                var uniforms=uniforms(clip,tick,camera);
                float clouds=Compat.eventCloudHeight();
                uniforms.put("CloudBase",Float.isFinite(clouds)?(float)(clouds-clip.launch.y()):0f);
                uniforms.put("CloudThickness",Float.isFinite(clouds)?5f:0f);
                if(clip.kind==EventKind.CARPET)for(int bomb=0;bomb<clip.bombs;bomb++) {
                    var point=BombFlight.target(clip,bomb);int x=(int)Math.floor(point.x()),z=(int)Math.floor(point.z());
                    if(mc.level.hasChunkAt(new net.minecraft.core.BlockPos(x,(int)point.y(),z))) {
                        double y=mc.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,x,z)+.01;
                        uniforms.put("Ground"+bomb,(float)(y-clip.impact.y()));
                    }
                }
                if(FilterGpuPipeline.isGlAvailable()) {
                    int color=FilterRenderBridge.colorTexture(mc),depth=FilterRenderBridge.depthTexture(mc);
                    if(color<=0 || depth<=0)continue;
                    if(pipeline==null)pipeline=new FilterGpuPipeline();
                    pipeline.worldEvent(color,depth,target.width,target.height,uniforms);
                } else if(!FilterRenderBridge.renderEvent(uniforms)) {
                    Events.status="Events are unavailable on the active graphics backend.";
                }
            }
        } catch(RuntimeException error) {
            failed=true;Events.status="Event rendering failed: "+error.getMessage();
            pdx.dev.fbactors.FlashbackActors.LOGGER.warn("Visual event render failed",error);
        }
    }
    public static Map<String,Float> uniforms(EventClip clip,double tick,EventCamera camera) {
        var frame=MissileFlight.sample(clip,tick);var map=new HashMap<String,Float>();
        var position=camera.position();var origin=clip.launch;
        vector(map,"Camera",position.x-origin.x(),position.y-origin.y(),position.z-origin.z());
        vector(map,"Target",clip.impact.x()-origin.x(),clip.impact.y()-origin.y(),clip.impact.z()-origin.z());
        vector(map,"Rocket",frame.position().x()-origin.x(),frame.position().y()-origin.y(),frame.position().z()-origin.z());
        var axis=frame.direction();vector(map,"Axis",axis.x(),axis.y(),axis.z());
        for(var entry:Map.of("Right",new Vector3f(1,0,0),"Up",new Vector3f(0,1,0),"Forward",new Vector3f(0,0,-1)).entrySet()) {
            var v=camera.rotation().transform(entry.getValue());vector(map,entry.getKey(),v.x,v.y,v.z);
        }
        var p=camera.projection();
        map.put("ProjectionX",1/p.m00());map.put("ProjectionY",1/p.m11());map.put("DepthA",p.m22());map.put("DepthB",p.m32());
        map.put("ReverseDepth",FilterRenderBridge.reverseDepth()?1f:0f);
        map.put("Progress",(float)frame.progress());map.put("BlastTime",(float)frame.explosion());
        map.put("Time",(float)(frame.progress()*MissileFlight.DEFAULT_TICKS/20.));map.put("Scale",clip.scale);map.put("BlastScale",clip.blast);
        map.put("CloudNoise",clip.kind.natural() || clip.kind!=EventKind.CARPET && frame.progress()>=clip.kind.impact?1f:0f);
        map.put("ReducedResolution",0f); // Detailed cloud integration stays at the scene's native resolution.
        map.put("CloudBase",0f);map.put("CloudThickness",0f);
        map.put("Preview",0f);
        map.put("DeltaOutput",0f);
        map.put("SpaceView",clip.kind==EventKind.ICBM && clip.eventCam?1f:0f);
        map.put("Travel",(float)MissileFlight.fraction(frame.progress()));map.put("ArcHeight",(float)MissileFlight.height(clip));
        map.put("Ignition",(float)MissileFlight.IGNITION);map.put("Separation",(float)MissileFlight.SEPARATION);
        map.put("SecondSeparation",(float)MissileFlight.SECOND_SEPARATION);map.put("Burnout",(float)MissileFlight.BURNOUT);map.put("Shroud",(float)MissileFlight.SHROUD);
        map.put("Reentry",(float)MissileFlight.REENTRY);map.put("Impact",(float)MissileFlight.IMPACT);
        map.put("Type",(float)clip.kind.ordinal());map.put("DropHeight",clip.dropHeight);map.put("Heading",(float)Math.toRadians(clip.heading));
        map.put("Spread",clip.spread);map.put("BombCount",(float)clip.bombs);map.put("Duration",clip.kind.duration/20f);
        int strike=0;
        for(int i=1;i<EventNature.count(clip.kind);i++)if(frame.progress()>=EventNature.cue(clip,i))strike=i;
        var bolt=EventNature.point(clip,strike);vector(map,"Bolt",bolt.x()-origin.x(),bolt.y()-origin.y(),bolt.z()-origin.z());
        map.put("BoltAge",(float)((frame.progress()-EventNature.cue(clip,strike))*clip.kind.duration/20.));map.put("BoltSeed",(float)strike);
        for(int bomb=0;bomb<24;bomb++)map.put("Ground"+bomb,0f);
        if(clip.kind!=EventKind.ICBM) {
            map.put("Scale",(float)BombFlight.size(clip));
            map.put("Impact",(float)clip.kind.impact);map.put("BlastTime",(float)Math.max(0,(frame.progress()-clip.kind.impact)/(1-clip.kind.impact)));
            map.put("Time",(float)(frame.progress()*clip.kind.duration/20.));
        }
        released(map,clip,frame.progress(),MissileFlight.SEPARATION,"Booster",-30.48);
        released(map,clip,frame.progress(),MissileFlight.SECOND_SEPARATION,"Upper",-15.925);
        released(map,clip,frame.progress(),MissileFlight.SHROUD+.02,"Third",-8.984);
        released(map,clip,frame.progress(),MissileFlight.SHROUD,"Fairing",-4.216);
        EventEnding.apply(map,clip,frame.progress());
        return map;
    }
    private static void released(Map<String,Float> map,EventClip clip,double progress,double phase,String name,double offset) {
        double duration=clip.end-clip.start;
        var at=MissileFlight.sample(clip,clip.start+duration*phase);
        var next=MissileFlight.sample(clip,clip.start+duration*(phase+.001));
        double seconds=Math.max(0,progress-phase)*MissileFlight.DEFAULT_TICKS/20.;
        double factor=seconds/(.001*MissileFlight.DEFAULT_TICKS/20.);
        var p=at.position();var q=next.position();var d=at.direction();
        vector(map,name,p.x()-clip.launch.x()+d.x()*offset*clip.scale+(q.x()-p.x())*factor,
            p.y()-clip.launch.y()+d.y()*offset*clip.scale+(q.y()-p.y())*factor-4.9*seconds*seconds,
            p.z()-clip.launch.z()+d.z()*offset*clip.scale+(q.z()-p.z())*factor);
        vector(map,name+"Axis",d.x(),d.y(),d.z());
    }
    private static void vector(Map<String,Float> map,String key,double x,double y,double z) {
        map.put(key+"X",(float)x);map.put(key+"Y",(float)y);map.put(key+"Z",(float)z);
    }
    public static void close() {EventPreview.close();if(pipeline!=null)pipeline.close();pipeline=null;failed=false;}
}
