package pdx.dev.fbactors.mixin;

import com.moulberry.flashback.editor.ui.windows.TimelineWindow;
import imgui.moulberry90.ImGui;
import org.joml.Vector2f;
import pdx.dev.fbactors.FlashbackActors;
import pdx.dev.fbactors.ui.ActorTrack;
import pdx.dev.fbactors.ui.TimelineGeometry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = TimelineWindow.class, remap = false)
public class MixinTimelineWindow {

    private static final int NO_SCROLLBAR = 8;

    @Shadow
    private static float width;

    @Shadow
    private static float height;

    @Shadow
    private static int middleY;

    @Shadow
    private static int timelineOffset;

    @Shadow
    private static int minTicks;

    @Shadow
    private static float availableTicks;

    @Shadow
    private static float timelineWidth;

    @Shadow
    private static float y;

    @Shadow
    private static Vector2f dragSelectOrigin;

    @Inject(method = "renderKeyframeElements",
        at = @At(value = "CONSTANT", args = "stringValue=flashback.add_element"))
    private static void fbactors$renderActorTracks(float trackX, float trackY, int cursorTicks,
                                                   int trackMiddleX, CallbackInfo ci) {
        TimelineGeometry geometry = new TimelineGeometry(trackX, width, y + middleY, y + height,
            trackMiddleX, timelineOffset, minTicks, availableTicks, timelineWidth);
        ActorTrack.render(geometry, cursorTicks);
        if (ActorTrack.isDragging()) {
            dragSelectOrigin = null;
        }
    }

    @ModifyVariable(method = "renderInner", at = @At("STORE"), name = "totalTrackHeight",
        require = 0)
    private static float fbactors$includeActorRows(float totalTrackHeight) {
        float lineHeight = ImGui.getTextLineHeightWithSpacing() + ImGui.getStyle().getItemSpacingY();
        return totalTrackHeight + (FlashbackActors.ACTORS.actors().size() + 1) * lineHeight;
    }

    @ModifyArg(method = "renderInner",
        at = @At(value = "INVOKE", target = "Limgui/moulberry90/ImGui;beginChild(Ljava/lang/String;FFZI)Z"),
        index = 4, require = 0)
    private static int fbactors$allowTrackScrollbar(int flags) {
        return flags & ~NO_SCROLLBAR;
    }
}

