package pdx.dev.fbactors.mixin;

import com.moulberry.flashback.exporting.ExportJob;
import java.util.List;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value=ExportJob.class,remap=false)
public class MixinExportCredits {
    @Redirect(method="finishFrame",at=@At(value="INVOKE",target="Ljava/util/List;add(Ljava/lang/Object;)Z"))
    private boolean fbactors$credit(List<Object> lines,Object value) {
        return pdx.dev.fbactors.events.ExportCredits.add(lines,value);
    }
}
