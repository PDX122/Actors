package pdx.dev.fbactors.mixin;

import net.minecraft.world.entity.Entity;
import pdx.dev.fbactors.swing.InvulnerabilityAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/**
 * The {@code Entity} half of the hidden-damage-state duck interface.
 *
 * <p>{@code invulnerableTime} is declared on {@code Entity}, not on
 * {@code LivingEntity}, and that is the whole reason it cannot live in
 * {@code DamageStateAccess} next to {@code attackStrengthTicker} and
 * {@code lastHurt}. A mixin class has exactly one target, so a single interface
 * spanning two owner classes could only be implemented by one of them and the
 * other half would stay unreachable: {@code MixinLivingEntity} shadows the two
 * fields that really are on {@code LivingEntity}, this one shadows the field
 * that is not.
 *
 * <p>The i-frame window is worth aliasing even though the field is public on
 * this target. Vanilla decrements it in {@code Entity.baseTick} and restores it
 * wholesale when an entity is re-added to the world, so an actor teleported back
 * to the position it was recorded at arrives with whatever window the boundary
 * left behind and swallows the punch it is supposed to take. Callers need to
 * zero it before the swing, and reading a public field directly would hide that
 * this is a two-sided handshake with vanilla rather than a plain read.
 *
 * <p>Duplicated per version for the same reason as {@code MixinLivingEntity}:
 * javac rejects two source roots declaring the same class. The only difference
 * between the copies is the visibility of the shadow, which has to track the
 * target: 26.3 made the field private, so its copy declares the shadow private
 * and the rest declare it public.
 */
@Mixin(Entity.class)
public abstract class MixinEntity implements InvulnerabilityAccess {

    @Shadow
    public int invulnerableTime;

    @Override
    public int fbactors$invulnerableTime() {
        return this.invulnerableTime;
    }

    @Override
    public void fbactors$setInvulnerableTime(int ticks) {
        this.invulnerableTime = ticks;
    }
}