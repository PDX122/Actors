package pdx.dev.fbactors.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import pdx.dev.fbactors.FlashbackActors;
import pdx.dev.fbactors.actor.ActorWorldSimulation;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

@Mixin(Level.class)
public class MixinActorMechanismQuery {
    @Inject(method = "getEntities(Lnet/minecraft/world/level/entity/EntityTypeTest;Lnet/minecraft/world/phys/AABB;Ljava/util/function/Predicate;)Ljava/util/List;",
        at = @At("RETURN"), cancellable = true)
    private <T extends Entity> void fbactors$mechanismActors(EntityTypeTest<Entity, T> type, AABB box,
        Predicate<? super T> predicate, CallbackInfoReturnable<List<T>> ci) {
        if (!ActorWorldSimulation.isSimulating()) return;
        List<T> result = null;
        for (var actor : FlashbackActors.ACTORS.actors()) {
            var clone = actor.clone();
            if (clone == null || clone.level() != (Object)this || clone.isRemoved() || !clone.getBoundingBox().intersects(box)) continue;
            T match = type.tryCast(clone);
            if (match == null || !predicate.test(match) || ci.getReturnValue().contains(match)) continue;
            if (result == null) result = new ArrayList<>(ci.getReturnValue());
            result.add(match);
        }
        if (result != null) ci.setReturnValue(result);
    }
}
