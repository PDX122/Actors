package pdx.dev.fbactors.camera;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CameraPreviewBudgetTest {
    @Test void aFullLibraryAndInspectorLeaveRoomInThe16BitDrawList() {
        int vertices=2000;
        for(int i=0;i<=CameraModesPreview.CARDS_PER_PAGE;i++) {
            int budget=CameraModesPreview.triangleBudget(vertices,i==CameraModesPreview.CARDS_PER_PAGE);
            assertTrue(budget>0);
            vertices+=budget*CameraModesPreview.VERTICES_PER_TRIANGLE+CameraModesPreview.CHROME_RESERVE;
            assertTrue(vertices<=CameraModesPreview.DRAW_LIST_CEILING);
        }
        assertEquals(0,CameraModesPreview.triangleBudget(64_000,false));
    }
    @Test void everyModeHasFiniteGeometryAndFitsBothThumbnailAndInspectorBudgets() {
        for(var entry:CameraModesCatalog.ENTRIES) for(float width:new float[]{90,640}) for(double time:new double[]{0,3.5,11}) {
            int budget=CameraModesPreview.triangleBudget(0,width>100);
            var triangles=CameraPreviewStudio.render(CameraModesCatalog.recommended(entry.mode()),time,width,width*9/16,budget);
            assertFalse(triangles.isEmpty(),entry.id());
            assertTrue(triangles.size()<=budget,entry.id());
            for(var t:triangles) for(var p:new CameraModePreviewScene.Pixel[]{t.a(),t.b(),t.c()}) {
                assertTrue(Float.isFinite(p.x()));assertTrue(Float.isFinite(p.y()));assertTrue(p.depth()>=.079);
            }
        }
    }
    @Test void viewRespondsToTheActualLensAndDistanceControls() {
        var a=CameraModesCatalog.recommended(0);
        var b=new BoatCam.Settings(0,8,1.8,0,0,0,0,35,0,.16,false);
        assertNotEquals(CameraPreviewStudio.render(a,3.5,640,360,3200),CameraPreviewStudio.render(b,3.5,640,360,3200));
        assertNotEquals(CameraPreviewStudio.render(a,0,640,360,3200),CameraPreviewStudio.render(a,3.5,640,360,3200));
    }
}
