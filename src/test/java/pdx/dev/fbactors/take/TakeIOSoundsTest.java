package pdx.dev.fbactors.take;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.minecraft.world.entity.Pose;


class TakeIOSoundsTest {

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
    void soundsRoundTrip(@TempDir Path dir) throws Exception {
        ActorTake take = take();
        take.sounds.add(new ActorSound(101, "minecraft:block.wood.place",
            1.0, 64.0, 3.0, 0.8f, 1.2f, "BLOCKS"));
        take.sounds.add(new ActorSound(103, "minecraft:block.stone.break",
            2.0, 65.0, 4.0, 1.0f, 1.0f, null));
        Path file = dir.resolve("sounds.take");
        TakeIO.write(file, take);
        ActorTake back = TakeIO.read(file);
        assertEquals(2, back.sounds.size());
        ActorSound first = back.sounds.get(0);
        assertEquals(101, first.tick());
        assertEquals("minecraft:block.wood.place", first.soundId());
        assertEquals(1.0, first.x());
        assertEquals(64.0, first.y());
        assertEquals(3.0, first.z());
        assertEquals(0.8f, first.volume());
        assertEquals(1.2f, first.pitch());
        assertEquals("BLOCKS", first.source());
        ActorSound second = back.sounds.get(1);
        assertEquals(103, second.tick());
        assertEquals("minecraft:block.stone.break", second.soundId());
        assertEquals(2.0, second.x());
        assertEquals(65.0, second.y());
        assertEquals(4.0, second.z());
        assertEquals(1.0f, second.volume());
        assertEquals(1.0f, second.pitch());
        assertNull(second.source());
    }

    @Test
    void emptySoundsRoundTrip(@TempDir Path dir) throws Exception {
        ActorTake take = take();
        Path file = dir.resolve("nosounds.take");
        TakeIO.write(file, take);
        assertTrue(TakeIO.read(file).sounds.isEmpty());
    }

    @Test
    void soundsAtReadsThroughRecordedTick() {
        ActorTake take = take();
        take.sounds.add(new ActorSound(102, "minecraft:block.wood.place",
            0, 64, 0, 1.0f, 1.0f, null));

        assertEquals(1, take.soundsAt(102).size());
        assertTrue(take.hasSoundAt(102));
        assertTrue(take.soundsAt(101).isEmpty());
        assertFalse(take.hasSoundAt(101));
    }

    @Test
    void clipEditsPreserveSounds() {
        ActorTake take = take();
        take.sounds.add(new ActorSound(101, "minecraft:block.wood.place",
            0, 64, 0, 1.0f, 1.0f, null));
        take.sounds.add(new ActorSound(103, "minecraft:block.stone.break",
            0, 64, 0, 1.0f, 1.0f, null));
        ActorTake[] halves = ClipEditor.splitAt(take, 2);
        assertEquals(1, halves[0].sounds.size());
        assertEquals(1, halves[1].sounds.size());
        assertEquals(2, ClipEditor.deleteRange(take, 0, 1).sounds.size());
        assertEquals(2, take.copyAs(UUID.randomUUID(), "C").sounds.size());
    }

    @Test
    void compactConstructorNormalizes() {
        ActorSound sound = new ActorSound(-5, "", Double.NaN, 64.0, 0.0,
            Float.POSITIVE_INFINITY, 1.0f, "");
        assertEquals(0, sound.tick());
        assertNull(sound.soundId());
        assertEquals(0.0, sound.x());
        assertEquals(0.0, sound.y());
        assertEquals(0.0, sound.z());


        assertEquals(1.0f, sound.volume());
        assertNull(sound.source());
    }
}

