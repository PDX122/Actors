package pdx.dev.fbactors.take;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;


class TakeIOLegacyTest {


    private static final class Legacy {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        final DataOutputStream out = new DataOutputStream(bytes);
        final int version;

        Legacy(int version) throws Exception {
            this.version = version;
            out.writeInt(0x46424154);
            out.writeInt(version);
        }

        Legacy header(UUID id, String name, int startTick, int frames) throws Exception {
            out.writeLong(id.getMostSignificantBits());
            out.writeLong(id.getLeastSignificantBits());
            out.writeUTF(name);
            out.writeUTF("");
            out.writeUTF("minecraft:overworld");
            out.writeInt(startTick);
            out.writeInt(frames);
            for (int i = 0; i < frames; i++) {
                out.writeDouble(i * 0.2);
                out.writeDouble(64.0);
                out.writeDouble(0.0);
                out.writeFloat(90.0f);
                out.writeFloat(0.0f);
                out.writeFloat(90.0f);
                out.writeByte(0);
                out.writeByte(4);
            }
            return this;
        }

        Legacy events() throws Exception {
            out.writeInt(1);
            out.writeInt(100);
            out.writeByte(0);
            out.writeBoolean(false);
            return this;
        }

        Legacy equipment(boolean withSaddle) throws Exception {
            out.writeInt(withSaddle ? 2 : 1);
            out.writeInt(100);
            out.writeByte(0);
            out.writeUTF("minecraft:diamond_sword");
            out.writeInt(1);
            if (withSaddle) {
                out.writeInt(100);
                out.writeByte(6);
                out.writeUTF("minecraft:saddle");
                out.writeInt(1);
            }
            return this;
        }


        Legacy hitsOld(float damage) throws Exception {
            out.writeInt(1);
            out.writeInt(100);
            out.writeFloat(damage);
            out.writeDouble(0.0);
            out.writeDouble(1.0);
            return this;
        }

        Legacy noHits() throws Exception {
            out.writeInt(0);
            return this;
        }

        Legacy v5(int trimIn) throws Exception {
            return v5(trimIn, false);
        }

        Legacy v5(int trimIn, boolean extra) throws Exception {
            out.writeInt(0);
            out.writeInt(trimIn);
            out.writeInt(0);
            out.writeBoolean(true);
            out.writeInt(7);
            if (extra) {
                out.writeInt(0);
            }
            return this;
        }

        Legacy deaths() throws Exception {
            out.writeInt(0);
            return this;
        }

        Legacy blocks() throws Exception {
            out.writeInt(1);
            out.writeInt(100);
            out.writeInt(-14);
            out.writeInt(99);
            out.writeInt(-4);
            out.writeUTF("minecraft:grass_block");
            out.writeUTF("minecraft:air");
            return this;
        }

        Legacy noBlocks() throws Exception {
            out.writeInt(0);
            return this;
        }

        Legacy voices() throws Exception {
            out.writeInt(1);
            out.writeInt(100);
            out.writeInt(2);
            out.writeShort((short) 10);
            out.writeShort((short) -10);
            return this;
        }

        Legacy noVoices() throws Exception {
            out.writeInt(0);
            return this;
        }

        Legacy voiceSettings() throws Exception {
            out.writeBoolean(false);
            out.writeUTF("");
            out.writeBoolean(true);
            out.writeInt(-45);
            return this;
        }

        Legacy vitals() throws Exception {
            out.writeInt(1);
            out.writeInt(100);
            out.writeFloat(20.0f);
            out.writeFloat(0.0f);
            return this;
        }

        Legacy noVitals() throws Exception {
            out.writeInt(0);
            return this;
        }

        Legacy mining() throws Exception {
            out.writeInt(1);
            out.writeInt(100);
            out.writeInt(-14);
            out.writeInt(99);
            out.writeInt(-4);
            out.writeInt(3);
            return this;
        }

        Legacy noMining() throws Exception {
            out.writeInt(0);
            return this;
        }

        Legacy effects() throws Exception {
            out.writeInt(1);
            out.writeInt(100);
            out.writeUTF("minecraft:speed");
            out.writeInt(0);
            out.writeInt(200);
            return this;
        }

        Legacy noEffects() throws Exception {
            out.writeInt(0);
            return this;
        }


        Legacy inputRun(int tick, int extra, int flags) throws Exception {
            out.writeInt(tick);
            out.writeInt(extra);
            out.writeShort(flags);
            out.writeFloat(1.5f);
            out.writeFloat(-0.5f);
            out.writeByte(3);
            return this;
        }

        Path writeTo(Path dir, String fileName) throws Exception {
            out.flush();
            Path file = dir.resolve(fileName);
            Files.write(file, bytes.toByteArray());
            return file;
        }
    }

