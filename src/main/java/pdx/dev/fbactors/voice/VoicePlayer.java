package pdx.dev.fbactors.voice;

import org.lwjgl.openal.AL10;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.jetbrains.annotations.Nullable;

import net.minecraft.world.phys.Vec3;
import pdx.dev.fbactors.FlashbackActors;
import pdx.dev.fbactors.take.ActorVoiceLine;


public final class VoicePlayer {

    private static final int RATE = VoiceBuffer.TARGET_RATE;
    private static final float REFERENCE_DISTANCE = 6.0f;
    private static final float MAX_DISTANCE = 128.0f;


    private static final int RING_BUFFERS = 8;


    private static final int PREFILL_BUFFERS = 4;


    private record Pending(UUID take, short[] samples, float x, float y, float z) {
    }


    private static final class Stream {
        final int source;
        final int[] buffers = new int[RING_BUFFERS];
        int cursor;

        Stream(int source) {
            this.source = source;
        }
    }


    private static final java.util.Queue<Pending> queue = new ConcurrentLinkedQueue<>();


    private static final Map<UUID, Stream> streams = new HashMap<>();


    private static final Set<UUID> stopping = ConcurrentHashMap.newKeySet();

    private static long sequence = 0L;
    private static int lastTick = Integer.MIN_VALUE;
    private static final Set<Integer> fired = new HashSet<>();


    private static final int CATCH_UP_TICKS = 6;

    private VoicePlayer() {
    }


    public static void tick(int replayTick, boolean scrubbed) {
        if (!VoiceSettings.systemEnabled()) {
            return;
        }
        if (scrubbed) {
            clear();
            return;
        }
        if (lastTick == replayTick) {
            return;
        }


        if (lastTick != Integer.MIN_VALUE && replayTick != lastTick && replayTick != lastTick + 1) {
            fired.clear();
        }
        int prevTick = lastTick;
        lastTick = replayTick;

        for (var actor : FlashbackActors.ACTORS.actors()) {
            var take = actor.take;
            if (take == null || take.voices.isEmpty() || !actor.enabled()) {
                continue;
            }
            if (!take.voiceEnabled) {
                continue;
            }
            Vec3 position = actor.livePosition();
            if (position == null) {
                continue;
            }
            for (ActorVoiceLine line : take.voices) {
                if (line.samples().length == 0) {
                    continue;
                }


                int absolute = line.tick() + take.startTick + actor.offset();


                boolean due = absolute == replayTick
                    || (prevTick != Integer.MIN_VALUE && absolute < replayTick && absolute > prevTick
                        && absolute > replayTick - CATCH_UP_TICKS);
                if (!due) {
                    continue;
                }


                if (!take.coversTick(replayTick - actor.offset())) {
                    continue;
                }
                if (!fired.add(absolute * 31 + take.id.hashCode())) {
                    continue;
                }
                queue.add(new Pending(take.id, line.samples().clone(),
                    (float) position.x, (float) position.y, (float) position.z));
            }
        }
    }


    public static void drain(int replayTick) {
        if (!VoiceSettings.systemEnabled()) {
            return;
        }
        if (!stopping.isEmpty()) {
            for (UUID take : List.copyOf(stopping)) {
                stopStream(take);
                stopping.remove(take);
            }
        }
        for (Map.Entry<UUID, Stream> entry : List.copyOf(streams.entrySet())) {
            if (FlashbackActors.ACTORS.byTakeId(entry.getKey()) == null) {
                deleteStream(entry.getKey(), entry.getValue());
            }
        }

        Pending next;
        while ((next = queue.poll()) != null) {
            play(next);
        }
    }


    private static void play(Pending pending) {
        try {
            Stream stream = streams.get(pending.take());
            if (stream == null) {
                stream = openStream();
                if (stream == null) {
                    return;
                }
                streams.put(pending.take(), stream);
            }
            unqueueProcessed(stream);
            if (isStarved(stream)) {
                prefill(stream, pending);
            }
            int slot = stream.buffers[stream.cursor];
            AL10.alBufferData(slot, AL10.AL_FORMAT_MONO16, toBuffer(pending.samples()), RATE);
            queueOne(stream, slot, pending);
            stream.cursor = (stream.cursor + 1) % RING_BUFFERS;
        } catch (Throwable t) {
            FlashbackActors.LOGGER.warn("Actor voice line failed to play", t);
        }
    }


