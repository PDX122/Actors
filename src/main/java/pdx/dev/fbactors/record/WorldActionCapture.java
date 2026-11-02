package pdx.dev.fbactors.record;

import java.util.ArrayDeque;

public final class WorldActionCapture {
    private record Anchor(int x, int y, int z, int tick) {}
    private static final int LIFETIME = 100;
    private static final int MAX_ANCHORS = 512;
    private final ArrayDeque<Anchor> anchors = new ArrayDeque<>();
    public void clear() { anchors.clear(); }
    public void note(int x, int y, int z, int tick) {
        prune(tick);
        anchors.addLast(new Anchor(x,y,z,tick));
        while (anchors.size() > MAX_ANCHORS) anchors.removeFirst();
    }
    public boolean contains(int x, int y, int z, int tick) {
        prune(tick);
        for (var anchor : anchors) {
            long distance = Math.abs((long)x-anchor.x) + Math.abs((long)y-anchor.y) + Math.abs((long)z-anchor.z);
            if (distance <= 2) return true;
        }
        return false;
    }
    private void prune(int tick) {
        while (!anchors.isEmpty() && (tick - (long)anchors.peekFirst().tick > LIFETIME || tick < anchors.peekFirst().tick)) anchors.removeFirst();
    }
}
