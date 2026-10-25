package pdx.dev.fbactors.filters;

import com.google.gson.Gson;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class FilterCatalog {
    public record Entry(String id, String name, String category, String origin, String description) {}
    public record Param(String id, String label, float min, float max, float value) {}

    private static final List<Entry> ENTRIES = load();
    private FilterCatalog() {}

    private static List<Entry> load() {
        try (var input = FilterCatalog.class.getResourceAsStream("/assets/fbactors/filters/catalog.json")) {
            if (input == null) throw new IllegalStateException("Filter catalog is missing");
            return List.copyOf(Arrays.asList(new Gson().fromJson(
                new InputStreamReader(input, StandardCharsets.UTF_8), Entry[].class)));
        } catch (java.io.IOException error) {
            throw new IllegalStateException(error);
        }
    }

    public static List<Entry> entries() { return ENTRIES; }
    public static Entry find(String id) {
        return ENTRIES.stream().filter(entry -> entry.id().equals(id)).findFirst().orElse(null);
    }
    public static List<Entry> search(String query, String category) {
        String needle = query.strip().toLowerCase(Locale.ROOT);
        return ENTRIES.stream().filter(entry -> category.equals("All") || entry.category().equals(category))
            .filter(entry -> (entry.name() + " " + entry.description()).toLowerCase(Locale.ROOT).contains(needle)).toList();
    }
    public static List<Param> params(String id) {
        return switch (id == null ? "" : id) {
            case "halation" -> List.of(new Param("Amount", "Light spill", 0, 1, 0.45f),
                new Param("Radius", "Spread", 1, 12, 3), new Param("Threshold", "Highlight threshold", 0.1f, 0.9f, 0.55f));
            case "diffusion" -> List.of(new Param("Amount", "Softness", 0, 1, 0.35f), new Param("Radius", "Spread", 1, 12, 3));
            case "pearl_bloom" -> List.of(new Param("Amount", "Bloom", 0, 2, 0.6f));
            case "contour_light" -> List.of(new Param("Amount", "Contour glow", 0, 3, 1));
            case "luma_relief" -> List.of(new Param("Amount", "Relief depth", 0, 5, 2));
            case "prism" -> List.of(new Param("Amount", "Refraction", 0, 3, 1));
            case "inkwash" -> List.of(new Param("Amount", "Ink weight", 0, 5, 2), new Param("Levels", "Pigment levels", 2, 16, 7));
            case "tessera" -> List.of(new Param("BlockSize", "Block size", 6, 80, 28));
            case "cel_shade" -> List.of(new Param("Levels", "Color bands", 2, 10, 5), new Param("Ink", "Ink weight", 0, 4, 1.8f));
            case "halftone" -> List.of(new Param("Spacing", "Dot spacing", 3, 18, 7));
            case "crosshatch" -> List.of(new Param("Spacing", "Line spacing", 3, 18, 7));
            case "risograph" -> List.of(new Param("Misregister", "Ink offset", 0, 6, 2));
            case "night_vision" -> List.of(new Param("Gain", "Gain", 0.5f, 4, 1.7f));
            case "infrared" -> List.of(new Param("Foliage", "Foliage color", 0, 1, 1));
            case "duotone" -> List.of(new Param("Contrast", "Ink balance", 0.4f, 2, 0.8f));
            case "solarize" -> List.of(new Param("Threshold", "Reversal threshold", 0.15f, 0.85f, 0.5f));
            case "arcade" -> List.of(new Param("PixelSize", "Pixel size", 1, 12, 3));
            case "selective_red" -> List.of(new Param("Tolerance", "Red isolation", 0.04f, 0.4f, 0.18f));
            case "neon_wire" -> List.of(new Param("Amount", "Neon intensity", 0.3f, 4, 2));
            case "anamorphic" -> List.of(new Param("Amount", "Streak strength", 0, 3, 1.2f), new Param("Reach", "Streak reach", 2, 24, 12));
            default -> List.of();
        };
    }
}
