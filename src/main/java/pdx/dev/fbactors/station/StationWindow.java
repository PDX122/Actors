package pdx.dev.fbactors.station;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.combo_options.Sizing;
import com.moulberry.flashback.editor.ui.ImGuiHelper;
import com.moulberry.flashback.keyframe.Keyframe;
import com.moulberry.flashback.keyframe.KeyframeType;
import com.moulberry.flashback.keyframe.impl.CameraShakeKeyframe;
import com.moulberry.flashback.keyframe.impl.FOVKeyframe;
import com.moulberry.flashback.keyframe.impl.TimeOfDayKeyframe;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.record.FlashbackMeta;
import com.moulberry.flashback.state.EditorScene;
import com.moulberry.flashback.state.EditorSceneHistoryAction;
import com.moulberry.flashback.state.EditorSceneHistoryEntry;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import com.moulberry.flashback.state.KeyframeTrack;
import com.moulberry.flashback.visuals.ReplayVisuals;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.ImGuiViewport;
import imgui.moulberry90.flag.ImGuiCond;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import pdx.dev.fbactors.FlashbackActors;
import pdx.dev.fbactors.actor.Actor;
import pdx.dev.fbactors.director.DirectorMenu;
import pdx.dev.fbactors.director.DirectorShotsWindow;
import pdx.dev.fbactors.settings.FbSettings;
import pdx.dev.fbactors.settings.FbSettingsMenu;
import pdx.dev.fbactors.take.TakeStore;
import pdx.dev.fbactors.theme.ThemeMenu;
import pdx.dev.fbactors.ui.ActorTrack;
import pdx.dev.fbactors.undo.UndoStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;


public final class StationWindow {

    private static final float[] FLOAT_BUFFER = new float[]{0.0F};
    private static final int[] INT_BUFFER = {0};

    private StationWindow() {
    }


    public static void render() {
        if (!FbSettings.stationSidebar()) {
            return;
        }
        ReplayServer replayServer = Flashback.getReplayServer();
        if (replayServer == null) {
            return;
        }
        FlashbackMeta metadata = replayServer.getMetadata();
        EditorState editorState = EditorStateManager.get(metadata.replayIdentifier);
        ReplayVisuals visuals = editorState.replayVisuals;


        ImGuiViewport viewport = ImGui.getMainViewport();
        float panelWidth = 258.0f;
        ImGui.setNextWindowPos(
            viewport.getWorkPosX() + viewport.getWorkSizeX() - panelWidth,
            viewport.getWorkPosY(), ImGuiCond.FirstUseEver);
        ImGui.setNextWindowSize(panelWidth, viewport.getWorkSizeY() * 0.78f,
            ImGuiCond.FirstUseEver);
        if (ImGui.begin("Station###Visuals")) {
            if (ImGui.beginTabBar("##StationTabs")) {
                if (ImGui.beginTabItem("Scene")) {
                    renderScene(editorState, visuals, replayServer);
                    ImGui.endTabItem();
                }
                if (ImGui.beginTabItem("Edit")) {
                    renderEdit(replayServer);
                    ImGui.endTabItem();
                }
                ImGui.endTabBar();
            }
        }
        ImGui.end();
    }


    @SuppressWarnings("deprecation")
    private static void renderScene(EditorState editorState, ReplayVisuals visuals,
                                    ReplayServer replayServer) {
        renderFrameSection(editorState, visuals);
        renderLensSection(editorState, visuals, replayServer);
        renderLightSection(editorState, visuals, replayServer);
        renderCaptureSection(editorState, visuals);

        if (ImGui.collapsingHeader("All scene controls")) {
            renderInterfaceSection(editorState, visuals);
            renderWorldSection(editorState, visuals);
            renderAtmosphereSection(editorState, visuals);
            renderShakeSection(editorState, visuals, replayServer);
            renderExportSection(editorState, visuals, replayServer);
        }
    }


