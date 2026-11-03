package pdx.dev.fbactors.settings;

import imgui.moulberry90.ImGui;
import imgui.moulberry90.type.ImBoolean;
import pdx.dev.fbactors.timer.TakeTimer;

public final class FbSettingsMenu {

    private static boolean open;
    private enum Page {
        RECORDING("Recording", "Timing and behavior before and after a take."),
        PLAYBACK("Playback", "Movement quality and simulated motion."),
        ACTORS("Actors", "Combat interactions and actor appearance."),
        WORLD("World", "Local simulation, terrain edits and actor reactions."),
        CAMERAS("Camera & boats", "Camera modes, clips and simulated boat handling."),
        AUDIO("Audio", "Voice recording for actor takes."),
        CAPTIONS("Captions", "Subtitle styling, placement and timing."),
        APPEARANCE("Interface", "Themes and editor panels."),
        SHORTCUTS("Shortcuts", "Keyboard controls for recording and editing.");

        final String label, description;
        Page(String label, String description) { this.label=label; this.description=description; }
    }
    private static Page page=Page.RECORDING;

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
        if (ImGui.menuItem("Settings##FbSettingsOpen", open)) {
            open();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setItemTooltip("Recording, playback, world edits, audio and interface preferences");
        }
    }

    public static void render() {
        if(!open) return;
        var viewport=ImGui.getMainViewport();
        float maxWidth=Math.max(1,viewport.getWorkSizeX()-24),maxHeight=Math.max(1,viewport.getWorkSizeY()-24);
        ImGui.setNextWindowSize(Math.min(740,maxWidth),Math.min(620,maxHeight),imgui.moulberry90.flag.ImGuiCond.Appearing);
        ImGui.setNextWindowSizeConstraints(Math.min(440,maxWidth),Math.min(340,maxHeight),maxWidth,maxHeight);
        ImGui.setNextWindowBgAlpha(1);
        pdx.dev.fbactors.ui.SkinPickerChrome.push(); OPEN.set(open);
        boolean visible=ImGui.begin("Actors settings###ActorSettings",OPEN);
        try {
            if(visible) {
                boolean sidebar=ImGui.getContentRegionAvailX()>=520;
                if(!sidebar) {
                    ImGui.setNextItemWidth(-1);
                    if(ImGui.beginCombo("##SettingsPage",page.label)) {
                        try { for(var entry:Page.values()) if(ImGui.selectable(entry.label,page==entry)) page=entry; }
                        finally { ImGui.endCombo(); }
                    }
                }
                float contentHeight=Math.max(1,ImGui.getContentRegionAvailY()-52);
                if(sidebar) {
                    boolean nav=ImGui.beginChild("##SettingsNavigation",156,contentHeight,false);
                    try {
                        if(nav) for(var entry:Page.values()) {
                            if(entry==Page.RECORDING) ImGui.textDisabled("RECORD & PLAY");
                            if(entry==Page.AUDIO) { ImGui.spacing(); ImGui.textDisabled("PREFERENCES"); }
                            if(pdx.dev.fbactors.ui.SkinPickerChrome.button(entry.label,page==entry,ImGui.getContentRegionAvailX())) page=entry;
                        }
                    } finally { ImGui.endChild(); }
                    ImGui.sameLine();
                }

                boolean body=ImGui.beginChild("##SettingsContent"+page.name(),0,contentHeight,false);
                try {
                    if(body) {
                        ImGui.pushTextWrapPos(0);
                        try {
                            ImGui.textUnformatted(page.label);
                            ImGui.textWrapped(page.description);
                            ImGui.spacing();
                            switch(page) {
                                case RECORDING -> { section("Before recording"); renderCountdown(); section("Take length"); renderTakeTimer(); renderRunOptions(); }
                                case PLAYBACK -> renderPlayback();
                                case ACTORS -> { section("Combat"); renderCombat(); section("Appearance"); renderMorph(); }
                                case WORLD -> renderWorldEdits();
                                case CAMERAS -> renderBoats();
                                case AUDIO -> { section("Microphone"); renderVoice(); ImGui.spacing(); ImGui.textWrapped("Choose your microphone and adjust gain when setting up an actor take."); }
                                case CAPTIONS -> renderSubtitles();
                                case APPEARANCE -> { section("Theme"); renderTheme(); section("Editor panels"); renderStation(); section("Looks");
                                    if(pdx.dev.fbactors.ui.SkinPickerChrome.button("Open looks gallery",false,ImGui.getContentRegionAvailX())) pdx.dev.fbactors.filters.LooksMenu.open(); }
                                case SHORTCUTS -> { section("Keyboard controls"); renderHotkeys(); }
                            }
                        } finally { ImGui.popTextWrapPos(); }
                    }
                } finally { ImGui.endChild(); }
                ImGui.separator();
                if(pdx.dev.fbactors.ui.SkinPickerChrome.button("Done",true,100)) OPEN.set(false);
                if(ImGui.getContentRegionAvailX()>ImGui.calcTextSizeX("Changes save when you close settings.")+120) {
                    ImGui.sameLine(); ImGui.textDisabled("Changes save when you close settings.");
                }
            }
        } finally { ImGui.end(); pdx.dev.fbactors.ui.SkinPickerChrome.pop(); }
        if(!OPEN.get()) close();
    }

    private static void section(String title) {
        ImGui.spacing(); ImGui.separator(); ImGui.spacing(); ImGui.textUnformatted(title);
    }

    private static void field(String label) {
        ImGui.textUnformatted(label);
        ImGui.setNextItemWidth(-1);
    }

    private static boolean option(String label, boolean enabled, String help) {
        boolean changed = ImGui.checkbox(label, enabled);
        ImGui.setItemTooltip(help);
        return changed ? !enabled : enabled;
    }
    private static void renderPlayback() {
        var s = FbSettings.settings();
        section("Movement quality");
        renderMovement();
        section("Simulated motion");
        s.flightPlayback = option("Simulated elytra and fireworks", s.flightPlayback, "Apply recorded gliding starts and tick attached firework boosts when movement is simulated.");
        ImGui.spacing();
        s.slidingMomentum = option("Preserve sliding momentum", s.slidingMomentum, "Keep measured actor and boat momentum on ice and when switching to physics. Off estimates momentum from positions.");
    }
    public static void openBoats() { page = Page.CAMERAS; open(); }
    private static void renderBoats() {
        section("Camera modes");
        ImGui.textWrapped("Browse shots, tune framing and attach camera clips to actors.");
        if(pdx.dev.fbactors.ui.SkinPickerChrome.button("Open camera modes",true,ImGui.getContentRegionAvailX()))
            pdx.dev.fbactors.camera.BoatMenu.open(0);
        section("Boat handling");
        pdx.dev.fbactors.camera.BoatMenu.handling();
    }
    private static void renderWorldEdits() {
        var s = FbSettings.settings();
        section("Local simulation");
        s.worldMechanics = option("Local redstone, fluids and geysers", s.worldMechanics, "Advance native scheduled blocks, fluids and block entities around actors and their interactions. Includes sulfur geysers where available.");
        s.environmentForces = option("Environmental movement forces", s.environmentForces, "React to geyser impulses, bubble columns and piston movement instead of snapping back to the saved path.");
        ImGui.spacing();
        section("Terrain edits");
        s.viewportEdits = option("Save viewport block edits", s.viewportEdits, "Save scene starting conditions and restore them after scrubbing. During playback, redstone and pistons can change these blocks. Off leaves saved edits inactive after the next seek.");
        ImGui.beginDisabled(!s.viewportEdits);
        s.terrainReactions = option("Actors react to terrain edits", s.terrainReactions, "Missing support and new obstacles switch affected actors and boats to physics. Off keeps their recorded paths.");
        ImGui.endDisabled();
        ImGui.textWrapped("Terrain reactions keep the take's movement controls. Unaffected actors follow their saved performance.");
    }
    private static void renderRunOptions() {
        var s = FbSettings.settings();
        ImGui.spacing();
        section("After recording");
        s.escapeEndsRun = option("Escape finishes at end", s.escapeEndsRun, "Escape finishes an actor run only at the end of the footage, with no screen open. Closing an inventory never ends the run. Use Stop recording to finish earlier.");
        s.restoreEditorCamera = option("Restore editor camera", s.restoreEditorCamera, "Return to the camera position from before the run. Off stays at the performer's final position.");
        s.runHints = option("Show run hints", s.runHints, "Show the stop shortcut during recording and at the end of footage.");
    }

    private static void renderMovement() {
        FbSettings settings = FbSettings.settings();
        if (ImGui.checkbox("Send actor movement every tick##FbActorPackets", settings.actorPacketMovement)) {
            settings.actorPacketMovement = !settings.actorPacketMovement;
        }
        ImGui.setItemTooltip("Send movement every tick for actors and their boats, for smoother playback.");
        ImGui.textDisabled("Off uses the normal Minecraft packet cadence.");
    }

    private static void renderCombat() {
        FbSettings settings = FbSettings.settings();
        if (ImGui.checkbox("Actor combat##FbCombat", settings.combatEnabled)) {
            settings.combatEnabled = !settings.combatEnabled;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setItemTooltip("When on, a viewer's punch lands on actors: damage, knockback and hit recording");
        }
        ImGui.textDisabled("When off, actors are scenery. Punches pass through.");
        if (ImGui.checkbox("Damage recorded players##FbHostDamage",
            settings.hostDamageEnabled)) {
            settings.hostDamageEnabled = !settings.hostDamageEnabled;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setItemTooltip("When on, an actor's swing on a recorded player is recorded and replayed: damage, knockback and death");
        }
        ImGui.textDisabled("When off, host hits land live for one tick and playback covers them up.");
    }

    private static void renderTakeTimer() {
        FbSettings settings = FbSettings.settings();
        int[] seconds = {settings.takeTimerSeconds};
        field("Stop automatically after");
        if (ImGui.sliderInt("##FbTakeTimer", seconds, 0, 300, "%ds")) {
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
        field("Countdown before recording");
        if (ImGui.sliderInt("##FbCountdown", seconds, 1, 10, "%ds")) {
            settings.countdownSeconds = Math.max(1, seconds[0]);
        }
        if (ImGui.isItemHovered()) {
            ImGui.setItemTooltip("Countdown with a colored number and sound each second. Used by /start with no argument.");
        }
    }

    private static void renderVoice() {
        FbSettings settings = FbSettings.settings();
        if (ImGui.checkbox("Microphone recording##FbVoice", settings.voiceEnabled)) {
            settings.voiceEnabled = !settings.voiceEnabled;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setItemTooltip("Microphone capture for takes and voice playback");
        }
        ImGui.textDisabled("Game sounds remain available when microphone recording is off.");
    }

    private static void renderMorph() {
        FbSettings settings = FbSettings.settings();
        if (ImGui.checkbox("Actor morphs##FbMorph", settings.morphEnabled)) {
            settings.morphEnabled = !settings.morphEnabled;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setItemTooltip("Mob morphs for takes: picker and commands");
        }
        ImGui.textDisabled("When off, takes play as players.");
    }

    private static void renderHotkeys() {
        FbSettings settings = FbSettings.settings();
        if (ImGui.checkbox("Actor shortcuts##FbHotkeysOn", settings.hotkeysEnabled)) {
            settings.hotkeysEnabled = !settings.hotkeysEnabled;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setItemTooltip("Disable the addon shortcuts. Flashback's Hide [F1] control stays available.");
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
        ImGui.textDisabled("Current: " + current.label());
        if (ImGui.button("UI themes##FbOpenThemeMaker", Math.max(1, ImGui.getContentRegionAvailX()), 0.0f)) {
            ThemeMakerMenu.open();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setItemTooltip("Pick a preset or build a custom UI theme");
        }
    }

    private static void renderStation() {
        FbSettings settings = FbSettings.settings();
        if (ImGui.checkbox("Actors Studio##FbStation", settings.stationSidebar)) {
            settings.stationSidebar = !settings.stationSidebar;
            settings.save();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setItemTooltip("Show Actors Studio in place of Flashback's Visuals panel. Drag its title to dock it.");
        }
        ImGui.textDisabled("When off, the stock Visuals panel draws.");
    }

    private static void renderSubtitles() {
        FbSettings settings = FbSettings.settings();
        section("Visibility");
        if (ImGui.checkbox("Captions on##FbSubOn", settings.subEnabled)) {
            settings.subEnabled = !settings.subEnabled;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setItemTooltip("Voice captions and manual lines, in preview and in exports with GUI on");
        }
        ImGui.textDisabled("When off, captions hide and the Subtitles window stays closed.");

        section("Text style");
        ImGui.beginDisabled(!settings.subEnabled);
        float[] scale = {Math.max(0.5f, Math.min(2.0f, settings.subScale))};
        field("Size");
        if (ImGui.sliderFloat("##FbSubScale", scale, 0.5f, 2.0f, "%.2fx")) {
            settings.subScale = Math.max(0.5f, Math.min(2.0f, scale[0]));
        }

        field("Text");
        ImGui.colorEdit3("##FbSubText", settings.subTextColor);

        if (ImGui.checkbox("Outline##FbSubOutline", settings.subOutline)) {
            settings.subOutline = !settings.subOutline;
        }
        if (settings.subOutline) {
            field("Outline colour");
            ImGui.colorEdit3("##FbSubOutlineColor", settings.subOutlineColor);
        }
        if (ImGui.checkbox("Drop shadow##FbSubShadow", settings.subShadow)) {
            settings.subShadow = !settings.subShadow;
        }
        if (ImGui.isItemHovered()) {
            ImGui.setItemTooltip("Vanilla shadow under each line. Best with the outline off");
        }

        section("Background");
        if (ImGui.checkbox("Background box##FbSubBg", settings.subBackground)) {
            settings.subBackground = !settings.subBackground;
        }
        if (settings.subBackground) {
            field("Box colour");
            ImGui.colorEdit3("##FbSubBgColor", settings.subBgColor);
            float[] opacity = {settings.subBgOpacity};
            field("Box opacity");
            if (ImGui.sliderFloat("##FbSubBgOpacity", opacity, 0.0f, 0.9f, "%.2f")) {
                settings.subBgOpacity = Math.max(0.0f, Math.min(0.9f, opacity[0]));
            }
            int[] padding = {Math.max(0, settings.subPadding)};
            field("Box padding");
            if (ImGui.sliderInt("##FbSubPad", padding, 0, 16, "%d px")) {
                settings.subPadding = Math.max(0, Math.min(16, padding[0]));
            }
        }

        section("Placement and speaker");
        field("Position");
        if (ImGui.beginCombo("##FbSubPos", settings.subPosition == 1 ? "Top" : "Bottom")) {
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
            field("Name colour");
            ImGui.colorEdit3("##FbSubSpeakerColor", settings.subSpeakerColor);
        }

        section("Timing and wrapping");
        int[] fade = {Math.max(0, settings.subFadeTicks)};
        field("Fade");
        if (ImGui.sliderInt("##FbSubFade", fade, 0, 20, "%d ticks")) {
            settings.subFadeTicks = Math.max(0, Math.min(20, fade[0]));
        }
        int[] maxLines = {Math.max(1, Math.min(4, settings.subMaxLines))};
        field("Lines at once");
        if (ImGui.sliderInt("##FbSubLines", maxLines, 1, 4, "%d")) {
            settings.subMaxLines = Math.max(1, Math.min(4, maxLines[0]));
        }
        int[] maxWidth = {Math.max(200, Math.min(1200, settings.subMaxWidth))};
        field("Wrap width");
        if (ImGui.sliderInt("##FbSubWidth", maxWidth, 200, 1200, "%d px")) {
            settings.subMaxWidth = Math.max(200, Math.min(1200, maxWidth[0]));
        }

        ImGui.endDisabled();
        section("Caption editor");
        if (settings.subEnabled) {
            if (ImGui.button("Open Subtitles##FbOpenSubtitles", Math.max(1, ImGui.getContentRegionAvailX()), 0.0f)) {
                pdx.dev.fbactors.subtitle.SubtitleMenu.open();
            }
            if (ImGui.isItemHovered()) {
                ImGui.setItemTooltip("Write captions and manual lines for every actor");
            }
        } else {
            ImGui.textDisabled("Turn captions on to write subtitles.");
        }
    }

}
