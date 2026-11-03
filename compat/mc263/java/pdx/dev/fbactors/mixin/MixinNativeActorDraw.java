package pdx.dev.fbactors.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.mojang.renderpearl.api.buffers.*;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.IndexType;
import com.mojang.renderpearl.backend.api.RenderPassBackend;
import com.mojang.renderpearl.frontend.*;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.function.Consumer;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pdx.dev.fbactors.filters.NativeActorMasks;

@Mixin(value = FrontendRenderPass.class, remap = false)
public abstract class MixinNativeActorDraw {
    @Shadow private FrontendRenderPipeline boundPipeline;
    @Shadow @Final private GpuBufferSlice[] vertexBuffers;
    @Shadow protected GpuBuffer indexBuffer;
    @Shadow @Final protected HashMap<String, Object> uniforms;
    @Unique private IndexType fbactors$indexType;
    @Unique private ByteBuffer fbactors$constants;
    @Unique private int[] fbactors$scissor;
    @Unique private GpuBuffer fbactors$boundIndex;
    @Unique private final GpuBufferSlice[] fbactors$boundVertices = new GpuBufferSlice[RenderPass.MAX_VERTEX_BUFFERS];

    @Inject(method = "setIndexBuffer", at = @At("TAIL"))
    private void fbactors$index(GpuBuffer buffer, IndexType type, CallbackInfo ci) { fbactors$indexType = type; }
    @Inject(method = "pushConstants", at = @At("TAIL"))
    private void fbactors$constants(ByteBuffer bytes, CallbackInfo ci) {
        fbactors$constants = ByteBuffer.allocateDirect(bytes.remaining()).put(bytes.duplicate()).flip();
    }
    @Inject(method = "enableScissor", at = @At("TAIL"))
    private void fbactors$scissor(int x, int y, int w, int h, CallbackInfo ci) { fbactors$scissor = new int[]{x,y,w,h}; }
    @Inject(method = "disableScissor", at = @At("TAIL"))
    private void fbactors$noScissor(CallbackInfo ci) { fbactors$scissor = null; }

    @WrapOperation(method = {"setIndexBuffer", "drawMultipleIndexed"}, at = @At(value = "INVOKE",
        target = "Lcom/mojang/renderpearl/backend/api/RenderPassBackend;setIndexBuffer(Lcom/mojang/renderpearl/api/buffers/GpuBuffer;Lcom/mojang/renderpearl/api/pipeline/IndexType;)V"))
    private void fbactors$boundIndex(RenderPassBackend backend, GpuBuffer buffer, IndexType type, Operation<Void> original) {
        original.call(backend, buffer, type); fbactors$boundIndex = buffer; fbactors$indexType = type;
    }
    @WrapOperation(method = {"setVertexBuffer", "drawMultipleIndexed"}, at = @At(value = "INVOKE",
        target = "Lcom/mojang/renderpearl/backend/api/RenderPassBackend;setVertexBuffer(ILcom/mojang/renderpearl/api/buffers/GpuBufferSlice;)V"))
    private void fbactors$boundVertex(RenderPassBackend backend, int slot, GpuBufferSlice buffer, Operation<Void> original) {
        original.call(backend, slot, buffer); fbactors$boundVertices[slot] = buffer;
    }

    @Unique private void fbactors$capture(Consumer<RenderPass> draw) {
        var pass = (RenderPass) (Object) this;
        if (!NativeActorMasks.tracked(pass)) return;
        var bound = NativeActorMasks.boundUniforms(boundPipeline, uniforms);
        if (!NativeActorMasks.candidate(bound)) return;
        NativeActorMasks.capture(pass, new NativeActorMasks.DrawState(boundPipeline, bound,
            fbactors$boundVertices.clone(), fbactors$boundIndex, fbactors$indexType, fbactors$constants, fbactors$scissor), draw);
    }

    @WrapOperation(method = {"drawIndexed", "drawMultipleIndexed"}, at = @At(value = "INVOKE",
        target = "Lcom/mojang/renderpearl/backend/api/RenderPassBackend;drawIndexed(IIIII)V"))
    private void fbactors$indexed(RenderPassBackend backend, int a, int b, int c, int d, int e, Operation<Void> original) {
        original.call(backend, a,b,c,d,e);
        fbactors$capture(pass -> pass.drawIndexed(a,b,c,d,e));
    }
    @Inject(method = "draw", at = @At("TAIL"))
    private void fbactors$draw(int a, int b, int c, int d, CallbackInfo ci) { fbactors$capture(pass -> pass.draw(a,b,c,d)); }

    @Inject(method = "close", at = @At("TAIL"))
    private void fbactors$flush(CallbackInfo ci) { NativeActorMasks.finish((RenderPass) (Object) this); }
}
