package pdx.dev.fbactors.actor;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonObject;


class SkinFileAssignmentsTest {

    @Test
    void fullEntrySurvivesTheStringRoundTrip(@TempDir Path dir) throws Exception {
        Path skin = png(dir, "skin.png");
        Path cape = png(dir, "cape.png");
        UUID take = UUID.randomUUID();
        Map<UUID, SkinFileAssignments.Entry> files = Map.of(take,
            new SkinFileAssignments.Entry(skin.toString(), cape.toString(), Boolean.TRUE));

        String text = SkinFileAssignments.encode(files) + "\n";
        Map<UUID, SkinFileAssignments.Entry> back =
            SkinFileAssignments.parse(text, Files::isRegularFile);

        assertEquals(1, back.size());
        assertEquals(skin.toString(), back.get(take).skin());
        assertEquals(cape.toString(), back.get(take).cape());
        assertEquals(Boolean.TRUE, back.get(take).slim());
    }

    @Test
    void documentCarriesAVersionAndAnActorsMap() {
        UUID take = UUID.randomUUID();
        Map<UUID, SkinFileAssignments.Entry> files = Map.of(take,
            new SkinFileAssignments.Entry("skin.png", null, null));

        JsonObject root = SkinFileAssignments.encode(files);

        assertEquals(SkinFileAssignments.VERSION, root.get("version").getAsInt());
        assertTrue(root.get("actors").isJsonObject());
        JsonObject value = root.getAsJsonObject("actors").getAsJsonObject(take.toString());
        assertEquals("skin.png", value.get("skin").getAsString());
        assertFalse(value.has("cape"));
        assertFalse(value.has("slim"));
    }

    @Test
    void slimFalseSurvivesAsClassicRatherThanAuto(@TempDir Path dir) throws Exception {
        Path skin = png(dir, "skin.png");
        UUID take = UUID.randomUUID();
        Map<UUID, SkinFileAssignments.Entry> files = Map.of(take,
            new SkinFileAssignments.Entry(skin.toString(), null, Boolean.FALSE));

        String text = SkinFileAssignments.encode(files) + "\n";
        Map<UUID, SkinFileAssignments.Entry> back =
            SkinFileAssignments.parse(text, Files::isRegularFile);

        assertEquals(Boolean.FALSE, back.get(take).slim());
    }

    @Test
    void capeOnlyPickHasNoSkinAndStillCounts(@TempDir Path dir) throws Exception {
        Path cape = png(dir, "cape.png");
        UUID take = UUID.randomUUID();
        Map<UUID, SkinFileAssignments.Entry> files = Map.of(take,
            new SkinFileAssignments.Entry(null, cape.toString(), null));

        String text = SkinFileAssignments.encode(files) + "\n";
        Map<UUID, SkinFileAssignments.Entry> back =
            SkinFileAssignments.parse(text, Files::isRegularFile);

        assertEquals(1, back.size());
        assertNull(back.get(take).skin());
        assertEquals(cape.toString(), back.get(take).cape());
    }

    @Test
    void emptyChoicesAreNotWritten() {
        UUID bare = UUID.randomUUID();
        Map<UUID, SkinFileAssignments.Entry> files = new LinkedHashMap<>();
        files.put(bare, new SkinFileAssignments.Entry(null, null, null));
        files.put(bare, new SkinFileAssignments.Entry(null, null, Boolean.TRUE));

        String text = SkinFileAssignments.encode(files).toString();

        assertFalse(text.contains(bare.toString()));
    }

    @Test
    void goneSkinDropsTheEntryButGoneCapeOnlyDropsTheCape(@TempDir Path dir)
            throws Exception {
        Path skin = png(dir, "skin.png");
        UUID dropped = UUID.randomUUID();
        UUID kept = UUID.randomUUID();
        String text = "{\"version\":1,\"actors\":{"
            + "\"" + dropped + "\":{\"skin\":\"" + escape(dir.resolve("gone.png").toString()) + "\"},"
            + "\"" + kept + "\":{\"skin\":\"" + escape(skin.toString()) + "\","
            + "\"cape\":\"" + escape(dir.resolve("gone-cape.png").toString()) + "\","
            + "\"slim\":true}}}";

        Map<UUID, SkinFileAssignments.Entry> back =
            SkinFileAssignments.parse(text, Files::isRegularFile);

        assertFalse(back.containsKey(dropped));
        assertEquals(skin.toString(), back.get(kept).skin());
        assertNull(back.get(kept).cape());
        assertEquals(Boolean.TRUE, back.get(kept).slim());
    }

    @Test
    void oneBadEntryCostsOneActor() {
        UUID good = UUID.randomUUID();
        String text = "{\"version\":1,\"actors\":{"
            + "\"not-a-uuid\":{\"skin\":\"skin.png\"},"
            + "\"" + good + "\":{\"skin\":\"skin.png\"},"
            + "\"" + UUID.randomUUID() + "\":42}}";

        Map<UUID, SkinFileAssignments.Entry> back =
            SkinFileAssignments.parse(text, path -> true);

        assertEquals(1, back.size());
        assertEquals("skin.png", back.get(good).skin());
    }

    @Test
    void nonBooleanSlimReadsAsAuto() {
        UUID take = UUID.randomUUID();
        String text = "{\"version\":1,\"actors\":{"
            + "\"" + take + "\":{\"skin\":\"skin.png\",\"slim\":\"slim\"}}}";

        Map<UUID, SkinFileAssignments.Entry> back =
            SkinFileAssignments.parse(text, path -> true);

        assertNull(back.get(take).slim());
    }

    @Test
    void userBytesNeverThrow() {
        assertTrue(SkinFileAssignments.parse("not json", path -> true).isEmpty());
        assertTrue(SkinFileAssignments.parse("[1,2]", path -> true).isEmpty());
        assertTrue(SkinFileAssignments.parse("{\"version\":1}", path -> true).isEmpty());
        assertTrue(SkinFileAssignments.parse("", path -> true).isEmpty());
    }


    private static Path png(Path dir, String name) throws Exception {
        Path file = dir.resolve(name);
        Files.write(file, new byte[]{(byte) 0x89});
        assertTrue(Files.isRegularFile(file));
        return file;
    }


    private static String escape(String path) {
        return path.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @Test
    void fixtureShapeMatchesTheDocumentedContract(@TempDir Path dir) throws Exception {
        Path skin = png(dir, "skin.png");
        UUID take = UUID.randomUUID();
        String text = "{\"version\":1,\"actors\":{\"" + take + "\":"
            + "{\"skin\":\"" + escape(skin.toString()) + "\","
            + "\"cape\":\"" + escape(skin.toString()) + "\",\"slim\":true}}}";
        Path file = dir.resolve("files.json");
        Files.writeString(file, text + "\n", StandardCharsets.UTF_8);

        String read = Files.readString(file, StandardCharsets.UTF_8);
        Map<UUID, SkinFileAssignments.Entry> back =
            SkinFileAssignments.parse(read, Files::isRegularFile);

        assertEquals(new SkinFileAssignments.Entry(skin.toString(), skin.toString(),
            Boolean.TRUE), back.get(take));
    }
}

