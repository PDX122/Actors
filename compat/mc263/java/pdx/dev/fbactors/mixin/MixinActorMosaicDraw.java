package pdx.dev.fbactors.mixin;

@org.spongepowered.asm.mixin.Mixin(targets = "com.mojang.renderpearl.backend.opengl.GlCommandEncoder")
public abstract class MixinActorMosaicDraw {
    @org.spongepowered.asm.mixin.injection.Group(name = "fbactors_mosaic_draw", min = 1)
    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method = "executeDraw",
        at = @org.spongepowered.asm.mixin.injection.At(value = "INVOKE", target = "Lorg/lwjgl/opengl/ARBBaseInstance;glDrawElementsInstancedBaseVertexBaseInstance(IIIJIII)V", remap = false), require = 0)
    private void fbactors$mosaic0(int arg0, int arg1, int arg2, long arg3, int arg4, int arg5, int arg6, com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
        original.call(arg0, arg1, arg2, arg3, arg4, arg5, arg6);
        if (pdx.dev.fbactors.filters.ActorMosaic.isCapturing()) pdx.dev.fbactors.filters.ActorMosaic.capture(() -> original.call(arg0, arg1, arg2, arg3, arg4, arg5, arg6));
    }

    @org.spongepowered.asm.mixin.injection.Group(name = "fbactors_mosaic_draw", min = 1)
    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method = "executeDraw",
        at = @org.spongepowered.asm.mixin.injection.At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL33C;glDrawElementsInstancedBaseVertex(IIIJII)V", remap = false), require = 0)
    private void fbactors$mosaic1(int arg0, int arg1, int arg2, long arg3, int arg4, int arg5, com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
        original.call(arg0, arg1, arg2, arg3, arg4, arg5);
        if (pdx.dev.fbactors.filters.ActorMosaic.isCapturing()) pdx.dev.fbactors.filters.ActorMosaic.capture(() -> original.call(arg0, arg1, arg2, arg3, arg4, arg5));
    }

    @org.spongepowered.asm.mixin.injection.Group(name = "fbactors_mosaic_draw", min = 1)
    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method = "executeDraw",
        at = @org.spongepowered.asm.mixin.injection.At(value = "INVOKE", target = "Lorg/lwjgl/opengl/ARBBaseInstance;glDrawArraysInstancedBaseInstance(IIIII)V", remap = false), require = 0)
    private void fbactors$mosaic2(int arg0, int arg1, int arg2, int arg3, int arg4, com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
        original.call(arg0, arg1, arg2, arg3, arg4);
        if (pdx.dev.fbactors.filters.ActorMosaic.isCapturing()) pdx.dev.fbactors.filters.ActorMosaic.capture(() -> original.call(arg0, arg1, arg2, arg3, arg4));
    }

    @org.spongepowered.asm.mixin.injection.Group(name = "fbactors_mosaic_draw", min = 1)
    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method = "executeDraw",
        at = @org.spongepowered.asm.mixin.injection.At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL33C;glDrawArraysInstanced(IIII)V", remap = false), require = 0)
    private void fbactors$mosaic3(int arg0, int arg1, int arg2, int arg3, com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
        original.call(arg0, arg1, arg2, arg3);
        if (pdx.dev.fbactors.filters.ActorMosaic.isCapturing()) pdx.dev.fbactors.filters.ActorMosaic.capture(() -> original.call(arg0, arg1, arg2, arg3));
    }

    @org.spongepowered.asm.mixin.injection.Group(name = "fbactors_mosaic_draw", min = 1)
    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method = "executeDraws",
        at = @org.spongepowered.asm.mixin.injection.At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL33C;nglMultiDrawArrays(IJJI)V", remap = false), require = 0)
    private void fbactors$mosaic4(int arg0, long arg1, long arg2, int arg3, com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
        original.call(arg0, arg1, arg2, arg3);
        if (pdx.dev.fbactors.filters.ActorMosaic.isCapturing()) pdx.dev.fbactors.filters.ActorMosaic.capture(() -> original.call(arg0, arg1, arg2, arg3));
    }

    @org.spongepowered.asm.mixin.injection.Group(name = "fbactors_mosaic_draw", min = 1)
    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method = "executeDraws",
        at = @org.spongepowered.asm.mixin.injection.At(value = "INVOKE", target = "Lorg/lwjgl/opengl/GL33C;nglMultiDrawElementsBaseVertex(IJIJIJ)V", remap = false), require = 0)
    private void fbactors$mosaic5(int arg0, long arg1, int arg2, long arg3, int arg4, long arg5, com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
        original.call(arg0, arg1, arg2, arg3, arg4, arg5);
        if (pdx.dev.fbactors.filters.ActorMosaic.isCapturing()) pdx.dev.fbactors.filters.ActorMosaic.capture(() -> original.call(arg0, arg1, arg2, arg3, arg4, arg5));
    }

    @org.spongepowered.asm.mixin.injection.Group(name = "fbactors_mosaic_draw", min = 1)
    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method = "executeDrawIndirect",
        at = @org.spongepowered.asm.mixin.injection.At(value = "INVOKE", target = "Lorg/lwjgl/opengl/ARBMultiDrawIndirect;glMultiDrawArraysIndirect(IJII)V", remap = false), require = 0)
    private void fbactors$mosaic6(int arg0, long arg1, int arg2, int arg3, com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
        original.call(arg0, arg1, arg2, arg3);
        if (pdx.dev.fbactors.filters.ActorMosaic.isCapturing()) pdx.dev.fbactors.filters.ActorMosaic.capture(() -> original.call(arg0, arg1, arg2, arg3));
    }

    @org.spongepowered.asm.mixin.injection.Group(name = "fbactors_mosaic_draw", min = 1)
    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method = "executeDrawIndirect",
        at = @org.spongepowered.asm.mixin.injection.At(value = "INVOKE", target = "Lorg/lwjgl/opengl/ARBDrawIndirect;glDrawArraysIndirect(IJ)V", remap = false), require = 0)
    private void fbactors$mosaic7(int arg0, long arg1, com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
        original.call(arg0, arg1);
        if (pdx.dev.fbactors.filters.ActorMosaic.isCapturing()) pdx.dev.fbactors.filters.ActorMosaic.capture(() -> original.call(arg0, arg1));
    }

    @org.spongepowered.asm.mixin.injection.Group(name = "fbactors_mosaic_draw", min = 1)
    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method = "executeDrawIndirect",
        at = @org.spongepowered.asm.mixin.injection.At(value = "INVOKE", target = "Lorg/lwjgl/opengl/ARBMultiDrawIndirect;glMultiDrawElementsIndirect(IIJII)V", remap = false), require = 0)
    private void fbactors$mosaic8(int arg0, int arg1, long arg2, int arg3, int arg4, com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
        original.call(arg0, arg1, arg2, arg3, arg4);
        if (pdx.dev.fbactors.filters.ActorMosaic.isCapturing()) pdx.dev.fbactors.filters.ActorMosaic.capture(() -> original.call(arg0, arg1, arg2, arg3, arg4));
    }

    @org.spongepowered.asm.mixin.injection.Group(name = "fbactors_mosaic_draw", min = 1)
    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method = "executeDrawIndirect",
        at = @org.spongepowered.asm.mixin.injection.At(value = "INVOKE", target = "Lorg/lwjgl/opengl/ARBDrawIndirect;glDrawElementsIndirect(IIJ)V", remap = false), require = 0)
    private void fbactors$mosaic9(int arg0, int arg1, long arg2, com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
        original.call(arg0, arg1, arg2);
        if (pdx.dev.fbactors.filters.ActorMosaic.isCapturing()) pdx.dev.fbactors.filters.ActorMosaic.capture(() -> original.call(arg0, arg1, arg2));
    }
}
