package pdx.dev.fbactors.take;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import net.minecraft.world.entity.Pose;


class ClipEditorEdgeTest {

    private static ActorTake take() {
        List<ActorFrame> frames = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            frames.add(new ActorFrame(i, 64, 0, 0, 0, 0,
                (byte) Pose.STANDING.ordinal(), (byte) 0));
        }
        return new ActorTake(UUID.randomUUID(), "T", "minecraft:overworld", 100,
            frames, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
            new ArrayList<>());
    }

    @Test
    void junctionRideAndSoundFireOnceInSecondHalf() {
        ActorTake take = take();
        UUID boat = UUID.randomUUID();
        take.rides.add(ActorRide.mount(102, "minecraft:oak_boat", boat));
        take.rides.add(ActorRide.dismount(103));
        take.sounds.add(new ActorSound(102, "minecraft:block.wood.place",
            0, 64, 0, 1.0f, 1.0f, null));
        take.sounds.add(new ActorSound(103, "minecraft:block.stone.break",
            0, 64, 0, 1.0f, 1.0f, null));
        ActorTake[] halves = ClipEditor.splitAt(take, 2);
        assertEquals(3, halves[0].frames.size());
        assertEquals(3, halves[1].frames.size());
        assertTrue(halves[0].rides.isEmpty());
        assertTrue(halves[0].sounds.isEmpty());
        assertEquals(2, halves[1].rides.size());
        assertEquals(2, halves[1].sounds.size());
        assertTrue(halves[1].hasRideAt(102));
        assertTrue(halves[1].hasSoundAt(102));
    }

    @Test
    void splitAtEitherEndKeepsEveryFrame() {
        ActorTake take = take();
        ActorTake[] atZero = ClipEditor.splitAt(take, 0);
        assertEquals(1, atZero[0].frames.size());
        assertEquals(5, atZero[1].frames.size());
        assertEquals(100, atZero[1].startTick);
        ActorTake[] atEnd = ClipEditor.splitAt(take, 5);
        assertEquals(5, atEnd[0].frames.size());
        assertTrue(atEnd[1].frames.isEmpty());
        assertEquals(105, atEnd[1].startTick);
    }

    @Test
    void fullRangeDeleteLeavesAnEmptyValidTake() {
        ActorTake take = take();
        ActorTake cut = ClipEditor.deleteRange(take, 0, 5);
        assertTrue(cut.frames.isEmpty());
        assertEquals(0, cut.length());
        assertEquals(100, cut.startTick);
        assertEquals("T", cut.name);
    }

    @Test
    void emptyTakeSurvivesEveryOp() {
        ActorTake take = new ActorTake(UUID.randomUUID(), "E", "minecraft:overworld", 0);
        ActorTake[] halves = ClipEditor.splitAt(take, 0);
        assertTrue(halves[0].frames.isEmpty());
        assertTrue(halves[1].frames.isEmpty());
        assertTrue(ClipEditor.deleteRange(take, 0, 5).frames.isEmpty());
        assertTrue(ClipEditor.trimTo(take, 0, 5).frames.isEmpty());
    }
}

