package pdx.dev.fbactors.ui;

import imgui.moulberry90.ImGui;
import imgui.moulberry90.flag.ImGuiConfigFlags;
import imgui.moulberry90.flag.ImGuiDir;
import imgui.moulberry90.type.ImInt;
import org.junit.jupiter.api.Test;
import pdx.dev.fbactors.station.StationWindow;
import static org.junit.jupiter.api.Assertions.*;

class StudioDockingGpuTest {
    @org.junit.jupiter.api.BeforeAll static void loadFlashbackNative() throws Exception {
        org.junit.jupiter.api.Assumptions.assumeTrue(System.getProperty("os.name").startsWith("Windows"));
        var mods=java.nio.file.Path.of("run/mods");
        org.junit.jupiter.api.Assumptions.assumeTrue(java.nio.file.Files.isDirectory(mods), "Install Flashback in run/mods for native UI tests");
        try (var files=java.nio.file.Files.list(mods)) {
            var jars=files.filter(path -> path.getFileName().toString().toLowerCase().startsWith("flashback-") && path.toString().endsWith(".jar")).toList();
            for (var jar:jars) try (var zip=new java.util.zip.ZipFile(jar.toFile())) {
                var entry=zip.getEntry("com/moulberry/imgui-natives/imgui-moulberry90-java64.dll");
                if (entry==null) continue;
                var dir=java.nio.file.Files.createTempDirectory("fbactors-imgui-test");
                try (var input=zip.getInputStream(entry)) {
                    java.nio.file.Files.copy(input,dir.resolve("imgui-moulberry90-java64.dll"));
                }
                System.setProperty("imgui.library.path",dir.toAbsolutePath().toString());
                return;
            }
        }
        org.junit.jupiter.api.Assumptions.abort("Flashback ImGui native is unavailable");
    }
    @Test void studioSharesTheEditorDockspaceAndCanMoveBesideTheTimeline() {
        ImGui.createContext();
        try {
            var io=ImGui.getIO();io.setIniFilename(null);io.addConfigFlags(ImGuiConfigFlags.DockingEnable);
            io.setDisplaySize(960,640);io.setDeltaTime(1f/60);
            io.getFonts().addFontDefault();io.getFonts().build();
            ImGui.newFrame();
            int root=ImGui.dockSpaceOverViewport(0,ImGui.getMainViewport(),0);
            var side=new ImInt();var rest=new ImInt();
            imgui.moulberry90.internal.ImGui.dockBuilderSplitNode(root,ImGuiDir.Right,.3f,side,rest);
            imgui.moulberry90.internal.ImGui.dockBuilderDockWindow(StationWindow.WINDOW_NAME,side.get());
            imgui.moulberry90.internal.ImGui.dockBuilderDockWindow("Timeline###Timeline",rest.get());
            imgui.moulberry90.internal.ImGui.dockBuilderFinish(root);
            ImGui.begin(StationWindow.WINDOW_NAME);assertEquals(side.get(),ImGui.getWindowDockID());ImGui.end();
            ImGui.begin("Timeline###Timeline");assertEquals(rest.get(),ImGui.getWindowDockID());ImGui.end();
            ImGui.render();

            ImGui.newFrame();ImGui.dockSpaceOverViewport(0,ImGui.getMainViewport(),0);
            imgui.moulberry90.internal.ImGui.dockBuilderDockWindow(StationWindow.WINDOW_NAME,rest.get());
            ImGui.begin(StationWindow.WINDOW_NAME);assertEquals(rest.get(),ImGui.getWindowDockID());ImGui.end();
            ImGui.begin("Timeline###Timeline");ImGui.end();ImGui.render();
            String saved=ImGui.saveIniSettingsToMemory();
            assertTrue(saved.contains("###Visuals"));
            assertTrue(saved.contains("DockId="));
        } finally { ImGui.destroyContext(); }
    }
}
