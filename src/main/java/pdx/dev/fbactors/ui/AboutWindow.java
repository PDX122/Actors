package pdx.dev.fbactors.ui;

import imgui.moulberry90.ImGui;
import imgui.moulberry90.flag.ImGuiCond;
import imgui.moulberry90.flag.ImGuiWindowFlags;
import imgui.moulberry90.type.ImBoolean;
import net.fabricmc.loader.api.FabricLoader;
import pdx.dev.fbactors.compat.Compat;

public final class AboutWindow {
    private static final ImBoolean OPEN = new ImBoolean(false);
    private static final String VIDEO_URL = "https://www.youtube.com/@TheBlueTomato2023";
    private static final String VERSION = FabricLoader.getInstance().getModContainer("fbactors")
        .map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("");

    private AboutWindow() {}

    public static void open() { OPEN.set(true); }

    public static void render() {
        if (!OPEN.get()) return;
        var viewport = ImGui.getMainViewport();
        ImGui.setNextWindowSize(Math.max(1, Math.min(510, viewport.getWorkSizeX() - 24)),
            Math.max(1, Math.min(540, viewport.getWorkSizeY() - 24)), ImGuiCond.Always);
        SkinPickerChrome.push();
        boolean visible = ImGui.begin("About Flashback Actors###FbActorsAbout", OPEN, ImGuiWindowFlags.NoResize);
        try {
            if (!visible) return;
            boolean contents = ImGui.beginChild("##AboutCredits", 0,
                Math.max(1, ImGui.getContentRegionAvailY() - 48), false);
            try {
                if (contents) {
                    ImGui.textUnformatted("Flashback Actors");
                    ImGui.textDisabled("By PDX" + (VERSION.isBlank() ? "" : " | Version " + VERSION));
                    if(SkinPickerChrome.button("Read changelog",false,ImGui.getContentRegionAvailX())) ChangelogWindow.open();
                    ImGui.spacing();
                    ImGui.textWrapped("Thank you to everyone who helped shape Flashback Actors with ideas, bug reports, fixes and videos.");
                    ImGui.spacing();
                    ImGui.separator();
                    section("VIDEOS", "BlueTomato");
                    if (SkinPickerChrome.button("Watch BlueTomato on YouTube", false, ImGui.getContentRegionAvailX()))
                        Compat.openSkinBrowser(VIDEO_URL);
                    ImGui.setItemTooltip(VIDEO_URL);
                    ImGui.spacing();
                    section("IDEAS", "ShishuTochi", "feliz", "Corey", "bleh", "LastOreo");
                    ImGui.spacing();
                    ImGui.textWrapped("Earth: NASA Blue Marble; cloud, night and ocean maps by Solar System Scope (CC BY 4.0).");
                    ImGui.textWrapped("ICBM geometry and livery: supplied icbm-model.zip, adapted and optimized.");
                    section("3D EVENT MODEL", "Atomic Bomb - Easter Freebie", "Valentin Winkelmann | CC BY 4.0");
                    if(ImGui.smallButton("Model source and license"))Compat.openSkinBrowser("https://sketchfab.com/3d-models/atomic-bomb-easter-freebie-a3e1493da19d46fca7ae38b376bf9210");
                    ImGui.textWrapped("Model adapted for in-game rendering; simplified geometry with original texture maps.");
                    section("EVENT AUDIO", "Explosive.flac by qubodup / Iwan Gabovitch", "Atomic bomb explosion by craigsmith", "CC0 / public domain; edited and layered.", "Pressure rumble synthesized at playback.");
                    section("DUMPED SOUND ASSETS", "Some additional dumped sounds came from an old personal zip archive.");
                    ImGui.textWrapped("If an owner wants an asset removed or replaced, I will do so. If you want credit added, contact nyc.dunn@proton.me.");
                    section("IDEAS AND BUG FIXES", "Yark", "SkayMustDown", "Forensica", "ABDALLA");
                }
            } finally { ImGui.endChild(); }
            ImGui.separator();
            if (SkinPickerChrome.button("Close", true, ImGui.getContentRegionAvailX())) OPEN.set(false);
        } finally {
            ImGui.end();
            SkinPickerChrome.pop();
        }
    }

    private static void section(String title, String... names) {
        ImGui.textDisabled(title);
        for (String name : names) ImGui.textUnformatted(name);
    }
}
