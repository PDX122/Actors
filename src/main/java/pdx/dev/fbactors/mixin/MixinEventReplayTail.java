package pdx.dev.fbactors.mixin;

import com.moulberry.flashback.playback.ReplayServer;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import pdx.dev.fbactors.events.Events;

/** Continue presentation time over the final recorded frame, without reading beyond the archive. */
@Mixin(value=ReplayServer.class,remap=false)
public class MixinEventReplayTail {
    @Shadow @Final private int totalTicks;
    @Shadow private int currentTick;
    @Shadow private volatile int targetTick;
    @Shadow public volatile int jumpToTick;
    @Unique private int fbactors$pendingTail=-1;
    @Inject(method="getTotalReplayTicks",at=@At("RETURN"),cancellable=true)
    private void fbactors$length(CallbackInfoReturnable<Integer> cir) {cir.setReturnValue(Events.playbackEnd(cir.getReturnValue()));}
    @Redirect(method="tickServer",at=@At(value="FIELD",target="Lcom/moulberry/flashback/playback/ReplayServer;totalTicks:I"))
    private int fbactors$presentationEnd(ReplayServer owner) {return Events.playbackEnd(totalTicks);}
    @Inject(method="tickServer",at=@At("HEAD"))
    private void fbactors$seekFinalFrame(CallbackInfo ci) {
        if(jumpToTick>totalTicks && currentTick<totalTicks) {fbactors$pendingTail=jumpToTick;jumpToTick=totalTicks;}
    }
    @Inject(method="tickServer",at=@At("TAIL"))
    private void fbactors$seekTail(CallbackInfo ci) {
        if(fbactors$pendingTail>=0 && currentTick>=totalTicks) {jumpToTick=fbactors$pendingTail;fbactors$pendingTail=-1;}
    }
    @Inject(method="runUpdates",at=@At("HEAD"),cancellable=true)
    private void fbactors$holdRecordedWorld(CallbackInfo ci) {
        if(targetTick>totalTicks && Events.playbackEnd(totalTicks)>totalTicks) {currentTick=targetTick;ci.cancel();}
    }
}
