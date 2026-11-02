package pdx.dev.fbactors.take;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;

class MotionSmoothingTest {
    private static ActorFrame frame(double x, float yaw) {
        return new ActorFrame(x, 64, 0, yaw, 0, yaw, (byte) 0, (byte) ActorFrame.FLAG_ON_GROUND);
    }

    private static ActorTake take(double... xs) {
        var take = new ActorTake(UUID.randomUUID(), "Motion", "minecraft:overworld", 100);
        for (double x : xs) take.frames.add(frame(x, 0));
        return take;
    }

    @Test void reducesJitterWithoutChangingSourceOrEndpoints() {
        var take = take(0, 1, 2.4, 3, 4);
        var before = List.copyOf(take.frames);
        var result = MotionSmoothing.smooth(take, 2, 1, MotionSmoothing.Mode.ALL);
        assertTrue(result.get(2).x() < 2.4);
        assertTrue(result.get(2).x() > 2);
        assertEquals(before, take.frames);
        assertEquals(before.getFirst(), result.getFirst());
        assertEquals(before.getLast(), result.getLast());
    }

    @Test void preservesSteadyMotion() {
        var take = take(0, 1, 2, 3, 4, 5, 6);
        assertEquals(take.frames, MotionSmoothing.smooth(take, 3, 1, MotionSmoothing.Mode.ALL));
    }

    @Test void lookTakesShortestRouteAcrossYawSeam() {
        var take = take(0, 1, 2);
        take.frames.set(0, frame(0, 179));
        take.frames.set(1, frame(1, -179));
        take.frames.set(2, frame(2, 179));
        var result = MotionSmoothing.smooth(take, 1, 1, MotionSmoothing.Mode.LOOK);
        assertEquals(-180, result.get(1).yRot(), 0.001);
        assertEquals(-180, result.get(1).yHeadRot(), 0.001);
        assertEquals(1, result.get(1).x());
    }

    @Test void neverBlendsAcrossTeleports() {
        var take = take(0, 1, 2, 100, 101, 102);
        assertEquals(take.frames, MotionSmoothing.smooth(take, 10, 1, MotionSmoothing.Mode.ALL));
    }

    @Test void preservesJumpHeightAndFlags() {
        var take = take(0, 1.3, 2);
        take.frames.set(1, new ActorFrame(1.3, 65.2, 0, 20, 10, 20, (byte) 0,
            (byte) (ActorFrame.FLAG_ON_GROUND | ActorFrame.FLAG_SWINGING)));
        var result = MotionSmoothing.smooth(take, 1, 1, MotionSmoothing.Mode.ALL);
        assertEquals(65.2, result.get(1).y());
        assertEquals(take.frames.get(1).flags(), result.get(1).flags());
    }

    @Test void stateChangesSplitSmoothingSegments() {
        var take = take(0, 1.4, 2, 3);
        take.frames.set(2, new ActorFrame(2, 64, 0, 0, 0, 0, (byte) 1, (byte) 0));
        assertEquals(take.frames, MotionSmoothing.smooth(take, 10, 1, MotionSmoothing.Mode.ALL));
    }

    @Test void preservesContactFramesAfterRetiming() {
        var take = take(0, 1.4, 2);
        take.offsetTicks = 20;
        take.hits.add(new ActorHit(81, 1, 1, 0, 1, null));
        assertEquals(take.frames, MotionSmoothing.smooth(take, 2, 1, MotionSmoothing.Mode.ALL));
        take.hits.clear();
        take.uses.add(ActorUse.block(101, 1, 64, 0, false));
        assertEquals(take.frames, MotionSmoothing.smooth(take, 2, 1, MotionSmoothing.Mode.ALL));
    }

    @Test void zeroStrengthAndShortTakesAreUnchanged() {
        for (var take : List.of(take(), take(1), take(1, 2), take(1, 2.4, 3))) {
            assertEquals(take.frames, MotionSmoothing.smooth(take, 10, 0, MotionSmoothing.Mode.ALL));
        }
    }

    @Test void rejectsInvalidParameters() {
        var take = take(0, 1, 2);
        assertThrows(IllegalArgumentException.class, () -> MotionSmoothing.smooth(take, 0, 1, MotionSmoothing.Mode.ALL));
        assertThrows(IllegalArgumentException.class, () -> MotionSmoothing.smooth(take, 11, 1, MotionSmoothing.Mode.ALL));
        for (float strength : new float[] {-1, 2, Float.NaN, Float.POSITIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> MotionSmoothing.smooth(take, 1, strength, MotionSmoothing.Mode.ALL));
        }
    }

    @Test void pathModeKeepsLookAndReplacementInvalidatesLimbCache() {
        var take = take(0, 1.4, 2);
        take.frames.set(1, frame(1.4, 50));
        var original = new ArrayList<>(take.frames);
        take.walkDistAt(101);
        var result = MotionSmoothing.smooth(take, 1, 1, MotionSmoothing.Mode.PATH);
        assertEquals(50, result.get(1).yRot());
        MotionSmoothing.replaceFrames(take, result);
        assertEquals(result.get(1).x() * 0.6, take.walkDistAt(101), 0.0001);
        MotionSmoothing.replaceFrames(take, original);
        assertEquals(original, take.frames);
        assertEquals(1.4 * 0.6, take.walkDistAt(101), 0.0001);
        MotionSmoothing.replaceFrames(take, take.frames);
        assertEquals(original, take.frames);
    }

    @Test void smoothedFramesSurviveSaveAndLoad(@TempDir Path dir) throws Exception {
        var take = take(0, 1.4, 2);
        var frames = MotionSmoothing.smooth(take, 1, 0.5f, MotionSmoothing.Mode.ALL);
        MotionSmoothing.replaceFrames(take, frames);
        Path file = dir.resolve("motion.take");
        TakeIO.write(file, take);
        assertEquals(frames, TakeIO.read(file).frames);
    }
}
