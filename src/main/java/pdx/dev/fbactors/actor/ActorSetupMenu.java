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
        ImGui.setNextWindowSize(PANEL_WIDTH, PANEL_HEIGHT, imgui.moulberry90.flag.ImGuiCond.FirstUseEver);
        OPEN.set(open);
        if (!ImGui.begin("Actor Setup", OPEN)) {
            open = OPEN.get();
            ImGui.end();
            if (!open) cancel();
            return;
        }
        open = OPEN.get();
        if (!open) {
            ImGui.end();
            cancel();
            return;
        }

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
        ImGui.text("New actor take");
        ImGui.textWrapped("Set up your audio, then start the countdown. Press R to finish recording.");

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
        ImGui.textDisabled("Microphone settings");

        if (ImGui.checkbox("Voice activation##ActorVoiceActivation", voiceActivation[0])) {
            voiceActivation[0] = !voiceActivation[0];
        }
        if (voiceActivation[0]) {
            ImGui.setNextItemWidth(-90.0f);

            ImGui.sliderInt("Threshold##ActorVoiceThreshold", threshold, -60, -20, "%d dB");
            ImGui.setItemTooltip("Lower values pick up quieter speech. Higher values reject more background noise.");
        }
        ImGui.setNextItemWidth(-90.0f);
        if (ImGui.sliderInt("Mic gain##ActorVoiceGain", gainPercent, 10, 200, "%d%%")) {
            gainPercent[0] = Math.max(10, Math.min(200, gainPercent[0]));
        }
        if (ImGui.isItemHovered()) {
            ImGui.setItemTooltip("Input volume. Turn down when the meter hits CLIP");
        }
        ImGui.text("Microphone");
        ImGui.setNextItemWidth(-1.0f);
        if (ImGui.beginCombo("##ActorMicrophone", deviceLabel())) {
            for (int i = 0; i < devices.size(); i++) {
                MicDevices.Device device = devices.get(i);
                if (ImGui.selectable(device.isDefault() ? "System default" : device.name(), deviceIndex == i)) {
                    deviceIndex = i;
                }
            }
            ImGui.endCombo();
        }
    }

    private static void renderFooter() {
        if (ImGui.button("Start recording##ActorSetupConfirm", (PANEL_WIDTH - 50.0f) / 2.0f, 0.0f)) {
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
            cancel();
        }
    }

    private static void cancel() {
        MicCapture.stop();
        close();
        ActorMode.cancelSetup();
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
        return name == null ? "System default" : name;
    }
}