    private static void renderFrameSection(EditorState editorState, ReplayVisuals visuals) {
        ImGuiHelper.separatorWithText("Frame");
        if (ImGui.checkbox(I18n.get("flashback.rule_of_thirds_guide", new Object[0]), visuals.ruleOfThirdsGuide)) {
            visuals.ruleOfThirdsGuide = !visuals.ruleOfThirdsGuide;
            editorState.markDirty();
        }
        if (ImGui.checkbox(I18n.get("flashback.center_guide", new Object[0]), visuals.centerGuide)) {
            visuals.centerGuide = !visuals.centerGuide;
            editorState.markDirty();
        }
        visuals.sizing = ImGuiHelper.enumCombo(I18n.get("flashback.sizing", new Object[0]), visuals.sizing);
        if (visuals.sizing == Sizing.CHANGE_ASPECT_RATIO) {
            visuals.changeAspectRatio = ImGuiHelper.enumCombo(
                I18n.get("flashback.aspect", new Object[0]), visuals.changeAspectRatio);
            editorState.markDirty();
        }
    }


    private static void renderCaptureSection(EditorState editorState, ReplayVisuals visuals) {
        ImGuiHelper.separatorWithText("Capture");
        boolean anyVisible = visuals.showChat || visuals.showBossBar || visuals.showTitleText
            || visuals.showScoreboard || visuals.showActionBar || visuals.showHotbar;
        if (ImGui.checkbox("Hide interface", !anyVisible)) {
            boolean show = anyVisible;
            visuals.showChat = show;
            visuals.showBossBar = show;
            visuals.showTitleText = show;
            visuals.showScoreboard = show;
            visuals.showActionBar = show;
            visuals.showHotbar = show;
            editorState.markDirty();
        }
        ImGui.setItemTooltip("Hides chat, boss bar, titles, scoreboard, action bar and hotbar at once");
        if (ImGui.checkbox(I18n.get("flashback.visuals.world.render_sky", new Object[0]), visuals.renderSky)) {
            visuals.renderSky = !visuals.renderSky;
            editorState.markDirty();
        }
        if (!visuals.renderSky) {
            if (ImGui.colorButton(I18n.get("flashback.visuals.world.sky_colour", new Object[0]), visuals.skyColour)) {
                ImGui.openPopup("##EditSkyColour");
            }
            ImGui.sameLine();
            ImGui.textUnformatted(I18n.get("flashback.visuals.world.sky_colour", new Object[0]));
            if (ImGuiHelper.beginPopup("##EditSkyColour")) {
                ImGui.colorPicker3(I18n.get("flashback.visuals.world.sky_colour", new Object[0]), visuals.skyColour);
                ImGui.endPopup();
            }
        }
        if (ImGui.checkbox(I18n.get("flashback.visuals.world.render_nametags", new Object[0]), visuals.renderNametags)) {
            visuals.renderNametags = !visuals.renderNametags;
            editorState.markDirty();
        }
    }


    private static void renderInterfaceSection(EditorState editorState, ReplayVisuals visuals) {
        ImGuiHelper.separatorWithText(I18n.get("flashback.visuals.gui", new Object[0]));
        if (ImGui.checkbox(I18n.get("flashback.visuals.gui.chat", new Object[0]), visuals.showChat)) {
            visuals.showChat = !visuals.showChat;
            editorState.markDirty();
        }
        if (ImGui.checkbox(I18n.get("flashback.visuals.gui.boss_bar", new Object[0]), visuals.showBossBar)) {
            visuals.showBossBar = !visuals.showBossBar;
            editorState.markDirty();
        }
        if (ImGui.checkbox(I18n.get("flashback.visuals.gui.title_text", new Object[0]), visuals.showTitleText)) {
            visuals.showTitleText = !visuals.showTitleText;
            editorState.markDirty();
        }
        if (ImGui.checkbox(I18n.get("flashback.visuals.gui.scoreboard", new Object[0]), visuals.showScoreboard)) {
            visuals.showScoreboard = !visuals.showScoreboard;
            editorState.markDirty();
        }
        if (ImGui.checkbox(I18n.get("flashback.visuals.gui.action_bar", new Object[0]), visuals.showActionBar)) {
            visuals.showActionBar = !visuals.showActionBar;
            editorState.markDirty();
        }
        if (Minecraft.getInstance().getCameraEntity() != null
            && Minecraft.getInstance().getCameraEntity() != Minecraft.getInstance().player
            && ImGui.checkbox(I18n.get("flashback.visuals.gui.hotbar", new Object[0]), visuals.showHotbar)) {
            visuals.showHotbar = !visuals.showHotbar;
            editorState.markDirty();
        }

    }


