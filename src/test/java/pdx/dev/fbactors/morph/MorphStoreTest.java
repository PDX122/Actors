package pdx.dev.fbactors.morph;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;


class MorphStoreTest {

    private static MorphSelection sel(UUID take, String morph) {
        return new MorphSelection(take, morph, 1.5f, 0.75f, true);
    }

    @Test
    void unmorphedTakeReadsAsNone() {
        MorphStore store = new MorphStore();
        MorphSelection selection = store.selectionFor(UUID.randomUUID());
        assertNotNull(selection);
        assertFalse(selection.isMorphed());
        assertNull(store.selectionFor(null));
    }

    @Test
    void setThenClear() {
        MorphStore store = new MorphStore();
        UUID take = UUID.randomUUID();
        store.set(take, sel(take, "creeper"));
        assertTrue(store.selectionFor(take).isMorphed());
        assertTrue(store.clear(take));
        assertFalse(store.selectionFor(take).isMorphed());
        assertFalse(store.clear(take));
    }

    @Test
    void storingNoneClears() {
        MorphStore store = new MorphStore();
        UUID take = UUID.randomUUID();
        store.set(take, sel(take, "creeper"));
        store.set(take, MorphSelection.none(take));
        assertFalse(store.selectionFor(take).isMorphed());
        assertEquals(0, store.size());
    }

    @Test
    void roundTripThroughDisk(@TempDir Path dir) throws Exception {
        MorphStore store = new MorphStore();
        UUID take = UUID.randomUUID();
        store.set(take, sel(take, "enderman"));
        assertTrue(store.isDirty());
        assertEquals(1, store.saveAll(dir));
        assertFalse(store.isDirty());

        MorphStore reloaded = new MorphStore();
        assertEquals(1, reloaded.load(dir));
        MorphSelection back = reloaded.selectionFor(take);
        assertEquals("enderman", back.morphId());
        assertEquals(1.5f, back.scale());
        assertEquals(0.75f, back.alpha());
        assertTrue(back.hideName());
    }

    @Test
    void clearedActorsAreNotWritten(@TempDir Path dir) throws Exception {
        MorphStore store = new MorphStore();
        UUID take = UUID.randomUUID();
        store.set(take, sel(take, "creeper"));
        store.clear(take);
        store.saveAll(dir);
        String text = Files.readString(dir.resolve("selections.json"), StandardCharsets.UTF_8);
        assertFalse(text.contains(take.toString()));
    }

    @Test
    void oneBadEntryCostsOneActor(@TempDir Path dir) throws Exception {
        UUID good = UUID.randomUUID();
        Files.writeString(dir.resolve("selections.json"),
            "{\"version\":1,\"actors\":["
                + "{\"take\":\"not-a-uuid\",\"morph\":\"creeper\",\"scale\":1.0,\"alpha\":1.0,\"hideName\":false},"
                + "{\"take\":\"" + good + "\",\"morph\":\"zombie\",\"scale\":1.0,\"alpha\":1.0,\"hideName\":false},"
                + "{\"take\":\"" + UUID.randomUUID() + "\"},"
                + "42]}",
            StandardCharsets.UTF_8);
        MorphStore store = new MorphStore();
        assertEquals(1, store.load(dir));
        assertEquals("zombie", store.selectionFor(good).morphId());
    }

    @Test
    void newerVersionRefusedByName(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("selections.json"),
            "{\"version\":9999,\"actors\":[]}", StandardCharsets.UTF_8);
        assertEquals(0, new MorphStore().load(dir));
    }

    @Test
    void missingFileIsFirstRunNotError(@TempDir Path dir) {
        assertEquals(0, new MorphStore().load(dir));
    }

    @Test
    void exportImportRoundTrip() {
        MorphStore store = new MorphStore();
        UUID take = UUID.randomUUID();
        store.set(take, sel(take, "creeper"));
        com.google.gson.JsonObject exported = store.exportSelection(take);
        assertNotNull(exported);
        assertEquals("creeper", exported.get("morph").getAsString());

        MorphStore back = new MorphStore();
        assertTrue(back.importSelection(take.toString(), exported));
        assertTrue(back.isDirty());
        assertEquals("creeper", back.selectionFor(take).morphId());
        assertEquals(1.5f, back.selectionFor(take).scale());
    }

    @Test
    void exportReturnsNullWhenUnmorphed() {
        MorphStore store = new MorphStore();
        assertNull(store.exportSelection(UUID.randomUUID()));
        assertNull(store.exportSelection(null));
    }

    @Test
    void importRefusesBadEntries() {
        MorphStore store = new MorphStore();
        assertFalse(store.importSelection("not-a-uuid",
            new com.google.gson.JsonObject()));
        com.google.gson.JsonObject noMorph = new com.google.gson.JsonObject();
        noMorph.addProperty("scale", 1.0);
        assertFalse(store.importSelection(UUID.randomUUID().toString(), noMorph));
        assertFalse(store.importSelection(UUID.randomUUID().toString(), null));
        assertEquals(0, store.size());
    }
}

