package pdx.dev.fbactors.events;

/** Keep a live predicted playhead from briefly crossing a camera cut backwards. */
public final class EventFrameClock {
    private EventFrameClock() {}
    public static double sample(double before,int previousServerTick,double now,int serverTick,boolean paused,boolean exporting) {
        if(!paused && !exporting && Double.isFinite(before) && Double.isFinite(now)
            && serverTick>=previousServerTick && now<before && before-now<=1.5)return before;
        return now;
    }
}
