package pdx.dev.fbactors.events;

import com.moulberry.flashback.Flashback;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundEvent;
import pdx.dev.fbactors.compat.CompatSounds;

/** Only the user's ICBM pack plays. No synthesized or fallback audio for other events. */
public final class EventAudio {
    private static final TimelineAudio mixer = new TimelineAudio();
    private static final Map<String, SoundEvent> sounds = new LinkedHashMap<>();
    private static final Map<UUID, Double> arrivals = new HashMap<>();
    private static final List<SoundInstance> auditions = new ArrayList<>();
    private static long auditionSeen;
    private static UUID owner;
    private static boolean exporting;
    private static double previous = Double.NaN;
    private static int previousServerTick = Integer.MIN_VALUE;

    private EventAudio() {}
    public static void install() {
        // Keep registry keys readable in old replay snapshots; none are scheduled.
        for(String legacy:new String[]{"blast","impact","rumble","fall","nuclear","thunder","thunder-close","thunder-roll","impact-heavy","eruption-blast","meteor-rush","eruption-roar","storm-wind","launch-roar"})CompatSounds.registerEvent("event."+legacy);
        for(String name:EventAudioPlan.FILES)sounds.put(name,CompatSounds.registerEvent("icbm."+name));
    }
    public static boolean crossed(double before,double now,double cue) {
        return Double.isFinite(before) && Double.isFinite(now) && now>before && now-before<=10 && cue>before && cue<=now;
    }

    public static void clientTick() {
        var replay = Flashback.getReplayServer();
        if (replay == null && owner != null) {
            stop();
            owner = null;
        }
        if (replay != null && replay.replayPaused && !Flashback.isExporting()) mixer.pause();
        if (!auditions.isEmpty() && System.nanoTime() - auditionSeen > 500_000_000L) stopAudition();
    }

    public static void tick() {
        var replay = Flashback.getReplayServer();
        var client = Minecraft.getInstance();
        boolean export = Flashback.isExporting();
        if (!auditions.isEmpty() && (export || System.nanoTime() - auditionSeen > 500_000_000L)) {
            stopAudition();
        }
        UUID replayId = pdx.dev.fbactors.FlashbackActors.loadedReplayIdOrNull();
        if (!Objects.equals(owner, replayId) || export != exporting) {
            stop();
            owner = replayId;
            exporting = export;
        }
        if (replay == null || client.level == null) return;
        if (replay.replayPaused && !export) {
            mixer.pause();
            return;
        }

        int serverTick = replay.getReplayTick();
        double tick = EventFrameClock.sample(previous, previousServerTick,
            pdx.dev.fbactors.camera.BoatSubjects.clock(), serverTick, false, export);
        if (!Double.isFinite(tick)) return;
        if (Double.isFinite(previous) && (tick < previous || tick - previous > 10)) {
            arrivals.clear();
        }
        var track = Events.timeline();
        try {
            if (track.enabled) {
                var eye = pdx.dev.fbactors.compat.Compat.eventCamera().position();
                var listener = new EventClip.Point(eye.x, eye.y, eye.z);
                String dimension = client.level.dimension().toString();
                for (var clip : track.clips) {
                    if (clip.kind == EventKind.ICBM && clip.active(tick) && clip.sound && clip.volume > 0
                        && (clip.dimension.isEmpty() || clip.dimension.equals(dimension))) {
                        playMissile(clip, tick, listener, export);
                    } else {
                        arrivals.remove(clip.id);
                    }
                }
            }
        } finally {
            mixer.finishFrame(export);
        }
        previous = tick;
        previousServerTick = serverTick;
    }

    private static void playMissile(EventClip clip, double tick, EventClip.Point listener, boolean export) {
        double progress = clip.progress(tick);
        double duration = (clip.end - clip.start) / 20.0;
        EventClip.Point missile = MissileFlight.sample(clip, tick).position();
        for (var phase : EventAudioPlan.PHASES) {
            if (!phase.active(clip, progress)) continue;
            EventClip.Point origin = phase.start() >= MissileFlight.IMPACT ? clip.impact : missile;
            if (phase.start() < MissileFlight.IGNITION) origin = clip.launch;
            float gain = clip.volume * phase.level(progress)
                / (float) (1 + listener.distance(origin) / (2000 * clip.scale));
            double seconds = (progress - phase.start()) * duration * phase.pitch();
            mixer.play(clip.id, phase.name(), "events/icbm/" + phase.name(), seconds,
                phase.pitch(), gain, phase.loop(), export);
        }
        if (progress < MissileFlight.IMPACT) {
            arrivals.remove(clip.id);
            return;
        }
        double distance = arrivals.computeIfAbsent(clip.id, unused -> listener.distance(clip.impact));
        double age = (progress - MissileFlight.IMPACT) * duration - EventPressure.delay(distance);
        if (age < 0) return;
        String impact = EventAudioPlan.impact(distance);
        float pitch = impact.equals("detonation_distant") ? .78f : 1;
        float fade = (float) Math.min(1, (1 - progress) * 28);
        float gain = clip.volume * fade / (float) (1 + distance / (1600 * clip.blast));
        mixer.play(clip.id, "impact_main", "events/icbm/" + impact, age * pitch, pitch, gain, false, export);
        mixer.play(clip.id, "impact_low", "events/icbm/impact_bass", age, 1, gain * .28f, false, export);
    }

    private static SoundInstance instance(String name,float volume,EventClip.Point point) {
        return new SimpleSoundInstance(sounds.get(name),net.minecraft.sounds.SoundSource.BLOCKS,Math.min(1.5f,volume),name.equals("detonation_distant")?.78f:1,
            net.minecraft.util.RandomSource.create(),point.x(),point.y(),point.z()) {
            @Override public SoundInstance.Attenuation getAttenuation() {return SoundInstance.Attenuation.NONE;}
        };
    }
    public static void audition(String name) {
        stopAudition();if(Flashback.isExporting() || !sounds.containsKey(name))return;auditionVisible();
        var mc=Minecraft.getInstance();var eye=pdx.dev.fbactors.compat.Compat.eventCamera().position();
        var sound=instance(name,.6f,new EventClip.Point(eye.x,eye.y,eye.z));auditions.add(sound);mc.getSoundManager().play(sound);
    }
    public static void stopAudition() {
        if(auditions.isEmpty())return;var manager=Minecraft.getInstance().getSoundManager();for(var sound:auditions)manager.stop(sound);auditions.clear();
    }
    public static void auditionVisible() {auditionSeen=System.nanoTime();}
    public static boolean hasActiveBed(UUID clip) { return mixer.active(clip, "launch"); }
    public static boolean hasActiveImpact(UUID clip) { return mixer.active(clip, "impact_main"); }

    public static void stop() {
        mixer.stop();
        arrivals.clear();
        previous = Double.NaN;
        previousServerTick = Integer.MIN_VALUE;
    }
}
