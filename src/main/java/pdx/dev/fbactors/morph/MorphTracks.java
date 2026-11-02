package pdx.dev.fbactors.morph;

import com.google.gson.Gson;
import com.moulberry.flashback.Flashback;
import pdx.dev.fbactors.FlashbackActors;
import pdx.dev.fbactors.take.TakeStore;
import pdx.dev.fbactors.undo.UndoStack;
import java.nio.file.Files;
import java.util.UUID;

public final class MorphTracks {
    private static final Gson GSON=new Gson();
    private static UUID replay;
    private static volatile MorphTimeline timeline=new MorphTimeline();
    private static MorphTimeline before;
    public static UUID selected;
    public static String notice="";
    private static final pdx.dev.fbactors.util.RetryingJsonSaves<MorphTimeline> SAVES=new pdx.dev.fbactors.util.RetryingJsonSaves<>("morph-track.json");
    private static long retryAt;
    private MorphTracks() {}
    public static MorphTimeline timeline() { return timeline; }
    public static void tick() {
        if(!SAVES.isEmpty() && System.currentTimeMillis()>=retryAt) flush();
        var server=Flashback.getReplayServer();
        UUID next=server==null?null:server.getMetadata().replayIdentifier;
        if(before!=null && !MorphTrack.isDragging()) finish();
        if(java.util.Objects.equals(next,replay)) return;
        finish(); replay=next; selected=null; notice=""; timeline=new MorphTimeline();
        var pending=SAVES.pending(replay);
        if(pending!=null) timeline=pending.copy();
        else if(replay!=null) {
            var file=TakeStore.rootFor(replay).resolve("morph-track.json");
            if(Files.isRegularFile(file)) try {
                var loaded=pdx.dev.fbactors.util.JsonFiles.read(file,MorphTimeline.class,4_000_000);
                if(loaded!=null) { loaded.normalize(); timeline=loaded; }
            } catch(Exception e) { FlashbackActors.LOGGER.warn("Could not load morph track",e); }
        }
    }
    public static void begin() { if(before==null) before=timeline.copy(); }
    public static void finish() {
        if(before==null) return;
        var old=before; var after=timeline.copy(); var owner=replay; before=null;
        if(GSON.toJson(old).equals(GSON.toJson(after))) return;
        UndoStack.pushEdit(() -> restore(owner,old), () -> restore(owner,after)); save();
    }
    public static void edit(Runnable action) {
        finish(); begin();
        try { action.run(); timeline.normalize(); }
        catch (RuntimeException | LinkageError error) {
            timeline=before; before=null;
            throw error;
        }
        finish();
    }
    private static void restore(UUID owner,MorphTimeline value) {
        if(!java.util.Objects.equals(owner,replay)) return;
        timeline=value.copy(); before=null; save();
    }
    public static boolean waitingToSave() { return !SAVES.isEmpty(); }
    private static void save() {
        if(replay!=null) { SAVES.put(replay,timeline.copy()); flush(); }
    }
    private static void flush() {
        retryAt=System.currentTimeMillis()+1000;
        SAVES.flush(TakeStore::rootFor);
    }
    public static void add(UUID actor,String morph,String pose,int start,int end) {
        if(timeline.clips.size()>=256 || actor==null || morph==null) return;
        edit(() -> {
            var c=new MorphTimeline.Clip(); c.actor=actor; c.morph=morph; c.pose=pose;
            c.start=start; c.end=end; timeline.clips.add(c); selected=c.id;
        });
    }
    public static boolean drop(String payload,pdx.dev.fbactors.actor.Actor actor,int tick) {
        if(actor==null) { notice="Choose an actor first."; return false; }
        if(!pdx.dev.fbactors.settings.FbSettings.morphEnabled()) { notice="Enable morphs in Actors > Settings > Actors."; return false; }
        if(actor.take.locked) { notice="Unlock the actor track before adding a morph."; return false; }
        if(timeline.clips.size()>=256) { notice="The morph timeline is full. Remove a clip first."; return false; }
        int start=(int)Math.max(0,Math.min(99_999_999L,(long)actor.take.firstTick()+actor.offset()));
        int end=(int)Math.max(start+1L,Math.min(100_000_000L,(long)actor.take.lastTick()+actor.offset()+1));
        var clip=MorphLibraryPresets.create(payload,actor.take.id,tick,start,end,false,Morph.registry());
        if(clip==null) { notice="This morph could not be added."; return false; }
        edit(() -> { timeline.clips.add(clip); selected=clip.id; });
        Morph.refreshVisible();
        notice=clip.morph+" / "+MorphPoses.label(clip.pose)+" added to "+actor.displayName()+". Move or trim its Morph clip to set the timing.";
        return true;
    }
    public static void duplicate(UUID id) {
        var c=timeline.find(id); if(c==null || timeline.clips.size()>=256) return;
        edit(() -> {
            var copy=c.copy(); copy.id=UUID.randomUUID(); int length=c.end-c.start;
            copy.start=Math.min(100_000_000-length,c.end); copy.end=copy.start+length;
            timeline.clips.add(copy); selected=copy.id;
        });
    }
}
