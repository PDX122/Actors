package pdx.dev.fbactors.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.world.entity.Entity;
import pdx.dev.fbactors.morph.CustomMobRenderBridge;

@Mixin(EntityRenderDispatcher.class)
public class MixinCustomMobRenderer {
    @ModifyArg(method="onResourceManagerReload",at=@At(value="INVOKE",target="Lnet/minecraft/client/renderer/entity/EntityRenderers;createEntityRenderers(Lnet/minecraft/client/renderer/entity/EntityRendererProvider$Context;)Ljava/util/Map;"),index=0)
    private EntityRendererProvider.Context fbactors$customModels(EntityRendererProvider.Context context){CustomMobRenderBridge.context(context);return context;}
    @Inject(method="getRenderer(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/client/renderer/entity/EntityRenderer;",at=@At("HEAD"),cancellable=true)
    private void fbactors$customEntity(Entity entity,CallbackInfoReturnable<EntityRenderer> ci){var renderer=CustomMobRenderBridge.route(entity);if(renderer!=null)ci.setReturnValue(renderer);}
}