    private static void renderWorldSection(EditorState editorState, ReplayVisuals visuals) {
        ImGuiHelper.separatorWithText(I18n.get("flashback.visuals.world", new Object[0]));
        if (ImGui.checkbox(I18n.get("flashback.visuals.world.render_blocks", new Object[0]), visuals.renderBlocks)) {
            visuals.renderBlocks = !visuals.renderBlocks;
            editorState.markDirty();
        }
        if (ImGui.checkbox(I18n.get("flashback.visuals.world.render_entities", new Object[0]), visuals.renderEntities)) {
            visuals.renderEntities = !visuals.renderEntities;
            editorState.markDirty();
        }
        if (ImGui.checkbox(I18n.get("flashback.visuals.world.render_players", new Object[0]), visuals.renderPlayers)) {
            visuals.renderPlayers = !visuals.renderPlayers;
            editorState.markDirty();
        }
        if (ImGui.checkbox(I18n.get("flashback.visuals.world.render_particles", new Object[0]), visuals.renderParticles)) {
            visuals.renderParticles = !visuals.renderParticles;
            editorState.markDirty();
        }
        if (ImGui.checkbox(I18n.get("flashback.visuals.world.render_beacon_beams", new Object[0]), visuals.renderBeaconBeams)) {
            visuals.renderBeaconBeams = !visuals.renderBeaconBeams;
            editorState.markDirty();
        }
    }


    private static void renderAtmosphereSection(EditorState editorState, ReplayVisuals visuals) {
        ImGuiHelper.separatorWithText("Atmosphere");
        if (ImGui.checkbox(I18n.get("flashback.visuals.overrides.override_fog", new Object[0]), visuals.overrideFog)) {
            visuals.overrideFog = !visuals.overrideFog;
            editorState.markDirty();
        }
        if (visuals.overrideFog) {
            FLOAT_BUFFER[0] = visuals.overrideFogStart;
            if (ImGui.sliderFloat(I18n.get("flashback.fog_start", new Object[0]), FLOAT_BUFFER, 0.0F, 512.0F)) {
                visuals.overrideFogStart = FLOAT_BUFFER[0];
                editorState.markDirty();
            }
            FLOAT_BUFFER[0] = visuals.overrideFogEnd;
            if (ImGui.sliderFloat(I18n.get("flashback.fog_end", new Object[0]), FLOAT_BUFFER, 0.0F, 512.0F)) {
                visuals.overrideFogEnd = FLOAT_BUFFER[0];
                editorState.markDirty();
            }
        }
        if (ImGui.checkbox(I18n.get("flashback.visuals.overrides.override_fog_colour", new Object[0]), visuals.overrideFogColour)) {
            visuals.overrideFogColour = !visuals.overrideFogColour;
            editorState.markDirty();
        }
        if (visuals.overrideFogColour) {
            if (ImGui.colorButton(I18n.get("flashback.visuals.overrides.fog_colour", new Object[0]), visuals.fogColour)) {
                ImGui.openPopup("##EditFogColour");
            }
            ImGui.sameLine();
            ImGui.textUnformatted(I18n.get("flashback.visuals.overrides.fog_colour", new Object[0]));
            if (ImGuiHelper.beginPopup("##EditFogColour")) {
                ImGui.colorPicker3(I18n.get("flashback.visuals.overrides.fog_colour", new Object[0]), visuals.fogColour);
                ImGui.endPopup();
            }
        }
    }


