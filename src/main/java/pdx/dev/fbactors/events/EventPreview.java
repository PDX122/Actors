package pdx.dev.fbactors.events;

import imgui.moulberry90.ImGui;
import java.util.Map;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import pdx.dev.fbactors.filters.FilterGpuPipeline;
import pdx.dev.fbactors.filters.FilterRenderBridge;

/** An isolated 3D render of the actual event, with its own clock and GPU resources. */
public final class EventPreview {
    public static final int WIDTH=512,HEIGHT=320;
    private static FilterGpuPipeline pipeline;
    private static double started=Double.NaN,held,lastTime=Double.NaN;
    private static boolean sequence,paused,lastSequence;
    private static int detail,lastDetail=-1;
    private static long handle;
    private static EventKind kind=EventKind.ICBM;
    private static EventClip configured;
    private static String configuration="";
    private static double duration() {return configured==null?kind.duration/20.:(configured.end-configured.start)/20.;}
    public static void configure(EventClip clip) {
        String key=clip==null?"":clip.id+":"+clip.end+":"+clip.start+":"+clip.eventCam+":"+clip.ending+":"+clip.cameraStyle+":"+clip.cinematicBars+":"+clip.scale+":"+clip.blast+":"+clip.dropHeight+":"+clip.heading+":"+clip.spread+":"+clip.bombs;
        if(!key.equals(configuration)) {configuration=key;configured=clip==null?null:clip.copy();lastTime=Double.NaN;}
    }
    public static void select(EventKind selected) {EventAudio.stopAudition();kind=selected;configured=null;configuration="";lastTime=Double.NaN;detail=0;sequence=selected.natural();restart();}
    private static String error="";
    private record Thumbnail(Object source,net.minecraft.client.renderer.texture.DynamicTexture texture) {}
    private static final Map<EventKind,Thumbnail> thumbnails=new java.util.EnumMap<>(EventKind.class);
    public static long thumbnail(EventKind type) {
        var mc=net.minecraft.client.Minecraft.getInstance();String id="fbactors:events/gallery/"+type.name().toLowerCase(java.util.Locale.ROOT)+".png";
        var resource=mc.getResourceManager().getResource(pdx.dev.fbactors.compat.Compat.boatTextureId(id)).orElse(null);if(resource==null)return 0;
        var cached=thumbnails.get(type);
        if(cached==null || cached.source()!=resource.source() || mc.getTextureManager().getTexture(pdx.dev.fbactors.compat.Compat.boatTextureId(id))!=cached.texture()) {
            try(var input=resource.open()) {
                if(cached!=null)mc.getTextureManager().release(pdx.dev.fbactors.compat.Compat.boatTextureId(id));
                var texture=pdx.dev.fbactors.compat.Compat.registerBoatTexture(id,com.mojang.blaze3d.platform.NativeImage.read(input));
                thumbnails.put(type,new Thumbnail(resource.source(),texture));
            }catch(java.io.IOException failure) {error="Event thumbnail could not load: "+failure.getMessage();return 0;}
        }
        return pdx.dev.fbactors.compat.Compat.cameraPreviewTexture(id);
    }
    private EventPreview() {}
    public static void restart() {started=Double.NaN;held=0;paused=false;}
    private static double seconds() {
        if(!Double.isFinite(started))started=ImGui.getTime();
        double time=paused?held:ImGui.getTime()-started;
        if(sequence && time>=duration()) {held=duration();paused=true;return duration();}
        return time;
    }
    public static boolean card(float width) {
        float height=width*HEIGHT/WIDTH;
        boolean clicked=ImGui.invisibleButton("##ICBMCard",width,height);
        if(!ImGui.isItemVisible())return clicked;
        float x=ImGui.getItemRectMinX(),y=ImGui.getItemRectMinY();var draw=ImGui.getWindowDrawList();
        try {
            double time=Math.floor(seconds()*30)/30.;
            if(handle==0 || time!=lastTime || sequence!=lastSequence || detail!=lastDetail) {
                var values=configured==null?uniforms(kind,time,sequence,detail):uniforms(configured,time,sequence,detail);
                if(FilterGpuPipeline.isGlAvailable()) {
                    if(pipeline==null)pipeline=new FilterGpuPipeline();
                    int texture=pipeline.eventPreview(WIDTH,HEIGHT,values);
                    handle=FilterRenderBridge.eventPreviewHandle(texture,WIDTH,HEIGHT);
                } else handle=FilterRenderBridge.nativeEventPreview(values,WIDTH,HEIGHT);
                lastTime=time;lastSequence=sequence;lastDetail=detail;
            }
            if(handle==0)throw new IllegalStateException("The active renderer could not create a 3D event preview.");
            draw.addImage(handle,x,y,x+width,y+height,0,1,1,0);error="";
        } catch(RuntimeException failure) {
            draw.addRectFilled(x,y,x+width,y+height,0xff29231f,5);
            draw.addText(x+12,y+16,0xffbac4d4,"3D preview unavailable");error=failure.getMessage();
        }
        draw.addRect(x,y,x+width,y+height,0xff668fb5,5);
        draw.addRectFilled(x,y+height-37,x+width,y+height,0xc51b2026);
        draw.addText(x+10,y+height-29,0xfff2e7d8,kind==EventKind.ICBM?"ICBM / THREE-STAGE":kind.label);
        draw.addText(x+10,y+height-14,0xffafbfd0,sequence?phase(seconds()):"3D turntable - drag into the scene");
        return clicked;
    }
    public static void controls() {
        EventAudio.auditionVisible();
        if(!kind.natural() && ImGui.smallButton("3D model")) {sequence=false;restart();}
        if(!kind.natural())ImGui.sameLine();if(ImGui.smallButton("Play sequence")) {sequence=true;restart();}
        ImGui.sameLine();if(ImGui.smallButton(paused?"Resume":"Pause")) {
            if(paused) {if(sequence && held>=duration())held=0;started=ImGui.getTime()-held;paused=false;}else {held=seconds();paused=true;}
        }
        if(sequence) {
            if(ImGui.smallButton("Replay"))restart();ImGui.sameLine();ImGui.setNextItemWidth(-1);
            float[] time={(float)seconds()};
            if(ImGui.sliderFloat("##EventPreviewTime",time,0,(float)duration(),"%.1f s")) {held=time[0];paused=true;}
        } else {
            ImGui.setNextItemWidth(-1);
            String[] views=kind==EventKind.ICBM?new String[]{"Whole missile","Nose and service details","Engine and fins"}:new String[]{"Whole model","Surface details","Tail details"};
            if(ImGui.beginCombo("##EventModelView",views[detail])) {
                for(int i=0;i<views.length;i++)if(ImGui.selectable(views[i],detail==i))detail=i;
                ImGui.endCombo();
            }
        }
        if(!error.isEmpty())ImGui.textWrapped(error);
        if(kind==EventKind.ICBM) {
            ImGui.beginDisabled(com.moulberry.flashback.Flashback.isExporting());
            if(ImGui.smallButton("Hear launch"))EventAudio.audition("launch");
            ImGui.sameLine();if(ImGui.smallButton("Re-entry"))EventAudio.audition("reentry_scream");
            if(ImGui.smallButton("Detonation"))EventAudio.audition("detonation_close");
            ImGui.sameLine();if(ImGui.smallButton("Stop sound"))EventAudio.stopAudition();ImGui.endDisabled();
        }
    }
    public static Map<String,Float> uniforms(double seconds,boolean sequence) {
        return uniforms(seconds,sequence,0);
    }
    public static Map<String,Float> uniforms(double seconds,boolean sequence,int detail) {
        return uniforms(EventKind.ICBM,seconds,sequence,detail);
    }
    public static Map<String,Float> uniforms(EventKind type,double seconds,boolean sequence,int detail) {
        if(type.natural())return natureUniforms(type,seconds,sequence);
        if(type!=EventKind.ICBM)return bombUniforms(type,seconds,sequence,detail);
        var clip=new EventClip();clip.launch=new EventClip.Point(0,0,0);clip.impact=new EventClip.Point(95,0,0);
        double progress=sequence?Math.min(.9999,Math.max(0,seconds/(MissileFlight.DEFAULT_TICKS/20.))):0;
        var flight=MissileFlight.sample(clip,progress*clip.end);
        var focus=sequence && progress<MissileFlight.IMPACT?flight.position().add(0,-(progress<MissileFlight.SEPARATION?15:progress<MissileFlight.SHROUD?5:1),0)
            :sequence?clip.impact.add(0,18,0):new EventClip.Point(0,detail==1?25:detail==2?1.7:15,0);
        double angle=sequence?.65:seconds*.28+.65;
        double distance=sequence && progress>=MissileFlight.IMPACT?108:sequence && progress>=MissileFlight.SHROUD?26:!sequence && detail>0?(detail==1?18:10):47;
        double elevation=!sequence && detail>0?(detail==1?2:-.5):sequence && progress>=MissileFlight.IMPACT?30:9;
        double eyeX=focus.x()+Math.sin(angle)*distance,eyeY=focus.y()+elevation,eyeZ=focus.z()+Math.cos(angle)*distance;
        float fov=43;
        if(sequence) {
            var view=EventCameraModel.shot(clip,progress*clip.end,WIDTH/(double)HEIGHT,null);
            eyeX=view.x();eyeY=view.y();eyeZ=view.z();fov=view.fov();
            double yaw=Math.toRadians(view.yaw()),pitch=Math.toRadians(view.pitch());
            focus=new EventClip.Point(eyeX-Math.sin(yaw)*Math.cos(pitch),eyeY-Math.sin(pitch),eyeZ+Math.cos(yaw)*Math.cos(pitch));
        }
        var rotation=new Matrix4f().lookAt((float)eyeX,(float)eyeY,(float)eyeZ,(float)focus.x(),(float)focus.y(),(float)focus.z(),0,1,0)
            .getUnnormalizedRotation(new Quaternionf()).conjugate();
        var camera=new EventCamera(new Vec3(eyeX,eyeY,eyeZ),rotation,new Matrix4f().perspective((float)Math.toRadians(fov),WIDTH/(float)HEIGHT,.05f,100000));
        var result=EventRenderer.uniforms(clip,progress*clip.end,camera);result.put("Preview",sequence?2f:1f);result.put("SpaceView",sequence?1f:0f);return result;
    }
    public static Map<String,Float> uniforms(EventClip source,double seconds,boolean sequence,int detail) {
        double duration=Math.max(1,(source.end-source.start)/20.);
        var values=uniforms(source.kind,seconds*source.kind.duration/20./duration,sequence,detail);
        double progress=sequence?Math.min(.9999,Math.max(0,seconds/duration)):0;
        var clip=source.copy();clip.start=0;clip.end=source.end-source.start;
        clip.launch=new EventClip.Point(0,0,0);clip.impact=clip.kind==EventKind.ICBM?new EventClip.Point(95,0,0):clip.launch;
        if(sequence) {
            var view=EventCameraModel.shot(clip,progress*clip.end,WIDTH/(double)HEIGHT,null);
            double yaw=Math.toRadians(view.yaw()),pitch=Math.toRadians(view.pitch());
            var rotation=new Matrix4f().lookAt((float)view.x(),(float)view.y(),(float)view.z(),
                (float)(view.x()-Math.sin(yaw)*Math.cos(pitch)),(float)(view.y()-Math.sin(pitch)),(float)(view.z()+Math.cos(yaw)*Math.cos(pitch)),0,1,0)
                .getUnnormalizedRotation(new Quaternionf()).conjugate();
            values=EventRenderer.uniforms(clip,progress*clip.end,new EventCamera(new Vec3(view.x(),view.y(),view.z()),rotation,
                new Matrix4f().perspective((float)Math.toRadians(view.fov()),WIDTH/(float)HEIGHT,.05f,100000)));
            values.put("Preview",2f);values.put("SpaceView",clip.kind==EventKind.ICBM?1f:0f);
        }
        EventEnding.apply(values,clip,progress);return values;
    }
    private static Map<String,Float> bombUniforms(EventKind type,double seconds,boolean sequence,int detail) {
        var clip=new EventClip();clip.kind=type;clip.end=type.duration;if(type==EventKind.CARPET)clip.heading=90;clip.launch=clip.impact=new EventClip.Point(0,0,0);
        double progress=sequence?Math.min(.9999,Math.max(0,seconds/(type.duration/20.))):0;
        var focus=sequence && progress<type.impact?BombFlight.position(clip,0,progress):new EventClip.Point(0,sequence?55:2*BombFlight.size(clip),0);
        if(type==EventKind.CARPET)focus=new EventClip.Point(0,sequence?30:5,0);
        double angle=sequence?.7:seconds*.28+.6;
        double distance=sequence?(progress<type.impact?15:260):detail==0?9:5;
        if(type==EventKind.NUCLEAR)distance*=2.5;
        if(type==EventKind.CARPET)distance=sequence?140:50;
        double x=focus.x()+Math.sin(angle)*distance,y=focus.y()+distance*.25,z=focus.z()+Math.cos(angle)*distance;
        float fov=43;
        if(sequence && type==EventKind.NUCLEAR) {
            var shot=EventCameraModel.shot(clip,progress*clip.end,WIDTH/(double)HEIGHT,null);
            x=shot.x();y=shot.y();z=shot.z();fov=shot.fov();
            double yaw=Math.toRadians(shot.yaw()),pitch=Math.toRadians(shot.pitch());
            focus=new EventClip.Point(x-Math.sin(yaw)*Math.cos(pitch),y-Math.sin(pitch),z+Math.cos(yaw)*Math.cos(pitch));
        }
        var rotation=new Matrix4f().lookAt((float)x,(float)y,(float)z,(float)focus.x(),(float)focus.y(),(float)focus.z(),0,1,0).getUnnormalizedRotation(new Quaternionf()).conjugate();
        var camera=new EventCamera(new Vec3(x,y,z),rotation,new Matrix4f().perspective((float)Math.toRadians(fov),WIDTH/(float)HEIGHT,.05f,100000));
        var result=EventRenderer.uniforms(clip,progress*clip.end,camera);result.put("Preview",sequence?2f:1f);result.put("SpaceView",sequence?1f:0f);return result;
    }
    private static Map<String,Float> natureUniforms(EventKind type,double seconds,boolean sequence) {
        var c=new EventClip();c.kind=type;c.end=type.duration;c.launch=c.impact=new EventClip.Point(0,0,0);
        double p=sequence?Math.min(.9999,Math.max(0,seconds/(type.duration/20.))):EventNature.showcase(type);
        var view=EventCameraModel.shot(c,p*c.end,WIDTH/(double)HEIGHT,null);
        double yaw=Math.toRadians(view.yaw()),pitch=Math.toRadians(view.pitch());
        var rotation=new Matrix4f().lookAt((float)view.x(),(float)view.y(),(float)view.z(),(float)(view.x()-Math.sin(yaw)*Math.cos(pitch)),(float)(view.y()-Math.sin(pitch)),(float)(view.z()+Math.cos(yaw)*Math.cos(pitch)),0,1,0).getUnnormalizedRotation(new Quaternionf()).conjugate();
        var values=EventRenderer.uniforms(c,p*c.end,new EventCamera(new Vec3(view.x(),view.y(),view.z()),rotation,new Matrix4f().perspective((float)Math.toRadians(view.fov()),WIDTH/(float)HEIGHT,.05f,100000)));
        values.put("Preview",2f);return values;
    }
    private static String phase(double time) {
        var clip=new EventClip();clip.kind=kind;double p=Math.min(.9999,time/duration());return kind==EventKind.ICBM?MissileFlight.sample(clip,p*clip.end).phase():BombFlight.phase(clip,p);
    }
    public static void close() {
        EventAudio.stopAudition();
        for(var type:thumbnails.keySet())net.minecraft.client.Minecraft.getInstance().getTextureManager().release(pdx.dev.fbactors.compat.Compat.boatTextureId("fbactors:events/gallery/"+type.name().toLowerCase(java.util.Locale.ROOT)+".png"));thumbnails.clear();
        FilterRenderBridge.closeEventPreview();if(pipeline!=null)pipeline.close();pipeline=null;handle=0;lastTime=Double.NaN;error="";
    }
}
