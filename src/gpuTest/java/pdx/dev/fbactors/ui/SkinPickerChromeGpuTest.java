package pdx.dev.fbactors.ui;

import imgui.moulberry90.ImGui;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SkinPickerChromeGpuTest {
    @org.junit.jupiter.api.BeforeAll static void nativeLibrary() throws Exception { StudioDockingGpuTest.loadFlashbackNative(); }
    @Test void skinControlsRenderWithoutLeakingStylesOrClippingTheFooter() {
        ImGui.createContext();
        try {
            var io=ImGui.getIO();io.setIniFilename(null);io.setDisplaySize(1000,800);io.setDeltaTime(1f/60);
            io.getFonts().addFontDefault();io.getFonts().build();
            float originalPadding=ImGui.getStyle().getWindowPaddingX();
            ImGui.newFrame();
            SkinPickerChrome.push();
            try {
                ImGui.setNextWindowPos(20,20);ImGui.setNextWindowSize(600,510);
                ImGui.begin("Skin UI verification");
                try {
                    SkinPickerChrome.button("Find",false,64);
                    assertEquals(32,ImGui.getItemRectSizeY());
                    var layout=SkinPickerLayout.fit(ImGui.getContentRegionAvailX(),
                        ImGui.getContentRegionAvailY()-ImGui.getFrameHeightWithSpacing()*2,8);
                    assertTrue(layout.previewWidth()>layout.listWidth());
                    ImGui.beginChild("library",layout.listWidth(),layout.listHeight());
                    SkinPickerChrome.row("player","PDXJS",true,0);
                    SkinPickerChrome.row("long","A deliberately long saved skin display name",false,0);
                    ImGui.endChild();ImGui.sameLine();
                    ImGui.beginChild("preview",layout.previewWidth(),layout.previewHeight());
                    SkinPickerChrome.stage(0,ImGui.getContentRegionAvailX(),280,"Choose a player");
                    SkinPickerChrome.button("Front",true,70);ImGui.sameLine();
                    SkinPickerChrome.button("Back",false,70);ImGui.sameLine();
                    SkinPickerChrome.button("Walk",false,70);
                    ImGui.endChild();
                    SkinPickerChrome.apply(true,170);
                    assertTrue(ImGui.getItemRectMaxY()<ImGui.getWindowPosY()+ImGui.getWindowSizeY(),"Apply must remain inside the window");
                } finally { ImGui.end(); }
            } finally { SkinPickerChrome.pop(); }
            assertEquals(originalPadding,ImGui.getStyle().getWindowPaddingX());
            ImGui.render();
            assertTrue(ImGui.getDrawData().getTotalVtxCount()>0);
        } finally { ImGui.destroyContext(); }
    }
}
