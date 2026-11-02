package pdx.dev.fbactors.ui;

import imgui.moulberry90.ImGui;
import imgui.moulberry90.flag.ImGuiCond;
import imgui.moulberry90.flag.ImGuiInputTextFlags;
import imgui.moulberry90.type.ImBoolean;
import imgui.moulberry90.type.ImString;
import net.minecraft.client.Minecraft;
import pdx.dev.fbactors.FlashbackActors;
import pdx.dev.fbactors.debug.DiagnosticLog;
import pdx.dev.fbactors.debug.ErrorRecovery;

public final class ErrorWindow {
    private static final ImBoolean OPEN=new ImBoolean(false);
    private static final ImString DETAILS=new ImString(32_768);
    private static long seen,selected;
    private static String status="";
    private ErrorWindow() {}
    public static void open() { OPEN.set(true); }
    public static void render() {
        var failures=ErrorRecovery.FAILURES;
        if(seen!=failures.sequence()) {
            seen=failures.sequence(); OPEN.set(true); selected=seen; status="";
            ImGui.setNextWindowFocus();
        }
        if(!OPEN.get()) return;
        var viewport=ImGui.getMainViewport();
        float width=Math.max(1,viewport.getWorkSizeX()-24),height=Math.max(1,viewport.getWorkSizeY()-24);
        ImGui.setNextWindowSize(Math.min(640,width),Math.min(520,height),ImGuiCond.Appearing);
        ImGui.setNextWindowSizeConstraints(Math.min(360,width),Math.min(300,height),width,height);
        boolean visible=ImGui.begin("Flashback Actors error report###FbActorsError",OPEN);
        try {
            if(!visible) return;
            ImGui.pushTextWrapPos(0);
            try {
                var history=failures.history();
                if(history.isEmpty()) ImGui.textWrapped("No caught errors. Random triggers a deliberate failure to test recovery.");
                else {
                    var active=history.stream().filter(f -> f.sequence()==selected).findFirst().orElse(history.getFirst());
                    ImGui.textWrapped("A feature encountered an error: "+active.feature());
                    ImGui.textWrapped(failures.stopped(active.feature())
                        ?"This feature has been temporarily stopped to prevent the error repeating. Other guarded features can continue."
                        :"Retry is enabled for this feature. The original error remains here for reference.");
                    ImGui.textWrapped(active.summary());
                    if(active.feature().equals("Actor playback") || active.feature().equals("Actor end-of-tick updates"))
                        ImGui.textWrapped("Actor playback updates are stopped and the replay has been paused. Retry the feature before resuming playback.");
                    ImGui.setNextItemWidth(-1);
                    if(ImGui.beginCombo("##ErrorHistory",active.feature()+" / "+active.time())) {
                        try { for(var f:history) if(ImGui.selectable(f.feature()+" / "+f.time()+"##"+f.sequence(),selected==f.sequence())) selected=f.sequence(); }
                        finally { ImGui.endCombo(); }
                    }
                    DETAILS.set(active.stack());
                    ImGui.inputTextMultiline("##ErrorDetails",DETAILS,Math.max(1,ImGui.getContentRegionAvailX()),
                        Math.max(60,ImGui.getContentRegionAvailY()-150),ImGuiInputTextFlags.ReadOnly);
                    if(failures.stopped(active.feature()) && ImGui.button("Retry affected feature")) failures.retry(active.feature());
                }
                if(ImGui.button("Copy log")) copyLog();
                ImGui.sameLine();
                if(ImGui.button("Random")) ErrorRecovery.random();
                ImGui.setItemTooltip("Trigger an intentional error through the same recovery path used by real failures");
                ImGui.sameLine();
                if(ImGui.button("Dismiss")) OPEN.set(false);
                if(!status.isBlank()) ImGui.textWrapped(status);
                ImGui.textDisabled("Copy log includes error details and ~3 KB from latest.log.");
            } finally { ImGui.popTextWrapPos(); }
        } finally { ImGui.end(); }
    }
    private static void copyLog() {
        try {
            String log;
            try { log=DiagnosticLog.tail(Minecraft.getInstance().gameDirectory.toPath().resolve("logs/latest.log"),3_072); }
            catch(java.io.IOException | SecurityException e) { log="latest.log unavailable: "+e; }
            String text="Flashback Actors diagnostic report\nBuild: "+FlashbackActors.BUILD_TAG
                +"\nMod: "+version("fbactors")+"\nMinecraft: "+version("minecraft")
                +"\nJava: "+System.getProperty("java.version")+"\nOS: "+System.getProperty("os.name")
                +ErrorRecovery.FAILURES.reportText()+"\n--- latest.log (tail) ---\n"+log;

            if(text.length() > 4_096) text=text.substring(0,4_096)+"\n[truncated for clipboard]";
            ImGui.setClipboardText(text); status="Copied error details and recent log to clipboard.";
        } catch(Exception | LinkageError e) { status="Could not copy to clipboard: "+e.getMessage()+". Error details are still shown above."; }
    }
    private static String version(String id) {
        return net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer(id)
            .map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("unknown");
    }
}
