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
 * <p>This is the one copy of the mixin where the alias is not just tidiness.
 * On the other eight targets the field is public and mod code could reach it
 * without help; 26.3 made it private, so on this target there is nothing outside
 * {@code Entity} left to read it. Without the shadow, restoring an actor inside
 * a still-running i-frame window silently swallows the punch that was supposed
 * to land, and there is no accessor and no public field left to notice it with.
 * The shadow is declared private to match: a public shadow of a private field
 * fails to apply.
 *
 * <p>Duplicated per version for the same reason as {@code MixinLivingEntity}:
 * javac rejects two source roots declaring the same class. The only difference
 * between the copies is this visibility.
 */
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