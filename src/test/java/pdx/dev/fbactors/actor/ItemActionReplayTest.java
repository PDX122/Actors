package pdx.dev.fbactors.actor;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ItemActionReplayTest {
    @Test void placedEntitiesHaveOnePlaybackOwner() {
        for (String id : new String[]{"minecraft:oak_boat", "minecraft:bamboo_chest_raft",
            "minecraft:chest_minecart", "minecraft:pig_spawn_egg", "minecraft:armor_stand"})
            assertTrue(ItemActionPolicy.hasSpawnTrack(id), id);
        for (String id : new String[]{"minecraft:wind_charge", "minecraft:bow", "minecraft:iron_spear"})
            assertFalse(ItemActionPolicy.hasSpawnTrack(id), id);
    }
    @Test void vanillaOwnsThrowableAndChargedItemsWhileMeasuredArrowsStaySeparate() {
        for (String id : new String[]{"minecraft:ender_pearl", "minecraft:trident", "minecraft:shield", "minecraft:iron_spear", "minecraft:snowball"})
            assertTrue(ItemActionPolicy.usesVanilla(id), id);
        assertFalse(ItemActionPolicy.usesVanilla("minecraft:bow"));
        assertFalse(ItemActionPolicy.usesVanilla("minecraft:crossbow"));
        assertFalse(ItemActionPolicy.usesVanilla(null));
        assertFalse(ItemActionPolicy.usesVanilla("minecraft:air"));
    }
}
