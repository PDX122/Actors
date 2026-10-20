package pdx.dev.fbactors.take;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.world.entity.Pose;


class ActorTakeWalkClampTest {

    private static ActorTake steadyWalk(int startTick, int frames, double step) {
        List<ActorFrame> list = new ArrayList<>();
        for (int i = 0; i < frames; i++) {
            list.add(new ActorFrame(i * step, 64.0, 0.0, 0.0f, 0.0f, 0.0f,
                (byte) Pose.STANDING.ordinal(),
                ActorFrame.packFlags(false, false, true, false, false)));
        }
        return new ActorTake(UUID.randomUUID(), "Walk", "minecraft:overworld", startTick,
            list, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
            new ArrayList<>());
    }

    @Test
    void everyReaderAnswersRestBeforeTheFirstFrame() {
        ActorTake take = steadyWalk(100, 10, 1.0);
        assertEquals(0.0f, take.walkDistAt(50), 0.0001f);
        assertEquals(0.0f, take.walkDistOAt(50), 0.0001f);
        assertEquals(0.0f, take.walkSpeedAt(50), 0.0001f);
        assertEquals(0.0f, take.walkSpeedOldAt(50), 0.0001f);
        assertEquals(0.0f, take.walkPositionAt(50), 0.0001f);
    }

    @Test
    void everyReaderHoldsTheLastPhasePastTheEnd() {
        ActorTake take = steadyWalk(100, 10, 1.0);
        assertEquals(take.walkDistAt(109), take.walkDistAt(10_000), 0.0f);
        assertEquals(take.walkDistOAt(109), take.walkDistOAt(10_000), 0.0f);
        assertEquals(take.walkSpeedAt(109), take.walkSpeedAt(10_000), 0.0f);
        assertEquals(take.walkSpeedOldAt(109), take.walkSpeedOldAt(10_000), 0.0f);
        assertEquals(take.walkPositionAt(109), take.walkPositionAt(10_000), 0.0f);
    }
}

