package pdx.dev.fbactors.settings;

import imgui.moulberry90.ImGui;
import imgui.moulberry90.type.ImBoolean;
import pdx.dev.fbactors.timer.TakeTimer;


public final class FbSettingsMenu {

    private static final float PANEL_WIDTH = 320.0f;
    private static final float PANEL_HEIGHT = 420.0f;

    private static boolean open;


    private static final ImBoolean OPEN = new ImBoolean();

    private FbSettingsMenu() {
    }

    public static boolean isOpen() {
        return open;
    }

    public static void open() {
        open = true;
        OPEN.set(true);
    }


    public static void close() {
        open = false;
        OPEN.set(false);
        FbSettings.settings().save();
    }


    public static void renderMenuItem() {
        if (ImGui.menuItem("FBA Settings##FbSettingsOpen", open)) {
            open();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setItemTooltip("Flashback Actors settings: combat, take timer, countdown, voice, hotkeys, beta features and UI theme");
        }
    }


    public static void render() {
        if (!open) {
            return;
        }
        ImGui.setNextWindowSize(PANEL_WIDTH, PANEL_HEIGHT);
        OPEN.set(open);
        if (!ImGui.begin("FBA Settings (Beta)", OPEN)) {
            ImGui.end();
            return;
        }
        open = OPEN.get();

        renderCombat();
        ImGui.separator();
        renderTakeTimer();
        ImGui.separator();
        renderCountdown();
        ImGui.separator();
        renderVoice();
        ImGui.separator();
        renderMorph();
        ImGui.separator();
        renderHotkeys();
        ImGui.separator();
        renderTheme();
        ImGui.separator();
        renderStation();
        ImGui.separator();
        renderSubtitles();
        ImGui.separator();
        renderSelfTest();

        ImGui.end();
        open = OPEN.get();
    }


    private static void renderCombat() {
        FbSettings settings = FbSettings.settings();
        if (ImGui.checkbox("Combat (damage and knockback) [Beta]##FbCombat", settings.combatEnabled)) {
            settings.combatEnabled = !settings.combatEnabled;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setItemTooltip("BETA. When on, a viewer's punch lands on actors: damage, knockback and hit recording");
        }
        ImGui.textDisabled("When off, actors are scenery. Punches pass through.");
        if (ImGui.checkbox("Host damage (actors can hurt recorded players) [Beta]##FbHostDamage",
            settings.hostDamageEnabled)) {
            settings.hostDamageEnabled = !settings.hostDamageEnabled;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setItemTooltip("BETA. When on, an actor's swing on a recorded player is recorded and replayed: damage, knockback and death");
        }
        ImGui.textDisabled("When off, host hits land live for one tick and playback covers them up.");
    }


    private static void renderTakeTimer() {
        FbSettings settings = FbSettings.settings();
        int[] seconds = {settings.takeTimerSeconds};
        ImGui.text("Take timer");
        ImGui.setNextItemWidth(PANEL_WIDTH - 40.0f);
        if (ImGui.sliderInt("Budget##FbTakeTimer", seconds, 0, 300, "%ds")) {
            settings.takeTimerSeconds = Math.max(0, seconds[0]);
            TakeTimer.durationSeconds(settings.takeTimerSeconds);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setItemTooltip("How long a take runs before it stops itself. 0 = until you stop it");
        }
        if (settings.takeTimerSeconds <= 0) {
            ImGui.textDisabled("No limit - takes run until you stop them.");
        }
    }


    private static void renderCountdown() {
        FbSettings settings = FbSettings.settings();
        int[] seconds = {Math.max(1, settings.countdownSeconds)};
        ImGui.text("Countdown");
        ImGui.setNextItemWidth(PANEL_WIDTH - 40.0f);
        if (ImGui.sliderInt("Delay##FbCountdown", seconds, 1, 10, "%ds")) {
            settings.countdownSeconds = Math.max(1, seconds[0]);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setItemTooltip("3-2-1 delay before recording starts, used by /start with no argument");
        }
    }