    private static void renderLensSection(EditorState editorState, ReplayVisuals visuals,
                                          ReplayServer replayServer) {
        ImGuiHelper.separatorWithText("Lens");
        if (ImGui.checkbox(I18n.get("flashback.visuals.overrides.override_fov", new Object[0]), visuals.overrideFov)) {
            visuals.overrideFov = !visuals.overrideFov;
            editorState.markDirty();
        }
        if (visuals.overrideFov) {
            if (visuals.overrideFovAmount < 0.0F) {
                visuals.overrideFovAmount = Flashback.getConfig().internal.defaultOverrideFov;
            }
            ImGui.sameLine();
            if (ImGui.smallButton("+")) {
                addKeyframe(editorState, replayServer, new FOVKeyframe(visuals.overrideFovAmount));
            }
            ImGuiHelper.tooltip(I18n.get("flashback.add_fov_keyframe", new Object[0]));
            FLOAT_BUFFER[0] = visuals.overrideFovAmount;
            if (ImGui.sliderFloat(I18n.get("flashback.fov", new Object[0]), FLOAT_BUFFER, 1.0F, 110.0F, "%.1f")) {
                visuals.setFov(FLOAT_BUFFER[0]);
                editorState.markDirty();
            }
        }
        if (ImGui.checkbox(I18n.get("flashback.camera_roll", new Object[0]), visuals.overrideRoll)) {
            visuals.overrideRoll = !visuals.overrideRoll;
            editorState.markDirty();
        }
        if (visuals.overrideRoll) {
            FLOAT_BUFFER[0] = visuals.overrideRollAmount;
            if (ImGui.sliderFloat(I18n.get("flashback.roll", new Object[0]), FLOAT_BUFFER, -180.0F, 180.0F, "%.1f")) {
                visuals.overrideRollAmount = FLOAT_BUFFER[0];
                editorState.markDirty();
            }
        }
    }


    private static void renderLightSection(EditorState editorState, ReplayVisuals visuals,
                                           ReplayServer replayServer) {
        ImGuiHelper.separatorWithText("Light");
        if (ImGui.checkbox(I18n.get("flashback.visuals.overrides.override_time", new Object[0]), visuals.overrideTimeOfDay >= 0L)) {
            if (visuals.overrideTimeOfDay >= 0L) {
                visuals.overrideTimeOfDay = -1L;
            } else {
                visuals.overrideTimeOfDay = VisualsBridge.currentDayTime(
                    Minecraft.getInstance().level) % 24000L;
            }
            editorState.markDirty();
        }
        if (visuals.overrideTimeOfDay >= 0L) {
            ImGui.sameLine();
            if (ImGui.smallButton("+")) {
                addKeyframe(editorState, replayServer, new TimeOfDayKeyframe((int) visuals.overrideTimeOfDay));
            }
            ImGuiHelper.tooltip(I18n.get("flashback.add_time_keyframe", new Object[0]));
            INT_BUFFER[0] = (int) visuals.overrideTimeOfDay;
            if (ImGui.sliderInt(I18n.get("flashback.time", new Object[0]), INT_BUFFER, 0, 24000)) {
                visuals.overrideTimeOfDay = INT_BUFFER[0];
                editorState.markDirty();
            }
        }
        if (ImGui.checkbox(I18n.get("flashback.visuals.overrides.night_vision", new Object[0]), visuals.overrideNightVision)) {
            visuals.overrideNightVision = !visuals.overrideNightVision;
        }
        visuals.overrideWeatherMode = ImGuiHelper.enumCombo(
            I18n.get("flashback.weather", new Object[0]), visuals.overrideWeatherMode);
    }


