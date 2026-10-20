package pdx.dev.fbactors.mixin;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pdx.dev.fbactors.actor.ActorMode;


@Mixin(PauseScreen.class)
public class MixinPauseScreenWhileRecording {

    @Inject(method = "tick", at = @At("HEAD"))
    private void fbactors$keepViewerVisible(CallbackInfo ci) {
        if (!ActorMode.isActive()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.getConnection() == null) {
            return;
        }


        if (mc.player.isInvisible()) {
            mc.player.setInvisible(false);
        }
    }
}

