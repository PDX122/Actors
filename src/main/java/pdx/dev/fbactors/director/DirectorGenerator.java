package pdx.dev.fbactors.director;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import pdx.dev.fbactors.FlashbackActors;
import pdx.dev.fbactors.actor.Actor;
import pdx.dev.fbactors.take.ActorTake;
import pdx.dev.fbactors.take.CameraKeyframe;
import pdx.dev.fbactors.take.CameraMove;
import pdx.dev.fbactors.take.CameraTrack;
import pdx.dev.fbactors.take.DirectorConfig;
import pdx.dev.fbactors.take.DirectorShot;
import com.moulberry.flashback.keyframe.interpolation.InterpolationType;
import com.moulberry.flashback.keyframe.types.CameraKeyframeType;
import com.moulberry.flashback.keyframe.types.FOVKeyframeType;
import com.moulberry.flashback.state.EditorScene;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import com.moulberry.flashback.state.KeyframeTrack;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;


public final class DirectorGenerator {


    public static final int MAX_ACTORS = 10;


    public static final String TRACK_PREFIX = "Director - ";


    private static final String DIRECTOR_WORD = "Director";

    private DirectorGenerator() {
    }


    public static CameraTrack generate(List<Actor> allActors, DirectorConfig config,
                                       @Nullable Level level) {
        return generate(allActors, config, level,
            level == null ? -1 : currentReplayTick());
    }


    public static CameraTrack generate(List<Actor> allActors, DirectorConfig config,
                                       @Nullable Level level, int replayTick) {
        CameraTrack track = new CameraTrack();
        if (!config.valid()) {
            return track;
        }
        DirectorStyle style = DirectorStyle.of(config.style());
        List<Actor> actors = selectedActors(allActors, config);
        Set<UUID> marks = appendPlayerMarks(actors, config, level, replayTick);
        if (actors.isEmpty()) {
            FlashbackActors.LOGGER.warn("Director: no selected actor has a usable take");
            return track;
        }

        Random rng = seeded(config);
        if (!config.shots().isEmpty()) {


            generateShots(track, actors, config, style, rng);
        } else {
            int hold = config.holdTicks();
            List<DirectorAnalyzer.Moment> moments =
                DirectorAnalyzer.analyze(actors, config, style);
            addPlayerMoments(moments, marks, replayTick);
            if (moments.isEmpty()) {
                FlashbackActors.LOGGER.warn(
                    "Director: no moments found in the selected actors' takes");
                return track;
            }
            List<DirectorAnalyzer.Moment> chosen =
                DirectorShotSelector.select(moments, style, hold);


            float side = rng.nextBoolean() ? 1.0f : -1.0f;
            java.util.UUID lastSubject = null;
            for (DirectorAnalyzer.Moment moment : chosen) {
                side = DirectorShotSelector.nextSide(lastSubject, moment.actorId(), side);
                lastSubject = moment.actorId();
                CameraKeyframe.ShotType type =
                    DirectorShotSelector.choose(actors, moment, style, config, rng);
                CameraMove move =
                    DirectorShotSelector.chooseMove(actors, moment, style, config, rng);


                for (CameraKeyframe keyframe : DirectorChoreographer.choreograph(
                        move, actors, config, moment, type, style, hold, side, rng)) {
                    track.add(keyframe);
                }
            }
        }

        track.sort();
        DirectorInterpolator.apply(track, style, config);
        DirectorPolish.apply(track, style, config, actors, level, rng);
        return track;
    }


    private static List<Actor> selectedActors(List<Actor> allActors, DirectorConfig config) {
        List<Actor> selected = new ArrayList<>();
        for (Actor actor : allActors) {
            if (config.selectionFor(actor.take.id) != null && !actor.take.frames.isEmpty()) {
                selected.add(actor);
            }
        }
        selected.sort(Comparator.comparingDouble(
            (Actor actor) -> {
                DirectorConfig.ActorSelection selection = config.selectionFor(actor.take.id);
                return selection == null ? 0.0 : selection.weight();
            }).reversed());
        if (selected.size() > MAX_ACTORS) {
            FlashbackActors.LOGGER.warn("Director: {} selected actors, using the {} heaviest",
                selected.size(), MAX_ACTORS);
            selected = new ArrayList<>(selected.subList(0, MAX_ACTORS));
        }

        String dimension = dimensionOf(selected, config);
        if (dimension == null) {
            return selected;
        }
        List<Actor> sameDimension = new ArrayList<>(selected.size());
        for (Actor actor : selected) {
            if (dimension.equals(actor.take.dimension)) {
                sameDimension.add(actor);
            }
        }
        if (sameDimension.size() != selected.size()) {
            FlashbackActors.LOGGER.warn("Director: {} actor(s) are in another dimension and are skipped",
                selected.size() - sameDimension.size());
        }
        return sameDimension;
    }


