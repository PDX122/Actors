package pdx.dev.fbactors.settings;

import imgui.moulberry90.ImGui;
import imgui.moulberry90.ImGuiStyle;
import imgui.moulberry90.ImVec4;
import imgui.moulberry90.flag.ImGuiCol;
import org.jetbrains.annotations.Nullable;


public enum UiTheme {


    FLASHBACK("flashback", "Flashback default"),


    MIDNIGHT("midnight", "Midnight"),


    EMBER("ember", "Ember"),


    FOREST("forest", "Forest"),


    DISCORD("discord", "Discord"),


    MODRINTH("modrinth", "Modrinth"),


    CURSEFORGE("curseforge", "CurseForge"),


    DRACULA("dracula", "Dracula"),


    NORD("nord", "Nord"),


    CRIMSON("crimson", "Crimson"),


    OCEAN("ocean", "Ocean"),


    GRAPE("grape", "Grape"),


    COFFEE("coffee", "Coffee"),


    MINT("mint", "Mint"),


    ROSE("rose", "Rose"),


    STEEL("steel", "Steel"),


    CUSTOM("custom", "Custom");

    private final String id;
    private final String label;

    UiTheme(String id, String label) {
        this.id = id;
        this.label = label;
    }


    public String id() {
        return this.id;
    }


    public String label() {
        return this.label;
    }


    public String blurb() {
        switch (this) {
            case FLASHBACK:
                return "Flashback stock look. Safe default.";
            case MIDNIGHT:
                return "Deep slate with steel blue accents.";
            case EMBER:
                return "Warm charcoal with ember orange accents.";
            case FOREST:
                return "Moss charcoal with leaf green accents.";
            case DISCORD:
                return "Discord dark with blurple accents.";
            case MODRINTH:
                return "Modrinth dark with green accents.";
            case CURSEFORGE:
                return "CurseForge dark with orange accents.";
            case DRACULA:
                return "Dracula plum with purple accents.";
            case NORD:
                return "Nord polar night with frost accents.";
            case CRIMSON:
                return "Dark red with crimson accents.";
            case OCEAN:
                return "Deep sea with cyan accents.";
            case GRAPE:
                return "Dark purple with grape accents.";
            case COFFEE:
                return "Warm brown with coffee accents.";
            case MINT:
                return "Dark teal with mint accents.";
            case ROSE:
                return "Dark plum with rose accents.";
            case STEEL:
                return "Neutral steel with silver accents.";
            case CUSTOM:
                return "Your build from the Theme Maker code box.";
            default:
                return "";
        }
    }


    public static UiTheme byId(@Nullable String id) {
        if (id != null) {
            for (UiTheme theme : values()) {
                if (theme.id.equals(id)) {
                    return theme;
                }
            }
        }
        return FLASHBACK;
    }


    @Nullable
    private static float[][] snapshotColors;


    private static final float[] snapshotVars = new float[9];

    private static boolean snapshotTaken;


    @Nullable
    private static String lastApplied;


    public static void applyCurrent() {
        UiTheme theme = byId(FbSettings.settings().uiTheme);
        if (theme.id.equals(lastApplied)) {
            return;
        }
        try {
            theme.apply(ImGui.getStyle());
            lastApplied = theme.id;
        } catch (Exception ignored) {


        }
    }


    public static void applyNow(UiTheme theme) {
        try {
            theme.apply(ImGui.getStyle());
            lastApplied = theme.id;
        } catch (Exception ignored) {
        }
    }

    private void apply(ImGuiStyle style) {
        if (this == FLASHBACK) {
            restore(style);
            return;
        }
        snapshot(style);
        ImGui.styleColorsDark();
        paint(style);
    }