    private static void renderVoice() {
        FbSettings settings = FbSettings.settings();
        if (ImGui.checkbox("Voice system##FbVoice", settings.voiceEnabled)) {
            settings.voiceEnabled = !settings.voiceEnabled;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setItemTooltip("BETA. Microphone capture for takes and voice playback");
        }
        ImGui.textDisabled("When off, takes record silently.");
    }


    private static void renderMorph() {
        FbSettings settings = FbSettings.settings();
        if (ImGui.checkbox("Morph system##FbMorph", settings.morphEnabled)) {
            settings.morphEnabled = !settings.morphEnabled;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setItemTooltip("Mob morphs for takes: picker and commands");
        }
        ImGui.textDisabled("When off, takes play as players.");
    }


    private static void renderHotkeys() {
        FbSettings settings = FbSettings.settings();
        ImGui.text("Hotkeys");
        if (ImGui.checkbox("Hotkeys on##FbHotkeysOn", settings.hotkeysEnabled)) {
            settings.hotkeysEnabled = !settings.hotkeysEnabled;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setItemTooltip("When off, no shortcut fires and F1 does nothing; drive the mod from the timeline and menus");
        }
        ImGui.textDisabled("Change keys in Minecraft Settings > Controls > Flashback Actors.");
        ImGui.textDisabled("Modifiers in brackets are fixed; the key itself is rebindable.");
        if (!settings.hotkeysEnabled) {
            ImGui.textDisabled("Hotkeys are off. Turn them back on here.");
        }
    }


    private static void renderTheme() {
        FbSettings settings = FbSettings.settings();
        UiTheme current = UiTheme.byId(settings.uiTheme);
        ImGui.text("UI theme");
        ImGui.textDisabled("Current: " + current.label());
        if (ImGui.button("Open Theme Maker##FbOpenThemeMaker", PANEL_WIDTH - 40.0f, 0.0f)) {
            ThemeMakerMenu.open();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setItemTooltip("Pick a preset or build a custom UI theme");
        }
    }


    private static void renderStation() {
        FbSettings settings = FbSettings.settings();
        if (ImGui.checkbox("Station sidebar##FbStation", settings.stationSidebar)) {
            settings.stationSidebar = !settings.stationSidebar;
            settings.save();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setItemTooltip("Replace Flashback's Visuals panel with the Station (Scene + Edit tabs)");
        }
        ImGui.textDisabled("When off, the stock Visuals panel draws.");
    }


