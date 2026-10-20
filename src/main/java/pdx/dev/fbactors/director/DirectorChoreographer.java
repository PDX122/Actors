package pdx.dev.fbactors.director;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import pdx.dev.fbactors.actor.Actor;
import pdx.dev.fbactors.take.CameraHeight;
import pdx.dev.fbactors.take.CameraKeyframe;
import pdx.dev.fbactors.take.CameraMove;
import pdx.dev.fbactors.take.DirectorConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;


public final class DirectorChoreographer {


    private static final double PUSH_OVERSHOOT = 0.55;


    private static final double PASS_BY_SPAN = 1.3;


    private static final double CRANE_SPAN = 32.0;


    private static final double PAN_SPAN = 42.0;


    private static final double ORBIT_ARC = 95.0;


    private static final double FAVOR_BOOST = 2.0;


    private static final double THIRDS_FRACTION = 0.16;

    private DirectorChoreographer() {
    }


    private record Shot(
        List<Actor> actors,
        DirectorConfig config,
        DirectorStyle.ShotProfile profile,
        float fov,
        CameraKeyframe.ShotType shotType,
        UUID target,
        byte easing,
        int start,
        int end,
        @Nullable CameraHeight height,
        float side,
        @Nullable UUID favor
    ) {
        int mid() {
            return this.start + hold() / 2;
        }

        int hold() {
            return Math.max(1, this.end - this.start);
        }


        double distance() {
            return (this.profile.distanceMin() + this.profile.distanceMax()) * 0.5;
        }


        Vec3 aimAt(int tick) {
            Vec3 aim = DirectorPositioner.aimFavor(this.actors, this.config, tick,
                this.favor, FAVOR_BOOST);
            if (aim != null) {
                return lead(aim, tick);
            }
            Vec3 fallback = DirectorPositioner.aimFavor(this.actors, this.config, this.start,
                this.favor, FAVOR_BOOST);
            if (fallback != null) {
                return lead(fallback, this.start);
            }
            return Vec3.ZERO;
        }


        Vec3 lead(Vec3 aim, int tick) {
            Actor subject = DirectorShotSelector.find(this.actors, this.target);
            Vec3 offset = DirectorPositioner.leadOffset(subject, tick);
            return offset.equals(Vec3.ZERO) ? aim : aim.add(offset);
        }


        CameraKeyframe key(int tick, Vec3 camera, Vec3 aim) {
            float[] angles = DirectorPositioner.lookAngles(camera, thirds(aim, camera));
            return new CameraKeyframe(tick, camera.x, camera.y, camera.z,
                angles[0], angles[1], this.fov, this.shotType, this.target, this.easing);
        }


        CameraKeyframe keyLooking(int tick, Vec3 camera, float yaw, float pitch) {
            return new CameraKeyframe(tick, camera.x, camera.y, camera.z,
                yaw, pitch, this.fov, this.shotType, this.target, this.easing);
        }


        Vec3 thirds(Vec3 aim, Vec3 camera) {
            Vec3 offset = thirdsOffset(aim, camera, this.shotType, this.side);
            return new Vec3(aim.x + offset.x, aim.y, aim.z + offset.z);
        }


        float baseHeading(int tick) {
            float facing = facingAt(this, tick);
            return switch (this.shotType) {
                case WIDE -> facing + 55.0f * Math.signum(this.side == 0.0f ? 1.0f : this.side);
                case OVER_SHOULDER -> facing + 180.0f;
                default -> facing;
            };
        }


        Vec3 place(float heading, double distance, double elevationDegrees, Vec3 aim) {
            Vec3 point = this.height == null
                ? DirectorPositioner.orbitPoint(aim, heading, distance, elevationDegrees)
                : DirectorPositioner.orbitPointAt(aim, heading, distance, this.height.blocks());
            if (this.shotType != CameraKeyframe.ShotType.OVER_SHOULDER) {
                return point;
            }
            double[] sideways = DirectorPositioner.direction(heading + 90.0f);
            double offset = distance * DirectorPositioner.SHOULDER_OFFSET;
            return new Vec3(point.x + sideways[0] * offset, point.y, point.z + sideways[1] * offset);
        }
    }


