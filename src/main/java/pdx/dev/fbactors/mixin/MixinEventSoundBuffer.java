package pdx.dev.fbactors.mixin;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import com.mojang.blaze3d.audio.SoundBuffer;
import net.minecraft.client.sounds.SoundBufferLibrary;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pdx.dev.fbactors.events.EventPressure;

@Mixin(SoundBufferLibrary.class)
public class MixinEventSoundBuffer {
    @Shadow @Final private Map<Object,CompletableFuture<SoundBuffer>> cache;
    @Inject(method="getCompleteBuffer",at=@At("HEAD"),cancellable=true)
    private void fbactors$pressure(@Coerce Object location,CallbackInfoReturnable<CompletableFuture<SoundBuffer>> cir) {
        String key=location.toString();
        if(key.startsWith("fbactors:sounds/events/generated/pressure_")) {
            // At most 16 parameter combinations; vanilla owns disposal, mute and export.
            cir.setReturnValue(cache.computeIfAbsent(location,k->CompletableFuture.supplyAsync(()->EventPressure.buffer(key))));
        }
    }
}
