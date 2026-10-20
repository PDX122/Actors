package pdx.dev.fbactors.take;

import java.util.ArrayList;
import java.util.List;


public final class TimelineOps {

    private TimelineOps() {
    }


    public static int razorRelative(int startTick, int offsetTicks, int replayTick, int frameCount) {
        int relative = replayTick - offsetTicks - startTick;
        if (relative <= 0 || relative >= frameCount) {
            return -1;
        }
        return relative;
    }


    public static boolean canRazor(int startTick, int offsetTicks, int replayTick, int frameCount) {
        return razorRelative(startTick, offsetTicks, replayTick, frameCount) >= 0;
    }


    public static int snapStart(int desiredStart, int[] candidates,
                                float pixelsPerTick, float snapPixels) {
        if (candidates == null || candidates.length == 0 || pixelsPerTick <= 0.0f) {
            return desiredStart;
        }
        int best = desiredStart;
        long bestDist = Long.MAX_VALUE;
        for (int candidate : candidates) {
            long dist = Math.abs((long) candidate - desiredStart);
            if (dist < bestDist) {
                bestDist = dist;
                best = candidate;
            }
        }
        if ((float) bestDist * pixelsPerTick <= snapPixels) {
            return best;
        }
        return desiredStart;
    }


    public static int rippleShift(int clipFirst, int deletedStart, int deletedEnd) {
        if (clipFirst >= deletedEnd + 1 || (clipFirst >= deletedStart && deletedStart <= deletedEnd)) {
            if (clipFirst > deletedEnd) {
                return -(deletedEnd - deletedStart + 1);
            }
        }
        return 0;
    }


    public static List<CameraKeyframe> copyKeys(CameraTrack track, int fromTick, int toTick) {
        List<CameraKeyframe> out = new ArrayList<>();
        if (track == null) {
            return out;
        }
        int from = Math.min(fromTick, toTick);
        int to = Math.max(fromTick, toTick);
        for (CameraKeyframe key : track.keyframes) {
            if (key.tick() >= from && key.tick() <= to) {
                out.add(key);
            }
        }
        return out;
    }


    public static List<CameraKeyframe> pasteKeys(List<CameraKeyframe> keys, int destTick) {
        List<CameraKeyframe> out = new ArrayList<>();
        if (keys == null || keys.isEmpty()) {
            return out;
        }
        int earliest = Integer.MAX_VALUE;
        for (CameraKeyframe key : keys) {
            earliest = Math.min(earliest, key.tick());
        }
        int delta = destTick - earliest;
        for (CameraKeyframe key : keys) {
            out.add(key.withTick(Math.max(0, key.tick() + delta)));
        }
        out.sort(java.util.Comparator.comparingInt(CameraKeyframe::tick));
        return out;
    }


    public static boolean isAudible(boolean enabled, boolean anySolo, boolean solo) {
        if (!enabled) {
            return false;
        }
        return !anySolo || solo;
    }


    public static boolean canEdit(boolean locked) {
        return !locked;
    }
}