    public static List<CameraKeyframe> choreograph(CameraMove move, List<Actor> actors,
                                                   DirectorConfig config, DirectorAnalyzer.Moment moment,
                                                   CameraKeyframe.ShotType shotType, DirectorStyle style,
                                                   int holdTicks, float side, Random rng) {
        List<CameraKeyframe> keys = new ArrayList<>(3);
        DirectorStyle.ShotProfile profile = style.profile(shotType);
        Shot shot = new Shot(actors, config, profile,
            Mth.lerp(rng.nextFloat(), profile.fovMin(), profile.fovMax()),
            shotType, moment.actorId(), style.easing(),
            moment.tick(), moment.tick() + Math.max(1, holdTicks),
            config.height(), side == 0.0f ? 1.0f : side, moment.actorId());

        if (DirectorPositioner.aim(actors, config, shot.start()) == null) {
            return keys;
        }

        switch (move) {
            case STATIC -> staticShot(keys, shot);
            case PUSH_IN -> dolly(keys, shot, false);
            case PULL_OUT -> dolly(keys, shot, true);
            case FOLLOW -> trail(keys, shot, true);
            case LEAD -> trail(keys, shot, false);
            case PASS_BY -> passBy(keys, shot);
            case ORBIT -> orbit(keys, shot);
            case CRANE -> crane(keys, shot);
            case PAN -> pan(keys, shot);
        }
        return keys;
    }


    private static float facingAt(Shot shot, int tick) {
        return DirectorPositioner.facing(DirectorShotSelector.find(shot.actors(), shot.target()), tick);
    }


