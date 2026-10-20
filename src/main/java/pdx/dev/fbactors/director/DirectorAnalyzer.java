package pdx.dev.fbactors.director;

import pdx.dev.fbactors.FlashbackActors;
import pdx.dev.fbactors.actor.Actor;
import pdx.dev.fbactors.emote.Emotes;
import pdx.dev.fbactors.take.ActorBlockEdit;
import pdx.dev.fbactors.take.ActorDeath;
import pdx.dev.fbactors.take.ActorEvent;
import pdx.dev.fbactors.take.ActorFrame;
import pdx.dev.fbactors.take.ActorShot;
import pdx.dev.fbactors.take.ActorTake;
import pdx.dev.fbactors.take.DirectorConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


public final class DirectorAnalyzer {


    public enum MomentKind {
        MOVEMENT_START,
        MOVEMENT_STOP,
        COMBAT,
        ITEM_USE,
        DEATH,
        SHOT_FIRED,
        BLOCK_BREAK,
        EMOTE_START,
        EMOTE_END
    }


    public record Moment(int tick, UUID actorId, MomentKind kind, float priority) {
    }


    private static final double MOVING_EPSILON = 0.03;


    private static final int STILL_TICKS = 4;


    private static final int EMOTE_SAMPLE_STEP = 2;

    private DirectorAnalyzer() {
    }


    public static List<Moment> analyze(List<Actor> actors, DirectorConfig config, DirectorStyle style) {
        List<Moment> out = new ArrayList<>();
        for (Actor actor : actors) {
            ActorTake take = actor.take;
            DirectorConfig.ActorSelection selection = config.selectionFor(take.id);
            if (selection == null) {
                continue;
            }
            if (take.frames.isEmpty()) {
                FlashbackActors.LOGGER.warn(
                    "Director: actor '{}' has no frames and is skipped in analysis", actor.displayName());
                continue;
            }
            float scale = (0.5f + selection.weight()) * (selection.primary() ? 1.5f : 1.0f);
            movementMoments(out, take, scale, style);
            eventMoments(out, take, scale, style);
            discreteMoments(out, take, scale, style);
            emoteMoments(out, take, scale, style);
        }
        return out;
    }


    public static float basePriority(MomentKind kind) {
        return switch (kind) {
            case DEATH -> 10.0f;
            case COMBAT -> 7.0f;
            case SHOT_FIRED -> 6.0f;
            case MOVEMENT_START -> 5.0f;
            case BLOCK_BREAK -> 4.0f;
            case ITEM_USE -> 3.0f;
            case EMOTE_START -> 3.0f;
            case MOVEMENT_STOP -> 2.0f;
            case EMOTE_END -> 1.0f;
        };
    }

    private static void add(List<Moment> out, int tick, ActorTake take,
                            MomentKind kind, float scale, DirectorStyle style) {
        if (tick < 0) {
            return;
        }
        float priority = basePriority(kind) * scale * style.bias(kind);
        out.add(new Moment(tick, take.id, kind, priority));
    }


    private static void movementMoments(List<Moment> out, ActorTake take, float scale, DirectorStyle style) {
        int first = take.firstTick();
        int last = take.lastTick();
        boolean moving = false;
        int stillRun = 0;
        for (int tick = first + 1; tick <= last; tick++) {
            ActorFrame previous = take.frameAt(tick - 1);
            ActorFrame current = take.frameAt(tick);
            if (previous == null || current == null) {
                continue;
            }
            double dx = current.x() - previous.x();
            double dy = current.y() - previous.y();
            double dz = current.z() - previous.z();
            double speed = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (speed > MOVING_EPSILON) {
                stillRun = 0;
                if (!moving) {
                    moving = true;
                    add(out, tick, take, MomentKind.MOVEMENT_START, scale, style);
                }
            } else {
                stillRun++;
                if (moving && stillRun >= STILL_TICKS) {
                    moving = false;


                    add(out, tick - stillRun, take, MomentKind.MOVEMENT_STOP, scale, style);
                }
            }
        }
    }


    private static void eventMoments(List<Moment> out, ActorTake take, float scale, DirectorStyle style) {
        for (ActorEvent event : take.events) {
            switch (event.type()) {
                case ATTACK, HURT -> add(out, event.tick(), take, MomentKind.COMBAT, scale, style);
                case USE_ITEM -> add(out, event.tick(), take, MomentKind.ITEM_USE, scale, style);


                case SWING_MAIN, SWING_OFF -> {
                }
            }
        }
    }


    private static void discreteMoments(List<Moment> out, ActorTake take, float scale, DirectorStyle style) {
        int offset = take.offsetTicks;
        for (ActorDeath death : take.deaths) {
            add(out, death.tick() + offset, take, MomentKind.DEATH, scale, style);
        }
        for (ActorShot shot : take.shots) {
            add(out, shot.tick() + offset, take, MomentKind.SHOT_FIRED, scale, style);
        }
        for (ActorBlockEdit edit : take.blocks) {
            add(out, edit.tick() + offset, take, MomentKind.BLOCK_BREAK, scale, style);
        }
    }


    private static void emoteMoments(List<Moment> out, ActorTake take, float scale, DirectorStyle style) {
        int offset = take.offsetTicks;
        int length = take.length();
        boolean inEmote = false;
        for (int takeTick = 0; takeTick <= length; takeTick += EMOTE_SAMPLE_STEP) {
            boolean active = !Emotes.previewPose(take.id, takeTick).isIdentity();
            if (active && !inEmote) {
                inEmote = true;
                add(out, takeTick + offset, take, MomentKind.EMOTE_START, scale, style);
            } else if (!active && inEmote) {
                inEmote = false;
                add(out, takeTick + offset, take, MomentKind.EMOTE_END, scale, style);
            }
        }
    }
}

