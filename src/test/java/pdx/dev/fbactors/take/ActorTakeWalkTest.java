package pdx.dev.fbactors.take;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.world.entity.Pose;


class ActorTakeWalkTest {

    private static ActorFrame frame(double x, double y, double z) {
        return new ActorFrame(x, y, z, 0.0f, 0.0f, 0.0f,
            (byte) Pose.STANDING.ordinal(),
            ActorFrame.packFlags(false, false, true, false, false));
    }

    private static ActorTake take(int startTick, double... xs) {
        List<ActorFrame> list = new ArrayList<>();
        for (double x : xs) {
            list.add(frame(x, 64.0, 0.0));
        }
        return new ActorTake(UUID.randomUUID(), "Walk", "minecraft:overworld", startTick,
            list, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
            new ArrayList<>());
    }

    private static ActorTake steadyWalk(int startTick, int frames, double step) {
        double[] xs = new double[frames];
        for (int i = 0; i < frames; i++) {
            xs[i] = i * step;
        }
        return take(startTick, xs);
    }

    @Test
    void emptyTakeAnswersRest() {
        ActorTake take = take(100);
        assertEquals(0.0f, take.walkDistAt(100));
        assertEquals(0.0f, take.walkDistOAt(100));
        assertEquals(0.0f, take.walkSpeedAt(100));
        assertEquals(0.0f, take.walkSpeedOldAt(100));
        assertEquals(0.0f, take.walkPositionAt(100));
    }

    @Test
    void singleFrameAnswersRest() {
        ActorTake take = take(100, 5.0);
        assertEquals(0.0f, take.walkDistAt(100));
        assertEquals(0.0f, take.walkDistOAt(101));
        assertEquals(0.0f, take.walkSpeedAt(100));
        assertEquals(0.0f, take.walkPositionAt(100));
    }

    @Test
    void steadyWalkAccumulatesScaledDisplacement() {
        ActorTake take = steadyWalk(100, 10, 1.0);
        assertEquals(0.0f, take.walkDistAt(100), 0.0001f);
        assertEquals(0.6f, take.walkDistAt(101), 0.0001f);
        assertEquals(1.2f, take.walkDistAt(102), 0.0001f);
        assertEquals(0.6f, take.walkDistOAt(102), 0.0001f);
        assertEquals(0.0f, take.walkDistOAt(100), 0.0001f);
        assertEquals(0.0f, take.walkDistOAt(101 - 1), 0.0001f);
    }

    @Test
    void strideLagsOneTickBehindItself() {
        ActorTake take = steadyWalk(50, 10, 0.5);
        for (int tick = 50; tick < 60; tick++) {
            assertEquals(take.walkDistAt(tick - 1), take.walkDistOAt(tick), 0.0001f,
                "walkDistO at " + tick + " must be walkDist one tick earlier");
            assertEquals(take.walkSpeedAt(tick - 1), take.walkSpeedOldAt(tick), 0.0001f,
                "speedOld at " + tick + " must be speed one tick earlier");
        }
    }

    @Test
    void phaseIsPureFunctionOfTickAcrossScrubHistories() {
        ActorTake take = steadyWalk(0, 60, 0.25);


        int[] viewportOrder = {40, 41, 42, 10, 11, 12, 59, 58, 0, 1, 30, 31, 32, 20};
        for (int tick = 0; tick < 60; tick++) {
            float dist = take.walkDistAt(tick);
            float distO = take.walkDistOAt(tick);
            float speed = take.walkSpeedAt(tick);
            float speedOld = take.walkSpeedOldAt(tick);
            float position = take.walkPositionAt(tick);
            for (int visited : viewportOrder) {
                take.walkPositionAt(visited);
                take.walkSpeedAt(visited);
            }
            assertEquals(dist, take.walkDistAt(tick), 0.0f);
            assertEquals(distO, take.walkDistOAt(tick), 0.0f);
            assertEquals(speed, take.walkSpeedAt(tick), 0.0f);
            assertEquals(speedOld, take.walkSpeedOldAt(tick), 0.0f);
            assertEquals(position, take.walkPositionAt(tick), 0.0f);
        }
    }

