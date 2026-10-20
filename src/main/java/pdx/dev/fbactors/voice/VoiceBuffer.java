package pdx.dev.fbactors.voice;

import pdx.dev.fbactors.FlashbackActors;
import pdx.dev.fbactors.take.ActorTake;
import pdx.dev.fbactors.take.ActorVoiceLine;


public final class VoiceBuffer {


    public static final int TARGET_RATE = 48_000;


    private static volatile int speechThreshold = 200;


    private static final int GAP_BUFFERS_TO_CLOSE = 12;

    private static volatile boolean activation = true;

    private static short[] pending = new short[0];
    private static int silentBuffers = 0;
    private static boolean pendingAny = false;


    private static int pendingStartTick = 0;

    private VoiceBuffer() {
    }

    public static void reset() {
        pending = new short[0];
        silentBuffers = 0;
        pendingAny = false;
        pendingStartTick = 0;
    }


    public static void configure(boolean activationOn, int thresholdDb) {
        activation = activationOn;


        speechThreshold = (int) (32768.0 * Math.pow(10.0, thresholdDb / 20.0));
        speechThreshold = Math.max(8, Math.min(32767, speechThreshold));
    }


    public static void offerDirect(short[] samples, int sampleRate) {
        offerDirect(samples, sampleRate, false);
    }


    public static void offerDirect(short[] samples, int sampleRate, boolean forceSplit) {
        if (samples == null || samples.length == 0) {
            return;
        }
        ActorTake take = FlashbackActors.RECORDER.current();
        if (take == null) {
            return;
        }
        short[] normalised = sampleRate == TARGET_RATE
            ? samples
            : resample(samples, sampleRate, TARGET_RATE);

        int tick = FlashbackActors.RECORDER.currentTick();


        tick -= take.startTick;

        if (forceSplit && pendingAny) {
            flush(take);
        }
        offer(normalised, take, tick);
    }

    public static void offer(short[] samples) {
        ActorTake take = FlashbackActors.RECORDER.current();
        if (take == null) {
            return;
        }
        int tick = FlashbackActors.RECORDER.currentTick() - take.startTick;
        offer(samples, take, tick);
    }

    private static void offer(short[] samples, ActorTake take, int tick) {
        if (samples == null || samples.length == 0) {
            return;
        }

        if (!isSpeech(samples)) {
            silentBuffers++;
            if (silentBuffers >= GAP_BUFFERS_TO_CLOSE && pendingAny) {
                flush(take);
            }
            return;
        }

        silentBuffers = 0;

        int used = 0;
        for (ActorVoiceLine line : take.voices) {
            used += line.samples().length;
        }
        if (used + pending.length + samples.length > ActorVoiceLine.MAX_TOTAL_SAMPLES) {
            if (pendingAny) {
                flush(take);
            }
            return;
        }

        if (!pendingAny) {


            pendingStartTick = tick;
        }
        pending = append(pending, samples);
        pendingAny = true;
    }


    public static void finish() {
        ActorTake take = FlashbackActors.RECORDER.current();
        if (take != null && pendingAny) {
            flush(take);
        }
        reset();
    }

    private static void flush(ActorTake take) {
        if (pendingAny && pending.length > 0) {
            ActorVoiceLine line = new ActorVoiceLine(pendingStartTick,
                applyEdgeFades(limit(pending)));
            take.voices.add(line);
            FlashbackActors.LOGGER.info("Captured voice line at tick {} ({} samples, {} ticks long)",
                line.tick(), line.samples().length, line.durationTicks());
        }
        pending = new short[0];
        silentBuffers = 0;
        pendingAny = false;
    }


    private static final float LIMIT_PEAK = 0.89f;


    static final int FADE_SAMPLES = 240;


    static short[] limit(short[] samples) {
        int peak = 0;
        for (short s : samples) {
            int magnitude = Math.abs((int) s);
            if (magnitude > peak) {
                peak = magnitude;
            }
        }
        float target = LIMIT_PEAK * Short.MAX_VALUE;
        if (peak <= target || peak <= 0) {
            return samples;
        }
        float scale = target / peak;
        short[] out = new short[samples.length];
        for (int i = 0; i < samples.length; i++) {
            out[i] = (short) Math.round(samples[i] * scale);
        }
        return out;
    }


    static short[] applyEdgeFades(short[] samples) {
        if (samples.length == 0) {
            return samples;
        }
        int fade = Math.min(FADE_SAMPLES, samples.length / 2);
        if (fade <= 0) {
            return samples.clone();
        }
        short[] out = samples.clone();
        for (int i = 0; i < fade; i++) {
            float gain = 0.5f - 0.5f * (float) Math.cos(Math.PI * i / fade);
            out[i] = (short) Math.round(out[i] * gain);
            out[out.length - 1 - i] = (short) Math.round(out[out.length - 1 - i] * gain);
        }
        return out;
    }


    static short[] resample16k(short[] samples) {
        if (samples.length == 0) {
            return samples;
        }
        int outLength = samples.length / 3;
        if (outLength == 0) {
            return new short[] {samples[0]};
        }
        short[] out = new short[outLength];
        for (int i = 0; i < outLength; i++) {
            int sum = samples[i * 3] + samples[i * 3 + 1] + samples[i * 3 + 2];
            out[i] = (short) Math.round(sum / 3.0f);
        }
        return out;
    }


    static boolean isSpeech(short[] samples) {


        if (!activation) {
            return true;
        }
        long sum = 0L;
        for (short s : samples) {
            sum += Math.abs((int) s);
        }
        return (sum / Math.max(1, samples.length)) > speechThreshold;
    }

    private static short[] append(short[] existing, short[] addition) {
        if (existing.length == 0) {
            return addition.clone();
        }
        short[] merged = new short[existing.length + addition.length];
        System.arraycopy(existing, 0, merged, 0, existing.length);
        System.arraycopy(addition, 0, merged, existing.length, addition.length);
        return merged;
    }


    private static short[] resample(short[] input, int from, int to) {
        if (from <= 0 || from == to) {
            return input.clone();
        }
        int outLength = Math.max(1, (int) ((long) input.length * to / from));
        short[] out = new short[outLength];
        for (int i = 0; i < outLength; i++) {
            double src = (double) i * from / to;
            int low = (int) src;
            int high = Math.min(input.length - 1, low + 1);
            double frac = src - low;
            out[i] = (short) Math.round(
                input[low] * (1.0 - frac) + input[high] * frac);
        }
        return out;
    }
}

