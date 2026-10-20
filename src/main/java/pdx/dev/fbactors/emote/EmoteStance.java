package pdx.dev.fbactors.emote;

import net.minecraft.world.entity.Pose;
import org.jetbrains.annotations.Nullable;


final class EmoteStance {


    static final java.util.List<String> NAMES =
        java.util.List.of("standing", "crouching", "swimming", "sitting", "sleeping");

    private EmoteStance() {
    }


    @Nullable
    static Pose parse(@Nullable String stance) {
        if (stance == null || stance.isBlank()) {
            return null;
        }
        String name = stance.strip().toLowerCase(java.util.Locale.ROOT);
        if (!NAMES.contains(name)) {
            throw new IllegalArgumentException(
                "unknown stance '" + stance.strip() + "'; expected one of " + NAMES);
        }
        return Pose.valueOf(name.toUpperCase(java.util.Locale.ROOT));
    }


    @Nullable
    static String name(@Nullable Pose pose) {
        return pose == null ? null : pose.name().toLowerCase(java.util.Locale.ROOT);
    }
}

