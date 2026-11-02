package pdx.dev.fbactors.take;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.minecraft.world.entity.Pose;


class TakeBundleRidesSoundsTest {

    @Test
    void bundleRoundTripCarriesRidesAndSounds(@TempDir Path dir) throws Exception {
        List<ActorFrame> frames = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            frames.add(new ActorFrame(i, 64.0, 0.0, 0.0f, 0.0f, 0.0f,
                (byte) Pose.STANDING.ordinal(),
                ActorFrame.packFlags(false, false, true, false, false)));
        }
        ActorTake take = new ActorTake(UUID.randomUUID(), "Rider", "minecraft:overworld", 100,
            frames, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
            new ArrayList<>());
        UUID boat = UUID.randomUUID();
        take.rides.add(ActorRide.mount(101, "minecraft:oak_boat", boat));
        take.rides.add(ActorRide.dismount(103));
        take.sounds.add(new ActorSound(102, "minecraft:block.wood.place",
            1.0, 64.0, 3.0, 0.8f, 1.2f, "BLOCKS"));

        Path bundle = dir.resolve("rider.fbactors.zip");
        TakeBundle.exportBundle(bundle, UUID.randomUUID(), "show", List.of(take),
            new com.google.gson.JsonObject(), new com.google.gson.JsonObject());

        TakeBundle.Bundle parsed = TakeBundle.readBundle(bundle);
        assertEquals(1, parsed.takes().size());
        ActorTake back = parsed.takes().get(0);
        assertEquals(2, back.rides.size());
        assertEquals(boat, back.rides.get(0).vehicleUuid());
        assertEquals(1, back.sounds.size());
        assertEquals("minecraft:block.wood.place", back.sounds.get(0).soundId());
        assertTrue(back.hasRideAt(101));
        assertTrue(back.hasSoundAt(102));
    }
}