    private static void renderShakeSection(EditorState editorState, ReplayVisuals visuals,
                                           ReplayServer replayServer) {
        ImGuiHelper.separatorWithText("Camera shake");
        if (ImGui.checkbox(I18n.get("flashback.visuals.overrides.camera_shake", new Object[0]), visuals.overrideCameraShake)) {
            visuals.overrideCameraShake = !visuals.overrideCameraShake;
            editorState.markDirty();
        }
        if (visuals.overrideCameraShake) {
            ImGui.sameLine();
            if (ImGui.smallButton("+")) {
                if (visuals.cameraShakeSplitParams) {
                    addKeyframe(editorState, replayServer, new CameraShakeKeyframe(
                        visuals.cameraShakeXFrequency, visuals.cameraShakeXAmplitude,
                        visuals.cameraShakeYFrequency, visuals.cameraShakeYAmplitude, true));
                } else {
                    float frequency = (visuals.cameraShakeXFrequency + visuals.cameraShakeYFrequency) / 2.0F;
                    float amplitude = (visuals.cameraShakeXAmplitude + visuals.cameraShakeYAmplitude) / 2.0F;
                    addKeyframe(editorState, replayServer,
                        new CameraShakeKeyframe(frequency, amplitude, frequency, amplitude, false));
                }
            }
            ImGuiHelper.tooltip(I18n.get("flashback.add_camera_shake_keyframe", new Object[0]));
            if (ImGui.checkbox(I18n.get("flashback.split_yx", new Object[0]), visuals.cameraShakeSplitParams)) {
                visuals.cameraShakeSplitParams = !visuals.cameraShakeSplitParams;
                editorState.markDirty();
            }
            if (visuals.cameraShakeSplitParams) {
                shakeSlider(I18n.get("flashback.frequency_x", new Object[0]),
                    visuals.cameraShakeXFrequency, 0.1F, 10.0F, editorState, visuals, true, false);
                shakeSlider(I18n.get("flashback.amplitude_x", new Object[0]),
                    visuals.cameraShakeXAmplitude, 0.0F, 10.0F, editorState, visuals, false, false);
                shakeSlider(I18n.get("flashback.frequency_y", new Object[0]),
                    visuals.cameraShakeYFrequency, 0.1F, 10.0F, editorState, visuals, true, true);
                shakeSlider(I18n.get("flashback.amplitude_y", new Object[0]),
                    visuals.cameraShakeYAmplitude, 0.0F, 10.0F, editorState, visuals, false, true);
            } else {
                FLOAT_BUFFER[0] = (visuals.cameraShakeXFrequency + visuals.cameraShakeYFrequency) / 2.0F;
                if (ImGui.sliderFloat(I18n.get("flashback.frequency", new Object[0]), FLOAT_BUFFER, 0.1F, 10.0F, "%.1f")) {
                    visuals.cameraShakeXFrequency = FLOAT_BUFFER[0];
                    visuals.cameraShakeYFrequency = FLOAT_BUFFER[0];
                    editorState.markDirty();
                }
                FLOAT_BUFFER[0] = (visuals.cameraShakeXAmplitude + visuals.cameraShakeYAmplitude) / 2.0F;
                if (ImGui.sliderFloat(I18n.get("flashback.amplitude", new Object[0]), FLOAT_BUFFER, 0.0F, 10.0F, "%.1f")) {
                    visuals.cameraShakeXAmplitude = FLOAT_BUFFER[0];
                    visuals.cameraShakeYAmplitude = FLOAT_BUFFER[0];
                    editorState.markDirty();
                }
            }
        }
    }


    private static void renderExportSection(EditorState editorState, ReplayVisuals visuals,
                                            ReplayServer replayServer) {
        ImGuiHelper.separatorWithText(I18n.get("flashback.other", new Object[0]));
        if (VisualsBridge.hasRtcData(replayServer) && VisualsBridge.hasRtcOverlay()) {
            if (ImGui.checkbox(I18n.get("flashback.overlay.rtc_overlay", new Object[0]),
                    VisualsBridge.rtcOverlay())) {
                VisualsBridge.toggleRtcOverlay();
            }
            ImGui.setItemTooltip(I18n.get("flashback.overlay.rtc_overlay.description", new Object[0]));
        }
        if (ImGui.checkbox(I18n.get("flashback.camera_path", new Object[0]), visuals.cameraPath)) {
            visuals.cameraPath = !visuals.cameraPath;
            editorState.markDirty();
        }
        if (!editorState.hideDuringExport.isEmpty()
            && ImGui.button(I18n.get("flashback.unhide_all_entities", new Object[0]))) {
            editorState.hideDuringExport.clear();
            editorState.markDirty();
        }
        if (replayServer.hasServerResourcePack
            && ImGui.checkbox(I18n.get("flashback.disable_server_resource_packs", new Object[0]),
                visuals.disableServerResourcePack)) {
            visuals.disableServerResourcePack = !visuals.disableServerResourcePack;
        }
    }

