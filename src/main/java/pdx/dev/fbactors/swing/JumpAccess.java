package pdx.dev.fbactors.swing;

import net.minecraft.world.entity.LivingEntity;


public interface JumpAccess {

    boolean fbactors$jumpHeld();

    void fbactors$setJumpHeld(boolean jumping);

    static boolean jumpHeld(LivingEntity entity) {
        return ((JumpAccess) entity).fbactors$jumpHeld();
    }

    static void setJumpHeld(LivingEntity entity, boolean jumping) {
        ((JumpAccess) entity).fbactors$setJumpHeld(jumping);
    }
}

