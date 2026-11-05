package pdx.dev.fbactors.events;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.moulberry.flashback.Flashback;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;
import pdx.dev.fbactors.FlashbackActors;
import pdx.dev.fbactors.undo.UndoStack;
import pdx.dev.fbactors.util.AtomicSave;

public final class Events {
    private static final Gson GSON=new GsonBuilder().setPrettyPrinting().create();
    private static volatile UUID replay;
    private static volatile int presentationEnd;
    private static EventTimeline timeline=new EventTimeline(), before;
    private static boolean dirty;
    public static UUID selected;
    public static String status="";
    private Events() {}
    private static Path file() { return Flashback.getDataDirectory().resolve("fbactors").resolve(replay.toString()).resolve("events.json"); }
    public static EventTimeline timeline() {
        var current=FlashbackActors.loadedReplayIdOrNull();
        if(!Objects.equals(current,replay)) {
            finish();flush();replay=current;timeline=new EventTimeline();selected=null;dirty=false;status="";EventPlacement.cancel();
            if(replay!=null) try {
                if(Files.isRegularFile(file()) && Files.size(file())<=1_000_000) {
                    var loaded=GSON.fromJson(Files.readString(file()),EventTimeline.class);if(loaded!=null)timeline=loaded;
                }
            } catch(Exception error) { status="Could not read the events library for this replay."; }
            timeline.normalize();if(timeline.upgradeTiming())dirty=true;
        }
        presentationEnd=timeline.end(0);
        return timeline;
    }
    public static int playbackEnd(int footageEnd) {
        return Objects.equals(replay,FlashbackActors.loadedReplayIdOrNull())?Math.max(footageEnd,presentationEnd):footageEnd;
    }
    public static void begin() { timeline();if(before==null)before=timeline.copy(); }
    public static void changed() { timeline.normalize();presentationEnd=timeline.end(0);dirty=true; }
    public static void finish() {
        if(before==null)return;
        var old=before;var after=timeline.copy();before=null;var owner=replay;
        if(GSON.toJson(old).equals(GSON.toJson(after)))return;
        UndoStack.pushEdit(()->restore(owner,old),()->restore(owner,after));dirty=true;flush();
    }
    private static void restore(UUID owner,EventTimeline value) {
        if(!Objects.equals(owner,FlashbackActors.loadedReplayIdOrNull()))return;
        before=null;timeline=value.copy();presentationEnd=timeline.end(0);dirty=true;flush();
    }
    public static void edit(Runnable action) { finish();begin();action.run();changed();finish(); }
    public static EventClip selected() { return timeline().find(selected); }
    public static void add(EventClip.Point launch,EventClip.Point impact,int tick) {
        add(EventKind.ICBM,launch,impact,tick);
    }
    public static void add(EventKind kind,EventClip.Point launch,EventClip.Point impact,int tick) {
        if(kind==null || !kind.available()) {status="This event is no longer available.";return;}
        var track=timeline();
        if(replay==null || track.clips.size()>=EventTimeline.MAX_CLIPS) {status="Remove an event before adding another.";return;}
        var clip=new EventClip();clip.kind=kind;clip.name=kind.label;clip.launch=launch;clip.impact=impact;
        var level=net.minecraft.client.Minecraft.getInstance().level;
        if(level!=null)clip.dimension=level.dimension().toString();
        if(!clip.valid()) {status="Choose two different blocks for launch and impact.";return;}
        clip.start=Math.max(0,Math.min(EventClip.MAX_TICK-20,tick));
        clip.end=Math.min(EventClip.MAX_TICK,clip.start+kind.duration);clip.timingVersion=1;
        edit(()->{timeline.clips.add(clip);selected=clip.id;});status=kind.label+" placed. Play the replay or scrub its event row to preview.";
    }
    public static void remove(EventClip clip) { if(clip!=null && !clip.locked)edit(()->timeline.clips.remove(clip)); }
    public static void duplicate(EventClip clip) {
        if(clip==null || clip.locked || timeline().clips.size()>=EventTimeline.MAX_CLIPS)return;
        var copy=clip.copy();copy.id=UUID.randomUUID();copy.name=clip.name+" copy";
        edit(()->{timeline.clips.add(copy);selected=copy.id;});
    }
    public static void flush() {
        if(!dirty || replay==null)return;
        var file=file();
        if(AtomicSave.writeJson(file.getParent(),file.getFileName().toString(),w->GSON.toJson(timeline,w)))dirty=false;
        else status="Could not save events. Check folder permissions.";
    }
    public static void close() { EventCam.stop();EventAudio.stop();finish();flush();presentationEnd=0;EventPlacement.cancel();EventRenderer.close(); }
}
