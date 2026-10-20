package pdx.dev.fbactors.voice;

import pdx.dev.fbactors.settings.FbSettings;
import pdx.dev.fbactors.take.ActorTake;


public final class VoiceSettings {


    public static boolean systemEnabled() {
        return FbSettings.voiceEnabled();
    }

    private static boolean voiceEnabled = true;
    private static boolean voiceActivation = true;
    private static int threshold = -45;
    private static String deviceName;
    private static float gain = 1.0f;

    private VoiceSettings() {
    }


    public static void capture(boolean enabled, boolean activation, int threshold, String device,
                               float gain) {
        voiceEnabled = enabled;
        voiceActivation = activation;
        VoiceSettings.threshold = threshold;
        deviceName = device;
        VoiceSettings.gain = clampGain(gain);
    }

    public static void applyTo(ActorTake take) {
        take.voiceEnabled = systemEnabled() && voiceEnabled;
        take.voiceActivation = voiceActivation;
        take.voiceThreshold = threshold;
        take.voiceDevice = deviceName;
    }

    public static void reset() {
        voiceEnabled = true;
        voiceActivation = true;
        threshold = -45;
        deviceName = null;
        gain = 1.0f;
    }

    public static boolean enabled() {
        return systemEnabled() && voiceEnabled;
    }

    public static boolean activation() {
        return voiceActivation;
    }

    public static int threshold() {
        return threshold;
    }

    public static String deviceName() {
        return deviceName;
    }


    public static float gain() {
        return gain;
    }

    private static float clampGain(float value) {
        return Math.max(0.1f, Math.min(2.0f, value));
    }
}