    @Test
    void v10WithoutReservedLoads(@TempDir Path dir) throws Exception {
        UUID id = UUID.randomUUID();
        Path file = new Legacy(10).header(id, "Old", 1000, 10).events()
            .equipment(false).noHits().v5(0).deaths().noBlocks().noVoices()
            .voiceSettings().writeTo(dir, "a.take");

        assertTrue(TakeIO.isLegacy(file));
        ActorTake take = TakeIO.read(file);
        assertEquals(id, take.id);
        assertEquals("Old", take.name);
        assertEquals(10, take.frames.size());
        assertEquals(1, take.events.size());
        assertEquals(1, take.equipment.size());
        assertTrue(take.inputs.isEmpty());
        assertFalse(take.simulated());
        assertEquals(1.0f, take.getSpeedMultiplier());
        assertFalse(take.locked);
        assertEquals(0, take.speakerColorArgb);
        assertFalse(take.voiceEnabled);
        assertEquals(-45, take.voiceThreshold);
    }

    @Test
    void v10WithReservedLoadsIdentically(@TempDir Path dir) throws Exception {
        UUID id = UUID.randomUUID();
        Path file = new Legacy(10).header(id, "Old", 1000, 10).events()
            .equipment(false).noHits().v5(0, true).deaths().noBlocks().noVoices()
            .voiceSettings().writeTo(dir, "b.take");

        ActorTake take = TakeIO.read(file);
        assertEquals(id, take.id);
        assertEquals(10, take.frames.size());
        assertEquals(7, take.colour);
    }

    @Test
    void legacyHitsTakenOnTrust(@TempDir Path dir) throws Exception {
        UUID id = UUID.randomUUID();
        Path file = new Legacy(10).header(id, "Old", 1000, 10).events()
            .equipment(false).hitsOld(4.0f).v5(0, true).deaths().noBlocks().noVoices()
            .voiceSettings().writeTo(dir, "c.take");

        ActorTake take = TakeIO.read(file);
        assertEquals(1, take.hits.size());
        ActorHit hit = take.hits.get(0);
        assertEquals(4.0f, hit.damage());
        assertEquals(1.0f, hit.attackStrength());
        assertNull(hit.target());
        assertNull(hit.impulse());
        assertEquals(ActorHit.CRIT_NONE, hit.crit());
        assertEquals(16.0f, take.healthAt(1000), 0.001f);
    }

    @Test
    void unreadableHitsDroppedTakeKept(@TempDir Path dir) throws Exception {


        Legacy legacy = new Legacy(10).header(UUID.randomUUID(), "Old", 1000, 10).events()
            .equipment(false);
        legacy.out.writeInt(1);
        legacy.out.writeInt(1_000_000_000);
        legacy.out.writeFloat(4.0f);
        legacy.out.writeDouble(0.0);
        legacy.out.writeDouble(1.0);
        Path file = legacy.v5(0, true).deaths().noBlocks().noVoices()
            .voiceSettings().writeTo(dir, "d.take");

        ActorTake take = TakeIO.read(file);
        assertTrue(take.hits.isEmpty());
        assertEquals(10, take.frames.size());
    }

    @Test
    void saddleSlotDropped(@TempDir Path dir) throws Exception {
        UUID id = UUID.randomUUID();
        Path file = new Legacy(10).header(id, "Old", 1000, 10).events()
            .equipment(true).noHits().v5(0, true).deaths().noBlocks().noVoices()
            .voiceSettings().writeTo(dir, "e.take");

        ActorTake take = TakeIO.read(file);
        assertEquals(1, take.equipment.size());
        assertEquals(0, take.equipment.get(0).slot());
    }

    @Test
    void negativeTrimRepaired(@TempDir Path dir) throws Exception {
        UUID id = UUID.randomUUID();
        Path file = new Legacy(10).header(id, "Old", 1000, 10).events()
            .equipment(false).noHits().v5(-10, true).deaths().noBlocks().noVoices()
            .voiceSettings().writeTo(dir, "f.take");

        ActorTake take = TakeIO.read(file);
        assertEquals(0, take.trimIn);
    }

    @Test
    void v8EndsAfterBlocks(@TempDir Path dir) throws Exception {
        UUID id = UUID.randomUUID();
        Path file = new Legacy(8).header(id, "Ancient", 500, 5).events()
            .equipment(false).noHits().v5(0, true).deaths().blocks()
            .writeTo(dir, "g.take");

        ActorTake take = TakeIO.read(file);
        assertEquals(1, take.blocks.size());
        ActorBlockEdit block = take.blocks.get(0);
        assertEquals(-14, block.x());
        assertEquals("minecraft:grass_block", block.before());
        assertEquals("minecraft:air", block.after());
        assertTrue(take.voices.isEmpty());
    }

