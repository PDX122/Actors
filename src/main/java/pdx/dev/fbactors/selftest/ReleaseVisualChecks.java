package pdx.dev.fbactors.selftest;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import imgui.moulberry90.type.ImBoolean;
import pdx.dev.fbactors.debug.ErrorRecovery;
import pdx.dev.fbactors.settings.FbSettings;
import pdx.dev.fbactors.settings.FbSettingsMenu;
import pdx.dev.fbactors.ui.*;
import pdx.dev.fbactors.camera.*;
import pdx.dev.fbactors.filters.*;
import pdx.dev.fbactors.settings.ThemeMakerMenu;
import pdx.dev.fbactors.subtitle.SubtitleMenu;
import java.nio.file.*;
import java.util.*;

public final class ReleaseVisualChecks {
    private record Screen(String name, Runnable open) {}
    private static final List<Screen> screens = new ArrayList<>();
    private static final Class<?>[] WINDOWS = {AboutWindow.class, ChangelogWindow.class,
        FbSettingsMenu.class, ThemeMakerMenu.class, LooksMenu.class, EffectsMenu.class,
        BoatMenu.class, MorphLibrary.class, ErrorWindow.class, SubtitleMenu.class, pdx.dev.fbactors.actor.ActorSetupMenu.class};
    private static Path root, captureFile;
    private static volatile Path pendingCapture;
    private static volatile Path pendingWorldCapture;
    private static int worldFrames, uiFrames;
    private static volatile int uiCompleted;
    private static int index, ticks, originalMode;
    private static boolean initialized, done, motionRequested, copyChecked;
    private static boolean originalCaptions, originalVoice, originalStudio;
    private static volatile boolean captured;
    private static String error;
    private static java.lang.reflect.Method clearExportNotice;
    private static org.lwjgl.opengl.GLDebugMessageCallback glDiagnostic;
    private static final java.util.Set<String> glReported=new java.util.HashSet<>();