    private static void snapshot(ImGuiStyle style) {
        if (snapshotTaken) {
            return;
        }
        ImVec4[] colors = style.getColors();
        snapshotColors = new float[colors.length][4];
        for (int i = 0; i < colors.length; i++) {
            ImVec4 c = colors[i];
            snapshotColors[i][0] = c == null ? 0.0f : c.x;
            snapshotColors[i][1] = c == null ? 0.0f : c.y;
            snapshotColors[i][2] = c == null ? 0.0f : c.z;
            snapshotColors[i][3] = c == null ? 1.0f : c.w;
        }
        snapshotVars[0] = style.getWindowRounding();
        snapshotVars[1] = style.getFrameRounding();
        snapshotVars[2] = style.getGrabRounding();
        snapshotVars[3] = style.getTabRounding();
        snapshotVars[4] = style.getScrollbarRounding();
        snapshotVars[5] = style.getChildRounding();
        snapshotVars[6] = style.getPopupRounding();
        snapshotVars[7] = style.getWindowBorderSize();
        snapshotVars[8] = style.getFrameBorderSize();
        snapshotTaken = true;
    }


    private static void restore(ImGuiStyle style) {
        if (!snapshotTaken || snapshotColors == null) {
            return;
        }
        int count = Math.min(snapshotColors.length, ImGuiCol.COUNT);
        for (int i = 0; i < count; i++) {
            float[] c = snapshotColors[i];
            style.setColor(i, c[0], c[1], c[2], c[3]);
        }
        style.setWindowRounding(snapshotVars[0]);
        style.setFrameRounding(snapshotVars[1]);
        style.setGrabRounding(snapshotVars[2]);
        style.setTabRounding(snapshotVars[3]);
        style.setScrollbarRounding(snapshotVars[4]);
        style.setChildRounding(snapshotVars[5]);
        style.setPopupRounding(snapshotVars[6]);
        style.setWindowBorderSize(snapshotVars[7]);
        style.setFrameBorderSize(snapshotVars[8]);
    }

    private void paint(ImGuiStyle style) {
        switch (this) {
            case MIDNIGHT -> paintMidnight(style);
            case EMBER -> paintEmber(style);
            case FOREST -> paintForest(style);
            case DISCORD -> paintGeneric(style, 0.12f, 0.13f, 0.17f, 0.35f, 0.40f, 0.95f, 6.0f);
            case MODRINTH -> paintGeneric(style, 0.08f, 0.10f, 0.09f, 0.11f, 0.85f, 0.42f, 6.0f);
            case CURSEFORGE -> paintGeneric(style, 0.09f, 0.09f, 0.10f, 0.95f, 0.39f, 0.21f, 5.0f);
            case DRACULA -> paintGeneric(style, 0.16f, 0.17f, 0.22f, 0.74f, 0.58f, 0.89f, 6.0f);
            case NORD -> paintGeneric(style, 0.18f, 0.20f, 0.24f, 0.53f, 0.64f, 0.82f, 6.0f);
            case CRIMSON -> paintGeneric(style, 0.10f, 0.07f, 0.08f, 0.85f, 0.15f, 0.25f, 4.0f);
            case OCEAN -> paintGeneric(style, 0.05f, 0.11f, 0.16f, 0.15f, 0.70f, 0.90f, 6.0f);
            case GRAPE -> paintGeneric(style, 0.11f, 0.08f, 0.15f, 0.65f, 0.40f, 0.95f, 8.0f);
            case COFFEE -> paintGeneric(style, 0.11f, 0.09f, 0.08f, 0.80f, 0.55f, 0.30f, 4.0f);
            case MINT -> paintGeneric(style, 0.07f, 0.11f, 0.10f, 0.25f, 0.85f, 0.65f, 6.0f);
            case ROSE -> paintGeneric(style, 0.13f, 0.09f, 0.11f, 0.95f, 0.45f, 0.60f, 8.0f);
            case STEEL -> paintGeneric(style, 0.10f, 0.11f, 0.13f, 0.55f, 0.65f, 0.75f, 5.0f);
            case CUSTOM -> paintCustom(style);
            default -> {
            }
        }
    }

    private static void set(ImGuiStyle style, int slot, float r, float g, float b, float a) {
        style.setColor(slot, r, g, b, a);
    }

