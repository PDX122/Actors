package pdx.dev.fbactors.director;

import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import pdx.dev.fbactors.actor.Actor;
import pdx.dev.fbactors.take.ActorFrame;
import pdx.dev.fbactors.take.ActorTake;
import pdx.dev.fbactors.take.CameraKeyframe;
import pdx.dev.fbactors.take.CameraMove;
import pdx.dev.fbactors.take.DirectorConfig;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.UUID;


public final class DirectorShotSelector {


    private static final double WIDE_SPREAD = 8.0;


    private static final double INTERACTION_SPREAD = 2.0;


    private static final double TRACKING_SPEED = 4.0;


    private static final double MOVING_SUBJECT_SPEED = 0.06;

    private DirectorShotSelector() {
    }


    public static List<DirectorAnalyzer.Moment> select(List<DirectorAnalyzer.Moment> moments,
                                                       DirectorStyle style, int holdTicks) {


        int gap = Math.max(style.minGapTicks(), holdTicks);
        List<DirectorAnalyzer.Moment> sorted = new ArrayList<>(moments);
        sorted.sort(Comparator.comparingDouble(DirectorAnalyzer.Moment::priority).reversed());

        List<DirectorAnalyzer.Moment> chosen = new ArrayList<>();
        for (DirectorAnalyzer.Moment moment : sorted) {
            if (chosen.size() >= style.maxMoments()) {
                break;
            }
            boolean tooClose = false;
            for (DirectorAnalyzer.Moment kept : chosen) {
                if (Math.abs(kept.tick() - moment.tick()) < gap) {
                    tooClose = true;
                    break;
                }
            }
            if (!tooClose) {
                chosen.add(moment);
            }
        }
        chosen.sort(Comparator.comparingInt(DirectorAnalyzer.Moment::tick));
        return chosen;
    }


    public static CameraMove chooseMove(List<Actor> actors, DirectorAnalyzer.Moment moment,
                                        DirectorStyle style, DirectorConfig config, Random rng) {
        boolean moving = speedOf(actors, moment) > MOVING_SUBJECT_SPEED;

        CameraMove forced = config.move();
        if (forced != null) {
            return forced.needsMovingSubject() && !moving ? CameraMove.STATIC : forced;
        }

        List<CameraMove> candidates = new ArrayList<>(CameraMove.values().length);
        for (CameraMove candidate : CameraMove.values()) {
            if (candidate.needsMovingSubject() && !moving) {
                continue;
            }
            candidates.add(candidate);
        }
        if (candidates.isEmpty()) {
            return CameraMove.STATIC;
        }

        float total = 0.0f;
        for (CameraMove move : candidates) {
            total += style.moveBias(move);
        }
        if (total <= 0.0f) {
            return candidates.get(0);
        }
        float roll = rng.nextFloat() * total;
        for (CameraMove move : candidates) {
            roll -= style.moveBias(move);
            if (roll <= 0.0f) {
                return move;
            }
        }
        return candidates.get(candidates.size() - 1);
    }


    public static CameraKeyframe.ShotType choose(List<Actor> actors,
                                                 DirectorAnalyzer.Moment moment,
                                                 DirectorStyle style, DirectorConfig config,
                                                 Random rng) {
        return chooseTakes(takesOf(actors), moment, style, config, rng);
    }


    public static CameraKeyframe.ShotType chooseTakes(List<ActorTake> takes,
                                                      DirectorAnalyzer.Moment moment,
                                                      DirectorStyle style, DirectorConfig config,
                                                      Random rng) {
        List<CameraKeyframe.ShotType> plausible = plausibleTakes(takes, moment);
        CameraKeyframe.ShotType forced = config == null ? null : config.shotType();
        if (forced != null && plausible.contains(forced)) {
            return forced;
        }
        float total = 0.0f;
        for (CameraKeyframe.ShotType type : plausible) {
            total += style.profile(type).preference();
        }
        if (total <= 0.0f) {
            return plausible.get(0);
        }
        float roll = rng.nextFloat() * total;
        for (CameraKeyframe.ShotType type : plausible) {
            roll -= style.profile(type).preference();
            if (roll <= 0.0f) {
                return type;
            }
        }
        return plausible.get(plausible.size() - 1);
    }


    private static List<CameraKeyframe.ShotType> plausible(List<Actor> actors,
                                                           DirectorAnalyzer.Moment moment) {
        return plausibleTakes(takesOf(actors), moment);
    }


