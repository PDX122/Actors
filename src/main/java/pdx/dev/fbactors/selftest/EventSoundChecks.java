package pdx.dev.fbactors.selftest;

import com.moulberry.flashback.playback.ReplayServer;
import java.util.List;
import pdx.dev.fbactors.events.*;

/** Live mixer check: resume the supplied launch midway, then hear its detonation. */
final class EventSoundChecks {
    private static int phase,start,settle;
    private static long deadline;
    private static int pauseTick;
    private static boolean enabled;
    private static List<EventClip> original;
    private static EventTimeline track;
    private static EventClip clip;
    private EventSoundChecks() {}
    static boolean tick(ReplayServer replay) {
        if(phase==8)return true;
        if(phase==0) {
            start=replay.getReplayTick();track=Events.timeline();original=List.copyOf(track.clips);enabled=track.enabled;
            var mc=net.minecraft.client.Minecraft.getInstance();EventCam.stop();EventAudio.stop();
            clip=new EventClip();clip.kind=EventKind.ICBM;clip.eventCam=true;clip.end=clip.kind.duration;
            clip.launch=new EventClip.Point(mc.player.getX()+4,mc.player.getY(),mc.player.getZ()+4);clip.impact=clip.launch.add(95,0,0);
            track.enabled=true;track.clips.clear();track.clips.add(clip);Events.timeline();
            replay.replayPaused=true;replay.goToReplayTick(160);deadline=System.currentTimeMillis()+30000;phase=1;return false;
        }
        if(System.currentTimeMillis()>deadline)fail(replay,"Audio probe timed out at "+phase);
        if(phase==1 && replay.getReplayTick()==160 && ++settle>=6) {phase=2;replay.replayPaused=false;}
        if(phase==2 && replay.getReplayTick()>=172) {
            if(!EventAudio.hasActiveBed(clip.id))fail(replay,"No supplied launch audio after resuming mid-event");
            replay.replayPaused=true;
            pauseTick=replay.getReplayTick();
            settle=0;
            phase=6;
        }
        if(phase==6 && ++settle>=6) {
            if(!EventAudio.hasActiveBed(clip.id))fail(replay,"Pausing discarded the launch source");
            replay.replayPaused=false;
            phase=7;
        }
        if(phase==7 && replay.getReplayTick()>=pauseTick+8) {
            if(!EventAudio.hasActiveBed(clip.id))fail(replay,"Launch audio did not resume");
            replay.replayPaused=true;EventAudio.stop();clip.eventCam=false;clip.start=-600;clip.end=600;
            replay.goToReplayTick(262);settle=0;phase=3;
        }
        if(phase==3 && replay.getReplayTick()==262 && ++settle>=6) {phase=4;replay.replayPaused=false;}
        if(phase==4 && replay.getReplayTick()>=285) {
            if(!EventAudio.hasActiveImpact(clip.id))fail(replay,"Supplied ICBM detonation failed to reach the sound mixer");
            replay.replayPaused=true;restore();replay.goToReplayTick(start);settle=0;phase=5;
        }
        if(phase==5 && replay.getReplayTick()<=start+1 && ++settle>=6) {phase=8;return true;}
        return false;
    }
    private static void fail(ReplayServer replay,String message) {restore();replay.replayPaused=true;replay.goToReplayTick(start);throw new IllegalStateException(message);}
    private static void restore() {EventAudio.stop();track.clips.clear();track.clips.addAll(original);track.enabled=enabled;Events.timeline();}
}
