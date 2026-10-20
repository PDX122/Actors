package pdx.dev.fbactors.actor;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import pdx.dev.fbactors.FlashbackActors;
import pdx.dev.fbactors.take.ActorShot;


public final class ActorArrows {

    private ActorArrows() {
    }

    public static boolean fire(ServerLevel level, ActorClonePlayer clone, ActorShot shot) {


        if (shot.power() <= 0.0f) {
            return false;
        }

        try {
            Projectile arrow = create(level, clone);
            if (arrow == null) {
                FlashbackActors.LOGGER.warn(
                    "Actor '{}' had an arrow to fire and no ArrowItem to build it from",
                    clone.getName().getString());
                return false;
            }


            arrow.setOwner(clone);
            arrow.setDeltaMovement(shot.velocity());

            return level.addFreshEntity(arrow);
        } catch (RuntimeException | LinkageError failure) {


            FlashbackActors.LOGGER.warn(
                "Actor '{}' tried to fire and dropped it: {}",
                clone.getName().getString(), failure.toString());
            return false;
        }
    }


    private static Projectile create(ServerLevel level, LivingEntity shooter) {
        Item item = Items.ARROW;
        if (!(item instanceof ArrowItem arrowItem)) {
            return null;
        }
        ItemStack arrow = new ItemStack(item);
        return arrowItem.createArrow(level, arrow, shooter, arrow);
    }
}
