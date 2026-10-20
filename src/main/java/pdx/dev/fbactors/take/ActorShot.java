package pdx.dev.fbactors.take;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;


public record ActorShot(
    int tick,
    double dirX,
    double dirY,
    double dirZ,
    float power,
    @Nullable String itemId,
    @Nullable UUID target
) {

    private static final float MAX_SPEED = 8.0f;

    public ActorShot {
        tick = Math.max(0, tick);


        power = Float.isFinite(power) ? Mth.clamp(power, 0.0f, MAX_SPEED) : 0.0f;


        double lengthSquared = dirX * dirX + dirY * dirY + dirZ * dirZ;
        if (!Double.isFinite(lengthSquared) || lengthSquared < 1.0e-9) {


            dirX = 0.0;
            dirY = 1.0;
            dirZ = 0.0;
            power = 0.0f;
        } else {
            double length = Math.sqrt(lengthSquared);
            dirX /= length;
            dirY /= length;
            dirZ /= length;
        }


        if (itemId != null && itemId.isEmpty()) {
            itemId = null;
        }
    }


    public Vec3 velocity() {
        return new Vec3(dirX, dirY, dirZ).scale(power);
    }
}
