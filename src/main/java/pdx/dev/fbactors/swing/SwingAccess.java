package pdx.dev.fbactors.swing;

import net.minecraft.world.entity.LivingEntity;

public interface SwingAccess {

    boolean fbactors$isSwinging();

    void fbactors$tickSwing();

    static boolean isSwinging(LivingEntity entity) {
        return ((SwingAccess) entity).fbactors$isSwinging();
    }

    static void tickSwing(LivingEntity entity) {
        ((SwingAccess) entity).fbactors$tickSwing();
    }
}