    private static Set<UUID> appendPlayerMarks(List<Actor> actors, DirectorConfig config,
                                              @Nullable Level level, int replayTick) {
        Set<UUID> marks = new HashSet<>();
        if (level == null) {
            return marks;
        }
        Set<UUID> takeIds = takeIdsOf(actors);
        Map<UUID, String> names = new HashMap<>();
        for (DirectorSubjects.PlayerSubject subject : DirectorSubjects.players()) {
            names.put(subject.id(), subject.name());
        }
        for (DirectorConfig.ActorSelection selection : config.actors()) {
            UUID id = selection.actorId();
            if (takeIds.contains(id)) {
                continue;
            }
            Entity entity = DirectorSubjects.entityOf(id);
            Vec3 pos = entity == null ? null : DirectorSubjects.positionOf(id);
            if (entity == null || pos == null) {
                FlashbackActors.LOGGER.warn(
                    "Director: selected player {} is not loaded; skipping them", id);
                continue;
            }
            String name = names.getOrDefault(id, "Player");
            int markTick = Math.max(0, replayTick);
            ActorTake take = DirectorSubjects.syntheticTake(id, name,
                new Vec3(pos.x, pos.y, pos.z), markTick,
                DirectorSubjects.dimensionOf(entity));
            actors.add(new Actor(take));
            takeIds.add(id);
            marks.add(id);
        }
        return marks;
    }

    private static Set<UUID> takeIdsOf(List<Actor> actors) {
        Set<UUID> ids = new HashSet<>(actors.size() * 2);
        for (Actor actor : actors) {
            ids.add(actor.take.id);
        }
        return ids;
    }


    private static void addPlayerMoments(List<DirectorAnalyzer.Moment> moments,
                                         Set<UUID> markIds, int replayTick) {
        if (replayTick < 0) {
            return;
        }
        for (UUID id : markIds) {
            moments.add(new DirectorAnalyzer.Moment(replayTick, id,
                DirectorAnalyzer.MomentKind.MOVEMENT_STOP, 1.0f));
        }
    }

    private static int currentReplayTick() {
        try {
            return pdx.dev.fbactors.FlashbackActors.replayTick();
        } catch (RuntimeException e) {
            return -1;
        }
    }


    private static void generateShots(CameraTrack track, List<Actor> actors,
                                      DirectorConfig config, DirectorStyle style,
                                      Random rng) {
        List<ActorTake> takes = new ArrayList<>(actors.size());
        for (Actor actor : actors) {
            takes.add(actor.take);
        }
        float side = rng.nextBoolean() ? 1.0f : -1.0f;
        UUID lastSubject = null;
        for (DirectorShot shot : config.shots()) {
            Actor subject = DirectorShotSelector.find(actors, shot.subjectId());
            if (subject == null) {
                FlashbackActors.LOGGER.warn(
                    "Director: shot subject {} is not in the cast; skipping it",
                    shot.subjectId());
                continue;
            }
            side = DirectorShotSelector.nextSide(lastSubject, shot.subjectId(), side);
            lastSubject = shot.subjectId();
            DirectorConfig shotConfig = new DirectorConfig(config.actors(), config.style(),
                config.handheld(), config.anticipationTicks(), shot.holdTicks(),
                shot.move() != null ? shot.move() : config.move(),
                shot.height() != null ? shot.height() : config.height(),
                shot.shotType() != null ? shot.shotType() : config.shotType(),
                List.of());
            DirectorAnalyzer.Moment moment = new DirectorAnalyzer.Moment(
                shot.startTick(), shot.subjectId(),
                DirectorAnalyzer.MomentKind.MOVEMENT_STOP, 1.0f);
            CameraKeyframe.ShotType type = shot.shotType() != null ? shot.shotType()
                : DirectorShotSelector.chooseTakes(takes, moment, style, shotConfig, rng);
            CameraMove move =
                DirectorShotSelector.chooseMove(actors, moment, style, shotConfig, rng);
            for (CameraKeyframe keyframe : DirectorChoreographer.choreograph(
                    move, actors, shotConfig, moment, type, style,
                    shot.holdTicks(), side, rng)) {
                track.add(keyframe);
            }
        }
        if (track.isEmpty()) {
            FlashbackActors.LOGGER.warn("Director: no listed shot had a subject in the cast");
        }
    }


