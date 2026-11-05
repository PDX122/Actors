package pdx.dev.fbactors.events;

/** Shared deterministic cue timing and positions for visual events and their audio. */
public final class EventNature {
    private EventNature() {}
    public static int count(EventKind kind) {return kind==EventKind.THUNDERSTORM?8:kind==EventKind.METEOR_SHOWER?6:1;}
    public static double cue(EventClip c,int index) {return c.kind.impact+index*(c.kind==EventKind.THUNDERSTORM?.10:c.kind==EventKind.METEOR_SHOWER?.09:0);}
    public static EventClip.Point point(EventClip c,int index) {
        double x=0,z=0,y=c.kind==EventKind.AIRBURST?170:c.kind==EventKind.CRAWLER?230:0;
        if(c.kind==EventKind.THUNDERSTORM || c.kind==EventKind.METEOR_SHOWER) {
            x=Math.sin(index*2.39996)*index*22;z=Math.cos(index*2.39996)*index*18;
        }
        return c.impact.add(x*c.blast,y*c.blast,z*c.blast);
    }
    public static double showcase(EventKind kind) {return kind.lightning()?kind.impact+.004:kind==EventKind.VOLCANO?.46:kind==EventKind.METEOR_SHOWER?.37:kind==EventKind.AIRBURST?kind.impact+.06:kind.impact*.68;}
}
