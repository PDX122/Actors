package pdx.dev.fbactors.morph;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;


class MorphRenderStateTest {

    @Test
    void setGetClearRoundTrip() {
        UUID id = UUID.randomUUID();
        assertNull(MorphRenderState.get(id));
        MorphRenderState.set(id, new MorphRenderInfo("zombie", 1.0f, 1.0f));
        assertEquals("zombie", MorphRenderState.get(id).morphId());
        MorphRenderState.clear(id);
        assertNull(MorphRenderState.get(id));
    }

    @Test
    void nullInfoClears() {
        UUID id = UUID.randomUUID();
        MorphRenderState.set(id, new MorphRenderInfo("creeper", 1.4f, 1.0f));
        MorphRenderState.set(id, null);
        assertNull(MorphRenderState.get(id));
    }

    @Test
    void nullUuidIsIgnoredEverywhere() {
        assertDoesNotThrow(() -> {
            MorphRenderState.set(null, new MorphRenderInfo("zombie", 1.0f, 1.0f));
            MorphRenderState.clear(null);
            MorphRenderState.noteRenderLookup(null, true);
            MorphRenderState.noteVisibility(null, true);
        });
        assertNull(MorphRenderState.get(null));
    }

    @Test
    void setOverwritesAndClearAllEmpties() {
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        try {
            MorphRenderState.set(a, new MorphRenderInfo("bat", 0.5f, 1.0f));
            MorphRenderState.set(a, new MorphRenderInfo("zombie", 1.0f, 1.0f));
            assertEquals("zombie", MorphRenderState.get(a).morphId());
            MorphRenderState.set(b, new MorphRenderInfo("creeper", 1.0f, 1.0f));
            MorphRenderState.clearAll();
            assertNull(MorphRenderState.get(a));
            assertNull(MorphRenderState.get(b));
            assertTrue(MorphRenderState.snapshot().isEmpty());
        } finally {
            MorphRenderState.clear(a);
            MorphRenderState.clear(b);
        }
    }

    @Test
    void diagnosticsNeverThrow() {
        UUID id = UUID.randomUUID();
        assertDoesNotThrow(() -> {
            MorphRenderState.noteRenderLookup(id, false);
            MorphRenderState.noteRenderLookup(id, true);
            MorphRenderState.noteRenderLookup(id, false);
            MorphRenderState.noteVisibility(id, true);
            MorphRenderState.noteVisibility(id, false);
        });
        MorphRenderState.clear(id);
    }

    @Test
    void snapshotIsDetached() {
        UUID id = UUID.randomUUID();
        try {
            MorphRenderState.set(id, new MorphRenderInfo("zombie", 1.0f, 1.0f));
            Map<UUID, MorphRenderInfo> snap = MorphRenderState.snapshot();
            assertEquals(1, snap.size());
            assertEquals("zombie", snap.get(id).morphId());
            MorphRenderState.clear(id);
            assertEquals(1, snap.size());
            assertNotNull(snap.get(id));
        } finally {
            MorphRenderState.clear(id);
        }
    }

    @Test
    void renderInfoPlayerCheck() {
        assertTrue(new MorphRenderInfo("player", 1.0f, 1.0f).isPlayer());
        assertFalse(new MorphRenderInfo("zombie", 1.0f, 1.0f).isPlayer());
    }
}

