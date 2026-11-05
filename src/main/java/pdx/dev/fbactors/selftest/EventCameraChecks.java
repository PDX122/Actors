package pdx.dev.fbactors.selftest;

import com.moulberry.flashback.playback.ReplayServer;
import java.util.List;
import pdx.dev.fbactors.events.*;

/** Launch-only live-frame probe across the cover separation, beyond recorded footage. */
public final class EventCameraChecks {
    private static int phase,start,frames,settle;
    private static long deadline;
    private static double previous=Double.NaN;
    private static String error="";
    private static List<EventClip> original;
    private static EventTimeline track;
    private static EventClip clip;
    private EventCameraChecks() {}
    static boolean tick(ReplayServer replay) {
        if(phase==4)return true;
        if(phase==0) {
            start=replay.getReplayTick();track=Events.timeline();original=List.copyOf(track.clips);
            var mc=net.minecraft.client.Minecraft.getInstance();EventCam.stop();
            clip=new EventClip();clip.eventCam=true;clip.sound=false;
            clip.launch=new EventClip.Point(mc.player.getX(),mc.player.getY(),mc.player.getZ());clip.impact=clip.launch.add(95,0,0);
            track.clips.clear();track.clips.add(clip);Events.timeline();
            replay.replayPaused=true;replay.goToReplayTick(502);deadline=System.currentTimeMillis()+25000;phase=1;
            return false;
        }
        if(!error.isEmpty() || System.currentTimeMillis()>deadline) {
            restore();replay.replayPaused=true;replay.goToReplayTick(start);
            throw new IllegalStateException(error.isEmpty()?"Live event camera probe timed out at "+phase:error);
        }
        if(phase==1 && replay.getReplayTick()==502 && ++settle>=6) {phase=2;previous=Double.NaN;frames=0;replay.replayPaused=false;}
        if(phase==2 && replay.getReplayTick()>=514 && frames>=3) {
            replay.replayPaused=true;restore();replay.goToReplayTick(start);phase=3;settle=0;
        }
        if(phase==3 && replay.getReplayTick()<=start+1 && ++settle>=6) {phase=4;return true;}
        return false;
    }
    public static void frame(EventCamera camera,double tick) {
        if(phase!=2)return;
        var view=EventCam.renderedShot();if(view==null) {error="Live event camera lost ownership at "+tick;return;}
        double delta=camera.position().distanceTo(new net.minecraft.world.phys.Vec3(view.x(),view.y(),view.z()));
        if(delta>.10)error="Live camera pose differed from the event frame by "+delta+" at "+tick;
        if(Double.isFinite(previous) && tick<previous)error="Live event clock crossed the stage cut backwards: "+previous+" -> "+tick;
        previous=tick;frames++;
    }
    private static void restore() {EventCam.stop();track.clips.clear();track.clips.addAll(original);Events.timeline();}
}
