package pdx.dev.fbactors.actor;

import imgui.moulberry90.ImGui;
import imgui.moulberry90.type.ImBoolean;
import pdx.dev.fbactors.morph.MorphSettings;
import pdx.dev.fbactors.voice.MicCapture;
import pdx.dev.fbactors.voice.MicDevices;
import pdx.dev.fbactors.voice.VoiceSettings;

import java.util.List;


public final class ActorSetupMenu {

    private static final float PANEL_WIDTH = 340.0f;
    private static final float PANEL_HEIGHT = 420.0f;


    private static final List<Integer> THRESHOLDS = List.of(-30, -40, -50, -60);

    private static boolean open;


    private static final ImBoolean OPEN = new ImBoolean();


    private static final boolean[] wantVoice = {true};


    private static final boolean[] voiceActivation = {true};
    private static final int[] threshold = {-45};
    private static final int[] gainPercent = {100};
    private static int deviceIndex;

    private static List<MicDevices.Device> devices = List.of();

    private ActorSetupMenu() {
    }

    public static boolean isOpen() {
        return open;
    }


    public static void open() {
        pdx.dev.fbactors.settings.FbSettings saved = pdx.dev.fbactors.settings.FbSettings.settings();
        wantVoice[0] = VoiceSettings.enabled();
        voiceActivation[0] = saved.voiceActivation;
        threshold[0] = saved.voiceThreshold;
        gainPercent[0] = Math.max(10, Math.min(200, Math.round(saved.micGain * 100.0f)));

        devices = VoiceSettings.systemEnabled() ? MicDevices.list() : List.of();
        deviceIndex = 0;
        String name = saved.voiceDevice;
        if (name != null) {
            for (int i = 0; i < devices.size(); i++) {
                if (name.equals(devices.get(i).name())) {
                    deviceIndex = i;
                    break;
                }
            }
        }
        open = true;
        OPEN.set(true);
    }


    public static void close() {
        open = false;
        OPEN.set(false);
    }


    public static void render() {
        if (!open) {
            return;
        }
        ImGui.setNextWindowSize(PANEL_WIDTH, PANEL_HEIGHT);
        OPEN.set(open);
        if (!ImGui.begin("Actor Setup", OPEN)) {
            ImGui.end();
            return;
        }
        open = OPEN.get();

        renderStep1();
        ImGui.separator();
        if (wantVoice[0] && VoiceSettings.systemEnabled()) {
            renderStep2();
            ImGui.separator();
        }
        renderFooter();

        ImGui.end();
        open = OPEN.get();
    }


    private static void renderStep1() {
        ImGui.text("What do you want for your actor?");
        ImGui.textDisabled("Choose the features this actor will use.");

        if (VoiceSettings.systemEnabled()) {
            if (ImGui.checkbox("Voice##ActorWantVoice", wantVoice[0])) {
                wantVoice[0] = !wantVoice[0];
            }
            if (ImGui.isItemHovered()) {
                ImGui.setItemTooltip("Record a voice line for this actor.");
            }
        }
    }


    private static void renderStep2() {
        ImGui.text("Voice");
        ImGui.textDisabled("Record a voice line for this actor.");

        if (ImGui.checkbox("Voice activation##ActorVoiceActivation", voiceActivation[0])) {
            voiceActivation[0] = !voiceActivation[0];
        }
        if (voiceActivation[0]) {
            ImGui.setNextItemWidth(PANEL_WIDTH - 40.0f);

            if (ImGui.sliderInt("Threshold##ActorVoiceThreshold", threshold,
                    -60, -30, "%d dB")) {
                int nearest = THRESHOLDS.get(0);
                for (int t : THRESHOLDS) {
                    if (Math.abs(t - threshold[0]) < Math.abs(nearest - threshold[0])) {
                        nearest = t;
                    }
                }
                threshold[0] = nearest;
            }
        }
        ImGui.setNextItemWidth(PANEL_WIDTH - 40.0f);
        if (ImGui.sliderInt("Mic gain##ActorVoiceGain", gainPercent, 10, 200, "%d%%")) {
            gainPercent[0] = Math.max(10, Math.min(200, gainPercent[0]));
        }
        if (ImGui.isItemHovered()) {
            ImGui.setItemTooltip("Input volume. Turn down when the meter hits CLIP");
        }
        ImGui.text("Microphone");
        ImGui.setNextItemWidth(PANEL_WIDTH - 40.0f);
        if (ImGui.button(deviceLabel(), PANEL_WIDTH - 40.0f, 0.0f)) {
            deviceIndex = (deviceIndex + 1) % Math.max(1, devices.size());
        }
        if (ImGui.isItemHovered()) {
            ImGui.setItemTooltip("Click to cycle through available microphones");
        }
    }

    private static void renderFooter() {
        if (ImGui.button("Confirm##ActorSetupConfirm", (PANEL_WIDTH - 50.0f) / 2.0f, 0.0f)) {
            float gain = Math.max(10, Math.min(200, gainPercent[0])) / 100.0f;
            VoiceSettings.capture(wantVoice[0], voiceActivation[0], threshold[0], deviceName(),
                gain);

            pdx.dev.fbactors.settings.FbSettings saved =
                pdx.dev.fbactors.settings.FbSettings.settings();
            saved.voiceActivation = voiceActivation[0];
            saved.voiceThreshold = threshold[0];
            saved.voiceDevice = selectedDeviceOrNull();
            saved.micGain = gain;
            saved.save();
            MorphSettings.capture(null);
            close();
            ActorMode.confirmSetup();
        }
        ImGui.sameLine();
        if (ImGui.button("Cancel##ActorSetupCancel", (PANEL_WIDTH - 50.0f) / 2.0f, 0.0f)) {
            MicCapture.stop();
            close();
            ActorMode.cancelSetup();
        }
    }


    private static String deviceName() {
        return selectedDeviceOrNull();
    }

    private static String selectedDeviceOrNull() {
        if (devices.isEmpty() || deviceIndex >= devices.size()) {
            return null;
        }
        MicDevices.Device device = devices.get(deviceIndex);
        return device.isDefault() ? null : device.name();
    }

    private static String deviceLabel() {
        String name = selectedDeviceOrNull();
        return "Device: " + (name == null ? "Default" : name);
    }
}

