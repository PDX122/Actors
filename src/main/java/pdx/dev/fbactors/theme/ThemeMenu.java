package pdx.dev.fbactors.theme;

import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import com.moulberry.flashback.visuals.ReplayVisuals;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.type.ImBoolean;
import org.jetbrains.annotations.Nullable;


public final class ThemeMenu {

    private static final float PANEL_WIDTH = 340.0f;
    private static final float PANEL_HEIGHT = 420.0f;

    private static boolean open;


    private static final ImBoolean OPEN = new ImBoolean();

    private static Theme theme = Theme.NONE;


    private static boolean captured;


    private static final float[] originalSky = {0.50f, 0.70f, 1.00f};
    private static final float[] originalFog = {0.50f, 0.70f, 1.00f};
    private static boolean originalFogOn;
    private static boolean originalFogColourOn;
    private static float originalFogStart;
    private static float originalFogEnd;
    private static long originalTime = -1L;
    private static boolean originalNightVision;


    private static final int[] timeBuffer = {6000};


    private static final float[] fogStartBuffer = {24.0f};
    private static final float[] fogEndBuffer = {220.0f};

    private ThemeMenu() {
    }

    public static boolean isOpen() {
        return open;
    }


    public static void open() {
        open = true;
        OPEN.set(true);
        ReplayVisuals visuals = visuals();
        if (visuals != null) {
            ensureColors(visuals);
            if (!captured) {
                capture(visuals);
            }
        }
    }


    public static void close() {
        open = false;
        OPEN.set(false);
    }


    public static void renderMenuItem() {
        if (ImGui.menuItem("Theme##ThemeOpen", open)) {
            open();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setItemTooltip("Set the look of the replay: time of day, sky, fog and vision");
        }
    }


    @Nullable
    private static ReplayVisuals visuals() {
        EditorState state = EditorStateManager.getCurrent();
        return state == null ? null : state.replayVisuals;
    }

    private static void capture(ReplayVisuals visuals) {
        copyInto(visuals.skyColour, originalSky);
        copyInto(visuals.fogColour, originalFog);
        originalFogOn = visuals.overrideFog;
        originalFogColourOn = visuals.overrideFogColour;
        originalFogStart = visuals.overrideFogStart;
        originalFogEnd = visuals.overrideFogEnd;
        originalTime = visuals.overrideTimeOfDay;
        originalNightVision = visuals.overrideNightVision;
        captured = true;
    }

    private static void restore(ReplayVisuals visuals) {
        if (!captured) {
            return;
        }
        copyInto(originalSky, visuals.skyColour);
        copyInto(originalFog, visuals.fogColour);
        visuals.overrideFog = originalFogOn;
        visuals.overrideFogColour = originalFogColourOn;
        visuals.overrideFogStart = originalFogStart;
        visuals.overrideFogEnd = originalFogEnd;
        visuals.overrideTimeOfDay = originalTime;
        visuals.overrideNightVision = originalNightVision;
    }


    private static void copyInto(@Nullable float[] source, float[] destination) {
        if (source == null || source.length < 3 || destination.length < 3) {
            return;
        }
        System.arraycopy(source, 0, destination, 0, 3);
    }


    private static void ensureColors(ReplayVisuals visuals) {
        if (visuals.skyColour == null || visuals.skyColour.length < 3) {
            visuals.skyColour = new float[] {0.50f, 0.70f, 1.00f};
        }
        if (visuals.fogColour == null || visuals.fogColour.length < 3) {
            visuals.fogColour = new float[] {0.50f, 0.70f, 1.00f};
        }
    }


    public static void render() {
        if (!open) {
            return;
        }
        EditorState state = EditorStateManager.getCurrent();
        ReplayVisuals visuals = state == null ? null : state.replayVisuals;
        if (state == null || visuals == null) {


            open = false;
            OPEN.set(false);
            return;
        }
        ensureColors(visuals);
        if (!captured) {
            capture(visuals);
        }

        ImGui.setNextWindowSize(PANEL_WIDTH, PANEL_HEIGHT);
        OPEN.set(open);
        if (!ImGui.begin("Theme", OPEN)) {
            ImGui.end();
            return;
        }
        open = OPEN.get();

        renderTheme(state, visuals);
        ImGui.separator();
        renderControls(state, visuals);
        ImGui.separator();
        renderFooter(state, visuals);

        ImGui.end();
        open = OPEN.get();
    }