    private static void shakeSlider(String label, float value, float min, float max,
                                    EditorState editorState, ReplayVisuals visuals,
                                    boolean frequency, boolean y) {
        ImGui.setNextItemWidth(100.0F);
        FLOAT_BUFFER[0] = value;
        if (ImGui.sliderFloat(label, FLOAT_BUFFER, min, max, "%.1f")) {
            if (frequency) {
                if (y) {
                    visuals.cameraShakeYFrequency = FLOAT_BUFFER[0];
                } else {
                    visuals.cameraShakeXFrequency = FLOAT_BUFFER[0];
                }
            } else if (y) {
                visuals.cameraShakeYAmplitude = FLOAT_BUFFER[0];
            } else {
                visuals.cameraShakeXAmplitude = FLOAT_BUFFER[0];
            }
            editorState.markDirty();
        }
    }


    private static void addKeyframe(EditorState editorState, ReplayServer replayServer, Keyframe keyframe) {
        KeyframeType<?> keyframeType = keyframe.keyframeType();
        long stamp = editorState.acquireWrite();
        try {
            EditorScene scene = editorState.getCurrentScene(stamp);
            for (int i = 0; i < scene.keyframeTracks.size(); i++) {
                KeyframeTrack keyframeTrack = scene.keyframeTracks.get(i);
                if (keyframeTrack.enabled && keyframeTrack.keyframeType == keyframeType) {
                    scene.setKeyframe(i, replayServer.getReplayTick(), keyframe);
                    return;
                }
            }
            for (int i = 0; i < scene.keyframeTracks.size(); i++) {
                KeyframeTrack keyframeTrack = scene.keyframeTracks.get(i);
                if (keyframeTrack.keyframeType == keyframeType) {
                    scene.setKeyframe(i, replayServer.getReplayTick(), keyframe);
                    return;
                }
            }
            String description = I18n.get("flashback.added_named_keyframe", new Object[]{keyframeType.name()});
            int newKeyframeTrackIndex = scene.keyframeTracks.size();
            scene.push(new EditorSceneHistoryEntry(
                List.of(new EditorSceneHistoryAction.RemoveTrack(keyframeType, newKeyframeTrackIndex)),
                List.of(
                    new EditorSceneHistoryAction.AddTrack(keyframeType, newKeyframeTrackIndex),
                    new EditorSceneHistoryAction.SetKeyframe(keyframeType, newKeyframeTrackIndex,
                        replayServer.getReplayTick(), keyframe)),
                description));
            editorState.markDirty();
        } finally {
            editorState.release(stamp);
        }
    }


