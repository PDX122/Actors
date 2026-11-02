package pdx.dev.fbactors.take;

import org.junit.jupiter.api.Test;
import pdx.dev.fbactors.actor.TerrainEdits;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ActorRidingPoseTest {
    @Test void movingBoatIsNotPulledBackToTheBoardingPosition() {

        var rider = new ActorFrame(10.6499, -58.4125, -37.4074, 90, 10, 85, (byte) 0, (byte) 0);
        var boat = new ActorVehicleFrame(392, UUID.randomUUID(), "minecraft:oak_boat", 10.530, -58, -26.443, 90, 0);
        var seated = ActorRidingPose.resolve(rider, boat, 0, -0.4125, 0);
        assertEquals(boat.x(), seated.x());
        assertEquals(boat.y(), seated.y() + 0.4125, 0.000001);
        assertEquals(boat.z(), seated.z());
        assertEquals(rider.yRot(), seated.yRot());
        assertEquals(rider.xRot(), seated.xRot());

        var edits = new TerrainEdits();
        edits.record(new TerrainEdits.Entry("minecraft:overworld", 10, -59, -27, "minecraft:ice", "minecraft:air"));
        double boatX = seated.x(), boatY = seated.y() + 0.4125, boatZ = seated.z();
        assertTrue(edits.affects("minecraft:overworld", boatX - 0.7, boatY - 0.6, boatZ - 0.7,
            boatX + 0.7, boatY + 0.6, boatZ + 0.7));
        assertFalse(edits.affects("minecraft:overworld", rider.x() - 0.7, boatY - 0.6, rider.z() - 0.7,
            rider.x() + 0.7, boatY + 0.6, rider.z() + 0.7));
    }

    @Test void seatOffsetsPreserveVehiclePositionAndRiderPose() {
        var rider = new ActorFrame(0, 0, 0, 30, 15, 50, (byte) 2, (byte) 3);
        var boat = new ActorVehicleFrame(1, UUID.randomUUID(), "minecraft:oak_chest_boat", 8, 64, -4, 110, 0);
        var seated = ActorRidingPose.resolve(rider, boat, 0.25, -0.4125, -0.17);
        assertEquals(8.25, seated.x());
        assertEquals(-4.17, seated.z());
        assertEquals(rider.pose(), seated.pose());
        assertEquals(rider.flags(), seated.flags());
    }

    @Test void olderTakesWithoutVehicleFramesKeepTheirExistingFallback() {
        var rider = new ActorFrame(1, 64, 3, 90, 0, 90, (byte) 0, (byte) 0);
        assertSame(rider, ActorRidingPose.resolve(rider, null, 0, -0.4125, 0));
    }
}
