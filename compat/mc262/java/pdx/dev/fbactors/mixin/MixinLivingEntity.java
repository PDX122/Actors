package pdx.dev.fbactors.mixin;

import net.minecraft.world.entity.LivingEntity;
import pdx.dev.fbactors.swing.DamageStateAccess;
import pdx.dev.fbactors.swing.JumpAccess;
import pdx.dev.fbactors.swing.SwapPenaltyAccess;
import pdx.dev.fbactors.swing.SwingAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

// `@Shadow` rather than `@Accessor`/`@Invoker`: those generate new methods, and
// Loom's identity-mapping setup for 26.x drops them before javac sees them.
// `@Shadow` only aliases members that already exist, so it survives the
// annotation processor, and Loom remaps the references into intermediary for
// the published jar. Name-based reflection cannot do this: the shipped names
// are `field_6252`/`class_1309`, so the old lookup resolved a name that only
// existed in the unremapped dev jar and threw on every installed client.
//
// `attackStrengthTicker` and `lastHurt` ride along in the same mixin rather than
// getting one each. They are both protected members of `LivingEntity` with no
// setter, so a separate interface for them would only buy a second mixin on the
// same target; the interfaces in this package are split by owner class, and both
// of these live here. `invulnerableTime` is the one that did not, which is why
// it ended up on the sibling `MixinEntity`.
//
// `SwapPenaltyAccess` is here rather than folded into `DamageStateAccess`
// because `itemSwapTicker` is the one field in this group that came and went:
// it was removed with the swap penalty in the 1.21.2 combat changes and only
// returned in 1.21.11. A single interface covering all of these fields would
// fail to apply on 1.21.1 through 1.21.10.
//
// Duplicated per version rather than shared, because 26.3 has no `swinging`
// field and javac rejects two source roots declaring the same class.
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