    @Nullable
    private static String dimensionOf(List<Actor> selected, DirectorConfig config) {
        DirectorConfig.ActorSelection primary = config.primary();
        if (primary != null) {
            for (Actor actor : selected) {
                if (actor.take.id.equals(primary.actorId())) {
                    return actor.take.dimension;
                }
            }
        }
        return selected.isEmpty() ? null : selected.get(0).take.dimension;
    }


    private static Random seeded(DirectorConfig config) {
        long seed = 0x5DEECE66DL;
        seed = seed * 31L + config.style().ordinal();
        for (DirectorConfig.ActorSelection selection : config.actors()) {
            seed = seed * 31L + selection.actorId().getMostSignificantBits();
            seed = seed * 31L + selection.actorId().getLeastSignificantBits();
            seed = seed * 31L + Float.floatToIntBits(selection.weight());
            seed = seed * 31L + (selection.primary() ? 1L : 0L);
        }
        seed = seed * 31L + Float.floatToIntBits(config.anticipationTicks());
        seed = seed * 31L + (config.handheld() ? 1L : 0L);
        seed = seed * 31L + config.holdTicks();
        seed = seed * 31L + (config.move() == null ? -1 : config.move().ordinal());
        seed = seed * 31L + (config.height() == null ? -1 : config.height().ordinal());
        seed = seed * 31L + (config.shotType() == null ? -1 : config.shotType().ordinal());
        return new Random(seed);
    }


    public static String trackName(DirectorConfig.DirectorStyle style) {
        return TRACK_PREFIX + styleName(style);
    }


    public static boolean isDirectorTrackName(@Nullable String name) {
        if (name == null) {
            return false;
        }
        String trimmed = name.trim();
        int after = DIRECTOR_WORD.length();
        if (!trimmed.startsWith(DIRECTOR_WORD)) {
            return false;
        }
        return trimmed.length() == after || !Character.isLetter(trimmed.charAt(after));
    }


    public static String styleName(DirectorConfig.DirectorStyle style) {
        String[] words = style.name().toLowerCase(java.util.Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return builder.toString();
    }


    public static boolean writeToTimeline(CameraTrack track, DirectorConfig config) {
        EditorState editorState = EditorStateManager.getCurrent();
        if (editorState == null) {
            FlashbackActors.LOGGER.warn("Director: no replay editor is open; camera not written");
            return false;
        }
        long stamp = editorState.acquireWrite();
        try {
            EditorScene scene = editorState.getCurrentScene(stamp);
            if (scene == null) {
                return false;
            }


            scene.keyframeTracks.removeIf(existing -> isDirectorTrackName(existing.customName));

            String name = trackName(config.style());
            KeyframeTrack camera = new KeyframeTrack(CameraKeyframeType.INSTANCE);
            camera.customName = name;


            camera.enabled = true;
            scene.keyframeTracks.add(camera);
            int cameraIndex = scene.keyframeTracks.size() - 1;

            KeyframeTrack fov = new KeyframeTrack(FOVKeyframeType.INSTANCE);
            fov.customName = name;
            fov.enabled = true;
            scene.keyframeTracks.add(fov);
            int fovIndex = scene.keyframeTracks.size() - 1;

            InterpolationType interpolation = interpolationFor(config.style());
            for (CameraKeyframe keyframe : track.keyframes) {
                scene.setKeyframe(cameraIndex, keyframe.tick(),
                    new com.moulberry.flashback.keyframe.impl.CameraKeyframe(
                        new Vector3d(keyframe.x(), keyframe.y(), keyframe.z()),
                        keyframe.yaw(), keyframe.pitch(), 0.0f, interpolation));
                scene.setKeyframe(fovIndex, keyframe.tick(),
                    new com.moulberry.flashback.keyframe.impl.FOVKeyframe(
                        keyframe.fov(), interpolation));
            }
            editorState.markDirty();
            return true;
        } finally {
            editorState.release(stamp);
        }
    }


    private static InterpolationType interpolationFor(DirectorConfig.DirectorStyle style) {
        return DirectorStyle.of(style).easing() == CameraKeyframe.EASING_LINEAR
            ? InterpolationType.LINEAR
            : InterpolationType.SMOOTH;
    }
}

