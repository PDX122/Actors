package pdx.dev.fbactors.actor;

public final class PlaybackContinuity {
    private int previous = Integer.MIN_VALUE;
    public boolean restoreAt(int tick) {
        boolean restore = previous == Integer.MIN_VALUE || tick < previous || (long) tick - previous > 1;
        previous = tick;
        return restore;
    }
    public void reset() { previous = Integer.MIN_VALUE; }
}
