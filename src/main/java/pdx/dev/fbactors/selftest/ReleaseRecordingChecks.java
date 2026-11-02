package pdx.dev.fbactors.selftest;

import com.moulberry.flashback.playback.ReplayServer;
import net.minecraft.server.level.ServerPlayer;
import pdx.dev.fbactors.FlashbackActors;
import pdx.dev.fbactors.actor.ActorMode;
import pdx.dev.fbactors.compat.Compat;
import pdx.dev.fbactors.take.*;
import pdx.dev.fbactors.voice.VoiceSettings;
import java.nio.file.*;
import java.util.*;

final class ReleaseRecordingChecks {
    static void run(Path directory, ReplayServer replay, ServerPlayer viewer) throws Exception {
        boolean voice=VoiceSettings.enabled(), activation=VoiceSettings.activation();
        int threshold=VoiceSettings.threshold(); String device=VoiceSettings.deviceName(); float gain=VoiceSettings.gain();
        var position=viewer.position(); float yaw=viewer.getYRot(), pitch=viewer.getXRot();
        boolean shift=viewer.isShiftKeyDown(), sprint=viewer.isSprinting();
        try {

            VoiceSettings.capture(false,activation,threshold,device,gain);
            ActorMode.enterSetup(replay,viewer); ActorMode.confirmSetup();
            ActorMode.tick(replay,viewer,20);
            require(ActorMode.phase()==ActorMode.Phase.PREP,"setup confirmation");
            require(ActorMode.beginCountdown(1),"countdown start");
            for(int t=20;t<40;t++) ActorMode.tick(replay,viewer,t);
            require(ActorMode.phase()==ActorMode.Phase.RECORDING,"countdown completion");
            for(int t=40;t<80;t++) {
                Compat.snapTo(viewer,position.x+(t-40)*.05,position.y,position.z,t,0);
                viewer.setShiftKeyDown(t>=50 && t<60); viewer.setSprinting(t>=60);
                ActorMode.tick(replay,viewer,t);
            }
            pdx.dev.fbactors.record.TakeRecorder.captureUseAir(viewer.level(),false);
            pdx.dev.fbactors.record.TakeRecorder.captureUseAir(viewer.level(),false);
            ActorTake take=ActorMode.finish(replay,viewer);
            require(take!=null && take.length()==41,"recorded frame count");
            require(ActorMode.phase()==ActorMode.Phase.IDLE,"recording stop returns to editor");
            require(take.uses.size()==1,"item use capture and deduplication");
            require(take.frames.get(40).x()-take.frames.get(1).x()>1.8,"movement capture");
            Path binary=directory.resolve("recorded-take.fbact"); TakeIO.write(binary,take);
            ActorTake loaded=TakeIO.read(binary);
            require(loaded.frames.equals(take.frames),"take frame binary roundtrip");
            var expectedUses=take.uses.stream().map(u -> u.withItem(emptyToNull(u.itemId()),emptyToNull(u.itemNbt()))).toList();
            require(loaded.uses.equals(expectedUses),"take item-use binary roundtrip: "+loaded.uses+" / "+expectedUses);
            var empty=new com.google.gson.JsonObject();
            Path zip=directory.resolve("recorded-take.fbactors.zip");
            TakeBundle.exportBundle(zip,replay.getMetadata().replayIdentifier,"Release QA",List.of(take),empty,empty,empty);
            var imported=TakeBundle.readBundle(zip);
            require(imported.takes().size()==1 && imported.takes().get(0).frames.equals(take.frames),"sharing bundle roundtrip");
            Files.writeString(directory.resolve("recording.txt"),"PASS: setup, countdown, movement, crouch/sprint inputs, item use deduplication, stop, binary take and sharing bundle roundtrips\n");
        } finally {
            if(ActorMode.phase()!=ActorMode.Phase.IDLE || FlashbackActors.RECORDER.isRecording()) ActorMode.finish(replay,viewer);
            Compat.snapTo(viewer,position.x,position.y,position.z,yaw,pitch);
            viewer.setShiftKeyDown(shift); viewer.setSprinting(sprint);
            VoiceSettings.capture(voice,activation,threshold,device,gain);
            replay.replayPaused=true;
        }
    }
    private static void require(boolean condition,String step) {
        if(!condition) throw new IllegalStateException("Recording check failed: "+step);
    }
    private static String emptyToNull(String value) { return value==null || value.isEmpty()?null:value; }
}
