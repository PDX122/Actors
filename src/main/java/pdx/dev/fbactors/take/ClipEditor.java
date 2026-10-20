package pdx.dev.fbactors.take;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.IntPredicate;
import java.util.function.IntUnaryOperator;


public final class ClipEditor {

    private ClipEditor() {
    }


    public static ActorTake[] splitAt(ActorTake take, int tick) {
        int splitPoint = clamp(tick, 0, take.frames.size());


        ActorTake first = createTake(take, take.startTick,
            t -> t <= splitPoint,
            t -> t,
            displayName(take) + " (1)");
        stripJunctionEvents(first, take, splitPoint);


        ActorTake second = createTake(take, take.startTick + splitPoint,
            t -> t >= splitPoint,
            t -> t - splitPoint,
            displayName(take) + " (2)");

        return new ActorTake[] { first, second };
    }


    private static void stripJunctionEvents(ActorTake first, ActorTake source, int splitPoint) {
        first.events.removeIf(e -> e.tick() - source.startTick == splitPoint);
        first.equipment.removeIf(e -> e.tick() - source.startTick == splitPoint);
        first.blocks.removeIf(b -> b.tick() - source.startTick == splitPoint);
        first.vitals.removeIf(v -> v.tick() - source.startTick == splitPoint);
        first.mining.removeIf(m -> m.tick() - source.startTick == splitPoint);
        first.effects.removeIf(e -> e.tick() - source.startTick == splitPoint);
        first.inputs.removeIf(i -> i.tick() - source.startTick == splitPoint);
        first.containers.removeIf(c -> c.tick() - source.startTick == splitPoint);
        first.blockEntities.removeIf(b -> b.tick() - source.startTick == splitPoint);
        first.miningStates.removeIf(m -> m.tick() - source.startTick == splitPoint);
        first.rides.removeIf(r -> r.tick() - source.startTick == splitPoint);
        first.sounds.removeIf(s -> s.tick() - source.startTick == splitPoint);
        first.hits.removeIf(h ->
            h.tick() + source.offsetTicks - source.startTick == splitPoint);
        first.deaths.removeIf(d ->
            d.tick() + source.offsetTicks - source.startTick == splitPoint);
        first.voices.removeIf(v -> v.tick() == splitPoint);
        first.subtitles.removeIf(s -> s.tick() == splitPoint);
        first.shots.removeIf(s -> s.tick() == splitPoint);
        first.cameraTrack.keyframes.removeIf(k -> k.tick() == splitPoint);
    }


    public static ActorTake deleteRange(ActorTake take, int fromTick, int toTick) {
        int from = clamp(fromTick, 0, take.frames.size());
        int to = clamp(toTick, from, take.frames.size());
        int deleteLength = to - from;


        return createTake(take, take.startTick,
            t -> t < from || t >= to,
            t -> t < from ? t : t - deleteLength,
            displayName(take));
    }


    public static ActorTake trimTo(ActorTake take, int fromTick, int toTick) {
        int from = clamp(fromTick, 0, take.frames.size());
        int to = clamp(toTick, from, take.frames.size());


        return createTake(take, take.startTick + from,
            t -> t >= from && t < to,
            t -> t - from,
            displayName(take));
    }


