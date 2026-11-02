package pdx.dev.fbactors.take;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ActorVehicleFrameTest {
    private ActorTake take() {
        var take = new ActorTake(UUID.randomUUID(), "Boat", "minecraft:overworld", 100);
        for (int i = 0; i < 6; i++) take.frames.add(new ActorFrame(i, 64, 0, 0, 0, 0, (byte) 0, (byte) 0));
        return take;
    }

    @Test void posesRoundTripWithoutLosingVehicleHeightOrRotation(@TempDir Path dir) throws Exception {
        var take = take();
        var vehicle = new ActorVehicleFrame(102, UUID.randomUUID(), "minecraft:oak_boat", 1.25, 63.625, -9.5, 137.5f, -2f);
        take.vehicles.add(vehicle);
        var path = dir.resolve("boat.take");
        TakeIO.write(path, take);
        var loaded = TakeIO.read(path);
        assertEquals(vehicle, loaded.vehicleAt(102));
        assertNull(loaded.vehicleAt(101));
        assertNull(loaded.vehicleAt(103));
        loaded.offsetTicks = 20;
        assertEquals(vehicle, loaded.vehicleAt(82));
        assertNull(loaded.vehicleAt(102));
    }

    @Test void splittingMountedClipKeepsItsVehicleAndSnapshot() {
        var take = take();
        UUID id = UUID.randomUUID();
        String nbt = pdx.dev.fbactors.actor.ActorEntityIdentity.write(id);
        take.spawns.add(new ActorSpawn(100, "minecraft:oak_boat", 0, 63, 0, 0, 0, nbt));
        for (int i = 0; i < 6; i++) take.vehicles.add(new ActorVehicleFrame(100 + i, id, "minecraft:oak_boat", i, 63.5, 0, i * 15, 0));
        var halves = ClipEditor.splitAt(take, 3);
        assertEquals(3, halves[0].vehicles.size());
        assertEquals(3, halves[1].vehicles.size());
        assertFalse(halves[1].spawns.isEmpty());
        assertEquals(id, pdx.dev.fbactors.actor.ActorEntityIdentity.read(halves[1].spawns.getFirst().nbt()));
        var copy = take.copyAs(UUID.randomUUID(), "Copy");
        copy.vehicles.clear();
        assertEquals(6, take.vehicles.size());
    }

    @Test void version33StillLoadsWithNoVehiclePoses(@TempDir Path dir) throws Exception {
        var path = dir.resolve("old.take");
        TakeIO.write(path, take());
        byte[] bytes = Files.readAllBytes(path);
        ByteBuffer.wrap(bytes).putInt(4, 33);
        Files.write(path, java.util.Arrays.copyOf(bytes, bytes.length - 12));
        assertTrue(TakeIO.read(path).vehicles.isEmpty());
    }

    @Test void paddlesAndIceMomentumSurviveSavingAndClipEdits(@TempDir Path dir) throws Exception {
        var take = take();
        UUID boat = UUID.randomUUID();
        var pose = new ActorVehicleFrame(103, boat, "minecraft:oak_boat", 4, 63.5, 0, 90, 0)
            .withMotion(1.4, 0, 0.2, 1, 1.3926991f, 0, 0, true, false);
        take.vehicles.add(pose);

        take.momentum.add(new ActorMomentum(103, 0.78, -0.0784, 0.12));
        var path = dir.resolve("ice.take");
        TakeIO.write(path, take);
        var loaded = TakeIO.read(path);
        assertEquals(pose, loaded.vehicleAt(103));
        assertEquals(take.momentum.getFirst(), loaded.momentumAt(103));
        assertEquals(1.1963496f, loaded.vehicleAt(103).rowing(0, 0.5f), 0.00001f);
        assertEquals(0, loaded.vehicleAt(103).rowing(1, 0.5f));
        var halves = ClipEditor.splitAt(loaded, 2);
        assertEquals(pose.velocityX(), halves[1].vehicles.getFirst().velocityX());
        assertEquals(pose.left(), halves[1].vehicles.getFirst().left());
        assertEquals(0.78, halves[1].momentum.getFirst().x());
        loaded.copyAs(UUID.randomUUID(), "Copy").momentum.clear();
        assertEquals(1, loaded.momentum.size());
    }

    @Test void rowingPoseDoesNotAdvanceWhenReadAgainOrAfterASeek() {
        var pose = new ActorVehicleFrame(102, UUID.randomUUID(), "minecraft:oak_boat", 0, 63, 0, 0, 0)
            .withMotion(0.7, 0, 0, 2, 2.3926991f, 4, 4.3926992f, true, true);
        float halfStroke = pose.rowing(0, 0.5f);
        pose.rowing(0, 1);
        assertEquals(halfStroke, pose.rowing(0, 0.5f));
        assertEquals(halfStroke, pose.retimed(500).translated(3, 0, -2).rowing(0, 0.5f));
        assertEquals(0.7, pose.retimed(500).velocityX());
    }

    @Test void version34LoadsPosesWithUnknownMomentum(@TempDir Path dir) throws Exception {
        var take = take();
        take.vehicles.add(new ActorVehicleFrame(101, UUID.randomUUID(), "minecraft:oak_boat", 1, 63, 0, 90, 0));
        var path = dir.resolve("v34.take");
        TakeIO.write(path, take);
        byte[] bytes = Files.readAllBytes(path);
        ByteBuffer.wrap(bytes).putInt(4, 34);
        Files.write(path, java.util.Arrays.copyOf(bytes, bytes.length - 50));
        var loaded = TakeIO.read(path);
        assertEquals(take.vehicles, loaded.vehicles);
        assertFalse(loaded.vehicles.getFirst().hasMomentum());
        assertTrue(loaded.momentum.isEmpty());
    }
}
