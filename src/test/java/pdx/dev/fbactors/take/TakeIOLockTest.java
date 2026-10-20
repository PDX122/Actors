package pdx.dev.fbactors.take;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.minecraft.world.entity.Pose;


class TakeIOLockTest {

    private static ActorTake take() {
        List<ActorFrame> frames = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            frames.add(new ActorFrame(i, 64, 0, 0, 0, 0,
                (byte) Pose.STANDING.ordinal(), (byte) 0));
        }
        return new ActorTake(UUID.randomUUID(), "T", "minecraft:overworld", 0,
            frames, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
            new ArrayList<>());
    }

    @Test
    void lockRoundTrips(@TempDir Path dir) throws Exception {
        ActorTake take = take();
        take.locked = true;
        Path file = dir.resolve("locked.take");
        TakeIO.write(file, take);
        assertTrue(TakeIO.read(file).locked);

        take.locked = false;
        TakeIO.write(file, take);
        assertFalse(TakeIO.read(file).locked);
    }

    @Test
    void clipEditsPreserveLock() {
        ActorTake take = take();
        take.locked = true;
        ActorTake[] halves = ClipEditor.splitAt(take, 2);
        assertTrue(halves[0].locked);
        assertTrue(halves[1].locked);
        assertTrue(ClipEditor.deleteRange(take, 1, 3).locked);
        assertTrue(take.copyAs(UUID.randomUUID(), "C").locked);
    }
}

