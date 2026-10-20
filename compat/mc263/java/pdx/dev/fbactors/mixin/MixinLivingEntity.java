package pdx.dev.fbactors.mixin;

import net.minecraft.world.entity.LivingEntity;
import pdx.dev.fbactors.swing.DamageStateAccess;
import pdx.dev.fbactors.swing.JumpAccess;
import pdx.dev.fbactors.swing.SwapPenaltyAccess;
import pdx.dev.fbactors.swing.SwingAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

// 26.3 replaced the `swinging` field and `updateSwingTime()` with a private
// final `swingState` object, so it needs its own mixin body. Verified against
// the 26.3 mapped jar: `LivingEntity` declares `private final SwingState
// swingState` and no `swinging` field and no `updateSwingTime` method at all, so
// the eight other targets' copies would fail to apply here. Kept in the
// per-version dir because javac rejects two source roots declaring the same
// class, so the eight other targets carry their own identical copy.
//
// `attackStrengthTicker`, `itemSwapTicker` and `lastHurt` are unaffected by that
// rework and are still three plain protected fields here, which is the only
// reason one mixin class can still cover the whole damage-state group on 26.3.
//
// `SwapPenaltyAccess` is here rather than folded into `DamageStateAccess`
// because `itemSwapTicker` is the one field in this group that came and went: it
// was removed with the swap penalty in the 1.21.2 combat changes and only
// returned in 1.21.11. A single interface covering all of these fields would
// fail to apply on 1.21.1 through 1.21.10.
//
// The i-frame window is not here; see `MixinEntity`.
@Mixin(LivingEntity.class)
public abstract class MixinLivingEntity implements SwingAccess, JumpAccess, DamageStateAccess, SwapPenaltyAccess {

    @Shadow
    private LivingEntity.SwingState swingState;

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
        return this.swingState.isSwinging();
    }

    @Override
    public void fbactors$tickSwing() {
        this.swingState.tick();
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