package pdx.dev.fbactors.mixin;

import net.minecraft.world.entity.LivingEntity;
import pdx.dev.fbactors.swing.DamageStateAccess;
import pdx.dev.fbactors.swing.JumpAccess;
import pdx.dev.fbactors.swing.SwapPenaltyAccess;
import pdx.dev.fbactors.swing.SwingAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity implements SwingAccess, JumpAccess, DamageStateAccess, SwapPenaltyAccess {

    @Shadow
    public boolean swinging;

    @Shadow
    protected abstract void updateSwingTime();

    @Shadow
    protected boolean jumping;

    @Shadow
    protected int attackStrengthTicker;

    @Shadow
    protected int itemSwapTicker;

    @Shadow
    protected float lastHurt;

    @Override
    public boolean fbactors$jumpHeld() {
        return this.jumping;
    }

    @Override
    public void fbactors$setJumpHeld(boolean jumping) {
        this.jumping = jumping;
    }

    @Override
    public boolean fbactors$isSwinging() {
        return this.swinging;
    }

    @Override
    public void fbactors$tickSwing() {
        this.updateSwingTime();
    }

    @Override
    public int fbactors$attackStrengthTicker() {
        return this.attackStrengthTicker;
    }

    @Override
    public void fbactors$setAttackStrengthTicker(int ticker) {
        this.attackStrengthTicker = ticker;
    }

    @Override
    public float fbactors$lastHurt() {
        return this.lastHurt;
    }

    @Override
    public void fbactors$setLastHurt(float amount) {
        this.lastHurt = amount;
    }

    @Override
    public void fbactors$setItemSwapTicker(int ticks) {
        this.itemSwapTicker = ticks;
    }
}