    private static void round(ImGuiStyle style, float window, float frame, float grab,
                              float tab, float scrollbar, float child, float popup,
                              float windowBorder, float frameBorder) {
        style.setWindowRounding(window);
        style.setFrameRounding(frame);
        style.setGrabRounding(grab);
        style.setTabRounding(tab);
        style.setScrollbarRounding(scrollbar);
        style.setChildRounding(child);
        style.setPopupRounding(popup);
        style.setWindowBorderSize(windowBorder);
        style.setFrameBorderSize(frameBorder);
    }


    private static void paintMidnight(ImGuiStyle style) {
        set(style, ImGuiCol.WindowBg, 0.07f, 0.09f, 0.13f, 1.00f);
        set(style, ImGuiCol.ChildBg, 0.07f, 0.09f, 0.13f, 1.00f);
        set(style, ImGuiCol.PopupBg, 0.08f, 0.10f, 0.15f, 1.00f);
        set(style, ImGuiCol.TitleBg, 0.10f, 0.14f, 0.20f, 1.00f);
        set(style, ImGuiCol.TitleBgActive, 0.16f, 0.30f, 0.55f, 1.00f);
        set(style, ImGuiCol.MenuBarBg, 0.09f, 0.11f, 0.16f, 1.00f);
        set(style, ImGuiCol.FrameBg, 0.13f, 0.17f, 0.24f, 1.00f);
        set(style, ImGuiCol.FrameBgHovered, 0.18f, 0.26f, 0.38f, 1.00f);
        set(style, ImGuiCol.FrameBgActive, 0.22f, 0.34f, 0.50f, 1.00f);
        set(style, ImGuiCol.Button, 0.20f, 0.36f, 0.62f, 1.00f);
        set(style, ImGuiCol.ButtonHovered, 0.28f, 0.48f, 0.78f, 1.00f);
        set(style, ImGuiCol.ButtonActive, 0.34f, 0.56f, 0.88f, 1.00f);
        set(style, ImGuiCol.Header, 0.22f, 0.38f, 0.62f, 1.00f);
        set(style, ImGuiCol.HeaderHovered, 0.28f, 0.48f, 0.78f, 1.00f);
        set(style, ImGuiCol.HeaderActive, 0.34f, 0.56f, 0.88f, 1.00f);
        set(style, ImGuiCol.CheckMark, 0.55f, 0.75f, 1.00f, 1.00f);
        set(style, ImGuiCol.SliderGrab, 0.35f, 0.58f, 0.90f, 1.00f);
        set(style, ImGuiCol.SliderGrabActive, 0.45f, 0.68f, 1.00f, 1.00f);
        set(style, ImGuiCol.Separator, 0.25f, 0.35f, 0.50f, 1.00f);
        set(style, ImGuiCol.Tab, 0.16f, 0.28f, 0.48f, 1.00f);
        set(style, ImGuiCol.TabHovered, 0.28f, 0.48f, 0.78f, 1.00f);
        set(style, ImGuiCol.TabSelected, 0.22f, 0.38f, 0.62f, 1.00f);
        set(style, ImGuiCol.ScrollbarGrab, 0.30f, 0.42f, 0.58f, 1.00f);
        set(style, ImGuiCol.ScrollbarGrabHovered, 0.38f, 0.52f, 0.70f, 1.00f);
        set(style, ImGuiCol.ScrollbarGrabActive, 0.45f, 0.60f, 0.80f, 1.00f);
        set(style, ImGuiCol.TextSelectedBg, 0.28f, 0.48f, 0.78f, 0.35f);
        round(style, 6, 5, 4, 5, 6, 6, 6, 1, 1);
    }


