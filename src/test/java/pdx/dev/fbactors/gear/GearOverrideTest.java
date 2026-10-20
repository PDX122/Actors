package pdx.dev.fbactors.gear;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;


class GearOverrideTest {

    @Test
    void nbtNormalisesBlanksToNull() {
        GearOverride blank = new GearOverride(GearSlot.HEAD,
            "minecraft:diamond_helmet", 1, Map.of(), "  ", true);
        assertNull(blank.nbt());
        assertEquals(new GearOverride(GearSlot.HEAD,
            "minecraft:diamond_helmet", 1, Map.of(), null, true), blank);
    }

    @Test
    void emptySlotCarriesNoNbt() {
        GearOverride empty = new GearOverride(GearSlot.HEAD, "", 1, Map.of(),
            "{Damage:5}", true);
        assertTrue(empty.removesItem());
        assertNull(empty.nbt());
    }

    @Test
    void withNbtIsIdentityStable() {
        GearOverride base = GearOverride.of(GearSlot.MAINHAND, "minecraft:bow", 1);
        assertSame(base, base.withNbt(null));
        assertSame(base, base.withNbt(" "));
        GearOverride snbt = base.withNbt("{Damage:10}");
        assertEquals("{Damage:10}", snbt.nbt());
        assertSame(snbt, snbt.withNbt("{Damage:10}"));

        assertEquals(base, snbt.withNbt(null));
    }

    @Test
    void snbtSurvivesStoreRoundTrip(@TempDir Path dir) {
        UUID id = UUID.randomUUID();
        GearStore store = new GearStore();
        store.set(id, GearOverride.ofSnbt(GearSlot.CHEST,
            "minecraft:diamond_chestplate", 1, "{Damage:100,display:{Name:'{\"text\":\"Tank\"}'}}"));
        assertTrue(store.saveAll(dir) >= 1);

        GearStore back = new GearStore();
        back.load(dir);
        GearOverride read = back.overridesFor(id).get(GearSlot.CHEST);
        assertNotNull(read);
        assertEquals("minecraft:diamond_chestplate", read.itemId());
        assertEquals("{Damage:100,display:{Name:'{\"text\":\"Tank\"}'}}", read.nbt());
    }

    @Test
    void oldFilesWithoutNbtStillLoad(@TempDir Path dir) throws Exception {
        UUID id = UUID.randomUUID();
        GearStore store = new GearStore();
        store.set(id, GearOverride.of(GearSlot.HEAD,
            "minecraft:iron_helmet", 1, Map.of("minecraft:protection", 2)));
        store.saveAll(dir);


        GearStore back = new GearStore();
        back.load(dir);
        GearOverride read = back.overridesFor(id).get(GearSlot.HEAD);
        assertNotNull(read);
        assertNull(read.nbt());
        assertEquals(2, read.enchantments().get("minecraft:protection"));
    }
}

