package pdx.dev.fbactors.take;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import net.minecraft.world.entity.Pose;

import pdx.dev.fbactors.gear.GearOverride;
import pdx.dev.fbactors.gear.GearSlot;
import pdx.dev.fbactors.gear.GearStore;


class TakeBundleTest {

    private static ActorTake take(int startTick, int frames) {
        List<ActorFrame> list = new ArrayList<>();
        for (int i = 0; i < frames; i++) {
            list.add(new ActorFrame(i, 64.0, 0.0, 0.0f, 0.0f, 0.0f,
                (byte) Pose.STANDING.ordinal(),
                ActorFrame.packFlags(false, false, true, false, false)));
        }
        return new ActorTake(UUID.randomUUID(), "Test", "minecraft:overworld", startTick,
            list, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
            new ArrayList<>());
    }

    @Test
    void roundTripCarriesTakesGearAndEmotes(@TempDir Path dir) throws Exception {
        UUID replayId = UUID.randomUUID();
        ActorTake first = take(1000, 10);
        ActorTake second = take(2000, 10);

        GearStore gear = new GearStore();
        gear.set(first.id, GearOverride.of(GearSlot.HEAD,
            "minecraft:diamond_helmet", 1));
        com.google.gson.JsonObject gearActors = new com.google.gson.JsonObject();
        com.google.gson.JsonObject slice = gear.exportActor(first.id);
        assertNotNull(slice);
        gearActors.add(first.id.toString(), slice);


        com.google.gson.JsonObject emoteTracks = new com.google.gson.JsonObject();
        com.google.gson.JsonArray clips = new com.google.gson.JsonArray();
        com.google.gson.JsonObject clip = new com.google.gson.JsonObject();
        clip.addProperty("start", 2);
        clip.addProperty("duration", 20);
        clip.addProperty("emote", "wave");
        clips.add(clip);
        emoteTracks.add(second.id.toString(), clips);

        Path bundle = dir.resolve("siege.fbactors.zip");
        TakeBundle.Exported exported = TakeBundle.exportBundle(bundle, replayId, "siege",
            List.of(first, second), gearActors, emoteTracks);
        assertEquals(2, exported.takes());

        TakeBundle.Bundle parsed = TakeBundle.readBundle(bundle);
        assertEquals(replayId, parsed.replayId());
        assertEquals("siege", parsed.replayName());
        assertEquals(2, parsed.takes().size());
        assertTrue(parsed.gearActors().has(first.id.toString()));
        assertFalse(parsed.gearActors().has(second.id.toString()));
        assertTrue(parsed.emoteTracks().has(second.id.toString()));


        GearStore back = new GearStore();
        int actors = 0;
        for (Map.Entry<String, com.google.gson.JsonElement> entry
                : parsed.gearActors().entrySet()) {
            if (back.importActor(entry.getKey(), entry.getValue())) {
                actors++;
            }
        }
        assertEquals(1, actors);
        assertEquals("minecraft:diamond_helmet", back.overridesFor(
            first.id).get(GearSlot.HEAD).itemId());


        assertEquals(1, parsed.emoteTracks()
            .getAsJsonArray(second.id.toString()).size());
        assertEquals("wave", parsed.emoteTracks()
            .getAsJsonArray(second.id.toString()).get(0)
            .getAsJsonObject().get("emote").getAsString());
    }

    @Test
    void corruptBundleFailsBeforeInstallingAnything(@TempDir Path dir) throws Exception {
        Path bundle = dir.resolve("bad.fbactors.zip");
        Files.write(bundle, new byte[] {1, 2, 3, 4});
        assertThrows(java.io.IOException.class, () -> TakeBundle.readBundle(bundle));
    }

    @Test
    void findBundleForMatchesBasename(@TempDir Path dir) throws Exception {
        Path replay = dir.resolve("siege.zip");
        Files.write(replay, new byte[] {0});
        assertNull(TakeBundle.findBundleFor(replay));
        Path bundle = dir.resolve("siege.fbactors.zip");
        Files.write(bundle, new byte[] {0});
        assertEquals(bundle, TakeBundle.findBundleFor(replay));
    }

    @Test
    void roundTripCarriesMorphSelections(@TempDir Path dir) throws Exception {
        UUID replayId = UUID.randomUUID();
        ActorTake morphed = take(1000, 10);

        pdx.dev.fbactors.morph.MorphStore morphs = new pdx.dev.fbactors.morph.MorphStore();
        morphs.set(morphed.id, new pdx.dev.fbactors.morph.MorphSelection(
            morphed.id, "creeper", 1.5f, 1.0f, false));
        com.google.gson.JsonObject morphActors = new com.google.gson.JsonObject();
        com.google.gson.JsonObject slice = morphs.exportSelection(morphed.id);
        assertNotNull(slice);
        assertEquals("creeper", slice.get("morph").getAsString());
        morphActors.add(morphed.id.toString(), slice);

        Path bundle = dir.resolve("morphs.fbactors.zip");
        TakeBundle.exportBundle(bundle, replayId, "siege", List.of(morphed),
            new com.google.gson.JsonObject(), new com.google.gson.JsonObject(), morphActors);

        TakeBundle.Bundle parsed = TakeBundle.readBundle(bundle);
        assertTrue(parsed.morphActors().has(morphed.id.toString()));

        pdx.dev.fbactors.morph.MorphStore back = new pdx.dev.fbactors.morph.MorphStore();
        int installed = 0;
        for (Map.Entry<String, com.google.gson.JsonElement> entry
                : parsed.morphActors().entrySet()) {
            if (back.importSelection(entry.getKey(), entry.getValue())) {
                installed++;
            }
        }
        assertEquals(1, installed);
        assertEquals("creeper", back.selectionFor(morphed.id).morphId());
        assertEquals(1.5f, back.selectionFor(morphed.id).scale());
    }

    @Test
    void bundleWithoutMorphsStillReadsAsEmpty(@TempDir Path dir) throws Exception {


        UUID replayId = UUID.randomUUID();
        ActorTake first = take(1000, 10);
        Path bundle = dir.resolve("legacy.fbactors.zip");
        TakeBundle.exportBundle(bundle, replayId, "siege", List.of(first),
            new com.google.gson.JsonObject(), new com.google.gson.JsonObject(),
            new com.google.gson.JsonObject());
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(bundle.toFile())) {
            assertNotNull(zip.getEntry("morphs.json"));
        }

        Path v1 = dir.resolve("v1.fbactors.zip");
        try (java.util.zip.ZipInputStream in =
                 new java.util.zip.ZipInputStream(Files.newInputStream(bundle));
             java.util.zip.ZipOutputStream out =
                 new java.util.zip.ZipOutputStream(Files.newOutputStream(v1))) {
            java.util.zip.ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                if ("morphs.json".equals(entry.getName())) {
                    continue;
                }
                out.putNextEntry(new java.util.zip.ZipEntry(entry.getName()));
                out.write(in.readAllBytes());
                out.closeEntry();
            }
        }
        TakeBundle.Bundle parsed = TakeBundle.readBundle(v1);
        assertEquals(1, parsed.takes().size());
        assertTrue(parsed.morphActors().entrySet().isEmpty());
    }
}

