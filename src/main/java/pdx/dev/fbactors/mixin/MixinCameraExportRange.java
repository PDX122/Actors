package pdx.dev.fbactors.mixin;

import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pdx.dev.fbactors.camera.BoatStudioState;
import pdx.dev.fbactors.camera.CameraExport;

@Mixin(value=EditorState.class,remap=false)
public class MixinCameraExportRange {
    @Inject(method="getFirstAndLastTicksInTracks",at=@At("RETURN"),cancellable=true)
    private void fbactors$includeCameraClips(CallbackInfoReturnable<EditorState.StartAndEnd> cir) {
        if((Object)this!=EditorStateManager.getCurrent()) return;
        var original=cir.getReturnValue();
        var range=CameraExport.range(BoatStudioState.get().timeline,original.start(),original.end());
        cir.setReturnValue(new EditorState.StartAndEnd(range.start(),range.end()));
    }
}
