package pdx.dev.fbactors.mixin;

@org.spongepowered.asm.mixin.Mixin(targets = "com.mojang.blaze3d.platform.GlStateManager")
public abstract class MixinActorMosaicDraw {
    @org.spongepowered.asm.mixin.injection.Group(name = "fbactors_mosaic_draw", min = 1)
    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method = "_drawElements",
        at = @org.spongepowered.asm.mixin.injection.At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL11;glDrawElements(IIIJ)V", remap = false), require = 0)
    private static void fbactors$mosaic0(int arg0, int arg1, int arg2, long arg3, com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
        original.call(arg0, arg1, arg2, arg3);
        if (pdx.dev.fbactors.filters.ActorMosaic.isCapturing()) pdx.dev.fbactors.filters.ActorMosaic.capture(() -> original.call(arg0, arg1, arg2, arg3));
    }
}
