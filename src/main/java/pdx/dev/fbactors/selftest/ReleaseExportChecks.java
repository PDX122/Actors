package pdx.dev.fbactors.selftest;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.exporting.ExportJob;
import com.moulberry.flashback.exporting.ExportSettings;
import com.moulberry.flashback.state.EditorStateManager;
import net.minecraft.client.Minecraft;
import pdx.dev.fbactors.FlashbackActors;
import pdx.dev.fbactors.actor.Actor;
import pdx.dev.fbactors.camera.*;
import pdx.dev.fbactors.take.*;
import java.nio.file.*;
import java.util.*;

public final class ReleaseExportChecks {
    private static volatile Actor first, second;
    private static boolean initialized, done, active;
    private static int settle;
    private static Path root;
    private static String error, recordingError;
    private static final Set<Integer> modes = new TreeSet<>();
    private static final StringBuilder trace = new StringBuilder("mode,replayTick,x,y,z\n");
    private ReleaseExportChecks() {}

    static Actor actor() { return first; }
    static String error() { return recordingError==null?error:recordingError; }
    static boolean tick() {
        if (done) return true;
        if (active) return false;
        var replay = Flashback.getReplayServer();
        var mc = Minecraft.getInstance();
        try {
            if (!initialized) {
                initialized = true;
                String directory = System.getenv("FBACTORS_RELEASE_QA_DIR");
                if (directory == null || directory.isBlank()) { done=true; return true; }
                root=Path.of(directory); Files.createDirectories(root);
                double x=mc.player.getX(), z=mc.player.getZ();
                double clearY=mc.player.getY();

                for(int dx=-12;dx<=12;dx+=2) for(int dz=-12;dz<=12;dz+=2)
                    clearY=Math.max(clearY,mc.level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING,
                        (int)Math.floor(x)+dx,(int)Math.floor(z)+dz)+4);
                final double y=clearY;
                replay.replayPaused=true;
                replay.execute(() -> {
                    try {
                        var viewer=FlashbackActors.viewerOf(replay);
                        if (viewer==null) throw new IllegalStateException("No replay viewer");
                        try { ReleaseRecordingChecks.run(root,replay,viewer); }
                        catch(Exception failure) {
                            recordingError=failure.toString();
                            Files.writeString(root.resolve("recording.txt"),"FAIL: "+recordingError);
                        }
                        String dimension=pdx.dev.fbactors.compat.Compat.dimensionId((net.minecraft.server.level.ServerLevel)viewer.level());
                        first=fixture("Release QA A",dimension,x+4,y,z+4,replay.getTotalReplayTicks());
                        second=fixture("Release QA B",dimension,x+7,y,z+4,replay.getTotalReplayTicks());
                        replay.goToReplayTick(20);
                    } catch(Exception failure) { error=failure.toString(); done=true; }
                });
                return false;
            }
            if (first==null || second==null || ++settle<30) return false;
            if (mc.level.getPlayerByUUID(first.take.id)==null || mc.level.getPlayerByUUID(second.take.id)==null) {
                if(settle>200) throw new IllegalStateException("Export fixture players did not reach the client");
                return false;
            }
            var timeline=BoatStudioState.get().timeline;
            timeline.clips.clear(); timeline.enabled=true;
            int oldMode=pdx.dev.fbactors.settings.FbSettings.settings().boatCamMode;
            for(int mode=0;mode<BoatCam.MODES.length;mode++) {
                var clip=new BoatTimeline.Clip(); clip.start=20+mode*4; clip.end=clip.start+4;
                clip.target=first.take.id; clip.rival=second.take.id;
                pdx.dev.fbactors.settings.FbSettings.settings().boatCamMode=mode;
                clip.settings=BoatCam.snapshot(); timeline.clips.add(clip);
            }
            pdx.dev.fbactors.settings.FbSettings.settings().boatCamMode=oldMode;
            Path video=root.resolve("camera-modes.mp4"); Files.deleteIfExists(video);
            int exportEnd=20+BoatCam.MODES.length*4;
            var settings=exportSettings(video,20,exportEnd);
            Files.writeString(root.resolve("camera-export-fixture.json"),"{\"start\":20,\"end\":"+exportEnd+",\"expectedFrames\":"+(exportEnd-20)+"}");
            active=true;
            try { Flashback.EXPORT_JOB=new ExportJob(settings); Flashback.EXPORT_JOB.run(); }
            finally { active=false; Flashback.EXPORT_JOB=null; BoatCam.stop(); timeline.clips.clear(); }
            Files.writeString(root.resolve("camera-export.csv"),trace);
            Files.writeString(root.resolve("camera-modes.json"),new com.google.gson.Gson().toJson(BoatCam.MODES));
            if(error!=null) throw new IllegalStateException(error);
            if(!Files.isRegularFile(video) || Files.size(video)<1024) throw new IllegalStateException("No encoded camera video");
            if(modes.size()!=BoatCam.MODES.length) throw new IllegalStateException("Export camera modes reached: "+modes);
            Files.writeString(root.resolve("camera-export.txt"),"PASS: real MP4 export, nonzero start tick, all "+modes.size()+" modes reached accurate camera position\n");
            done=true;
        } catch(Exception | LinkageError failure) {
            error=failure.toString(); done=true;
            try { Files.writeString(root.resolve("camera-export.txt"),"FAIL: "+error+"\n"+trace); } catch(Exception ignored) {}
        }
        return done;
    }
    private static Actor fixture(String name,String dimension,double x,double y,double z,int ticks) {
        var take=new ActorTake(UUID.randomUUID(),name,dimension,0);
        for(int t=0;t<=ticks;t++) take.frames.add(new ActorFrame(x,y,z+t*.015,0,0,0,(byte)0,(byte)4));
        return FlashbackActors.ACTORS.add(take);
    }
    private static ExportSettings exportSettings(Path output,int start,int end) throws Exception {
        String[] encoders=com.moulberry.flashback.combo_options.VideoCodec.H264.getEncoders();
        String chosenEncoder=java.util.Arrays.stream(encoders).filter(e -> e.equals("libopenh264")).findFirst().orElse(encoders.length==0?"":encoders[0]);
        Files.writeString(root.resolve("export-encoders.txt"),Arrays.toString(encoders));
        if(encoders.length==0) throw new IllegalStateException("Flashback found no working H.264 encoder");
        var fields=ExportSettings.class.getRecordComponents();
        Object[] args=new Object[fields.length]; Class<?>[] types=new Class<?>[fields.length];
        var player=Minecraft.getInstance().player;
        for(int i=0;i<fields.length;i++) {
            var f=fields[i]; types[i]=f.getType();
            args[i]=switch(f.getName()) {
                case "name" -> "Release QA camera modes";
                case "editorState" -> EditorStateManager.getCurrent().copy();
                case "initialCameraPosition" -> player.position();
                case "initialCameraYaw" -> player.getYRot();
                case "initialCameraPitch" -> player.getXRot();
                case "resolutionX" -> 640; case "resolutionY" -> 360;
                case "startTick" -> start; case "endTick" -> end;
                case "framerate" -> 20d; case "orthographicZoom" -> 1f;
                case "container" -> enumValue(f.getType(),"MP4");
                case "codec" -> enumValue(f.getType(),"H264");
                case "projection" -> enumValue(f.getType(),"PERSPECTIVE");
                case "encoder" -> chosenEncoder; case "bitrate" -> 2_000_000;
                case "noGui" -> true; case "output" -> output;
                case "pngSequenceFormat" -> "%06d.png";
                case "audioCodec" -> null;
                case "resetRng", "depthMap", "transparent", "ssaa", "recordAudio", "stereoAudio" -> false;
                default -> throw new IllegalStateException("Untested export setting: "+f.getName());
            };
        }
        return ExportSettings.class.getDeclaredConstructor(types).newInstance(args);
    }
    private static Object enumValue(Class<?> type,String name) throws Exception { return type.getField(name).get(null); }
    public static void accurateCameraUsed(BoatCameraModel.Shot shot) {
        if(!active) return;
        double tick=BoatSubjects.clock(); var clip=BoatStudioState.get().timeline.active(tick);
        if(clip==null || !shot.valid()) { error="Invalid export camera at "+tick; return; }
        modes.add(clip.settings.mode());
        trace.append(clip.settings.mode()).append(',').append(tick).append(',').append(shot.x()).append(',').append(shot.y()).append(',').append(shot.z()).append('\n');
    }
    static void cleanup() {
        var replay=Flashback.getReplayServer();
        if(replay!=null) replay.execute(() -> {
            if(first!=null) FlashbackActors.ACTORS.remove(first);
            if(second!=null) FlashbackActors.ACTORS.remove(second);
        });
    }
}
