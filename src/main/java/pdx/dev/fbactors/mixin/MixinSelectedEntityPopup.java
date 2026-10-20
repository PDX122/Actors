package pdx.dev.fbactors.mixin;

import com.moulberry.flashback.editor.ui.ImGuiHelper;
import com.moulberry.flashback.editor.ui.windows.SelectedEntityPopup;
import com.moulberry.flashback.state.EditorState;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.type.ImString;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import pdx.dev.fbactors.FlashbackActors;
import pdx.dev.fbactors.actor.Actor;
import pdx.dev.fbactors.actor.ActorSkins;
import pdx.dev.fbactors.ui.MorphPicker;
import pdx.dev.fbactors.ui.SkinPicker;


@Mixin(value = SelectedEntityPopup.class, remap = false)
public class MixinSelectedEntityPopup {


    @Nullable
    private static Actor fbactors$actor;


    private static boolean fbactors$skinning;


    @Inject(method = "render", at = @At("HEAD"))
    private static void fbactors$resolveActor(Entity entity, EditorState editorState,
                                              CallbackInfo ci) {
        fbactors$actor = FlashbackActors.ACTORS.byUuid(entity.getUUID());
        fbactors$skinning = false;
    }


    @Inject(method = "render", at = @At("RETURN"))
    private static void fbactors$forgetActor(Entity entity, EditorState editorState,
                                             CallbackInfo ci) {
        fbactors$actor = null;
        fbactors$skinning = false;
    }


    @Redirect(method = "render",
        at = @At(value = "INVOKE",
            target = "Lcom/moulberry/flashback/editor/ui/ImGuiHelper;separatorWithText(Ljava/lang/String;)V"),
        require = 0)
    private static void fbactors$skinHeader(String text) {
        Actor actor = fbactors$actor;
        if (actor == null) {
            ImGuiHelper.separatorWithText(text);
            return;
        }
        fbactors$skinning = true;
        ImGui.separator();
        if (ImGui.button("Change skin...##ActorSkinOpen")) {
            ImGui.closeCurrentPopup();
            SkinPicker.open(actor);
        }
        if (pdx.dev.fbactors.settings.FbSettings.morphEnabled()) {
            ImGui.sameLine();
            if (ImGui.button("Change morph...##ActorMorphOpen")) {
                ImGui.closeCurrentPopup();
                MorphPicker.open(actor);
            }
        }


        ImGui.sameLine();
        String model = ActorSkins.modelName(actor);
        String modelLabel = model == null ? "Slim arms"
            : model.equals("Slim") ? "Classic arms" : "Auto arms";
        if (ImGui.button(modelLabel + "##ActorModelToggle")) {
            Boolean next = model == null ? Boolean.TRUE
                : model.equals("Slim") ? Boolean.FALSE : null;
            if (!ActorSkins.setModel(actor, next)) {
                ImGui.closeCurrentPopup();
                SkinPicker.open(actor);
            }
        }
    }


    @Redirect(method = "render",
        at = @At(value = "INVOKE", target = "Limgui/moulberry90/ImGui;setNextItemWidth(F)V"),
        require = 0)
    private static void fbactors$skipSkinWidth(float width) {
        if (!fbactors$skinning) {
            ImGui.setNextItemWidth(width);
        }
    }


    @Redirect(method = "render",
        at = @At(value = "INVOKE",
            target = "Limgui/moulberry90/ImGui;inputTextWithHint(Ljava/lang/String;Ljava/lang/String;Limgui/moulberry90/type/ImString;)Z"),
        require = 0)
    private static boolean fbactors$skipSkinInput(String id, String hint, ImString value) {
        if (fbactors$skinning) {
            return false;
        }
        return ImGui.inputTextWithHint(id, hint, value);
    }


    @Redirect(method = "render",
        at = @At(value = "INVOKE", target = "Limgui/moulberry90/ImGui;button(Ljava/lang/String;)Z"),
        require = 0)
    private static boolean fbactors$skipSkinButtons(String label) {
        if (fbactors$skinning) {
            return false;
        }
        return ImGui.button(label);
    }
}