    private static void renderTheme(EditorState state, ReplayVisuals visuals) {
        ImGui.text("Theme");
        ImGui.setNextItemWidth(PANEL_WIDTH - 40.0f);
        if (ImGui.beginCombo("##ThemePick", theme.label())) {
            for (Theme candidate : Theme.values()) {
                if (ImGui.menuItem(candidate.label(), candidate == theme)) {
                    theme = candidate;
                    if (candidate.isReset()) {
                        restore(visuals);
                    } else {
                        candidate.apply(visuals);
                    }
                    state.markDirty();
                }
            }
            ImGui.endCombo();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setItemTooltip("A whole look at once: sun, sky, fog and vision set together");
        }
        ImGui.textDisabled("Themes set every control below.");
    }


    private static void renderControls(EditorState state, ReplayVisuals visuals) {
        if (ImGui.checkbox("Time of day##ThemeTime", visuals.overrideTimeOfDay >= 0)) {
            visuals.overrideTimeOfDay = visuals.overrideTimeOfDay >= 0 ? -1L : 6000L;
            state.markDirty();
        }
        if (visuals.overrideTimeOfDay >= 0) {
            timeBuffer[0] = (int) visuals.overrideTimeOfDay;
            ImGui.setNextItemWidth(PANEL_WIDTH - 40.0f);
            if (ImGui.sliderInt("##ThemeTimeSlider", timeBuffer, 0, 24000)) {
                visuals.overrideTimeOfDay = timeBuffer[0];
                state.markDirty();
            }
            if (ImGui.isItemHovered()) {
                ImGui.setItemTooltip("0 sunrise, 6000 noon, 12000 sunset, 18000 midnight");
            }
        }

        ImGui.spacing();
        ImGui.text("Sky colour");
        float[] sky = visuals.skyColour;
        ImGui.setNextItemWidth(PANEL_WIDTH - 40.0f);
        if (ImGui.colorPicker3("##ThemeSky", sky)) {
            state.markDirty();
        }

        ImGui.spacing();
        if (ImGui.checkbox("Fog##ThemeFog", visuals.overrideFog)) {
            visuals.overrideFog = !visuals.overrideFog;
            state.markDirty();
        }
        if (visuals.overrideFog) {
            if (ImGui.checkbox("Fog colour##ThemeFogColour", visuals.overrideFogColour)) {
                visuals.overrideFogColour = !visuals.overrideFogColour;
                state.markDirty();
            }
            if (visuals.overrideFogColour) {
                float[] fog = visuals.fogColour;
                ImGui.setNextItemWidth(PANEL_WIDTH - 40.0f);
                if (ImGui.colorPicker3("##ThemeFogColour", fog)) {
                    state.markDirty();
                }
            }
            fogStartBuffer[0] = visuals.overrideFogStart;
            ImGui.setNextItemWidth(PANEL_WIDTH - 40.0f);
            if (ImGui.sliderFloat("Start##ThemeFogStart", fogStartBuffer, 0.0f, 512.0f, "%.0f blocks")) {
                visuals.overrideFogStart = fogStartBuffer[0];
                state.markDirty();
            }
            fogEndBuffer[0] = visuals.overrideFogEnd;
            ImGui.setNextItemWidth(PANEL_WIDTH - 40.0f);
            if (ImGui.sliderFloat("End##ThemeFogEnd", fogEndBuffer, 0.0f, 512.0f, "%.0f blocks")) {
                visuals.overrideFogEnd = fogEndBuffer[0];
                state.markDirty();
            }
        }

        ImGui.spacing();
        if (ImGui.checkbox("Night vision##ThemeNightVision", visuals.overrideNightVision)) {
            visuals.overrideNightVision = !visuals.overrideNightVision;
            state.markDirty();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setItemTooltip("Lifts the whole frame, so a night theme still reads");
        }
    }

    private static void renderFooter(EditorState state, ReplayVisuals visuals) {
        if (ImGui.button("Reset to how it was", PANEL_WIDTH - 40.0f, 0.0f)) {
            theme = Theme.NONE;
            restore(visuals);
            state.markDirty();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setItemTooltip("Put back the look this replay had before this window was opened");
        }
        ImGui.textDisabled("Theme values are saved with the replay.");
    }
}

