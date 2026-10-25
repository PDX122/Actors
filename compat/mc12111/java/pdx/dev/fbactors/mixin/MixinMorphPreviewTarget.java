package pdx.dev.fbactors.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(net.minecraft.client.gui.render.pip.PictureInPictureRenderer.class)
public interface MixinMorphPreviewTarget {
    @Accessor("textureView")
    com.mojang.blaze3d.textures.GpuTextureView fbactors$previewTexture();
}
