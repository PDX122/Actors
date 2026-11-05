package pdx.dev.fbactors.mixin;

import com.mojang.blaze3d.audio.Channel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Channel.class)
public interface TimelineSoundChannelAccess {
    @Accessor("source") int fbactors$source();
}
