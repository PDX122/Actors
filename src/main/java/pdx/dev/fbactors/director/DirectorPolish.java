package pdx.dev.fbactors.director;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import pdx.dev.fbactors.actor.Actor;
import pdx.dev.fbactors.take.CameraKeyframe;
import pdx.dev.fbactors.take.CameraTrack;
import pdx.dev.fbactors.take.DirectorConfig;

import java.util.List;
import java.util.Random;


public final class DirectorPolish {


    private static final double COLLISION_STEP = 0.5;


    private static final double SHAKE_BLOCKS = 0.12;

    private DirectorPolish() {
    }


    public static void apply(CameraTrack track, DirectorStyle style, DirectorConfig config,
                             List<Actor> actors, @Nullable Level level, Random rng) {
        if (track.isEmpty()) {
            return;
        }
        boolean shake = config.handheld() && style.shakeIntensity() > 0.0f;
        if (level == null && !shake) {
            return;
        }

        for (int i = 0; i < track.keyframes.size(); i++) {
            CameraKeyframe keyframe = track.keyframes.get(i);
            double x = keyframe.x();
            double y = keyframe.y();
            double z = keyframe.z();

            if (level != null) {
                Vec3 aim = DirectorPositioner.centroid(actors, config, keyframe.tick());
                if (aim != null) {
                    Vec3 pulled = avoid(aim, new Vec3(x, y, z), level);
                    x = pulled.x;
                    y = pulled.y;
                    z = pulled.z;
                }
            }

            if (shake) {
                double amount = SHAKE_BLOCKS * style.shakeIntensity();
                x += (rng.nextDouble() * 2.0 - 1.0) * amount;
                y += (rng.nextDouble() * 2.0 - 1.0) * amount;
                z += (rng.nextDouble() * 2.0 - 1.0) * amount;
            }

            if (x != keyframe.x() || y != keyframe.y() || z != keyframe.z()) {
                track.keyframes.set(i, new CameraKeyframe(keyframe.tick(), x, y, z,
                    keyframe.yaw(), keyframe.pitch(), keyframe.fov(), keyframe.shotType(),
                    keyframe.targetActorId(), keyframe.easing()));
            }
        }
    }


    private static Vec3 avoid(Vec3 aim, Vec3 camera, Level level) {
        Vec3 delta = camera.subtract(aim);
        double length = delta.length();
        if (length < 1.0) {
            return camera;
        }
        Vec3 step = delta.scale(1.0 / length);
        double safe = length;
        for (double distance = COLLISION_STEP; distance <= length; distance += COLLISION_STEP) {
            Vec3 point = aim.add(step.scale(distance));
            BlockPos pos = BlockPos.containing(point.x, point.y, point.z);
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                safe = Math.max(COLLISION_STEP, distance - COLLISION_STEP);
                break;
            }
        }
        return safe >= length ? camera : aim.add(step.scale(safe));
    }
}