    static Vec3 thirdsOffset(Vec3 aim, Vec3 camera, CameraKeyframe.ShotType shotType,
                             float side) {
        if (side == 0.0f || shotType == CameraKeyframe.ShotType.WIDE
            || shotType == CameraKeyframe.ShotType.OVER_SHOULDER) {
            return Vec3.ZERO;
        }
        double dx = aim.x - camera.x;
        double dz = aim.z - camera.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < 1.0e-4) {
            return Vec3.ZERO;
        }
        double distance = Math.sqrt(dx * dx + dz * dz
            + (aim.y - camera.y) * (aim.y - camera.y));
        double offset = distance * THIRDS_FRACTION * Math.signum(side);
        return new Vec3((-dz / horizontal) * offset, 0.0, (dx / horizontal) * offset);
    }


    private static void staticShot(List<CameraKeyframe> keys, Shot shot) {
        Vec3 startAim = shot.aimAt(shot.start());
        Vec3 camera = shot.place(shot.baseHeading(shot.start()), shot.distance(),
            shot.profile().elevationDegrees(), startAim);
        keys.add(shot.key(shot.start(), camera, startAim));
        keys.add(shot.key(shot.end(), camera, shot.aimAt(shot.end())));
    }


    private static void dolly(List<CameraKeyframe> keys, Shot shot, boolean out) {
        DirectorStyle.ShotProfile profile = shot.profile();
        double near = Math.max(1.5, profile.distanceMin());
        double far = profile.distanceMax() * (1.0 + PUSH_OVERSHOOT);

        keys.add(dollyKey(shot, shot.start(), out ? near : far,
            out ? profile.fovMin() : profile.fovMax()));
        keys.add(dollyKey(shot, shot.end(), out ? far : near,
            out ? profile.fovMax() : profile.fovMin()));
    }

    private static CameraKeyframe dollyKey(Shot shot, int tick, double distance, float fov) {
        Vec3 aim = shot.aimAt(tick);
        Vec3 camera = shot.place(shot.baseHeading(tick), distance, shot.profile().elevationDegrees(), aim);
        float[] angles = DirectorPositioner.lookAngles(camera, shot.thirds(aim, camera));
        return new CameraKeyframe(tick, camera.x, camera.y, camera.z, angles[0], angles[1],
            fov, shot.shotType(), shot.target(), shot.easing());
    }


    private static void trail(List<CameraKeyframe> keys, Shot shot, boolean behind) {
        int[] ticks = {shot.start(), shot.mid(), shot.end()};
        for (int tick : ticks) {
            Vec3 aim = shot.aimAt(tick);
            float heading = shot.baseHeading(tick) + (behind ? 180.0f : 0.0f);
            Vec3 camera = shot.place(heading, shot.distance(), shot.profile().elevationDegrees(), aim);
            keys.add(shot.key(tick, camera, aim));
        }
    }


    private static void passBy(List<CameraKeyframe> keys, Shot shot) {
        double span = shot.distance() * PASS_BY_SPAN;
        float direction = (float) Math.signum(shot.side() == 0.0f ? 1.0f : shot.side());
        for (int step = 0; step <= 2; step++) {
            double t = step / 2.0;
            int tick = (int) Math.round(Mth.lerp(t, shot.start(), shot.end()));
            Vec3 aim = shot.aimAt(tick);
            float facing = shot.baseHeading(tick);
            Vec3 front = shot.place(facing, shot.distance(), shot.profile().elevationDegrees(), aim);
            double[] sideways = DirectorPositioner.direction(facing + 90.0f);
            double lateral = Mth.lerp(t, -span, span) * direction;
            Vec3 camera = new Vec3(front.x + sideways[0] * lateral, front.y, front.z + sideways[1] * lateral);
            keys.add(shot.key(tick, camera, aim));
        }
    }


    private static void orbit(List<CameraKeyframe> keys, Shot shot) {
        double radius = shot.distance() * 1.15;
        float direction = (float) Math.signum(shot.side() == 0.0f ? 1.0f : shot.side());
        for (int step = 0; step <= 2; step++) {
            double t = step / 2.0;
            int tick = (int) Math.round(Mth.lerp(t, shot.start(), shot.end()));
            Vec3 aim = shot.aimAt(tick);
            float heading = shot.baseHeading(tick) + direction * (40.0f + (float) (ORBIT_ARC * t));
            Vec3 camera = shot.place(heading, radius, shot.profile().elevationDegrees(), aim);
            keys.add(shot.key(tick, camera, aim));
        }
    }


    private static void crane(List<CameraKeyframe> keys, Shot shot) {
        double radius = shot.distance() * 1.25;
        double base = shot.profile().elevationDegrees();
        float direction = (float) Math.signum(shot.side() == 0.0f ? 1.0f : shot.side());
        for (int step = 0; step <= 1; step++) {
            double t = step;
            int tick = (int) Math.round(Mth.lerp(t, shot.start(), shot.end()));
            Vec3 aim = shot.aimAt(tick);
            float heading = shot.baseHeading(tick) + direction * 55.0f;
            Vec3 camera = shot.place(heading, radius, base - CRANE_SPAN + CRANE_SPAN * 2.0 * t, aim);
            keys.add(shot.key(tick, camera, aim));
        }
    }


    private static void pan(List<CameraKeyframe> keys, Shot shot) {
        Vec3 startAim = shot.aimAt(shot.start());
        float direction = (float) Math.signum(shot.side() == 0.0f ? 1.0f : shot.side());
        float heading = shot.baseHeading(shot.start()) + direction * 55.0f;
        Vec3 camera = shot.place(heading, shot.distance() * 1.4,
            shot.profile().elevationDegrees(), startAim);
        float[] base = DirectorPositioner.lookAngles(camera, shot.thirds(startAim, camera));
        keys.add(shot.keyLooking(shot.start(), camera, base[0] - direction * (float) PAN_SPAN, base[1]));
        keys.add(shot.keyLooking(shot.end(), camera, base[0] + direction * (float) PAN_SPAN, base[1]));
    }
}

