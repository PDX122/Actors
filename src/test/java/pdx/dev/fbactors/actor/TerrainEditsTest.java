package pdx.dev.fbactors.actor;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TerrainEditsTest {
    private static final String DIMENSION = "minecraft:overworld";
    private TerrainEdits.Entry edit(int x, int y, int z, String before, String after) {
        return new TerrainEdits.Entry(DIMENSION, x, y, z, before, after);
    }

    @Test void deletingIceAffectsSupportUnderABoatButNotDistantBoats() {
        var edits = new TerrainEdits();
        edits.record(edit(4, 63, 8, "minecraft:blue_ice", "minecraft:air"));
        assertTrue(edits.affects(DIMENSION, 3.3, 63.4, 7.3, 5.7, 65, 9.7));
        assertFalse(edits.affects(DIMENSION, 20, 63.4, 7, 22, 65, 9));
        assertFalse(edits.affects("minecraft:the_nether", 3.3, 63.4, 7.3, 5.7, 65, 9.7));
    }

    @Test void placedObstacleAffectsTheSweptPathBeforeTheBoatEntersIt() {
        var edits = new TerrainEdits();
        edits.record(edit(5, 64, 0, "minecraft:air", "minecraft:stone"));
        assertFalse(edits.affects(DIMENSION, 2, 63.4, -1, 4, 65, 1));
        assertTrue(edits.affects(DIMENSION, 2, 63.4, -1, 6, 65, 1));
    }

    @Test void iceReplacementAndRestorationKeepTheOriginalSceneState() {
        var edits = new TerrainEdits();
        edits.record(edit(-2, 63, -1, "minecraft:ice", "minecraft:air"));
        edits.record(edit(-2, 63, -1, "minecraft:air", "minecraft:stone"));
        assertEquals("minecraft:ice", edits.entries().iterator().next().before());
        assertEquals("minecraft:stone", edits.entries().iterator().next().after());
        edits.record(edit(-2, 63, -1, "minecraft:stone", "minecraft:ice"));
        assertTrue(edits.entries().isEmpty());
    }

    @Test void sceneOverridesRoundTripIndependentlyOfTakes() {
        var edits = new TerrainEdits();
        edits.record(edit(1, 63, 2, "minecraft:ice", "minecraft:air"));
        edits.record(edit(3, 64, 2, "minecraft:air", "minecraft:oak_slab[type=top,waterlogged=false]"));
        var gson = new Gson();
        var restored = new TerrainEdits();
        for (var entry : gson.fromJson(gson.toJson(edits.entries()), TerrainEdits.Entry[].class)) restored.record(entry);
        assertEquals(edits.entries(), restored.entries());
        assertTrue(restored.affects(DIMENSION, 1, 63.5, 2, 2, 65, 3));
    }
}