    @Test
    void identicalTakesAgreeTickForTick() {
        ActorTake first = steadyWalk(10, 40, 0.3);
        ActorTake second = steadyWalk(10, 40, 0.3);
        for (int tick = 10; tick < 50; tick++) {
            assertEquals(first.walkDistAt(tick), second.walkDistAt(tick), 0.0f);
            assertEquals(first.walkSpeedAt(tick), second.walkSpeedAt(tick), 0.0f);
            assertEquals(first.walkPositionAt(tick), second.walkPositionAt(tick), 0.0f);
        }
    }

    @Test
    void smoothingConvergesToFullSwingOnASteadyStride() {


        ActorTake take = steadyWalk(0, 30, 0.3);
        assertEquals(0.0f, take.walkSpeedAt(0), 0.0001f);
        assertEquals(0.4f, take.walkSpeedAt(1), 0.0001f);
        assertEquals(0.64f, take.walkSpeedAt(2), 0.0001f);
        assertEquals(1.0f, take.walkSpeedAt(29), 0.001f);
        float before = take.walkPositionAt(10);
        float after = take.walkPositionAt(20);
        assertTrue(after > before, "phase must advance while walking");
        assertEquals(after - before, 10.0f, 0.5f,
            "at full swing the phase advances about one per tick");
    }

    @Test
    void standingStillDecaysToRest() {
        List<ActorFrame> list = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            list.add(frame(i * 1.0, 64.0, 0.0));
        }
        for (int i = 0; i < 30; i++) {
            list.add(frame(4.0, 64.0, 0.0));
        }
        ActorTake take = new ActorTake(UUID.randomUUID(), "Halt", "minecraft:overworld", 0,
            list, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
            new ArrayList<>());
        float walking = take.walkSpeedAt(4);
        assertTrue(walking > 0.5f, "moving frames must carry swing, was " + walking);
        assertEquals(0.0f, take.walkSpeedAt(34), 0.05f,
            "a long standstill must decay back to rest");
        assertEquals(take.walkDistAt(4), take.walkDistAt(34), 0.0001f,
            "stride must stop accumulating once the body stops");
    }

    @Test
    void verticalMovementNeverSwingsTheLimbs() {
        List<ActorFrame> list = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            list.add(frame(0.0, 64.0 + i * 2.0, 0.0));
        }
        ActorTake take = new ActorTake(UUID.randomUUID(), "Rise", "minecraft:overworld", 0,
            list, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
            new ArrayList<>());
        assertEquals(0.0f, take.walkDistAt(9), 0.0001f);
        assertEquals(0.0f, take.walkSpeedAt(9), 0.0001f);
        assertEquals(0.0f, take.walkPositionAt(9), 0.0001f);
    }

    @Test
    void clampsBothEndsLikeFrameAt() {
        ActorTake take = steadyWalk(100, 10, 1.0);
        assertEquals(0.0f, take.walkDistAt(50), 0.0001f);
        assertEquals(0.0f, take.walkPositionAt(50), 0.0001f);
        assertEquals(take.walkDistAt(109), take.walkDistAt(10_000), 0.0f);
        assertEquals(take.walkPositionAt(109), take.walkPositionAt(10_000), 0.0f);
    }

    @Test
    void cacheInvalidatesWhenFramesGrow() {
        ActorTake take = steadyWalk(0, 5, 1.0);
        assertEquals(2.4f, take.walkDistAt(4), 0.0001f);
        assertEquals(2.4f, take.walkDistAt(100), 0.0001f);
        for (int i = 5; i < 10; i++) {
            take.frames.add(frame(i * 1.0, 64.0, 0.0));
        }
        assertEquals(5.4f, take.walkDistAt(9), 0.0001f);
        assertEquals(5.4f, take.walkDistAt(100), 0.0f);
    }

    @Test
    void explicitInvalidateCoversInPlaceEdits() {
        ActorTake take = steadyWalk(0, 5, 1.0);
        assertEquals(2.4f, take.walkDistAt(4), 0.0001f);
        take.frames.set(4, frame(100.0, 64.0, 0.0));
        take.invalidateWalkCache();
        float moved = take.walkDistAt(4);
        assertTrue(moved > 50.0f, "edited frames must move the stride, was " + moved);
    }
}

