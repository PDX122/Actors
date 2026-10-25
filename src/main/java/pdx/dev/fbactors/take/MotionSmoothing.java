package pdx.dev.fbactors.take;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;

public final class MotionSmoothing {
    public enum Mode { LOOK, PATH, ALL }

    public static final int MAX_RADIUS = 10;
    private static final double TELEPORT_DISTANCE_SQUARED = 16.0;

    private MotionSmoothing() {}

    public static List<ActorFrame> smooth(ActorTake take, int radius, float strength, Mode mode) {
        if (radius < 1 || radius > MAX_RADIUS || !Float.isFinite(strength)
            || strength < 0 || strength > 1 || mode == null) {
            throw new IllegalArgumentException("Radius must be 1..10 and strength 0..1; mode is required");
        }
        List<ActorFrame> source = take.frames;
        List<ActorFrame> result = new ArrayList<>(source);
        if (strength == 0) return result;
        int count = source.size();
        boolean[] pinned = new boolean[count];
        for (ActorHit hit : take.hits) pin(pinned, (long) hit.tick() + take.offsetTicks - take.startTick);
        for (ActorBlockEdit block : take.blocks) pin(pinned, (long) block.tick() - take.startTick);
        for (ActorUse use : take.uses) pin(pinned, (long) use.tick() - take.startTick);
        for (ActorRide ride : take.rides) pin(pinned, (long) ride.tick() - take.startTick);
        for (ActorEvent event : take.events) pin(pinned, (long) event.tick() - take.startTick);
        for (ActorShot shot : take.shots) pin(pinned, shot.tick());
        for (ActorContainerInteraction container : take.containers) pin(pinned, (long) container.tick() - take.startTick);
        for (ActorSpawn spawn : take.spawns) pin(pinned, (long) spawn.tick() - take.startTick);
        for (ActorDeath death : take.deaths) pin(pinned, (long) death.tick() + take.offsetTicks - take.startTick);

        int start = 0;
        for (int end = 1; end <= count; end++) {
            if (end < count && !cut(source.get(end - 1), source.get(end))) continue;
            for (int i = start + 1; i < end - 1; i++) {
                if (pinned[i]) continue;
                int window = Math.min(radius, Math.min(i - start, end - 1 - i));
                ActorFrame center = source.get(i);
                double x = 0, z = 0, yaw = 0, pitch = 0, head = 0, weights = 0;
                for (int j = i - window; j <= i + window; j++) {
                    ActorFrame f = source.get(j);
                    double weight = window + 1 - Math.abs(j - i);
                    x += (f.x() - center.x()) * weight;
                    z += (f.z() - center.z()) * weight;
                    yaw += Mth.wrapDegrees(f.yRot() - center.yRot()) * weight;
                    pitch += (f.xRot() - center.xRot()) * weight;
                    head += Mth.wrapDegrees(f.yHeadRot() - center.yHeadRot()) * weight;
                    weights += weight;
                }
                double blend = strength / weights;
                boolean path = mode != Mode.LOOK;
                boolean look = mode != Mode.PATH;
                result.set(i, new ActorFrame(
                    path ? center.x() + x * blend : center.x(), center.y(),
                    path ? center.z() + z * blend : center.z(),
                    look ? center.yRot() + (float) (yaw * blend) : center.yRot(),
                    look ? center.xRot() + (float) (pitch * blend) : center.xRot(),
                    look ? center.yHeadRot() + (float) (head * blend) : center.yHeadRot(),
                    center.pose(), center.flags()));
            }
            start = end;
        }
        return result;
    }

    private static void pin(boolean[] pinned, long index) {
        if (index >= 0 && index < pinned.length) pinned[(int) index] = true;
    }

    private static boolean cut(ActorFrame a, ActorFrame b) {
        if (!finite(a) || !finite(b)) return true;
        double dx = b.x() - a.x(), dy = b.y() - a.y(), dz = b.z() - a.z();
        int state = ActorFrame.FLAG_ON_GROUND | ActorFrame.FLAG_SNEAKING | ActorFrame.FLAG_FALL_FLYING;
        return a.pose() != b.pose() || ((a.flags() ^ b.flags()) & state) != 0
            || dx * dx + dy * dy + dz * dz > TELEPORT_DISTANCE_SQUARED;
    }

    private static boolean finite(ActorFrame frame) {
        return Double.isFinite(frame.x()) && Double.isFinite(frame.y()) && Double.isFinite(frame.z())
            && Float.isFinite(frame.yRot()) && Float.isFinite(frame.xRot()) && Float.isFinite(frame.yHeadRot());
    }

    public static void replaceFrames(ActorTake take, List<ActorFrame> frames) {
        List<ActorFrame> snapshot = new ArrayList<>(frames);
        take.frames.clear();
        take.frames.addAll(snapshot);
        take.invalidateWalkCache();
    }
}
