package pdx.dev.fbactors.take;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;


class TransformBakeTest {

    private static ActorTake takeOf(double x, double y, double z, float yaw) {
        List<ActorFrame> frames = new ArrayList<>();
        frames.add(new ActorFrame(x, y, z, yaw, 0.0f, yaw, (byte) 0, (byte) 0));
        ActorTake take = new ActorTake(UUID.randomUUID(), "Mover", "minecraft:overworld", 0,
            frames, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        return take;
    }

    @Test
    void translationMovesFrames() {
        ActorTake take = takeOf(10.0, 64.0, 20.0, 0.0f);
        TransformBake.apply(take, 1.5, 2.0, -3.0, 0.0, 0.0, 0.0);
        ActorFrame frame = take.frames.get(0);
        assertEquals(11.5, frame.x(), 0.0001);
        assertEquals(66.0, frame.y(), 0.0001);
        assertEquals(17.0, frame.z(), 0.0001);
        assertEquals(0.0f, frame.yRot(), 0.0001f);
    }

    @Test
    void rotationMovesFramesAroundPivot() {
        ActorTake take = takeOf(1.0, 64.0, 0.0, 0.0f);
        TransformBake.apply(take, 0.0, 0.0, 0.0, 90.0, 0.0, 0.0);
        ActorFrame frame = take.frames.get(0);
        assertEquals(0.0, frame.x(), 0.0001);
        assertEquals(1.0, frame.z(), 0.0001);
        assertEquals(90.0f, frame.yRot(), 0.0001f);
        assertEquals(90.0f, frame.yHeadRot(), 0.0001f);
    }

    @Test
    void inverseRestoresOriginal() {
        ActorTake take = takeOf(10.0, 64.0, 20.0, 30.0f);
        take.blocks.add(new ActorBlockEdit(0, 11, 65, 21, "a", "b"));
        take.sounds.add(new ActorSound(0, "minecraft:block.stone.break", 10.0, 64.0, 20.0,
            1.0f, 1.0f, "block"));
        TransformBake.apply(take, 3.0, -1.0, 2.0, 0.0, 10.0, 20.0);
        TransformBake.apply(take, -3.0, 1.0, -2.0, -0.0, 10.0, 20.0);
        ActorFrame frame = take.frames.get(0);
        assertEquals(10.0, frame.x(), 0.0001);
        assertEquals(64.0, frame.y(), 0.0001);
        assertEquals(20.0, frame.z(), 0.0001);
        assertEquals(30.0f, frame.yRot(), 0.0001f);
        assertEquals(11, take.blocks.get(0).x());
        assertEquals(65, take.blocks.get(0).y());
        assertEquals(21, take.blocks.get(0).z());
        assertEquals(10.0, take.sounds.get(0).x(), 0.0001);
    }

    @Test
    void nullTakeIsNoOp() {
        TransformBake.apply(null, 1.0, 1.0, 1.0, 10.0, 0.0, 0.0);
    }

    @Test
    void wrapYawStaysInRange() {
        assertEquals(-90.0f, TransformBake.wrapYaw(270.0), 0.0001f);
        assertEquals(0.0f, TransformBake.wrapYaw(360.0), 0.0001f);
        assertEquals(179.0f, TransformBake.wrapYaw(179.0), 0.0001f);
    }
}
