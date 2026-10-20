package pdx.dev.fbactors.take;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.Test;


class RideSoundNormTest {

    @Test
    void rideNormalizesTickAndBlankType() {
        UUID boat = UUID.randomUUID();
        ActorRide ride = new ActorRide(-5, true, "", boat);
        assertEquals(0, ride.tick());
        assertNull(ride.vehicleType());
        assertEquals(boat, ride.vehicleUuid());
        assertTrue(ActorRide.mount(10, "minecraft:oak_boat", boat).mount());
        assertFalse(ActorRide.dismount(7).mount());
        assertNull(ActorRide.dismount(7).vehicleType());
    }

    @Test
    void soundClampsVolumeAndPitch() {
        ActorSound quiet = new ActorSound(5, "minecraft:block.wood.place",
            0, 64, 0, -2.0f, 5.0f, null);
        assertEquals(0.0f, quiet.volume());
        assertEquals(2.0f, quiet.pitch());
        ActorSound loud = new ActorSound(5, "minecraft:block.wood.place",
            0, 64, 0, 100.0f, Float.NaN, null);
        assertEquals(8.0f, loud.volume());
        assertEquals(1.0f, loud.pitch());
    }
}

