package pdx.dev.fbactors.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.moulberry.flashback.editor.ui.CustomImGuiImplB3D;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import pdx.dev.fbactors.ui.UiClipGuard;

@Mixin(value=CustomImGuiImplB3D.class,remap=false)
public class MixinImGuiScissor {
    @Unique private final UiClipGuard fbactors$clip=new UiClipGuard();

    @WrapOperation(method="renderDrawData",at=@At(value="INVOKE",
        target="Lcom/mojang/renderpearl/api/commands/RenderPass;enableScissor(IIII)V"))
    private void fbactors$scissor(RenderPass pass,int x,int y,int width,int height,Operation<Void> original) {
        if(fbactors$clip.scissor(width,height)) original.call(pass,x,y,width,height);
    }

    @WrapOperation(method="renderDrawData",at=@At(value="INVOKE",
        target="Lcom/mojang/renderpearl/api/commands/RenderPass;drawIndexed(IIIII)V"))
    private void fbactors$draw(RenderPass pass,int elements,int instances,int firstIndex,int vertexOffset,int firstInstance,Operation<Void> original) {
        if(fbactors$clip.draw(elements,instances)) original.call(pass,elements,instances,firstIndex,vertexOffset,firstInstance);
    }
}