    private static void renderSubtitles() {
        FbSettings settings = FbSettings.settings();
        ImGui.text("Subtitles");
        if (ImGui.checkbox("Captions on##FbSubOn", settings.subEnabled)) {
            settings.subEnabled = !settings.subEnabled;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setItemTooltip("Voice captions and manual lines, in preview and in exports with GUI on");
        }
        ImGui.textDisabled("When off, captions hide and the Subtitles window stays closed.");

        float[] scale = {Math.max(0.5f, Math.min(2.0f, settings.subScale))};
        ImGui.setNextItemWidth(PANEL_WIDTH - 40.0f);
        if (ImGui.sliderFloat("Size##FbSubScale", scale, 0.5f, 2.0f, "%.2fx")) {
            settings.subScale = Math.max(0.5f, Math.min(2.0f, scale[0]));
        }

        ImGui.setNextItemWidth(PANEL_WIDTH - 40.0f);
        ImGui.colorEdit3("Text##FbSubText", settings.subTextColor);

        if (ImGui.checkbox("Outline##FbSubOutline", settings.subOutline)) {
            settings.subOutline = !settings.subOutline;
        }
        if (settings.subOutline) {
            ImGui.setNextItemWidth(PANEL_WIDTH - 40.0f);
            ImGui.colorEdit3("Outline colour##FbSubOutlineColor", settings.subOutlineColor);
        }
        if (ImGui.checkbox("Drop shadow##FbSubShadow", settings.subShadow)) {
            settings.subShadow = !settings.subShadow;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setItemTooltip("Vanilla shadow under each line. Best with the outline off");
        }

        if (ImGui.checkbox("Background box##FbSubBg", settings.subBackground)) {
            settings.subBackground = !settings.subBackground;
        }
        if (settings.subBackground) {
            ImGui.setNextItemWidth(PANEL_WIDTH - 40.0f);
            ImGui.colorEdit3("Box colour##FbSubBgColor", settings.subBgColor);
            float[] opacity = {settings.subBgOpacity};
            ImGui.setNextItemWidth(PANEL_WIDTH - 40.0f);
            if (ImGui.sliderFloat("Box opacity##FbSubBgOpacity", opacity, 0.0f, 0.9f, "%.2f")) {
                settings.subBgOpacity = Math.max(0.0f, Math.min(0.9f, opacity[0]));
            }
            int[] padding = {Math.max(0, settings.subPadding)};
            ImGui.setNextItemWidth(PANEL_WIDTH - 40.0f);
            if (ImGui.sliderInt("Box padding##FbSubPad", padding, 0, 16, "%d px")) {
                settings.subPadding = Math.max(0, Math.min(16, padding[0]));
            }
        }

        ImGui.setNextItemWidth(PANEL_WIDTH - 40.0f);
        if (ImGui.beginCombo("Spot##FbSubPos", settings.subPosition == 1 ? "Top" : "Bottom")) {
            if (ImGui.menuItem("Bottom", settings.subPosition == 0)) {
                settings.subPosition = 0;
            }
            if (ImGui.menuItem("Top", settings.subPosition == 1)) {
                settings.subPosition = 1;
            }
            ImGui.endCombo();
        }

        if (ImGui.checkbox("Speaker names##FbSubSpeaker", settings.subSpeaker)) {
            settings.subSpeaker = !settings.subSpeaker;
        }
        if (settings.subSpeaker) {
            ImGui.setNextItemWidth(PANEL_WIDTH - 40.0f);
            ImGui.colorEdit3("Name colour##FbSubSpeakerColor", settings.subSpeakerColor);
        }

        int[] fade = {Math.max(0, settings.subFadeTicks)};
        ImGui.setNextItemWidth(PANEL_WIDTH - 40.0f);
        if (ImGui.sliderInt("Fade##FbSubFade", fade, 0, 20, "%d ticks")) {
            settings.subFadeTicks = Math.max(0, Math.min(20, fade[0]));
        }
        int[] maxLines = {Math.max(1, Math.min(4, settings.subMaxLines))};
        ImGui.setNextItemWidth(PANEL_WIDTH - 40.0f);
        if (ImGui.sliderInt("Lines at once##FbSubLines", maxLines, 1, 4, "%d")) {
            settings.subMaxLines = Math.max(1, Math.min(4, maxLines[0]));
        }
        int[] maxWidth = {Math.max(200, Math.min(1200, settings.subMaxWidth))};
        ImGui.setNextItemWidth(PANEL_WIDTH - 40.0f);
        if (ImGui.sliderInt("Wrap width##FbSubWidth", maxWidth, 200, 1200, "%d px")) {
            settings.subMaxWidth = Math.max(200, Math.min(1200, maxWidth[0]));
        }

        if (settings.subEnabled) {
            if (ImGui.button("Open Subtitles##FbOpenSubtitles", PANEL_WIDTH - 40.0f, 0.0f)) {
                pdx.dev.fbactors.subtitle.SubtitleMenu.open();
            }
            if (ImGui.isItemHovered()) {
                ImGui.setItemTooltip("Write captions and manual lines for every actor");
            }
        } else {
            ImGui.textDisabled("Turn captions on to write subtitles.");
        }
    }


    private static void renderSelfTest() {
        FbSettings settings = FbSettings.settings();
        if (ImGui.checkbox("Self-test on replay load##FbSelfTestAuto", settings.selfTestOnLoad)) {
            settings.selfTestOnLoad = !settings.selfTestOnLoad;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setItemTooltip("Spawn three scratch actors and verify a full take plays back");
        }
        if (ImGui.button("Run actor self-test now##FbSelfTestRun")) {
            pdx.dev.fbactors.selftest.SelfTest.requestStart();
        }
        ImGui.textDisabled(pdx.dev.fbactors.selftest.SelfTest.status());
    }
}