    private static void renderEdit(ReplayServer replayServer) {
        List<Actor> actors = FlashbackActors.ACTORS.actors();
        List<pdx.dev.fbactors.take.ActorTake> takes = new ArrayList<>(actors.size());
        for (Actor actor : actors) {
            takes.add(actor.take);
        }
        ImGui.textDisabled(StationSummary.statusLine(takes, ActorTrack.selectedIds()));
        ImGui.separator();

        ImGuiHelper.separatorWithText("Timeline");
        int tick = replayServer.getReplayTick();
        boolean hasSelection = ActorTrack.selected() != null || !ActorTrack.selectedIds().isEmpty();
        if (ImGui.button("Razor at playhead")) {
            ActorTrack.razorSelectedAt(tick);
        }
        ImGui.setItemTooltip("Split every selected take at tick " + tick + " (Ctrl+B)");
        if (ImGui.button("Ripple-delete selection")) {
            ActorTrack.rippleDeleteSelected();
        }
        ImGui.setItemTooltip("Delete the selection and close the gap (Shift+Delete)");
        if (!UndoStack.canUndoEdit() && !UndoStack.canUndo()) {
            ImGui.beginDisabled();
        }
        if (ImGui.button("Undo")) {
            undoThroughStack();
        }
        if (!UndoStack.canUndoEdit() && !UndoStack.canUndo()) {
            ImGui.endDisabled();
        }
        ImGui.sameLine();
        if (!UndoStack.canRedoEdit() && !UndoStack.canRedo()) {
            ImGui.beginDisabled();
        }
        if (ImGui.button("Redo")) {
            redoThroughStack();
        }
        if (!UndoStack.canRedoEdit() && !UndoStack.canRedo()) {
            ImGui.endDisabled();
        }
        if (!hasSelection) {
            ImGui.textDisabled("Select a take on the timeline first.");
        }
        ImGui.separator();

        ImGuiHelper.separatorWithText("Actors");
        if (ImGui.button("New actor take")) {
            ActorTrack.newActor();
        }
        ImGui.setItemTooltip("Start recording a take (Ctrl+N)");
        ImGui.textDisabled("Ctrl+click toggles multi-select, Ctrl+A selects all.");
        ImGui.separator();

        ImGuiHelper.separatorWithText("Camera");
        if (ImGui.button("Director")) {
            DirectorMenu.open();
        }
        ImGui.setItemTooltip("Auto-generate a cinematic camera from the recorded actors");
        ImGui.sameLine();
        if (ImGui.button("Shots")) {
            DirectorShotsWindow.open();
        }
        ImGui.setItemTooltip("Direct the shot list: who, how, framed how, from when");
        ImGui.separator();

        ImGuiHelper.separatorWithText("Titles & Audio");
        if (FbSettings.subtitlesEnabled()) {
            if (ImGui.button("Subtitles")) {
                pdx.dev.fbactors.subtitle.SubtitleMenu.open();
            }
            ImGui.setItemTooltip("Captions on voice lines and hand-authored subtitle lines");
        } else {
            ImGui.textDisabled("Subtitles are off (FBA Settings).");
        }
        ImGui.separator();

        ImGuiHelper.separatorWithText("Sidebar");
        boolean station = FbSettings.settings().stationSidebar;
        if (ImGui.checkbox("Station sidebar", station)) {
            FbSettings.settings().stationSidebar = !station;
            FbSettings.settings().save();
        }
        ImGui.setItemTooltip("Off brings back Flashback's own Visuals panel");
        if (ImGui.button("FBA Settings")) {
            FbSettingsMenu.open();
        }
        ImGui.sameLine();
        if (ImGui.button("Theme")) {
            ThemeMenu.open();
        }
    }


    private static void undoThroughStack() {
        if (UndoStack.undoEdit()) {
            saveAll();
            return;
        }
        pdx.dev.fbactors.take.ActorTake take = UndoStack.undo();
        if (take == null) {
            return;
        }
        ReplayServer replay = Flashback.getReplayServer();
        if (replay != null) {
            replay.execute(() -> {
                FlashbackActors.ACTORS.add(take);
                UUID replayId = FlashbackActors.loadedReplayIdOrNull();
                if (replayId != null) {
                    TakeStore.save(replayId, take);
                }
            });
        }
    }


    private static void redoThroughStack() {
        if (UndoStack.redoEdit()) {
            return;
        }
        pdx.dev.fbactors.take.ActorTake take = UndoStack.redo();
        if (take == null) {
            return;
        }
        ReplayServer replay = Flashback.getReplayServer();
        if (replay != null) {
            replay.execute(() -> {
                Actor actor = FlashbackActors.ACTORS.byTakeId(take.id);
                if (actor != null) {
                    FlashbackActors.removeActor(actor);
                }
            });
        }
    }

    private static void saveAll() {
        ReplayServer replay = Flashback.getReplayServer();
        if (replay == null) {
            return;
        }
        replay.execute(() -> {
            UUID replayId = FlashbackActors.loadedReplayIdOrNull();
            if (replayId != null) {
                for (Actor actor : FlashbackActors.ACTORS.actors()) {
                    TakeStore.save(replayId, actor.take);
                }
            }
        });
    }
}