    static List<CameraKeyframe.ShotType> plausibleTakes(List<ActorTake> takes,
                                                        DirectorAnalyzer.Moment moment) {
        List<CameraKeyframe.ShotType> types = new ArrayList<>(3);
        List<Vec3> positions = positionsOfTakes(takes, moment.tick());
        double spread = spread(positions);
        double speed = speedOfTakes(takes, moment);


        if (moment.kind() == DirectorAnalyzer.MomentKind.DEATH) {
            types.add(CameraKeyframe.ShotType.CLOSE_UP);
            return types;
        }

        boolean fast = speed > TRACKING_SPEED;
        boolean interacting = positions.size() >= 2 && spread >= INTERACTION_SPREAD;

        if (fast) {
            types.add(CameraKeyframe.ShotType.TRACKING);
        }
        if (positions.size() <= 1) {


            boolean emotional = moment.kind() == DirectorAnalyzer.MomentKind.COMBAT
                || moment.kind() == DirectorAnalyzer.MomentKind.EMOTE_START
                || moment.kind() == DirectorAnalyzer.MomentKind.ITEM_USE;
            types.add(emotional ? CameraKeyframe.ShotType.CLOSE_UP : CameraKeyframe.ShotType.MEDIUM);
            types.add(emotional ? CameraKeyframe.ShotType.MEDIUM : CameraKeyframe.ShotType.CLOSE_UP);
            return types;
        }

        if (spread > WIDE_SPREAD) {
            types.add(CameraKeyframe.ShotType.WIDE);
        } else {
            types.add(interacting && moment.kind() == DirectorAnalyzer.MomentKind.COMBAT
                ? CameraKeyframe.ShotType.OVER_SHOULDER
                : CameraKeyframe.ShotType.MEDIUM);
            types.add(CameraKeyframe.ShotType.MEDIUM);
            if (interacting) {
                types.add(CameraKeyframe.ShotType.OVER_SHOULDER);
            }
        }
        return types;
    }


    public static List<Vec3> positions(List<Actor> actors, int tick) {
        return positionsOfTakes(takesOf(actors), tick);
    }


    public static List<Vec3> positionsOfTakes(List<ActorTake> takes, int tick) {
        List<Vec3> out = new ArrayList<>(takes.size());
        for (ActorTake take : takes) {
            ActorFrame frame = take.frameAt(tick);
            if (frame != null) {
                out.add(new Vec3(frame.x(), frame.y(), frame.z()));
            }
        }
        return out;
    }


    private static double spread(List<Vec3> positions) {
        double max = 0.0;
        for (int i = 0; i < positions.size(); i++) {
            for (int j = i + 1; j < positions.size(); j++) {
                max = Math.max(max, positions.get(i).distanceTo(positions.get(j)));
            }
        }
        return max;
    }


    private static double speedOf(List<Actor> actors, DirectorAnalyzer.Moment moment) {
        return speedOfTakes(takesOf(actors), moment);
    }


    static double speedOfTakes(List<ActorTake> takes, DirectorAnalyzer.Moment moment) {
        ActorTake take = findTake(takes, moment.actorId());
        if (take == null) {
            return 0.0;
        }
        ActorFrame current = take.frameAt(moment.tick());
        ActorFrame previous = take.frameAt(moment.tick() - 1);
        if (current == null || previous == null) {
            return 0.0;
        }
        double dx = current.x() - previous.x();
        double dy = current.y() - previous.y();
        double dz = current.z() - previous.z();
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }


    @Nullable
    static ActorTake findTake(List<ActorTake> takes, UUID takeId) {
        for (ActorTake take : takes) {
            if (take.id.equals(takeId)) {
                return take;
            }
        }
        return null;
    }

    private static List<ActorTake> takesOf(List<Actor> actors) {
        List<ActorTake> takes = new ArrayList<>(actors.size());
        for (Actor actor : actors) {
            takes.add(actor.take);
        }
        return takes;
    }


    @Nullable
    public static Actor find(List<Actor> actors, UUID actorId) {
        for (Actor actor : actors) {
            ActorTake take = actor.take;
            if (take.id.equals(actorId)) {
                return actor;
            }
        }
        return null;
    }


    static float nextSide(@Nullable UUID lastSubject, UUID subject, float side) {
        if (lastSubject != null && subject != null && !lastSubject.equals(subject)) {
            return -side;
        }
        return side;
    }
}