    static boolean tick() {
        if (done) return true;
        try {
            if (!initialized) {
                initialized = true;
                String directory = System.getenv("FBACTORS_RELEASE_QA_DIR");
                if (directory == null || directory.isBlank()) { done = true; return true; }
                root = Path.of(directory).resolve("visuals");
                Files.createDirectories(root);
                if(FilterGpuPipeline.isGlAvailable()) installGlDiagnostic();

                set(com.moulberry.flashback.editor.ui.ReplayUI.class,"displayingTip",0);
                clearExportNotice = Class.forName("com.moulberry.flashback.editor.ui.windows.ExportDoneWindow").getDeclaredMethod("clear");
                clearExportNotice.setAccessible(true); clearExportNotice.invoke(null);
                originalMode = FbSettings.settings().boatCamMode;
                originalCaptions=FbSettings.settings().subEnabled;
                originalVoice=FbSettings.settings().voiceEnabled;
                originalStudio=FbSettings.settings().stationSidebar;
                FbSettings.settings().subEnabled=true;
                FbSettings.settings().voiceEnabled=true;
                FbSettings.settings().stationSidebar=true;
                screens.add(new Screen("editor", () -> {}));
                screens.add(new Screen("about", AboutWindow::open));
                screens.add(new Screen("changelog", ChangelogWindow::open));
                for (int page = 0; page < 9; page++) {
                    final int p = page;
                    screens.add(new Screen("settings-" + page, () -> {
                        FbSettingsMenu.open(); enumValue(FbSettingsMenu.class, "page", p);
                    }));
                }
                screens.add(new Screen("ui-themes", ThemeMakerMenu::open));
                screens.add(new Screen("looks", LooksMenu::open));
                screens.add(new Screen("effects", () -> { Effects.add("noir", 0); EffectsMenu.open(); }));
                screens.add(new Screen("actor-setup", () -> {
                    pdx.dev.fbactors.actor.ActorSetupMenu.open();
                    try { var f=pdx.dev.fbactors.actor.ActorSetupMenu.class.getDeclaredField("wantVoice"); f.setAccessible(true); ((boolean[])f.get(null))[0]=true; }
                    catch(ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
                }));
                screens.add(new Screen("skin-library", () -> {
                    if(ReleaseExportChecks.actor()==null) throw new IllegalStateException("No skin preview fixture");
                    SkinPicker.open(ReleaseExportChecks.actor());
                }));
                screens.add(new Screen("subtitles", SubtitleMenu::open));
                for(String tab:List.of("scene","edit","looks")) screens.add(new Screen("studio-"+tab,() -> {}));
                screens.add(new Screen("shortcut-help",() -> set(pdx.dev.fbactors.input.Hotkey.class,"helpOpen",true)));
                for (int mode = 0; mode < BoatCam.MODES.length; mode++) {
                    final int m = mode;
                    screens.add(new Screen("camera-" + mode, () -> {
                        FbSettings.settings().boatCamMode = m; BoatMenu.open(0);
                    }));
                }
                screens.add(new Screen("camera-timeline", () -> BoatMenu.open(1)));
                screens.add(new Screen("boat-handling", () -> BoatMenu.open(2)));
                for (String body : List.of("cat", "wolf", "parrot", "zombie", "husk", "parched", "drowned", "skeleton", "piglin")) {
                    if(!pdx.dev.fbactors.morph.Morph.registry().has(body)) continue;
                    for (String pose : pdx.dev.fbactors.morph.MorphPoses.choices(body)) {
                        screens.add(new Screen("morph-" + body + "-" + pose, () -> {
                            MorphLibrary.open(body, null); set(MorphLibrary.class, "pose", pose);
                        }));
                    }
                }
                screens.add(new Screen("morph-ui-stability", () -> {
                    MorphLibrary.open("cat", null); set(MorphLibrary.class, "pose", "walking");
                }));
                screens.add(new Screen("morph-ui-cache", () -> {
                    MorphLibrary.open("cat", null); set(MorphLibrary.class, "pose", "sitting");
                }));
                screens.add(new Screen("morph-world-stability", () -> {
                    var client=Minecraft.getInstance();
                    var frame=ReleaseExportChecks.actor().take.frames.getFirst();
                    var camera=client.getCameraEntity();
                    camera.setPos(frame.x()+1.5,frame.y()+2,frame.z()-8);
                    camera.setYRot(0); camera.setXRot(18);
                    MorphLibrary.open("cat", null); set(MorphLibrary.class, "pose", "sitting");
                }));
                screens.add(new Screen("morph-cat-relaxing", () -> {
                    MorphLibrary.open("cat", null); set(MorphLibrary.class, "pose", "relaxing");
                }));
                int catalogPages=(pdx.dev.fbactors.morph.Morph.registry().all().size()+3)/4;
                for(int page=0;page<catalogPages;page++) {
                    final int p=page;
                    screens.add(new Screen("morph-catalog-"+page,() -> { MorphLibrary.open("cat",null); set(MorphLibrary.class,"page",p); }));
                }
                screens.add(new Screen("error-recovery", () -> {
                    ErrorRecovery.random(); ErrorWindow.open();

                }));
                screens.add(new Screen("error-retry", () -> {
                    ErrorRecovery.FAILURES.retry("Random test");
                    if(!ErrorRecovery.run("Random test",() -> {})) throw new IllegalStateException("Retry remained disabled");
                    ErrorWindow.open();
                }));
                if("1".equals(System.getenv("FBACTORS_VISUAL_FOCUS"))) {
                    Effects.add("noir",0);
                    screens.removeIf(s -> !List.of("actor-setup","subtitles","studio-scene","studio-edit","studio-looks","shortcut-help","morph-cat-sitting","morph-zombie-sitting","morph-ui-stability","morph-ui-cache","morph-world-stability").contains(s.name));
                }
            }
            if (index == screens.size()) {
                closeWindows(); FbSettings.settings().boatCamMode = originalMode;
                restoreSettings();
                var unexpected = ErrorRecovery.FAILURES.history().stream()
                    .filter(f -> !f.feature().equals("Random test")).toList();
                if (!unexpected.isEmpty()) throw new IllegalStateException(String.join("; ",unexpected.stream().map(f -> f.feature()+": "+f.summary()).toList()));
                Files.writeString(root.resolve("manifest.txt"), "CAPTURED=" + index + "\n" +
                    String.join("\n", screens.stream().map(Screen::name).toList()));
                done = true; return true;
            }
            if (ticks++ == 0) {
                closeWindows(); screens.get(index).open.run(); captured = false; motionRequested=false; worldFrames=0; uiFrames=0; uiCompleted=0;
                Files.writeString(root.getParent().resolve("progress.txt"),(index+1)+"/"+screens.size()+" "+screens.get(index).name);
            }
            if (ticks == 12) {
                Path directory = root.resolve(String.format("%03d-%s", index, screens.get(index).name));
                Files.createDirectories(directory);
                captureFile = directory.resolve("editor-ui.png");
                Files.deleteIfExists(Path.of(captureFile + ".ready"));
                pendingCapture = captureFile;
            }
            boolean animated=screens.get(index).name.endsWith("-walking") || screens.get(index).name.endsWith("-running");
            if(animated && ticks>=24 && captured && !motionRequested) {
                motionRequested=true; captured=false;
                pendingCapture=captureFile.getParent().resolve("motion.png");
            }
            boolean uiSequence=screens.get(index).name.startsWith("morph-ui-");
            if(screens.get(index).name.equals("morph-ui-cache") && ticks%8==0)
                set(MorphLibrary.class,"page",(ticks/8)%((pdx.dev.fbactors.morph.Morph.registry().all().size()+3)/4));
            boolean stability=screens.get(index).name.equals("morph-world-stability");
            if(stability && ticks>12 && captured && worldFrames<12) {
                captured=false;
                pendingWorldCapture=captureFile.getParent().resolve(String.format("world-%02d.png",worldFrames++));
            }
            if (ticks > 12 && captured && (!animated || motionRequested) && (!stability || worldFrames==12) && (!uiSequence || uiCompleted>=90)) { index++; ticks = 0; }
            if (ticks > 300) throw new IllegalStateException("Screenshot timeout: " + screens.get(index).name);
        } catch (Exception | LinkageError failure) {
            restoreSettings();
            error = failure.toString(); done = true;
            try { Files.writeString(root.resolve("failure.txt"), error + "\n" + ErrorRecovery.FAILURES.reportText()); }
            catch (Exception ignored) {}
        }
        return done;
    }

    public static int tabFlags(String tab) {
        if(!initialized || root==null || done || index>=screens.size()) return 0;
        return screens.get(index).name.equals("studio-"+tab.toLowerCase(java.util.Locale.ROOT))
            ?imgui.moulberry90.flag.ImGuiTabItemFlags.SetSelected:0;
    }
    private static void restoreSettings() {
        if(root==null) return;
        FbSettings.settings().subEnabled=originalCaptions;
        FbSettings.settings().voiceEnabled=originalVoice;
        FbSettings.settings().stationSidebar=originalStudio;
    }
    public static void layoutFrame() {
        if (!initialized || root == null || done) return;
        try { clearExportNotice.invoke(null); }
        catch(ReflectiveOperationException failure) { error=failure.toString(); }
        if(imgui.moulberry90.ImGui.beginPopupModal("###ExportDone",new ImBoolean(true),0)) {
            imgui.moulberry90.ImGui.closeCurrentPopup(); imgui.moulberry90.ImGui.endPopup();
        }
        if(index<screens.size() && screens.get(index).name.equals("error-recovery") && ticks>0 && !copyChecked) {
            copyChecked=true;
            String clipboard=imgui.moulberry90.ImGui.getClipboardText();
            try {
                var copy=ErrorWindow.class.getDeclaredMethod("copyLog"); copy.setAccessible(true); copy.invoke(null);
                String readback=imgui.moulberry90.ImGui.getClipboardText();
                var statusField=ErrorWindow.class.getDeclaredField("status"); statusField.setAccessible(true);
                String status=(String)statusField.get(null);
                boolean clipboardWorks=readback.contains("Intentional Random test failure");
                Files.writeString(root.getParent().resolve("copy-log.txt"),"Clipboard readback characters="+readback.length()+"\n"+status);
                if(!clipboardWorks) throw new IllegalStateException("Copy log failed: "+status);
            } catch(Exception failure) { error=failure.toString(); }
            finally { imgui.moulberry90.ImGui.setClipboardText(clipboard==null?"":clipboard); }
        }
        for (String name : List.of("About Flashback Actors###FbActorsAbout", "Changelog###FbActorsChangelog",
            "Actors settings###ActorSettings", "UI themes###Theme Maker", "Looks gallery###ActorLooks",
            "Edit Looks###ActorEffects", "Camera modes###FbBoatCam", "Morph library###FbActorsPoses",
            "Actor Setup", "Subtitles", "Actor error report###FbActorsError")) {
            imgui.moulberry90.ImGui.setWindowPos(name, 12, 36);
        }
    }

    public static void captureFrame() {
        if(initialized && !done && index<screens.size() && ticks>12
            && screens.get(index).name.startsWith("morph-ui-") && uiFrames<90) {
            Path frame=captureFile.getParent().resolve("sequence").resolve(String.format("%03d",uiFrames++)).resolve("editor-ui.png");
            try { Files.createDirectories(frame.getParent()); }
            catch(Exception failure) { error=failure.toString(); return; }
            ReleaseFrameCapture.capture(frame,failure -> { if(failure!=null) error=failure; uiCompleted++; });
        }
        Path world=pendingWorldCapture;
        if(world!=null) {
            pendingWorldCapture=null;
            ReleaseFrameCapture.captureWorld(world, failure -> { if(failure!=null) error=failure; captured=true; });
            return;
        }
        Path output=pendingCapture;
        if(output==null) return;
        pendingCapture=null;
        ReleaseFrameCapture.capture(output, failure -> { if(failure!=null) error=failure; captured=true; });
    }
    static String error() { return error; }
    private static void installGlDiagnostic() {
        if(glDiagnostic!=null || !org.lwjgl.opengl.GL.getCapabilities().OpenGL43) return;
        glDiagnostic=org.lwjgl.opengl.GLDebugMessageCallback.create((source,type,id,severity,length,message,user) -> {
            if(type!=org.lwjgl.opengl.GL43.GL_DEBUG_TYPE_ERROR || index>=screens.size() || !glReported.add(screens.get(index).name)) return;
            try {
                var writer=new java.io.StringWriter();
                new Throwable(org.lwjgl.opengl.GLDebugMessageCallback.getMessage(length,message)).printStackTrace(new java.io.PrintWriter(writer));
                Files.writeString(root.getParent().resolve("gl-errors.txt"),screens.get(index).name+"\n"+writer+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
            } catch(Exception ignored) {}
        });
        org.lwjgl.opengl.GL43.glDebugMessageCallback(glDiagnostic,0);
        org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL43.GL_DEBUG_OUTPUT);
        org.lwjgl.opengl.GL11.glEnable(org.lwjgl.opengl.GL43.GL_DEBUG_OUTPUT_SYNCHRONOUS);
    }
    private static void closeWindows() {
        set(pdx.dev.fbactors.input.Hotkey.class,"helpOpen",false);
        SkinPicker.close();
        for (Class<?> type : WINDOWS) {
            try { var field = type.getDeclaredField("OPEN"); field.setAccessible(true); ((ImBoolean)field.get(null)).set(false); }
            catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
            try { set(type, "open", false); } catch (IllegalStateException ignored) {}
        }
    }
    private static void set(Class<?> type, String name, Object value) {
        try { var field = type.getDeclaredField(name); field.setAccessible(true); field.set(null, value); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
    private static void enumValue(Class<?> type, String name, int index) {
        try { var field = type.getDeclaredField(name); field.setAccessible(true); field.set(null, field.getType().getEnumConstants()[index]); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
}
