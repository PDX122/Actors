package pdx.dev.fbactors.mixin;

import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundEngineExecutor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SoundEngine.class)
public interface TimelineSoundEngineAccess {
    @Accessor("channelAccess") ChannelAccess fbactors$channels();
    @Accessor("soundBuffers") SoundBufferLibrary fbactors$buffers();
    @Accessor("executor") SoundEngineExecutor fbactors$executor();
}
