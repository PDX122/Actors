package pdx.dev.fbactors.take;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.minecraft.world.entity.Pose;

class TakeIOUsesSpawnsTest {

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
    void usesRoundTrip(@TempDir Path dir) throws Exception {
        ActorTake take = take();
        UUID target = UUID.randomUUID();
        take.uses.add(ActorUse.block(101, 1, 2, 3, false));
        take.uses.add(ActorUse.entity(102, target, true));
        take.uses.add(ActorUse.air(103, false));
        Path file = dir.resolve("uses.take");
        TakeIO.write(file, take);
        ActorTake back = TakeIO.read(file);
        assertEquals(3, back.uses.size());
        assertEquals(ActorUse.Kind.BLOCK, back.uses.get(0).kind());
        assertEquals(1, back.uses.get(0).x());
        assertEquals(ActorUse.Kind.ENTITY, back.uses.get(1).kind());
        assertEquals(target, back.uses.get(1).target());
        assertTrue(back.uses.get(1).offHand());
        assertEquals(ActorUse.Kind.AIR, back.uses.get(2).kind());
    }

    @Test
    void spawnsRoundTrip(@TempDir Path dir) throws Exception {
        ActorTake take = take();
        take.spawns.add(new ActorSpawn(101, "minecraft:oak_boat", 1.5, 64.0, -2.5, 90.0f, 0.0f, null));
        take.spawns.add(new ActorSpawn(103, "minecraft:painting", 2.0, 65.0, -3.0, 180.0f, 0.0f, "{Motive:\"minecraft:aztec\"}"));
        Path file = dir.resolve("spawns.take");
        TakeIO.write(file, take);
        ActorTake back = TakeIO.read(file);
        assertEquals(2, back.spawns.size());
        assertEquals("minecraft:oak_boat", back.spawns.get(0).entityId());
        assertNull(back.spawns.get(0).nbt());
        assertEquals("{Motive:\"minecraft:aztec\"}", back.spawns.get(1).nbt());
    }

    @Test
    void emptySectionsRoundTrip(@TempDir Path dir) throws Exception {
        ActorTake take = take();
        Path file = dir.resolve("empty.take");
        TakeIO.write(file, take);
        ActorTake back = TakeIO.read(file);
        assertTrue(back.uses.isEmpty());
        assertTrue(back.spawns.isEmpty());
    }

    @Test
    void usesAtReadsThroughRecordedTick() {
        ActorTake take = take();
        take.uses.add(ActorUse.block(102, 1, 2, 3, false));
        assertEquals(1, take.usesAt(102).size());
        assertTrue(take.hasUseAt(102));
        assertTrue(take.usesAt(101).isEmpty());
        assertFalse(take.hasUseAt(101));
    }

    @Test
    void spawnsAtReadsThroughRecordedTick() {
        ActorTake take = take();
        take.spawns.add(new ActorSpawn(102, "minecraft:oak_boat", 0, 64, 0, 0, 0, null));
        assertEquals(1, take.spawnsAt(102).size());
        assertTrue(take.hasSpawnAt(102));
        assertTrue(take.spawnsAt(101).isEmpty());
    }

    @Test
    void clipEditsPreserveUsesAndSpawns() {
        ActorTake take = take();
        take.uses.add(ActorUse.block(101, 1, 2, 3, false));
        take.uses.add(ActorUse.air(103, true));
        take.spawns.add(new ActorSpawn(101, "minecraft:oak_boat", 0, 64, 0, 0, 0, null));
        take.spawns.add(new ActorSpawn(103, "minecraft:painting", 0, 65, 0, 0, 0, null));
        ActorTake[] halves = ClipEditor.splitAt(take, 2);
        assertEquals(1, halves[0].uses.size());
        assertEquals(1, halves[1].uses.size());
        assertEquals(1, halves[0].spawns.size());
        assertEquals(1, halves[1].spawns.size());
        assertEquals(2, ClipEditor.deleteRange(take, 0, 1).uses.size());
        assertEquals(2, take.copyAs(UUID.randomUUID(), "C").spawns.size());
    }

    @Test
    void spawnTrackIds() {
        assertTrue(pdx.dev.fbactors.record.TakeRecorder.isSpawnTrackId("minecraft:oak_boat"));
        assertTrue(pdx.dev.fbactors.record.TakeRecorder.isSpawnTrackId("minecraft:cherry_chest_boat"));
        assertTrue(pdx.dev.fbactors.record.TakeRecorder.isSpawnTrackId("minecraft:minecart"));
        assertTrue(pdx.dev.fbactors.record.TakeRecorder.isSpawnTrackId("minecraft:chest_minecart"));
        assertTrue(pdx.dev.fbactors.record.TakeRecorder.isSpawnTrackId("minecraft:armor_stand"));
        assertTrue(pdx.dev.fbactors.record.TakeRecorder.isSpawnTrackId("minecraft:painting"));
        assertTrue(pdx.dev.fbactors.record.TakeRecorder.isSpawnTrackId("minecraft:item_frame"));
        assertTrue(pdx.dev.fbactors.record.TakeRecorder.isSpawnTrackId("minecraft:glow_item_frame"));
        assertTrue(pdx.dev.fbactors.record.TakeRecorder.isSpawnTrackId("minecraft:end_crystal"));
        assertFalse(pdx.dev.fbactors.record.TakeRecorder.isSpawnTrackId("minecraft:arrow"));
        assertFalse(pdx.dev.fbactors.record.TakeRecorder.isSpawnTrackId("minecraft:player"));
        assertFalse(pdx.dev.fbactors.record.TakeRecorder.isSpawnTrackId(null));
    }
}