    private static void paintEmber(ImGuiStyle style) {
        set(style, ImGuiCol.WindowBg, 0.10f, 0.08f, 0.07f, 1.00f);
        set(style, ImGuiCol.ChildBg, 0.10f, 0.08f, 0.07f, 1.00f);
        set(style, ImGuiCol.PopupBg, 0.12f, 0.09f, 0.08f, 1.00f);
        set(style, ImGuiCol.TitleBg, 0.16f, 0.11f, 0.08f, 1.00f);
        set(style, ImGuiCol.TitleBgActive, 0.55f, 0.25f, 0.08f, 1.00f);
        set(style, ImGuiCol.MenuBarBg, 0.11f, 0.09f, 0.08f, 1.00f);
        set(style, ImGuiCol.FrameBg, 0.18f, 0.13f, 0.10f, 1.00f);
        set(style, ImGuiCol.FrameBgHovered, 0.26f, 0.17f, 0.12f, 1.00f);
        set(style, ImGuiCol.FrameBgActive, 0.34f, 0.22f, 0.14f, 1.00f);
        set(style, ImGuiCol.Button, 0.75f, 0.32f, 0.10f, 1.00f);
        set(style, ImGuiCol.ButtonHovered, 0.88f, 0.42f, 0.14f, 1.00f);
        set(style, ImGuiCol.ButtonActive, 0.98f, 0.52f, 0.18f, 1.00f);
        set(style, ImGuiCol.Header, 0.55f, 0.28f, 0.12f, 1.00f);
        set(style, ImGuiCol.HeaderHovered, 0.70f, 0.36f, 0.14f, 1.00f);
        set(style, ImGuiCol.HeaderActive, 0.85f, 0.45f, 0.18f, 1.00f);
        set(style, ImGuiCol.CheckMark, 1.00f, 0.62f, 0.25f, 1.00f);
        set(style, ImGuiCol.SliderGrab, 0.90f, 0.45f, 0.15f, 1.00f);
        set(style, ImGuiCol.SliderGrabActive, 1.00f, 0.55f, 0.20f, 1.00f);
        set(style, ImGuiCol.Separator, 0.45f, 0.28f, 0.16f, 1.00f);
        set(style, ImGuiCol.Tab, 0.45f, 0.24f, 0.12f, 1.00f);
        set(style, ImGuiCol.TabHovered, 0.70f, 0.36f, 0.14f, 1.00f);
        set(style, ImGuiCol.TabSelected, 0.60f, 0.30f, 0.12f, 1.00f);
        set(style, ImGuiCol.ScrollbarGrab, 0.40f, 0.28f, 0.20f, 1.00f);
        set(style, ImGuiCol.ScrollbarGrabHovered, 0.52f, 0.36f, 0.24f, 1.00f);
        set(style, ImGuiCol.ScrollbarGrabActive, 0.62f, 0.44f, 0.28f, 1.00f);
        set(style, ImGuiCol.TextSelectedBg, 0.85f, 0.42f, 0.14f, 0.35f);
        round(style, 4, 4, 3, 4, 5, 4, 4, 1, 1);
    }


    private static void paintForest(ImGuiStyle style) {
        set(style, ImGuiCol.WindowBg, 0.06f, 0.10f, 0.08f, 1.00f);
        set(style, ImGuiCol.ChildBg, 0.06f, 0.10f, 0.08f, 1.00f);
        set(style, ImGuiCol.PopupBg, 0.07f, 0.12f, 0.09f, 1.00f);
        set(style, ImGuiCol.TitleBg, 0.08f, 0.15f, 0.11f, 1.00f);
        set(style, ImGuiCol.TitleBgActive, 0.15f, 0.40f, 0.22f, 1.00f);
        set(style, ImGuiCol.MenuBarBg, 0.07f, 0.11f, 0.09f, 1.00f);
        set(style, ImGuiCol.FrameBg, 0.11f, 0.17f, 0.13f, 1.00f);
        set(style, ImGuiCol.FrameBgHovered, 0.15f, 0.25f, 0.18f, 1.00f);
        set(style, ImGuiCol.FrameBgActive, 0.20f, 0.32f, 0.22f, 1.00f);
        set(style, ImGuiCol.Button, 0.20f, 0.52f, 0.28f, 1.00f);
        set(style, ImGuiCol.ButtonHovered, 0.26f, 0.64f, 0.34f, 1.00f);
        set(style, ImGuiCol.ButtonActive, 0.32f, 0.74f, 0.40f, 1.00f);
        set(style, ImGuiCol.Header, 0.20f, 0.48f, 0.26f, 1.00f);
        set(style, ImGuiCol.HeaderHovered, 0.26f, 0.60f, 0.32f, 1.00f);
        set(style, ImGuiCol.HeaderActive, 0.32f, 0.70f, 0.38f, 1.00f);
        set(style, ImGuiCol.CheckMark, 0.45f, 0.90f, 0.55f, 1.00f);
        set(style, ImGuiCol.SliderGrab, 0.30f, 0.68f, 0.38f, 1.00f);
        set(style, ImGuiCol.SliderGrabActive, 0.38f, 0.80f, 0.45f, 1.00f);
        set(style, ImGuiCol.Separator, 0.25f, 0.40f, 0.28f, 1.00f);
        set(style, ImGuiCol.Tab, 0.16f, 0.36f, 0.22f, 1.00f);
        set(style, ImGuiCol.TabHovered, 0.26f, 0.55f, 0.32f, 1.00f);
        set(style, ImGuiCol.TabSelected, 0.22f, 0.46f, 0.28f, 1.00f);
        set(style, ImGuiCol.ScrollbarGrab, 0.28f, 0.42f, 0.32f, 1.00f);
        set(style, ImGuiCol.ScrollbarGrabHovered, 0.36f, 0.52f, 0.40f, 1.00f);
        set(style, ImGuiCol.ScrollbarGrabActive, 0.44f, 0.62f, 0.48f, 1.00f);
        set(style, ImGuiCol.TextSelectedBg, 0.26f, 0.60f, 0.34f, 0.35f);
        round(style, 8, 6, 5, 6, 7, 8, 8, 1, 1);
    }


