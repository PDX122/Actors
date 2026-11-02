package pdx.dev.fbactors.mixin;

import net.minecraft.world.entity.Entity;
import pdx.dev.fbactors.swing.InvulnerabilityAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(Entity.class)
public abstract class MixinEntity implements InvulnerabilityAccess {

    @Shadow
    private int invulnerableTime;

    @Override
    public int fbactors$invulnerableTime() {
        return this.invulnerableTime;
    }

    @Override
    public void fbactors$setInvulnerableTime(int ticks) {
        this.invulnerableTime = ticks;
    }
}