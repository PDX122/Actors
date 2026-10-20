package pdx.dev.fbactors.director;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import pdx.dev.fbactors.actor.Actor;
import pdx.dev.fbactors.take.ActorFrame;
import pdx.dev.fbactors.take.ActorTake;
import pdx.dev.fbactors.take.DirectorConfig;

import java.util.List;


public final class DirectorPositioner {


    public static final double AIM_HEIGHT = 1.4;


    public static final double SHOULDER_OFFSET = 0.45;


    private static final double LEAD_EPSILON = 0.06;


    private static final double LEAD_TICKS = 8.0;


    private static final double LEAD_BLOCKS = 2.0;

    private DirectorPositioner() {
    }


    @Nullable
    public static Vec3 centroid(List<Actor> actors, DirectorConfig config, int tick) {
        List<ActorTake> takes = new java.util.ArrayList<>(actors.size());
        for (Actor actor : actors) {
            takes.add(actor.take);
        }
        return centroidOfTakes(takes, take -> pull(take, config), tick);
    }


    @Nullable
    static Vec3 centroidOfTakes(List<ActorTake> takes, java.util.function.ToDoubleFunction<ActorTake> pull,
                                int tick) {
        double sx = 0.0;
        double sy = 0.0;
        double sz = 0.0;
        double weight = 0.0;
        for (ActorTake take : takes) {
            ActorFrame frame = take.frameAt(tick);
            if (frame == null) {
                continue;
            }
            double pullValue = pull.applyAsDouble(take);
            sx += frame.x() * pullValue;
            sy += frame.y() * pullValue;
            sz += frame.z() * pullValue;
            weight += pullValue;
        }
        if (weight <= 0.0) {
            return null;
        }
        return new Vec3(sx / weight, sy / weight, sz / weight);
    }


    private static double pull(ActorTake take, DirectorConfig config) {
        DirectorConfig.ActorSelection selection = config.selectionFor(take.id);
        if (selection == null) {
            return 1.0;
        }
        double weight = 0.5 + selection.weight();
        if (selection.primary()) {
            weight *= 1.5;
        }
        return weight;
    }


    @Nullable
    public static Actor subject(List<Actor> actors, DirectorAnalyzer.Moment moment, DirectorConfig config) {
        Actor actor = DirectorShotSelector.find(actors, moment.actorId());
        if (actor != null) {
            return actor;
        }
        DirectorConfig.ActorSelection primary = config.primary();
        return primary == null ? null : DirectorShotSelector.find(actors, primary.actorId());
    }


    public static float facing(@Nullable Actor subject, int tick) {
        if (subject == null) {
            return 0.0f;
        }
        ActorFrame frame = subject.take.frameAt(tick);
        return frame == null ? 0.0f : frame.yRot();
    }


    public static double[] direction(float yaw) {
        double radians = Math.toRadians(yaw);
        return new double[] {-Math.sin(radians), Math.cos(radians)};
    }


    @Nullable
    public static Vec3 aim(List<Actor> actors, DirectorConfig config, int tick) {
        Vec3 centre = centroid(actors, config, tick);
        return centre == null ? null : centre.add(0.0, AIM_HEIGHT, 0.0);
    }


    @Nullable
    public static Vec3 positionOf(List<Actor> actors, @Nullable java.util.UUID actorId, int tick) {
        if (actorId == null) {
            return null;
        }
        Actor found = DirectorShotSelector.find(actors, actorId);
        if (found == null || found.take == null) {
            return null;
        }
        return positionOfTake(found.take, found.takeTickFor(tick));
    }


    @Nullable
    public static Vec3 positionOfTake(ActorTake take, int takeTick) {
        if (take == null || !take.coversTick(takeTick)) {
            return null;
        }
        ActorFrame frame = take.frameAt(takeTick);
        return frame == null ? null : new Vec3(frame.x(), frame.y() + AIM_HEIGHT, frame.z());
    }


    @Nullable
    public static Vec3 aimFavor(List<Actor> actors, DirectorConfig config, int tick,
                                @Nullable java.util.UUID favor, double boost) {
        Vec3 centre = centroidFavor(actors, config, tick, favor, boost);
        return centre == null ? null : centre.add(0.0, AIM_HEIGHT, 0.0);
    }


    @Nullable
    public static Vec3 centroidFavor(List<Actor> actors, DirectorConfig config, int tick,
                                     @Nullable java.util.UUID favor, double boost) {
        List<ActorTake> takes = new java.util.ArrayList<>(actors.size());
        for (Actor actor : actors) {
            takes.add(actor.take);
        }
        return centroidOfTakes(takes, take -> pullFavor(take, config, favor, boost), tick);
    }


    static double pullFavor(ActorTake take, DirectorConfig config,
                            @Nullable java.util.UUID favor, double boost) {
        double pull = pull(take, config);
        if (favor != null && favor.equals(take.id)) {
            pull *= Math.max(1.0, boost);
        }
        return pull;
    }


    public static Vec3 leadOffset(@Nullable Actor subject, int tick) {
        if (subject == null) {
            return Vec3.ZERO;
        }
        return leadOffsetFor(subject.take, tick);
    }


    static Vec3 leadOffsetFor(ActorTake take, int tick) {
        ActorFrame current = take.frameAt(tick);
        ActorFrame previous = take.frameAt(tick - 1);
        if (current == null || previous == null) {
            return Vec3.ZERO;
        }
        double vx = current.x() - previous.x();
        double vz = current.z() - previous.z();
        double speed = Math.sqrt(vx * vx + vz * vz);
        if (speed < LEAD_EPSILON) {
            return Vec3.ZERO;
        }
        double lead = Math.min(LEAD_BLOCKS, speed * LEAD_TICKS);
        return new Vec3(vx / speed * lead, 0.0, vz / speed * lead);
    }


    public static Vec3 orbitPoint(Vec3 aim, float heading, double distance, double elevationDegrees) {
        return orbitPointAt(aim, heading, distance,
            Math.tan(Math.toRadians(elevationDegrees)) * distance);
    }


    public static Vec3 orbitPointAt(Vec3 aim, float heading, double distance, double heightBlocks) {
        double[] offset = direction(heading);
        return new Vec3(aim.x + offset[0] * distance, aim.y + heightBlocks, aim.z + offset[1] * distance);
    }


    public static float[] lookAngles(Vec3 camera, Vec3 aim) {
        double dx = aim.x - camera.x;
        double dz = aim.z - camera.z;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(Math.atan2(camera.y - aim.y, Math.max(1.0e-4, horizontal)));
        return new float[] {yaw, pitch};
    }

}

