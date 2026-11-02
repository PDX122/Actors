package pdx.dev.fbactors.camera;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CinematicCameraTest {
    private BoatCameraModel.Shot shot(int mode,double seconds) {
        return CameraModeModel.shot(0,0,0,0,0,.1,0,seconds,1.62,CameraModesCatalog.recommended(mode));
    }
    @Test void newModesHaveStableSavedIdsAndDistinctFraming() {
        assertEquals("pair",CameraModesCatalog.ENTRIES.get(7).id());
        for(int mode=8;mode<14;mode++) {
            assertEquals(mode,CameraModesCatalog.recommended(mode).mode());
            assertTrue(shot(mode,3).valid());
        }
        assertTrue(shot(8,0).x()>.5);
        assertTrue(shot(9,0).pitch()<0,"Low angle looks upward");
        assertTrue(shot(9,0).y()<.5);
    }
    @Test void revealsEaseInAndOutThenHoldTheirFinalComposition() {
        assertTrue(shot(10,8).y()>shot(10,0).y()+3);
        assertTrue(Math.abs(shot(11,8).z())<Math.abs(shot(11,0).z()));
        assertTrue(Math.abs(shot(12,8).z())>Math.abs(shot(12,0).z()));
        for(int mode=10;mode<14;mode++) {
            assertEquals(shot(mode,0),shot(mode,-10));
            assertEquals(shot(mode,8),shot(mode,80));
            double start=Math.abs(shot(mode,.01).z()-shot(mode,0).z());
            double middle=Math.abs(shot(mode,4.01).z()-shot(mode,4).z());
            assertTrue(start<middle*.02,"Shot should ease in");
        }
    }
    @Test void dollyZoomChangesLensWhilePreservingTheSubjectScaleFactor() {
        double scale=-1;
        for(double t:new double[]{0,1,4,8}) {
            var s=shot(13,t);
            double distance=Math.sqrt(s.x()*s.x()+Math.pow(s.y()-CameraModeModel.aimHeight(1.62),2)+s.z()*s.z());
            double value=distance*Math.tan(Math.toRadians(s.fov())*.5);
            if(scale<0) scale=value; else assertEquals(scale,value,1e-6);
        }
        assertTrue(shot(13,0).fov()>shot(13,8).fov());
    }
}
