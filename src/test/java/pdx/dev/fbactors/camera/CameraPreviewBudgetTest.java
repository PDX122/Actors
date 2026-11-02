package pdx.dev.fbactors.camera;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CameraPreviewBudgetTest {
    @Test void aFullLibraryAndInspectorLeaveRoomInThe16BitDrawList() {
        int vertices=2000;
        for(int i=0;i<=CameraModesCatalog.ENTRIES.size();i++) {
            int budget=CameraModesPreview.triangleBudget(vertices,i==CameraModesCatalog.ENTRIES.size());
            assertTrue(budget>0,"Every mode card and the inspector should retain a preview");
            if(budget>0) vertices+=budget*CameraModesPreview.VERTICES_PER_TRIANGLE+CameraModesPreview.CHROME_RESERVE;
            assertTrue(vertices<=CameraModesPreview.DRAW_LIST_CEILING);
        }
        assertEquals(0,CameraModesPreview.triangleBudget(48_000,false));
        assertEquals(0,CameraModesPreview.triangleBudget(65_000,true));
    }

    @Test void actualScenesRespectTheirBudgetAcrossModesSizesAndCameraPlaneCrossings() {
        for(int mode=0;mode<CameraModesCatalog.ENTRIES.size();mode++) for(float width:new float[]{90,640}) for(double time:new double[]{0,3.5,11}) {
            int budget=CameraModesPreview.triangleBudget(0,width>=100);
            var key=new CameraModesPreview.Key(CameraModesCatalog.recommended(mode),1,2,3,4,false,true,width,width*.62f);
            var triangles=CameraModesPreview.scene(key,time,budget);
            assertFalse(triangles.isEmpty(),"Empty scene for mode "+mode);
            assertTrue(triangles.size()<=budget,"Mode "+mode+" exceeded "+budget);
            for(var t:triangles) if(t.texture()==3 || t.texture()==4) {
                assertGroundScale(t.ua(),t.ub()); assertGroundScale(t.ub(),t.uc());
            }
            if(mode!=3) assertTrue(triangles.stream().anyMatch(t -> t.texture()==1),"Actor lost for mode "+mode);
        }
    }
    private static void assertGroundScale(CameraPreviewMesh.Vertex a,CameraPreviewMesh.Vertex b) {
        assertEquals(b.point().x()-a.point().x(),(b.u()-a.u())*64,1e-4);
        assertEquals(b.point().z()-a.point().z(),(b.v()-a.v())*64,1e-4);
        assertTrue(a.u()>=0 && a.u()<=1); assertTrue(a.v()>=0 && a.v()<=1);
    }
}
