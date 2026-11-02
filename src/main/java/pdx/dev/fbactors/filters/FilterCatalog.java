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

    public static List<Entry> builtins() { return ENTRIES; }
    public static List<Entry> entries() {
        return java.util.stream.Stream.concat(ENTRIES.stream().filter(entry -> ManagedShaders.visible(entry.id())),ManagedShaders.entries().stream()).toList();
    }
    public static Entry find(String id) {
        return entries().stream().filter(entry -> entry.id().equals(id)).findFirst().orElse(null);
    }
    public static List<Entry> search(String query, String category) {
        String needle = query.strip().toLowerCase(Locale.ROOT);
        return entries().stream().filter(entry -> category.equals("All") || entry.category().equals(category))
            .filter(entry -> (entry.name() + " " + entry.description()).toLowerCase(Locale.ROOT).contains(needle)).toList();
    }
    public static List<Param> params(String id) {
        if(ManagedShaders.custom(id)) return ManagedShaders.params(id);
        return switch (id == null ? "" : id) {
            case "vhs_damage", "digital_glitch", "hologram", "sonar", "melt", "matrix_rain", "neon_spectrum" ->
                List.of(new Param("Amount", "Effect strength", 0, 1, 0.85f),
                    new Param("Speed", "Animation speed", 0, 3, 1));
            case "cinema_35mm" -> List.of(new Param("Amount", "Film strength", 0, 1, 1),
                new Param("FilmGrain", "Film grain", 0, .04f, .012f));
            case "noir", "desert_chrome", "moonlight" -> List.of(new Param("Amount", "Grade strength", 0, 1, 1));
            case "pixel_sort" -> List.of(new Param("Amount", "Sort strength", 0, 1, 1),
                new Param("Span", "Sort span", 16, 256, 96), new Param("Threshold", "Brightness threshold", 0, 0.95f, 0.25f),
                new Param("Vertical", "Vertical sorting", 0, 1, 1), new Param("Speed", "Animation speed", 0, 3, 0.7f));
            case "codec_smear" -> List.of(new Param("Damage", "Block damage", 0, 1, 0.7f),
                new Param("Drift", "Smear distance", 0, 2, 1), new Param("Speed", "Animation speed", 0, 3, 1));
            case "signal_rot" -> List.of(new Param("Damage", "Signal decay", 0, 1, 0.65f),
                new Param("BlockSize", "Corruption blocks", 8, 80, 24), new Param("Speed", "Animation speed", 0, 3, 1));
            case "bitcrush" -> List.of(new Param("Levels", "Color levels", 2, 12, 4),
                new Param("PixelSize", "Pixel size", 1, 12, 3), new Param("Dither", "Digital noise", 0, 1, 0.5f),
                new Param("Speed", "Animation speed", 0, 3, 1));
            case "ascii" -> List.of(new Param("Amount", "Effect strength", 0, 1, 1),
                new Param("GlyphSize", "Character size", 7, 36, 14),
                new Param("ColorMix", "Original glyph color", 0, 1, 0.55f));
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
