package pdx.dev.fbactors.camera;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ElytraCameraTest {
    private BoatCameraModel.Shot shot(int mode,double yaw,double vy,double turn,double time) {
        return CameraModeModel.shot(0,100,0,yaw,0,vy,1,turn,time,.4,CameraModesCatalog.recommended(mode));
    }
    @Test void flightModesFollowVelocityThroughClimbsAndDives() {
        for(int mode=30;mode<=35;mode++) {
            assertEquals("Elytra flight",CameraModesCatalog.group(mode));
            assertEquals(mode,CameraModesCatalog.recommended(mode).mode());
            assertEquals(shot(mode,0,-1,0,2),shot(mode,90,-1,0,2));
            for(double vy:new double[]{-6,-1,0,1,6}) {
                var frame=shot(mode,0,vy,40,2);
                assertTrue(frame.valid());
                assertTrue(Math.abs(frame.roll())<=CameraModesCatalog.recommended(mode).bank());
                assertTrue(frame.fov()>=30 && frame.fov()<=110);
            }
            if(mode!=34) assertTrue(shot(mode,0,-1,0,0).y()>shot(mode,0,1,0,0).y() || mode==32);
        }
        assertTrue(shot(30,0,-1,0,0).pitch()>15);
        assertTrue(shot(33,0,1,0,0).pitch()<-15);
        assertTrue(shot(32,0,0,0,0).z()>0);
        assertTrue(shot(31,0,0,0,0).x()>3);
        assertTrue(shot(34,0,0,0,0).y()>=104);
    }
    @Test void liveAndExportSamplesRetainVerticalFlightMotion() {
        var settings=CameraModesCatalog.recommended(30);
        var sample=new BoatSubjects.Sample(2,100,4,30,.2,-1,.4,3,null,null);
        assertEquals(CameraModeModel.shot(2,100,4,30,.2,-1,.4,3,2,1.62,settings),
            BoatCam.desired(sample,null,settings,40,16.0/9));
    }
    @Test void verticalAndStationaryFlightRemainValidAndSpiralsUseReplayTime() {
        for(int mode=30;mode<=35;mode++) for(double vy:new double[]{-4,0,4}) {
            var settings=CameraModesCatalog.recommended(mode);
            var a=CameraModeModel.shot(2,100,4,179,0,vy,0,0,3,.4,settings);
            assertTrue(a.valid());

        }
        assertNotEquals(shot(35,0,-.2,0,0),shot(35,0,-.2,0,3));
        assertTrue(shot(30,0,0,8,0).roll()<0);
        assertTrue(shot(30,0,0,-8,0).roll()>0);
    }
}