    @Test
    void v12InputsExpandedAndMapped(@TempDir Path dir) throws Exception {
        UUID id = UUID.randomUUID();
        Legacy legacy = new Legacy(12).header(id, "Fighter", 0, 10).events()
            .equipment(false).noHits().v5(0, true).deaths().noBlocks().noVoices()
            .voiceSettings().noVitals().noMining().noEffects();
        legacy.out.writeInt(30);
        Path file = legacy.inputRun(0, 2, 64).inputRun(5, 0, 256).inputRun(6, 0, 32)
            .writeTo(dir, "h.take");

        ActorTake take = TakeIO.read(file);
        assertTrue(take.simulated());

        ActorInput first = take.inputs.get(0);
        assertEquals(0, first.tick());
        assertTrue(first.sprint());
        assertEquals(1.0f, first.forwardAxis());
        assertEquals(1.5f, first.yawDelta());
        assertEquals(-0.5f, first.pitchDelta());
        assertEquals(3, first.selectedSlot());
        assertEquals(2, take.inputs.get(2).tick());

        ActorInput attack = take.inputs.get(3);
        assertEquals(5, attack.tick());
        assertTrue(attack.attackHeld());
        assertFalse(attack.sprint());
        assertEquals(0.0f, attack.forwardAxis());

        assertTrue(take.inputs.get(4).sneak());
        assertEquals(5, take.inputs.size());
    }

    @Test
    void v11SectionsRead(@TempDir Path dir) throws Exception {
        UUID id = UUID.randomUUID();
        Path file = new Legacy(11).header(id, "Mid", 0, 10).events()
            .equipment(false).noHits().v5(0, true).deaths().noBlocks().noVoices()
            .voiceSettings().vitals().mining().effects()
            .writeTo(dir, "i.take");

        ActorTake take = TakeIO.read(file);
        assertEquals(20.0f, take.vitals.get(0).health());
        assertEquals(3, take.mining.get(0).stage());
        assertEquals("minecraft:speed", take.effects.get(0).effectId());
    }

    @Test
    void v7RefusedHonestly(@TempDir Path dir) throws Exception {
        Path file = new Legacy(7).header(UUID.randomUUID(), "TooOld", 0, 5).events()
            .equipment(false).noHits().v5(0).deaths().noBlocks()
            .writeTo(dir, "j.take");

        assertThrows(java.io.IOException.class, () -> TakeIO.read(file));
    }

    @Test
    void currentFileNotLegacy(@TempDir Path dir) throws Exception {
        java.util.List<ActorFrame> frames = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            frames.add(new ActorFrame(i, 64.0, 0.0, 0.0f, 0.0f, 0.0f,
                (byte) net.minecraft.world.entity.Pose.STANDING.ordinal(),
                ActorFrame.packFlags(false, false, true, false, false)));
        }
        ActorTake take = new ActorTake(UUID.randomUUID(), "New", "minecraft:overworld", 0,
            frames, new java.util.ArrayList<>(), new java.util.ArrayList<>(),
            new java.util.ArrayList<>(), new java.util.ArrayList<>());
        Path file = dir.resolve("new.take");
        TakeIO.write(file, take);
        assertFalse(TakeIO.isLegacy(file));
        assertEquals(5, TakeIO.read(file).frames.size());
    }

    @Test
    void v26RoundTripKeepsHitsImpulseAndCrit(@TempDir Path dir) throws Exception {


        java.util.List<ActorFrame> frames = new java.util.ArrayList<>();
        for (int i = 0; i < 5; i++) {
            frames.add(new ActorFrame(i, 64.0, 0.0, 0.0f, 0.0f, 0.0f,
                (byte) net.minecraft.world.entity.Pose.STANDING.ordinal(),
                ActorFrame.packFlags(false, false, true, false, false)));
        }
        java.util.List<ActorHit> hits = new java.util.ArrayList<>();
        hits.add(new ActorHit(2, 4.0f, 1.0, 0.0, 1.0f, null,
            new net.minecraft.world.phys.Vec3(0.5, 0.1, 0.0), ActorHit.CRIT_HIT));
        hits.add(new ActorHit(3, 6.0f, 0.0, 1.0, 0.8f, null, null, ActorHit.CRIT_MAGIC));
        ActorTake take = new ActorTake(UUID.randomUUID(), "Fighter", "minecraft:overworld", 0,
            frames, new java.util.ArrayList<>(), new java.util.ArrayList<>(),
            hits, new java.util.ArrayList<>());
        Path file = dir.resolve("v26.take");
        TakeIO.write(file, take);

        ActorTake back = TakeIO.read(file);
        assertEquals(2, back.hits.size());
        assertNotNull(back.hitAt(2).impulse());
        assertTrue(back.hitAt(2).isCritical());
        assertNull(back.hitAt(3).impulse());
        assertTrue(back.hitAt(3).isMagicCritical());
        assertEquals(10.0f, back.healthAt(3), 0.001f);
    }

    @Test
    void oldTakeLoadsWithNoHostDamage(@TempDir Path dir) throws Exception {


        UUID id = UUID.randomUUID();
        Path file = new Legacy(10).header(id, "Old", 1000, 10).events()
            .equipment(false).hitsOld(4.0f).v5(0, true).deaths().noBlocks().noVoices()
            .voiceSettings().writeTo(dir, "old.take");

        ActorTake take = TakeIO.read(file);
        assertEquals(1, take.hits.size());
        assertTrue(HostStore.load(dir.resolve("host-hits")).isEmpty());
    }
}

