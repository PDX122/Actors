package pdx.dev.fbactors.compat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import org.jetbrains.annotations.Nullable;

public final class CompatSounds {
    private CompatSounds() {
    }

    /**
     * Resolves a recorded sound registry id (e.g. "minecraft:block.wood.place")
     * back to its event, or null when unknown or malformed. Never throws:
     * playback must not die on a missing sound.
     */
    @Nullable
    public static SoundEvent resolveSound(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        try {
            Identifier loc = Identifier.tryParse(id);
            if (loc == null) {
                return null;
            }
            return BuiltInRegistries.SOUND_EVENT.getOptional(loc).orElse(null);
        } catch (Exception e) {
            return null;
        }
    }
}