    private static ActorTake createTake(ActorTake source, int newStartTick,
                                         IntPredicate include, IntUnaryOperator map,
                                         String newName) {
        int sourceLength = source.frames.size();


        List<ActorFrame> frames = new ArrayList<>();
        for (int i = 0; i < sourceLength; i++) {
            if (include.test(i)) {
                frames.add(source.frames.get(i));
            }
        }


        List<ActorEvent> events = new ArrayList<>();
        for (ActorEvent event : source.events) {
            int takeRelative = event.tick() - source.startTick;
            if (inRange(takeRelative, sourceLength) && include.test(takeRelative)) {
                events.add(new ActorEvent(
                    map.applyAsInt(takeRelative) + newStartTick,
                    event.type(), event.target()));
            }
        }


        List<ActorEquip> equipment = new ArrayList<>();
        for (ActorEquip equip : source.equipment) {
            int takeRelative = equip.tick() - source.startTick;
            if (inRange(takeRelative, sourceLength) && include.test(takeRelative)) {
                equipment.add(new ActorEquip(
                    map.applyAsInt(takeRelative) + newStartTick,
                    equip.slot(), equip.itemId(), equip.count(), equip.nbt()));
            }
        }


        List<ActorHit> hits = new ArrayList<>();
        for (ActorHit hit : source.hits) {
            int takeRelative = hit.tick() + source.offsetTicks - source.startTick;
            if (inRange(takeRelative, sourceLength) && include.test(takeRelative)) {
                hits.add(new ActorHit(
                    map.applyAsInt(takeRelative) + newStartTick - source.offsetTicks,
                    hit.damage(), hit.dirX(), hit.dirZ(),
                    hit.attackStrength(), hit.target(), hit.impulse(), hit.crit()));
            }
        }


        List<ActorDeath> deaths = new ArrayList<>();
        for (ActorDeath death : source.deaths) {
            int takeRelative = death.tick() + source.offsetTicks - source.startTick;
            if (inRange(takeRelative, sourceLength) && include.test(takeRelative)) {
                deaths.add(new ActorDeath(
                    map.applyAsInt(takeRelative) + newStartTick - source.offsetTicks,
                    death.kind()));
            }
        }


        List<ActorBlockEdit> blocks = new ArrayList<>();
        for (ActorBlockEdit block : source.blocks) {
            int takeRelative = block.tick() - source.startTick;
            if (inRange(takeRelative, sourceLength) && include.test(takeRelative)) {
                blocks.add(new ActorBlockEdit(
                    map.applyAsInt(takeRelative) + newStartTick,
                    block.x(), block.y(), block.z(), block.before(), block.after()));
            }
        }


        List<ActorVoiceLine> voices = new ArrayList<>();
        for (ActorVoiceLine voice : source.voices) {
            if (inRange(voice.tick(), sourceLength) && include.test(voice.tick())) {
                voices.add(new ActorVoiceLine(
                    map.applyAsInt(voice.tick()),
                    voice.samples().clone(), voice.caption()));
            }
        }


        List<ActorSubtitle> subtitles = new ArrayList<>();
        for (ActorSubtitle subtitle : source.subtitles) {
            if (inRange(subtitle.tick(), sourceLength) && include.test(subtitle.tick())) {
                int start = Math.max(0, map.applyAsInt(subtitle.tick()));
                int duration = Math.max(1, Math.min(subtitle.durationTicks(),
                    Math.max(1, frames.size() - start)));
                subtitles.add(new ActorSubtitle(start, duration, subtitle.speaker(),
                    subtitle.text(), subtitle.colorArgb(), subtitle.size(), subtitle.position()));
            }
        }


        List<ActorVitals> vitals = new ArrayList<>();
        for (ActorVitals vital : source.vitals) {
            int takeRelative = vital.tick() - source.startTick;
            if (inRange(takeRelative, sourceLength) && include.test(takeRelative)) {
                vitals.add(new ActorVitals(
                    map.applyAsInt(takeRelative) + newStartTick,
                    vital.health(), vital.absorption()));
            }
        }


        List<ActorMining> mining = new ArrayList<>();
        for (ActorMining step : source.mining) {
            int takeRelative = step.tick() - source.startTick;
            if (inRange(takeRelative, sourceLength) && include.test(takeRelative)) {
                mining.add(new ActorMining(
                    map.applyAsInt(takeRelative) + newStartTick,
                    step.pos(), step.stage()));
            }
        }


        List<ActorEffect> effects = new ArrayList<>();
        for (ActorEffect effect : source.effects) {
            int takeRelative = effect.tick() - source.startTick;
            if (inRange(takeRelative, sourceLength) && include.test(takeRelative)) {
                effects.add(new ActorEffect(
                    map.applyAsInt(takeRelative) + newStartTick,
                    effect.effectId(), effect.amplifier(), effect.duration()));
            }
        }


        List<ActorShot> shots = new ArrayList<>();
        for (ActorShot shot : source.shots) {
            if (inRange(shot.tick(), sourceLength) && include.test(shot.tick())) {
                shots.add(new ActorShot(
                    map.applyAsInt(shot.tick()),
                    shot.dirX(), shot.dirY(), shot.dirZ(),
                    shot.power(), shot.itemId(), shot.target()));
            }
        }


        List<ActorBlockEntityEdit> blockEntities = new ArrayList<>();
        for (ActorBlockEntityEdit edit : source.blockEntities) {
            int takeRelative = edit.tick() - source.startTick;
            if (inRange(takeRelative, sourceLength) && include.test(takeRelative)) {
                blockEntities.add(new ActorBlockEntityEdit(
                    map.applyAsInt(takeRelative) + newStartTick,
                    edit.x(), edit.y(), edit.z(), edit.type(), edit.nbt()));
            }
        }


        List<ActorContainerInteraction> containers = new ArrayList<>();
        for (ActorContainerInteraction interaction : source.containers) {
            int takeRelative = interaction.tick() - source.startTick;
            if (inRange(takeRelative, sourceLength) && include.test(takeRelative)) {
                containers.add(new ActorContainerInteraction(
                    map.applyAsInt(takeRelative) + newStartTick,
                    interaction.x(), interaction.y(), interaction.z(),
                    interaction.action(), interaction.slot(),
                    interaction.itemId(), interaction.count(), interaction.nbt()));
            }
        }


        List<ActorMiningState> miningStates = new ArrayList<>();
        for (ActorMiningState state : source.miningStates) {
            int takeRelative = state.tick() - source.startTick;
            if (inRange(takeRelative, sourceLength) && include.test(takeRelative)) {
                miningStates.add(new ActorMiningState(
                    map.applyAsInt(takeRelative) + newStartTick,
                    state.x(), state.y(), state.z(), state.stage(), state.swinging()));
            }
        }


        List<ActorRide> rides = new ArrayList<>();
        for (ActorRide ride : source.rides) {
            int takeRelative = ride.tick() - source.startTick;
            if (inRange(takeRelative, sourceLength) && include.test(takeRelative)) {
                rides.add(new ActorRide(
                    map.applyAsInt(takeRelative) + newStartTick,
                    ride.mount(), ride.vehicleType(), ride.vehicleUuid()));
            }
        }


        List<ActorSound> sounds = new ArrayList<>();
        for (ActorSound cue : source.sounds) {
            int takeRelative = cue.tick() - source.startTick;
            if (inRange(takeRelative, sourceLength) && include.test(takeRelative)) {
                sounds.add(new ActorSound(
                    map.applyAsInt(takeRelative) + newStartTick,
                    cue.soundId(), cue.x(), cue.y(), cue.z(),
                    cue.volume(), cue.pitch(), cue.source()));
            }
        }


        ActorTake take = new ActorTake(UUID.randomUUID(), newName, source.dimension, newStartTick,
            frames, events, equipment, hits, deaths, blocks, voices, vitals, mining, effects, shots,
            blockEntities, containers, miningStates, subtitles, new ArrayList<>(), rides, sounds);


        for (ActorInput input : source.inputs) {
            int takeRelative = input.tick() - source.startTick;
            if (inRange(takeRelative, sourceLength) && include.test(takeRelative)) {
                take.inputs.add(input.retimed(map.applyAsInt(takeRelative) + newStartTick));
            }
        }


        take.skinName = source.skinName;
        take.offsetTicks = source.offsetTicks;
        take.enabled = source.enabled;
        take.locked = source.locked;
        take.colour = source.colour;
        take.voiceEnabled = source.voiceEnabled;
        take.voiceDevice = source.voiceDevice;
        take.voiceActivation = source.voiceActivation;
        take.voiceThreshold = source.voiceThreshold;
        take.setSpeedMultiplier(source.getSpeedMultiplier());


        for (ActorVisibility range : source.hiddenRanges) {
            int start = range.startTick();
            int end = range.endTick();
            boolean anyKept = false;
            for (int t = start; t <= end; t++) {
                if (inRange(t, sourceLength) && include.test(t)) {
                    anyKept = true;
                    break;
                }
            }
            if (!anyKept) {
                continue;
            }
            int newStart = Math.max(0, map.applyAsInt(Math.max(0, start)));
            int newEnd = Math.max(newStart, map.applyAsInt(Math.min(end, sourceLength - 1)));
            if (newEnd >= 0 && newStart < Math.max(1, frames.size())) {
                take.hiddenRanges.add(new ActorVisibility(newStart,
                    Math.min(newEnd, Math.max(0, frames.size() - 1))));
            }
        }


        for (CameraKeyframe keyframe : source.cameraTrack.keyframes) {
            if (inRange(keyframe.tick(), sourceLength) && include.test(keyframe.tick())) {
                take.cameraTrack.add(new CameraKeyframe(
                    map.applyAsInt(keyframe.tick()),
                    keyframe.x(), keyframe.y(), keyframe.z(),
                    keyframe.yaw(), keyframe.pitch(), keyframe.fov(),
                    keyframe.shotType(), keyframe.targetActorId(), keyframe.easing()));
            }
        }
        take.directorConfig = source.directorConfig;

        return take;
    }


    private static boolean inRange(int takeRelative, int sourceLength) {
        return takeRelative >= 0 && takeRelative < sourceLength;
    }


    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(value, max));
    }


    private static String displayName(ActorTake take) {
        String name = take.name;
        return name == null || name.isBlank() ? "Actor" : name;
    }
}