    private static void paintGeneric(ImGuiStyle style,
                                     float bgR, float bgG, float bgB,
                                     float acR, float acG, float acB,
                                     float rounding) {
        float[] base = {bgR, bgG, bgB};
        float[] accent = {acR, acG, acB};
        float[] hover = lighten(accent, 0.12f);
        float[] active = darken(accent, 0.14f);
        float[] soft = mix(accent, base, 0.72f);
        float[] frame = lighten(base, 0.08f);
        float[] frameHover = lighten(base, 0.14f);
        float[] grab = lighten(base, 0.30f);
        float[] titleActive = mix(accent, base, 0.55f);
        float[] tabSelected = mix(accent, base, 0.60f);
        float[] headerHover = mix(accent, base, 0.35f);
        set(style, ImGuiCol.WindowBg, base[0], base[1], base[2], 1.00f);
        set(style, ImGuiCol.ChildBg, base[0], base[1], base[2], 1.00f);
        set(style, ImGuiCol.PopupBg, lighten(base, 0.04f)[0], lighten(base, 0.04f)[1], lighten(base, 0.04f)[2], 1.00f);
        set(style, ImGuiCol.TitleBg, frame[0], frame[1], frame[2], 1.00f);
        set(style, ImGuiCol.TitleBgActive, titleActive[0], titleActive[1], titleActive[2], 1.00f);
        set(style, ImGuiCol.MenuBarBg, frame[0], frame[1], frame[2], 1.00f);
        set(style, ImGuiCol.FrameBg, frame[0], frame[1], frame[2], 1.00f);
        set(style, ImGuiCol.FrameBgHovered, frameHover[0], frameHover[1], frameHover[2], 1.00f);
        set(style, ImGuiCol.FrameBgActive, soft[0], soft[1], soft[2], 1.00f);
        set(style, ImGuiCol.Button, accent[0], accent[1], accent[2], 1.00f);
        set(style, ImGuiCol.ButtonHovered, hover[0], hover[1], hover[2], 1.00f);
        set(style, ImGuiCol.ButtonActive, active[0], active[1], active[2], 1.00f);
        set(style, ImGuiCol.Header, soft[0], soft[1], soft[2], 1.00f);
        set(style, ImGuiCol.HeaderHovered, headerHover[0], headerHover[1], headerHover[2], 1.00f);
        set(style, ImGuiCol.HeaderActive, active[0], active[1], active[2], 1.00f);
        set(style, ImGuiCol.CheckMark, lighten(accent, 0.20f)[0], lighten(accent, 0.20f)[1], lighten(accent, 0.20f)[2], 1.00f);
        set(style, ImGuiCol.SliderGrab, accent[0], accent[1], accent[2], 1.00f);
        set(style, ImGuiCol.SliderGrabActive, hover[0], hover[1], hover[2], 1.00f);
        set(style, ImGuiCol.Separator, soft[0], soft[1], soft[2], 1.00f);
        set(style, ImGuiCol.Tab, frame[0], frame[1], frame[2], 1.00f);
        set(style, ImGuiCol.TabHovered, headerHover[0], headerHover[1], headerHover[2], 1.00f);
        set(style, ImGuiCol.TabSelected, tabSelected[0], tabSelected[1], tabSelected[2], 1.00f);
        set(style, ImGuiCol.TabSelectedOverline, accent[0], accent[1], accent[2], 1.00f);
        set(style, ImGuiCol.TabDimmedSelectedOverline, accent[0], accent[1], accent[2], 1.00f);
        set(style, ImGuiCol.ScrollbarGrab, grab[0], grab[1], grab[2], 1.00f);
        set(style, ImGuiCol.ScrollbarGrabHovered, lighten(grab, 0.08f)[0], lighten(grab, 0.08f)[1], lighten(grab, 0.08f)[2], 1.00f);
        set(style, ImGuiCol.ScrollbarGrabActive, darken(grab, 0.08f)[0], darken(grab, 0.08f)[1], darken(grab, 0.08f)[2], 1.00f);
        set(style, ImGuiCol.TextSelectedBg, accent[0], accent[1], accent[2], 0.35f);
        float frameRound = Math.max(0.0f, rounding * 0.8f);
        round(style, rounding, frameRound, Math.max(0.0f, rounding * 0.6f),
            frameRound, rounding, rounding, rounding, 1, 1);
    }


