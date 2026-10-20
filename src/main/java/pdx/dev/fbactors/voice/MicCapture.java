package pdx.dev.fbactors.voice;

import org.lwjgl.openal.ALUtil;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.ALC11;
import org.lwjgl.openal.EXTFloat32;

import java.util.ArrayList;
import java.util.List;


public final class MicCapture {

    private static final int RATE = VoiceBuffer.TARGET_RATE;
    private static final int FRAME_SIZE = RATE / 50;
    private static final int CAPTURE_BUFFER = 1024;

    private static long device = 0L;
    private static boolean started = false;
    private static boolean stereo = false;
    private static Thread thread;
    private static volatile boolean running;


    private static volatile float inputGain = 1.0f;

    private MicCapture() {
    }

    public static boolean isRunning() {
        return running;
    }


    public static boolean canCapture() {
        return ALC10.alcIsExtensionPresent(0L, "ALC_EXT_CAPTURE");
    }

    public static List<String> listDevices() {
        List<String> devices = new ArrayList<>();
        if (!canCapture()) {
            return devices;
        }
        try {
            List<String> names = ALUtil.getStringList(0L, ALC11.ALC_CAPTURE_DEVICE_SPECIFIER);
            if (names != null) {
                devices.addAll(names);
            }
        } catch (Throwable t) {

        }
        return devices;
    }

    public static synchronized boolean start(String deviceName, boolean activation, int thresholdDb,
                                             float gain) {
        if (running) {
            return true;
        }
        if (!canCapture()) {
            pdx.dev.fbactors.FlashbackActors.LOGGER.warn(
                "This OpenAL build has no ALC_EXT_CAPTURE, actor voice is unavailable");
            return false;
        }
        try {
            stereo = useStereoWorkaround();
            device = openMic(deviceName);


            ALC11.alcCaptureStart(device);
            started = true;
            inputGain = Math.max(0.1f, Math.min(2.0f, gain));
            VoiceBuffer.configure(activation, thresholdDb);
            running = true;
            thread = new Thread(MicCapture::pump, "fbactors-voice-capture");
            thread.setDaemon(true);
            thread.start();
            pdx.dev.fbactors.FlashbackActors.LOGGER.info(
                "Recording actor voice from {} ({}Hz, {} frame{})",
                deviceName == null || deviceName.isBlank() ? "default microphone" : deviceName,
                RATE, FRAME_SIZE, stereo ? ", stereo workaround" : "");
            return true;
        } catch (Throwable t) {
            pdx.dev.fbactors.FlashbackActors.LOGGER.warn(
                "Could not open the microphone, actor voice is unavailable", t);
            close();
            return false;
        }
    }

    public static synchronized void stop() {
        running = false;
        if (thread != null) {
            try {
                thread.join(500);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            thread = null;
        }
        close();
    }

    private static synchronized void close() {
        if (device == 0L) {
            return;
        }
        try {
            if (started) {
                ALC11.alcCaptureStop(device);
                started = false;


                int leftover = available();
                if (leftover > 0) {
                    ALC11.alcCaptureSamples(device, new float[leftover * (stereo ? 2 : 1)], leftover);
                }
            }
            ALC11.alcCaptureCloseDevice(device);
        } catch (Throwable ignored) {
        }
        device = 0L;
    }


    private static final int MAX_LINE_FRAMES = 25;


    private static void pump() {
        int speechFrames = 0;
        try {
            while (running) {
                if (available() < FRAME_SIZE) {
                    Thread.sleep(5);
                    continue;
                }
                short[] audio = read();
                if (audio == null) {
                    continue;
                }


                lastPeak = peak(audio);


                if (VoiceBuffer.isSpeech(audio)) {
                    speechFrames++;
                } else {
                    speechFrames = 0;
                }


                boolean split = speechFrames >= MAX_LINE_FRAMES;
                VoiceBuffer.offerDirect(audio, RATE, split);
                if (split) {
                    speechFrames = 0;
                }
            }
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        } catch (Throwable t) {
            pdx.dev.fbactors.FlashbackActors.LOGGER.warn("Microphone capture stopped", t);
        }
    }


    private static volatile float lastPeak = 0f;

    public static float lastPeak() {
        return lastPeak;
    }

    private static int available() {
        return Math.max(0, ALC10.alcGetInteger(device, ALC11.ALC_CAPTURE_SAMPLES));
    }

    private static short[] read() {
        float[] buffer = new float[stereo ? FRAME_SIZE * 2 : FRAME_SIZE];
        ALC11.alcCaptureSamples(device, buffer, FRAME_SIZE);
        return stereo ? stereoToMono(buffer) : floatsToShorts(buffer);
    }


    private static float peak(short[] samples) {
        int max = 0;
        for (short s : samples) {
            int magnitude = Math.abs((int) s);
            if (magnitude > max) {
                max = magnitude;
            }
        }
        return max / (float) Short.MAX_VALUE;
    }

    private static long openMic(String name) {


        if (name != null && !name.isBlank() && !"Default".equalsIgnoreCase(name.trim())) {
            long opened = tryOpen(name);
            if (opened != 0L) {
                return opened;
            }
            pdx.dev.fbactors.FlashbackActors.LOGGER.warn(
                "Failed to open microphone '{}', falling back to the default", name);
        }
        long opened = tryOpen(null);
        if (opened == 0L) {
            throw new IllegalStateException("failed to open any microphone");
        }
        return opened;
    }

    private static long tryOpen(String name) {
        return ALC11.alcCaptureOpenDevice(name, RATE,
            stereo ? EXTFloat32.AL_FORMAT_STEREO_FLOAT32 : EXTFloat32.AL_FORMAT_MONO_FLOAT32,
            CAPTURE_BUFFER);
    }

    private static boolean useStereoWorkaround() {
        try {
            String version = org.lwjgl.openal.AL10.alGetString(org.lwjgl.openal.AL10.AL_VERSION);
            if (version == null) {
                return false;
            }

            return version.startsWith("1.25.0") || version.startsWith("1.25.1");
        } catch (Throwable t) {
            return false;
        }
    }


    private static short[] floatsToShorts(float[] input) {
        short[] out = new short[input.length];
        for (int i = 0; i < input.length; i++) {
            out[i] = (short) Math.max(Short.MIN_VALUE,
                Math.min(Short.MAX_VALUE, (int) (input[i] * inputGain * Short.MAX_VALUE)));
        }
        return out;
    }

    private static short[] stereoToMono(float[] input) {
        int frames = input.length / 2;
        short[] out = new short[frames];
        for (int i = 0; i < frames; i++) {
            float mixed = (input[i * 2] + input[i * 2 + 1]) * 0.5f * inputGain;
            out[i] = (short) Math.max(Short.MIN_VALUE,
                Math.min(Short.MAX_VALUE, (int) (mixed * Short.MAX_VALUE)));
        }
        return out;
    }
}