    @Nullable
    private static Stream openStream() {
        try {
            int source = AL10.alGenSources();
            Stream stream = new Stream(source);
            for (int i = 0; i < RING_BUFFERS; i++) {
                stream.buffers[i] = AL10.alGenBuffers();
            }
            AL10.alSourcei(source, AL10.AL_SOURCE_RELATIVE, 0);
            AL10.alSourcef(source, AL10.AL_REFERENCE_DISTANCE, REFERENCE_DISTANCE);
            AL10.alSourcef(source, AL10.AL_MAX_DISTANCE, MAX_DISTANCE);
            return stream;
        } catch (Throwable t) {
            FlashbackActors.LOGGER.warn("Actor voice source failed to open", t);
            return null;
        }
    }


    private static void queueOne(Stream stream, int slot, Pending pending) {
        AL10.alSource3f(stream.source, AL10.AL_POSITION, pending.x(), pending.y(), pending.z());
        AL10.alSourceQueueBuffers(stream.source, slot);
        if (isStarved(stream)) {
            AL10.alSourcePlay(stream.source);
        }
    }


    private static boolean isStarved(Stream stream) {
        try {
            int state = AL10.alGetSourcei(stream.source, AL10.AL_SOURCE_STATE);
            if (state == AL10.AL_INITIAL || state == AL10.AL_STOPPED) {
                return true;
            }
            return AL10.alGetSourcei(stream.source, AL10.AL_BUFFERS_QUEUED) <= 0;
        } catch (Throwable ignored) {
            return true;
        }
    }


    private static void prefill(Stream stream, Pending pending) {
        short[] silence = new short[RATE / 50];
        for (int i = 0; i < PREFILL_BUFFERS; i++) {
            int slot = stream.buffers[stream.cursor];
            AL10.alBufferData(slot, AL10.AL_FORMAT_MONO16, toBuffer(silence), RATE);
            AL10.alSource3f(stream.source, AL10.AL_POSITION,
                pending.x(), pending.y(), pending.z());
            AL10.alSourceQueueBuffers(stream.source, slot);
            stream.cursor = (stream.cursor + 1) % RING_BUFFERS;
        }
    }


    private static void unqueueProcessed(Stream stream) {
        try {
            int processed = AL10.alGetSourcei(stream.source, AL10.AL_BUFFERS_PROCESSED);
            for (int i = 0; i < processed; i++) {
                AL10.alSourceUnqueueBuffers(stream.source);
            }
        } catch (Throwable ignored) {

        }
    }


    private static void stopStream(UUID take) {
        Stream stream = streams.get(take);
        if (stream == null) {
            return;
        }
        try {
            AL10.alSourceStop(stream.source);
        } catch (Throwable ignored) {
        }
        unqueueAll(stream);
    }


    private static void deleteStream(UUID take, Stream stream) {
        if (!streams.remove(take, stream)) {
            return;
        }
        unqueueAll(stream);
        try {
            AL10.alDeleteBuffers(stream.buffers);
        } catch (Throwable ignored) {
        }
        try {
            AL10.alDeleteSources(stream.source);
        } catch (Throwable ignored) {
        }
    }


    private static void unqueueAll(Stream stream) {
        try {
            int queued = AL10.alGetSourcei(stream.source, AL10.AL_BUFFERS_QUEUED);
            for (int i = 0; i < queued; i++) {
                try {
                    AL10.alSourceUnqueueBuffers(stream.source);
                } catch (Throwable ignored) {
                    break;
                }
            }
        } catch (Throwable ignored) {
        }
    }

    private static void clear() {
        queue.clear();
        fired.clear();
        lastTick = Integer.MIN_VALUE;
        for (UUID take : streams.keySet()) {
            stopping.add(take);
        }
    }

    private static ByteBuffer toBuffer(short[] samples) {
        ByteBuffer buffer = org.lwjgl.BufferUtils.createByteBuffer(samples.length * 2);
        for (short s : samples) {
            buffer.putShort(s);
        }
        buffer.flip();
        return buffer;
    }
}

