package pdx.dev.fbactors.take;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.minecraft.world.entity.Pose;

class ActorInputUseTest {

    private static ActorFrame frame(double x) {
        return new ActorFrame(x, 64.0, 0.0, 0.0f, 0.0f, 0.0f,
            (byte) Pose.STANDING.ordinal(),
            ActorFrame.packFlags(false, false, true, false, false));
    }

    @Test
    void offhandFlagRoundTripsThroughPack() {
        ActorInput main = new ActorInput(10, 0f, 0f, false, false, false,
            0f, 0f, (byte) 0, true, false, 1f, false, false);
        ActorInput off = new ActorInput(10, 0f, 0f, false, false, false,
            0f, 0f, (byte) 0, true, false, 1f, false, true);

        assertEquals(0, main.packFlags() & ActorInput.USE_OFFHAND);
        assertNotEquals(0, off.packFlags() & ActorInput.USE_OFFHAND);

        ActorInput backMain = ActorInput.unpack(10, main.packFlags(),
            0f, 0f, 0f, 0f, (byte) 0, 1f);
        ActorInput backOff = ActorInput.unpack(10, off.packFlags(),
            0f, 0f, 0f, 0f, (byte) 0, 1f);

        assertFalse(backMain.useOffhand());
        assertTrue(backOff.useHeld());
        assertTrue(backOff.useOffhand());
    }

    @Test
    void offhandIgnoredWhenNotUsing() {
        ActorInput idle = new ActorInput(10, 0f, 0f, false, false, false,
            0f, 0f, (byte) 0, false, false, 1f, false, false);

        ActorInput back = ActorInput.unpack(10,
            idle.packFlags() | ActorInput.USE_OFFHAND,
            0f, 0f, 0f, 0f, (byte) 0, 1f);
        assertFalse(back.useHeld());
        assertFalse(back.useOffhand());
    }

    @Test
    void handChangeBreaksRun() {
        ActorInput main = new ActorInput(10, 0f, 0f, false, false, false,
            0f, 0f, (byte) 0, true, false, 1f, false, false);
        ActorInput off = new ActorInput(11, 0f, 0f, false, false, false,
            0f, 0f, (byte) 0, true, false, 1f, false, true);
        assertFalse(main.samePresses(off));
    }

    @Test
    void takeIoPreservesHand(@TempDir Path dir) throws Exception {
        List<ActorFrame> frames = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            frames.add(frame(i));
        }
        ActorTake take = new ActorTake(UUID.randomUUID(), "Scope", "minecraft:overworld", 0,
            frames, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
            new ArrayList<>());
        take.inputs.add(new ActorInput(0, 0f, 0f, false, false, false,
            0f, 0f, (byte) 0, true, false, 1f, false, true));
        take.inputs.add(new ActorInput(1, 0f, 0f, false, false, false,
            0f, 0f, (byte) 0, false, false, 1f, false, false));

        Path file = dir.resolve("scope.take");
        TakeIO.write(file, take);
        ActorTake back = TakeIO.read(file);

        assertEquals(2, back.inputs.size());
        assertTrue(back.inputs.get(0).useHeld());
        assertTrue(back.inputs.get(0).useOffhand());
        assertFalse(back.inputs.get(1).useHeld());
    }
}

