package pdx.dev.fbactors.take;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.minecraft.world.entity.Pose;


class TakeIORidesTest {

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
    void ridesRoundTrip(@TempDir Path dir) throws Exception {
        ActorTake take = take();
        UUID boat = UUID.randomUUID();
        take.rides.add(ActorRide.mount(101, "minecraft:oak_boat", boat));
        take.rides.add(ActorRide.dismount(103));
        Path file = dir.resolve("rides.take");
        TakeIO.write(file, take);
        ActorTake back = TakeIO.read(file);
        assertEquals(2, back.rides.size());
        assertTrue(back.rides.get(0).mount());
        assertEquals("minecraft:oak_boat", back.rides.get(0).vehicleType());
        assertEquals(boat, back.rides.get(0).vehicleUuid());
        assertFalse(back.rides.get(1).mount());
    }

    @Test
    void emptyRidesRoundTrip(@TempDir Path dir) throws Exception {
        ActorTake take = take();
        Path file = dir.resolve("norides.take");
        TakeIO.write(file, take);
        assertTrue(TakeIO.read(file).rides.isEmpty());
    }

    @Test
    void ridesAtReadsThroughRecordedTick() {
        ActorTake take = take();
        take.rides.add(ActorRide.mount(102, "minecraft:oak_boat", UUID.randomUUID()));

        assertEquals(1, take.ridesAt(102).size());
        assertTrue(take.hasRideAt(102));
        assertTrue(take.ridesAt(101).isEmpty());
        assertFalse(take.hasRideAt(101));
    }

    @Test
    void clipEditsPreserveRides() {
        ActorTake take = take();
        take.rides.add(ActorRide.mount(101, "minecraft:oak_boat", UUID.randomUUID()));
        take.rides.add(ActorRide.dismount(103));
        ActorTake[] halves = ClipEditor.splitAt(take, 2);
        assertEquals(1, halves[0].rides.size());
        assertEquals(1, halves[1].rides.size());
        assertEquals(2, ClipEditor.deleteRange(take, 0, 1).rides.size());
        assertEquals(2, take.copyAs(UUID.randomUUID(), "C").rides.size());
    }
}