    private static void paintCustom(ImGuiStyle style) {
        FbSettings settings = FbSettings.settings();
        float[] accent = safeRgb(settings.customAccent, 0.30f, 0.55f, 0.90f);
        float[] base = safeRgb(settings.customBackground, 0.09f, 0.11f, 0.16f);
        float rounding = Math.max(0.0f, Math.min(12.0f, settings.customRounding));
        boolean light = settings.customLightBase;
        if (light) {
            ImGui.styleColorsLight();
        } else {
            ImGui.styleColorsDark();
        }
        float[] hover = lighten(accent, 0.12f);
        float[] active = darken(accent, 0.14f);
        float[] soft = mix(accent, base, 0.72f);
        float[] frame = light ? darken(base, 0.07f) : lighten(base, 0.07f);
        float[] frameHover = light ? darken(base, 0.12f) : lighten(base, 0.12f);
        float[] titleActive = mix(accent, base, light ? 0.45f : 0.55f);
        float[] tabSelected = mix(accent, base, light ? 0.50f : 0.60f);
        float[] headerHover = mix(accent, base, 0.35f);
        float[] grab = light
            ? new float[] {0.62f, 0.65f, 0.71f}
            : lighten(base, 0.28f);
        set(style, ImGuiCol.WindowBg, base[0], base[1], base[2], 1.00f);
        set(style, ImGuiCol.ChildBg, base[0], base[1], base[2], 1.00f);
        set(style, ImGuiCol.PopupBg, lighten(base, 0.03f)[0], lighten(base, 0.03f)[1], lighten(base, 0.03f)[2], 1.00f);
        set(style, ImGuiCol.TitleBg, frame[0], frame[1], frame[2], 1.00f);
        set(style, ImGuiCol.TitleBgActive, titleActive[0], titleActive[1], titleActive[2], 1.00f);
        set(style, ImGuiCol.MenuBarBg, frame[0], frame[1], frame[2], 1.00f);
        set(style, ImGuiCol.FrameBg, frame[0], frame[1], frame[2], 1.00f);
        set(style, ImGuiCol.FrameBgHovered, frameHover[0], frameHover[1], frameHover[2], 1.00f);
        set(style, ImGuiCol.FrameBgActive, soft[0], soft[1], soft[2], 1.00f);
        set(style, ImGuiCol.Button, accent[0], accent[1], accent[2], 1.00f);
        set(style, ImGuiCol.ButtonHovered, hover[0], hover[1], hover[2], 1.00f);
        set(style, ImGuiCol.ButtonActive, active[0], active[1], active[2], 1.00f);
        set(style, ImGuiCol.Header, soft[0], soft[1], soft[2], 1.00f);
        set(style, ImGuiCol.HeaderHovered, headerHover[0], headerHover[1], headerHover[2], 1.00f);
        set(style, ImGuiCol.HeaderActive, active[0], active[1], active[2], 1.00f);
        set(style, ImGuiCol.CheckMark, light ? darken(accent, 0.15f)[0] : lighten(accent, 0.15f)[0],
            light ? darken(accent, 0.15f)[1] : lighten(accent, 0.15f)[1],
            light ? darken(accent, 0.15f)[2] : lighten(accent, 0.15f)[2], 1.00f);
        set(style, ImGuiCol.SliderGrab, accent[0], accent[1], accent[2], 1.00f);
        set(style, ImGuiCol.SliderGrabActive, hover[0], hover[1], hover[2], 1.00f);
        set(style, ImGuiCol.Separator, soft[0], soft[1], soft[2], 1.00f);
        set(style, ImGuiCol.Tab, frame[0], frame[1], frame[2], 1.00f);
        set(style, ImGuiCol.TabHovered, headerHover[0], headerHover[1], headerHover[2], 1.00f);
        set(style, ImGuiCol.TabSelected, tabSelected[0], tabSelected[1], tabSelected[2], 1.00f);
        set(style, ImGuiCol.TabSelectedOverline, accent[0], accent[1], accent[2], 1.00f);
        set(style, ImGuiCol.TabDimmedSelectedOverline, accent[0], accent[1], accent[2], 1.00f);
        set(style, ImGuiCol.ScrollbarGrab, grab[0], grab[1], grab[2], 1.00f);
        set(style, ImGuiCol.ScrollbarGrabHovered, lighten(grab, 0.08f)[0], lighten(grab, 0.08f)[1], lighten(grab, 0.08f)[2], 1.00f);
        set(style, ImGuiCol.ScrollbarGrabActive, darken(grab, 0.08f)[0], darken(grab, 0.08f)[1], darken(grab, 0.08f)[2], 1.00f);
        set(style, ImGuiCol.TextSelectedBg, accent[0], accent[1], accent[2], 0.35f);
        float frameRound = Math.max(0.0f, rounding * 0.8f);
        round(style, rounding, frameRound, Math.max(0.0f, rounding * 0.6f),
            frameRound, rounding, rounding, rounding, 1, 1);
    }

