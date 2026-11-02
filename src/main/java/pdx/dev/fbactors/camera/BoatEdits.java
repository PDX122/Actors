package pdx.dev.fbactors.camera;

import java.util.UUID;
import pdx.dev.fbactors.undo.UndoStack;

public final class BoatEdits {
    private static BoatStudioState owner;
    private static BoatTimeline before;
    public static UUID selected;
    private BoatEdits() {}
    public static void begin() {
        var state=BoatStudioState.get();
        if(owner!=state) finish();
        if(before==null) { owner=state; before=state.timeline.copy(); }
    }
    public static void finish() {
        if(before==null) return;
        var state=owner; var old=before; var after=state.timeline.copy(); before=null;
        if(old.enabled==after.enabled && old.locked==after.locked && sameClips(old,after)) return;
        UndoStack.pushEdit(() -> restore(state,old), () -> restore(state,after));
        BoatStudioState.flush(true);
    }
    private static boolean sameClips(BoatTimeline a,BoatTimeline b) {
        if(a.clips.size()!=b.clips.size()) return false;
        for(int i=0;i<a.clips.size();i++) {
            var x=a.clips.get(i); var y=b.clips.get(i);
            if(!x.id.equals(y.id) || x.start!=y.start || x.end!=y.end || x.enabled!=y.enabled
                || !java.util.Objects.equals(x.target,y.target) || !java.util.Objects.equals(x.rival,y.rival)
                || !java.util.Objects.equals(x.settings,y.settings)) return false;
        }
        return true;
    }
    private static void restore(BoatStudioState state,BoatTimeline timeline) {
        if(BoatStudioState.get()!=state) return;
        state.timeline=timeline.copy(); before=null;
        BoatStudioState.changed(); BoatStudioState.flush(true); BoatCam.reset();
    }
    public static void edit(Runnable action) {
        finish(); begin(); action.run(); BoatStudioState.changed(); BoatCam.reset(); finish();
    }
}
