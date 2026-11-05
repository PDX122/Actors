package pdx.dev.fbactors.events;

import com.mojang.blaze3d.audio.Library;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.ChannelAccess.ChannelHandle;
import net.minecraft.sounds.SoundSource;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import pdx.dev.fbactors.compat.CompatSounds;
import pdx.dev.fbactors.mixin.TimelineSoundChannelAccess;
import pdx.dev.fbactors.mixin.TimelineSoundEngineAccess;
import pdx.dev.fbactors.mixin.TimelineSoundManagerAccess;

/** Seeks static sound buffers by replay time on Minecraft's current audio device. */
final class TimelineAudio {
    private record Key(UUID clip, String cue) {}
    private static final class Source {
        ChannelHandle channel;
        volatile float duration = Float.POSITIVE_INFINITY;
        boolean used;
    }

    private final Map<Key, Source> sources = new HashMap<>();
    private TimelineSoundEngineAccess engine;
    private boolean paused;

    void play(UUID clip, String cue, String asset, double seconds, float pitch, float gain,
              boolean loop, boolean exporting) {
        if (seconds < 0 || gain <= 0) return;
        var client = Minecraft.getInstance();
        if (engine == null) {
            var manager = (TimelineSoundManagerAccess) client.getSoundManager();
            engine = (TimelineSoundEngineAccess) manager.fbactors$soundEngine();
        }
        var key = new Key(clip, cue);
        var source = sources.computeIfAbsent(key, unused -> new Source());
        source.used = true;
        if (!loop && seconds >= source.duration) return;
        if (source.channel == null || source.channel.isStopped()) {
            var path = CompatSounds.eventFile(asset).getPath();
            var buffer = engine.fbactors$buffers().getCompleteBuffer(path);
            if (!exporting && !buffer.isDone()) return;
            var decoded = buffer.join();
            var handle = engine.fbactors$channels().createHandle(Library.Pool.STATIC).join();
            if (handle == null) return;
            source.channel = handle;
            handle.execute(channel -> {
                channel.attachStaticBuffer(decoded);
                channel.setRelative(true);
                channel.disableAttenuation();
                channel.setLooping(loop);
                int id = ((TimelineSoundChannelAccess) channel).fbactors$source();
                int bufferId = AL10.alGetSourcei(id, AL10.AL_BUFFER);
                if (bufferId == 0) {
                    channel.stop();
                    return;
                }
                int bytes = AL10.alGetBufferi(bufferId, AL10.AL_SIZE);
                int bits = AL10.alGetBufferi(bufferId, AL10.AL_BITS);
                int channels = AL10.alGetBufferi(bufferId, AL10.AL_CHANNELS);
                int rate = AL10.alGetBufferi(bufferId, AL10.AL_FREQUENCY);
                source.duration = (float) (8.0 * bytes / (bits * channels * rate));
            });
        }
        float volume = Math.min(1, gain * client.options.getSoundSourceVolume(SoundSource.BLOCKS));
        source.channel.execute(channel -> {
            float duration = source.duration;
            if (duration <= 0 || !loop && seconds >= duration) {
                channel.stop();
                return;
            }
            float offset = (float) (loop ? seconds % duration : seconds);
            int id = ((TimelineSoundChannelAccess) channel).fbactors$source();
            int state = AL10.alGetSourcei(id, AL10.AL_SOURCE_STATE);
            channel.setVolume(volume);
            channel.setPitch(pitch);
            float current = AL10.alGetSourcef(id, AL11.AL_SEC_OFFSET);
            if (state != AL10.AL_PLAYING || Math.abs(current - offset) > (exporting ? .002 : .12)) {
                AL10.alSourcef(id, AL11.AL_SEC_OFFSET, offset);
            }
            if (state == AL10.AL_PAUSED) channel.unpause();
            else if (state != AL10.AL_PLAYING) channel.play();
        });
    }

    void pause() {
        if (paused) return;
        paused = true;
        for (var source : sources.values()) {
            if (source.channel != null) source.channel.execute(channel -> channel.pause());
        }
    }

    void finishFrame(boolean exporting) {
        paused = false;
        boolean hadSources = !sources.isEmpty();
        sources.values().removeIf(source -> {
            if (source.used) {
                source.used = false;
                return false;
            }
            if (source.channel != null) source.channel.execute(channel -> channel.stop());
            return true;
        });
        if (exporting && hadSources && engine != null) {
            // Flashback pulls the loopback samples immediately after rendering the frame.
            CompletableFuture.runAsync(() -> {}, engine.fbactors$executor()).join();
        }
    }

    boolean active(UUID clip, String cue) {
        return sources.entrySet().stream().anyMatch(entry -> entry.getKey().clip.equals(clip)
            && entry.getKey().cue.startsWith(cue) && entry.getValue().channel != null
            && !entry.getValue().channel.isStopped());
    }

    void stop() {
        for (var source : sources.values()) {
            if (source.channel != null) source.channel.execute(channel -> channel.stop());
        }
        sources.clear();
        paused = false;
        engine = null;
    }
}
