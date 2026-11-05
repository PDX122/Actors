package pdx.dev.fbactors.events;

import java.util.Map;

/** Optional offscreen impact; retired map IDs resolve to the world blast. */
public final class EventEnding {
    public static final String[] NAMES={"World blast","Cut to black"};
    public static final int[] IDS={0,3};
    private EventEnding() {}
    public static int value(EventClip c) {return c.ending==3?3:0;}
    public static String label(EventClip c) {return NAMES[value(c)==3?1:0];}
    public static double start(EventClip c) {return c.kind==EventKind.ICBM?.635:Math.max(0,c.kind.impact-.05);}
    public static boolean active(EventClip c,double progress) {return c.eventCam && value(c)==3 && progress>=start(c);}
    public static void apply(Map<String,Float> values,EventClip c,double progress) {
        values.put("Ending",c.eventCam?(float)value(c):0f);values.put("EndingStart",(float)start(c));
        values.put("ClipSeconds",(c.end-c.start)/20f);values.put("CinematicBars",c.eventCam && c.cinematicBars?1f:0f);
    }
}
