package pdx.dev.fbactors.filters;

import java.util.Set;

public final class ActorEffectPlan {
    private static final Set<String> SCREEN = Set.of("tessera", "ascii", "vhs_damage", "digital_glitch",
        "hologram", "sonar", "melt", "matrix_rain", "neon_spectrum", "arcade", "halftone",
        "crosshatch", "risograph", "cel_shade", "neon_wire", "pixel_sort", "codec_smear", "signal_rot", "bitcrush");
    private ActorEffectPlan() {}

    public static boolean needsBounds(LookSettings look) {
        return look != null && look.filters.contains("tessera");
    }

    public static boolean usesScreen(LookSettings look) {
        return look != null && look.filters.stream().anyMatch(id -> SCREEN.contains(id) || ManagedShaders.custom(id));
    }

    public static LookSettings material(LookSettings look) {
        if (!usesScreen(look)) return look.copy();

        var identity = new LookSettings();
        identity.enabled = look.enabled;
        return identity;
    }
}
