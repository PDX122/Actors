package pdx.dev.fbactors.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(net.minecraft.client.Minecraft.class)
public interface MixinMorphPreviewTarget {
    @Accessor("mainRenderTarget")
    @org.spongepowered.asm.mixin.Mutable
    void fbactors$previewTarget(com.mojang.blaze3d.pipeline.RenderTarget target);
}
