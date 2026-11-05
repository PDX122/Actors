package pdx.dev.fbactors.selftest;

import com.moulberry.flashback.playback.ReplayServer;
import java.util.List;
import pdx.dev.fbactors.events.*;

/** Launch-only probe: play a missile through impact past the archive's final frame using the viewer's camera. */
final class EventTailChecks {
    private static int phase,footage,start,impact,approach;
    private static long deadline;
    private static List<EventClip> original;
    private static EventTimeline track;
    private static boolean enabled;
    private EventTailChecks() {}
    static boolean tick(ReplayServer replay) {
        if(phase==4)return true;
        if(phase==0) {
            try {
                var field=ReplayServer.class.getDeclaredField("totalTicks");field.setAccessible(true);footage=field.getInt(replay);
            } catch(ReflectiveOperationException error) {throw new IllegalStateException(error);}
            start=replay.getReplayTick();track=Events.timeline();original=List.copyOf(track.clips);enabled=track.enabled;
            var clip=new EventClip();
            clip.sound=false;
            clip.launch=new EventClip.Point(0,64,0);clip.impact=clip.launch.add(95,0,0);
            clip.start=Math.max(0,footage-60);clip.end=footage+45;
            impact=(int)Math.ceil(clip.start+(clip.end-clip.start)*MissileFlight.IMPACT);
            approach=Math.max(0,footage-2);
            EventCam.stop();track.enabled=true;track.clips.clear();track.clips.add(clip);Events.timeline();
            replay.replayPaused=true;replay.goToReplayTick(approach);deadline=System.currentTimeMillis()+25000;phase=1;
            return false;
        }
        if(System.currentTimeMillis()>deadline) {
            restore();replay.replayPaused=true;replay.goToReplayTick(start);
            throw new IllegalStateException("Event tail integration timed out at phase "+phase+", tick "+replay.getReplayTick()+", footage "+footage);
        }
        if(phase==1 && replay.getReplayTick()==approach) {replay.replayPaused=false;phase=2;}
        if(phase==2 && replay.getReplayTick()>=impact+8) {
            if(EventCam.renderedShot()!=null)throw new IllegalStateException("Event Cam took over during fixed-camera impact");
            replay.replayPaused=true;restore();replay.goToReplayTick(start);phase=3;
        }
        if(phase==3 && replay.getReplayTick()<=start+1) {phase=4;return true;}
        return false;
    }
    private static void restore() {track.clips.clear();track.clips.addAll(original);track.enabled=enabled;Events.timeline();}
}