    private static float[] safeRgb(float[] in, float r, float g, float b) {
        if (in == null || in.length < 3) {
            return new float[] {r, g, b};
        }
        return new float[] {clamp01(in[0]), clamp01(in[1]), clamp01(in[2])};
    }

    private static float clamp01(float v) {
        if (v < 0.0f) {
            return 0.0f;
        }
        return Math.min(1.0f, v);
    }

    private static float[] lighten(float[] c, float amount) {
        return new float[] {
            clamp01(c[0] + (1.0f - c[0]) * amount),
            clamp01(c[1] + (1.0f - c[1]) * amount),
            clamp01(c[2] + (1.0f - c[2]) * amount)};
    }

    private static float[] darken(float[] c, float amount) {
        float keep = Math.max(0.0f, 1.0f - amount);
        return new float[] {clamp01(c[0] * keep), clamp01(c[1] * keep), clamp01(c[2] * keep)};
    }

    private static float[] mix(float[] a, float[] b, float bias) {
        float t = Math.max(0.0f, Math.min(1.0f, bias));
        return new float[] {
            clamp01(a[0] * (1.0f - t) + b[0] * t),
            clamp01(a[1] * (1.0f - t) + b[1] * t),
            clamp01(a[2] * (1.0f - t) + b[2] * t)};
    }


