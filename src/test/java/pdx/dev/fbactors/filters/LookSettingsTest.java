package pdx.dev.fbactors.filters;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LookSettingsTest {
    @Test void catalogContainsOnlyOriginalShaderSources() {
        assertEquals(44, FilterCatalog.entries().size());
        assertTrue(FilterCatalog.entries().stream().allMatch(entry -> entry.origin().equals("Actors")));
        assertEquals(44, FilterCatalog.entries().stream().map(FilterCatalog.Entry::id).distinct().count());
        for (var entry : FilterCatalog.entries()) {
            String shader = FilterGpuPipeline.source(entry.id());
            assertTrue(shader.contains("void main("), entry.id());
            assertFalse(shader.contains("layout(std140)"), entry.id());
            for (var param : FilterCatalog.params(entry.id())) {
                assertTrue(shader.contains("uniform float " + param.id() + ";"), entry.id() + ": " + param.id());
            }
        }
    }

    @Test void staticDemoAtlasMatchesTheCatalogAndContainsDistinctPhotoPreviews() throws Exception {
        try (var stream = getClass().getResourceAsStream("/assets/fbactors/filters/demo-atlas.png");
             var manifest = getClass().getResourceAsStream("/assets/fbactors/filters/demo-atlas.json");
             var license = getClass().getResourceAsStream("/assets/fbactors/filters/DEMO-LICENSE.txt")) {
            assertNotNull(stream);
            assertNotNull(manifest);
            assertNotNull(license);
            var image = javax.imageio.ImageIO.read(stream);
            assertEquals(1024, image.getWidth());
            assertEquals(144 * ((FilterCatalog.entries().size() + 3) / 4), image.getHeight());
            var ids = com.google.gson.JsonParser.parseString(new String(manifest.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)).getAsJsonArray();
            var unique = new java.util.HashSet<Integer>();
            for (int i = 0; i < FilterCatalog.entries().size(); i++) {
                assertEquals(FilterCatalog.entries().get(i).id(), ids.get(i).getAsString());
                int hash = 1;
                int min = 255, max = 0;
                for (int y = 0; y < 144; y++) for (int x = 0; x < 256; x++) {
                    int rgb = image.getRGB(i % 4 * 256 + x, i / 4 * 144 + y);
                    hash = hash * 31 + rgb;
                    int value = rgb >> 16 & 255;
                    min = Math.min(min, value);
                    max = Math.max(max, value);
                }
                assertTrue(max - min > 30);
                assertTrue(unique.add(hash), "Repeated preview: " + ids.get(i));
            }
            assertTrue(new String(license.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).contains("CC0"));
        }
    }

    @Test void expandedEffectsKeepTheirTuningWhenSavedAndClampUnsafeInput(@TempDir Path folder) {
        for (String id : List.of("cel_shade", "halftone", "crosshatch", "risograph", "night_vision", "infrared", "duotone", "solarize", "arcade", "selective_red", "neon_wire", "anamorphic")) {
            var look = new LookSettings();
            assertTrue(look.add(id));
            assertFalse(FilterCatalog.params(id).isEmpty());
            for (var param : FilterCatalog.params(id)) {
                look.parameter(id, param, param.max() + 100);
                assertEquals(param.max(), look.parameter(id, param));
                look.parameter(id, param, Float.NaN);
                assertEquals(param.value(), look.parameter(id, param));
                look.parameter(id, param, param.min());
            }
            var file = folder.resolve(id + ".json");
            assertTrue(LookStore.save(file, look));
            var loaded = LookStore.load(file);
            assertEquals(List.of(id), loaded.filters);
            for (var param : FilterCatalog.params(id)) assertEquals(param.min(), loaded.parameter(id, param));
        }
    }

    @Test void mosaicKeepsSavedIdAndMigratesOldTileTuning() {
        LookSettings look = new LookSettings();
        look.add("tessera");
        look.tuning.put("tessera", Map.of("TileSize", 6f));
        look.normalize();
        var block = FilterCatalog.params("tessera").getFirst();
        assertEquals("Mosaic", FilterCatalog.find("tessera").name());
        assertEquals("BlockSize", block.id());
        assertEquals(28, look.parameter("tessera", block));
        assertFalse(look.tuning.get("tessera").containsKey("TileSize"));
        assertEquals(List.of("tessera"), look.filters);
    }

    @Test void stackLimitsAndUnknownFiltersAreHandled() {
        LookSettings look = new LookSettings();
        assertTrue(look.add("aurora"));
        assertFalse(look.add("aurora"));
        assertFalse(look.add("missing"));
        assertTrue(look.add("daybreak"));
        assertTrue(look.add("carbon"));
        assertFalse(look.add("ember"));
        assertTrue(look.enabled);
    }

    @Test void normalizationSanitizesCorruptSettings() {
        LookSettings look = new LookSettings();
        look.filters = new ArrayList<>(List.of("missing", "aurora", "aurora", "daybreak", "carbon", "ember"));
        look.exposure = Float.NaN;
        look.saturation = Float.POSITIVE_INFINITY;
        look.intensity = -1;
        look.contrast = 50;
        look.normalize();
        assertEquals(List.of("aurora", "daybreak", "carbon"), look.filters);
        assertEquals(0, look.exposure);
        assertEquals(1, look.saturation);
        assertEquals(0, look.intensity);
        assertEquals(1.8f, look.contrast);
        look.filters = null;
        look.tuning = null;
        look.normalize();
        assertTrue(look.filters.isEmpty());
        assertNotNull(look.tuning);
    }

    @Test void removedImportsAreDiscardedFromSavedLooks() {
        LookSettings look = new LookSettings();
        look.filters = new ArrayList<>(List.of("film", "custom", "ditherfine", "aurora"));
        look.tuning.put("custom", Map.of("Warp", 2f));
        look.normalize();
        assertEquals(List.of("aurora"), look.filters);
        assertTrue(look.tuning.isEmpty());
    }

    @Test void copiesDoNotShareMutableSettings() {
        LookSettings look = new LookSettings();
        look.add("prism");
        var warp = FilterCatalog.params("prism").getFirst();
        look.parameter("prism", warp, 1);
        LookSettings copy = look.copy();
        copy.filters.clear();
        copy.parameter("prism", warp, 2);
        assertEquals(List.of("prism"), look.filters);
        assertEquals(1, look.parameter("prism", warp));
    }

    @Test void OriginalOpticsTuningClampNonFiniteAndOutOfRangeValues() {
        LookSettings look = new LookSettings();
        var param = FilterCatalog.params("prism").getFirst();
        look.parameter("prism", param, 100);
        assertEquals(3, look.parameter("prism", param));
        look.parameter("prism", param, Float.NaN);
        assertEquals(param.value(), look.parameter("prism", param));
        look.tuning.put("missing", Map.of("bad", 99f));
        look.tuning.put("prism", Map.of("Amount", Float.NaN, "unknown", 3f));
        look.normalize();
        assertFalse(look.tuning.containsKey("missing"));
        assertEquals(Map.of("Amount", 1f), look.tuning.get("prism"));
    }

    @Test void replayLooksRemainIndependentAndRoundTrip(@TempDir Path folder) {
        LookSettings first = new LookSettings();
        first.add("ember");
        first.exposure = 0.5f;
        Path a = folder.resolve("a/looks.json"), b = folder.resolve("b/looks.json");
        assertTrue(LookStore.save(a, first));
        assertTrue(LookStore.save(b, new LookSettings()));
        assertEquals(List.of("ember"), LookStore.load(a).filters);
        assertEquals(0.5f, LookStore.load(a).exposure);
        assertFalse(LookStore.load(b).enabled);
    }

    @Test void corruptFilesFallBackToNeutral(@TempDir Path folder) throws Exception {
        Path file = folder.resolve("looks.json");
        Files.writeString(file, "{broken");
        assertFalse(LookStore.load(file).enabled);
        Files.writeString(file, "null");
        assertFalse(LookStore.load(file).enabled);
        Files.writeString(file, "{\"filters\":null,\"tuning\":null,\"exposure\":999}");
        assertEquals(3, LookStore.load(file).exposure);
    }

    @Test void presetLibraryRoundTripsAndIgnoresInvalidEntries(@TempDir Path folder) throws Exception {
        LookSettings look = new LookSettings();
        look.add("opal");
        Path file = folder.resolve("presets.json");
        assertTrue(LookStore.presets(file, new LinkedHashMap<>(Map.of("Pearl", look))));
        assertEquals(List.of("opal"), LookStore.presets(file).get("Pearl").filters);
        Files.writeString(file, "{\"\":{},\"Broken\":null,\"Good\":{\"filters\":[\"missing\"]}}");
        assertEquals(List.of("Good"), new ArrayList<>(LookStore.presets(file).keySet()));
        assertTrue(LookStore.presets(file).get("Good").filters.isEmpty());
    }
}
