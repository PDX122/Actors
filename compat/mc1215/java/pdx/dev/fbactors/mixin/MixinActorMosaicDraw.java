package pdx.dev.fbactors.mixin;

@org.spongepowered.asm.mixin.Mixin(targets = "com.mojang.blaze3d.opengl.GlCommandEncoder")
public abstract class MixinActorMosaicDraw {
    @org.spongepowered.asm.mixin.injection.Group(name = "fbactors_mosaic_draw", min = 1)
    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method = "drawFromBuffers",
        at = @org.spongepowered.asm.mixin.injection.At(value = "INVOKE", target = "Lcom/mojang/blaze3d/opengl/GlStateManager;_drawElements(IIIJ)V", remap = false), require = 0)
    private void fbactors$mosaic0(int arg0, int arg1, int arg2, long arg3, com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
        original.call(arg0, arg1, arg2, arg3);
        if (pdx.dev.fbactors.filters.ActorMosaic.isCapturing()) pdx.dev.fbactors.filters.ActorMosaic.capture(() -> original.call(arg0, arg1, arg2, arg3));
    }

    @org.spongepowered.asm.mixin.injection.Group(name = "fbactors_mosaic_draw", min = 1)
    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method = "drawFromBuffers",
        at = @org.spongepowered.asm.mixin.injection.At(value = "INVOKE", target = "Lcom/mojang/blaze3d/opengl/GlStateManager;_drawArrays(III)V", remap = false), require = 0)
    private void fbactors$mosaic1(int arg0, int arg1, int arg2, com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
        original.call(arg0, arg1, arg2);
        if (pdx.dev.fbactors.filters.ActorMosaic.isCapturing()) pdx.dev.fbactors.filters.ActorMosaic.capture(() -> original.call(arg0, arg1, arg2));
    }
}
