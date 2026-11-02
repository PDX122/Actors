package pdx.dev.fbactors.selftest;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.state.EditorStateManager;

import net.minecraft.client.Minecraft;
import pdx.dev.fbactors.FlashbackActors;
import pdx.dev.fbactors.actor.Actor;
import pdx.dev.fbactors.camera.*;
import pdx.dev.fbactors.settings.FbSettings;
import pdx.dev.fbactors.take.*;
import java.nio.file.*;
import java.util.*;

public final class BoatStudioSelfTest {
    private static boolean read, done;
    private static Path report;
    private static int stage, tracks, mode;
    private static long began;
    private static volatile Actor first, second;
    private static UUID savedTarget, savedRival;
    private static String result;
    private BoatStudioSelfTest() {}
    static void arm(Path file) { read=true; done=false; report=file; began=System.currentTimeMillis(); }
    public static void tick() {
        if (!read) {
            read=true;
            var path=System.getenv("FBACTORS_BOAT_STUDIO_TEST");
            if(path!=null && !path.isBlank()) report=Path.of(path);
            began=System.currentTimeMillis();
        }
        if(report==null || done) return;
        var replay=Flashback.getReplayServer(); var mc=Minecraft.getInstance();
        if(System.currentTimeMillis()-began>180000) { finish("FAIL: timed out at stage "+stage); return; }
        if(replay==null || mc.level==null || mc.player==null || EditorStateManager.getCurrent()==null) return;
        try {
            if(stage==0) {
                var settings=BoatStudioState.get();
                savedTarget=settings.target; savedRival=settings.rival;
                mode=FbSettings.settings().boatCamMode;
                var editor=EditorStateManager.getCurrent(); long stamp=editor.acquireRead();
                try { tracks=editor.getCurrentScene(stamp).keyframeTracks.size(); } finally { editor.release(stamp); }
                double x=mc.player.getX(),y=mc.player.getY(),z=mc.player.getZ();
                stage=1;
                replay.execute(()-> {
                    var viewer=FlashbackActors.viewerOf(replay);
                    if(viewer==null) return;
                    String dimension=pdx.dev.fbactors.compat.Compat.dimensionId((net.minecraft.server.level.ServerLevel)viewer.level());
                    first=fixture("Boat probe A",dimension,x-3,y,z);
                    second=fixture("Boat probe B",dimension,x+3,y,z);
                });
            } else if(stage==1 && first!=null && second!=null) {
                var settings=BoatStudioState.get(); settings.target=first.take.id; settings.rival=second.take.id;
                FbSettings.settings().boatCamMode=7;
                var clip = new BoatTimeline.Clip();
                clip.start=20; clip.end=24; clip.target=first.take.id; clip.rival=second.take.id; clip.settings=BoatCam.snapshot();
                settings.timeline.clips.add(clip);
                try {
                    require(settings.timeline.active(19)==null,"camera active before clip");
                    require(settings.timeline.active(20)==clip,"camera missing at clip start");
                    require(settings.timeline.active(23.5)==clip,"camera missing at fractional tick");
                    require(settings.timeline.active(24)==null,"camera active after clip");
                    var editor=EditorStateManager.getCurrent(); long stamp=editor.acquireRead();
                    try { require(editor.getCurrentScene(stamp).keyframeTracks.size()==tracks,"timeline modified native tracks"); }
                    finally { editor.release(stamp); }
                } finally { settings.timeline.clips.remove(clip); }
                finish("PASS: boat clip boundaries and native track preservation");
            }
        } catch(Exception e) { finish("FAIL: "+e); }
    }
    private static Actor fixture(String name,String dimension,double x,double y,double z) {
        var take=new ActorTake(UUID.randomUUID(),name,dimension,0);
        UUID boat=UUID.randomUUID();
        for(int tick=0;tick<61;tick++) {
            take.frames.add(new ActorFrame(x,y+.5,z+tick*.25,0,0,0,(byte)0,(byte)4));
            take.vehicles.add(new ActorVehicleFrame(tick,boat,"minecraft:oak_boat",x,y,z+tick*.25,0,0)
                .withMotion(0,0,.25f,0,0,0,0,true,true));
            take.inputs.add(ActorInput.unpack(tick,0,1,0,0,0,(byte)0,0));
        }
        return FlashbackActors.ACTORS.add(take);
    }
    private static void require(boolean condition,String message) { if(!condition) throw new IllegalStateException(message); }
    private static void finish(String verdict) {
        if(done) return;
        done=true; result=verdict;
        BoatCam.stop();
        var replay=Flashback.getReplayServer();
        if(replay!=null && stage>0) {
            var settings=BoatStudioState.get(); settings.target=savedTarget; settings.rival=savedRival;
            FbSettings.settings().boatCamMode=mode;
            replay.execute(()-> {
                if(first!=null) FlashbackActors.ACTORS.remove(first);
                if(second!=null) FlashbackActors.ACTORS.remove(second);
            });
        }
        try { Files.createDirectories(report.toAbsolutePath().getParent()); Files.writeString(report,result); }
        catch(Exception e) { FlashbackActors.LOGGER.warn("Could not write boat studio probe verdict",e); }
        FlashbackActors.LOGGER.info("Boat studio probe: {}",result);
    }
}