    public static String customConfigText() {
        FbSettings settings = FbSettings.settings();
        float[] accent = safeRgb(settings.customAccent, 0.30f, 0.55f, 0.90f);
        float[] base = safeRgb(settings.customBackground, 0.09f, 0.11f, 0.16f);
        float rounding = Math.max(0.0f, Math.min(12.0f, settings.customRounding));
        return "accent=" + toHex(accent)
            + ";bg=" + toHex(base)
            + ";rounding=" + String.format(java.util.Locale.ROOT, "%.1f", rounding)
            + ";light=" + (settings.customLightBase ? "1" : "0");
    }


    @Nullable
    public static String applyConfigText(String text) {
        if (text == null) {
            return "Empty config. Paste something like accent=4D8DE6;bg=171A26;rounding=6.0;light=0";
        }
        String clean = text.trim();
        if (clean.isEmpty()) {
            return "Empty config. Paste something like accent=4D8DE6;bg=171A26;rounding=6.0;light=0";
        }
        float[] accent = null;
        float[] base = null;
        Float rounding = null;
        Boolean light = null;
        String[] parts = clean.split("[;\\n,]+");
        for (String part : parts) {
            String p = part.trim();
            if (p.isEmpty()) {
                continue;
            }
            int eq = p.indexOf('=');
            if (eq < 0) {
                eq = p.indexOf(':');
            }
            if (eq < 0) {
                return "Bad entry '" + p + "'. Use key=value pairs.";
            }
            String key = p.substring(0, eq).trim().toLowerCase(java.util.Locale.ROOT);
            String value = p.substring(eq + 1).trim();
            switch (key) {
                case "accent" -> {
                    float[] c = parseHex(value);
                    if (c == null) {
                        return "Bad accent '" + value + "'. Use hex like 4D8DE6.";
                    }
                    accent = c;
                }
                case "bg", "background" -> {
                    float[] c = parseHex(value);
                    if (c == null) {
                        return "Bad bg '" + value + "'. Use hex like 171A26.";
                    }
                    base = c;
                }
                case "rounding", "round", "corners" -> {
                    try {
                        rounding = Math.max(0.0f, Math.min(12.0f, Float.parseFloat(value)));
                    } catch (NumberFormatException e) {
                        return "Bad rounding '" + value + "'. Use 0 to 12.";
                    }
                }
                case "light", "lightbase", "light_base" -> {
                    String v = value.toLowerCase(java.util.Locale.ROOT);
                    if (v.equals("1") || v.equals("true") || v.equals("on") || v.equals("yes")) {
                        light = true;
                    } else if (v.equals("0") || v.equals("false") || v.equals("off") || v.equals("no")) {
                        light = false;
                    } else {
                        return "Bad light '" + value + "'. Use 0 or 1.";
                    }
                }
                default -> {
                    return "Unknown key '" + key + "'. Keys are accent, bg, rounding, light.";
                }
            }
        }
        if (accent == null && base == null && rounding == null && light == null) {
            return "Nothing to apply. Include at least one of accent, bg, rounding, light.";
        }
        FbSettings settings = FbSettings.settings();
        if (accent != null) {
            settings.customAccent = accent;
        }
        if (base != null) {
            settings.customBackground = base;
        }
        if (rounding != null) {
            settings.customRounding = rounding;
        }
        if (light != null) {
            settings.customLightBase = light;
        }
        settings.uiTheme = CUSTOM.id();
        applyNow(CUSTOM);
        settings.save();
        return null;
    }

    @Nullable
    private static float[] parseHex(String value) {
        String v = value.trim();
        if (v.startsWith("#")) {
            v = v.substring(1);
        }
        if (v.length() != 6) {
            return null;
        }
        try {
            int rgb = Integer.parseInt(v, 16);
            return new float[] {
                ((rgb >> 16) & 0xFF) / 255.0f,
                ((rgb >> 8) & 0xFF) / 255.0f,
                (rgb & 0xFF) / 255.0f};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String toHex(float[] c) {
        int r = Math.round(clamp01(c[0]) * 255.0f);
        int g = Math.round(clamp01(c[1]) * 255.0f);
        int b = Math.round(clamp01(c[2]) * 255.0f);
        return String.format(java.util.Locale.ROOT, "%02X%02X%02X", r, g, b);
    }
}

