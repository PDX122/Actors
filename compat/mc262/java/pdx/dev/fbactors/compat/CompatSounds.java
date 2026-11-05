package pdx.dev.fbactors.compat;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import org.jetbrains.annotations.Nullable;

public final class CompatSounds {
    private CompatSounds() {
    }

    public static SoundEvent registerEvent(String name) {
        var id=Identifier.fromNamespaceAndPath("fbactors",name);
        return net.minecraft.core.Registry.register(BuiltInRegistries.SOUND_EVENT,id,SoundEvent.createVariableRangeEvent(id));
    }

    public static net.minecraft.client.resources.sounds.Sound eventFile(String path) {
        return new net.minecraft.client.resources.sounds.Sound(Identifier.fromNamespaceAndPath("fbactors",path),
            net.minecraft.util.valueproviders.ConstantFloat.of(1),net.minecraft.util.valueproviders.ConstantFloat.of(1),1,
            net.minecraft.client.resources.sounds.Sound.Type.FILE,false,false,16);
    }

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
