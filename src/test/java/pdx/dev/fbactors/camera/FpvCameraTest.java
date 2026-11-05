package pdx.dev.fbactors.camera;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FpvCameraTest {
    private BoatCameraModel.Shot shot(int mode,double pitch,double speed,double turn) {
        return CameraModeModel.shot(4,100,8,0,pitch,0,0,speed,turn,3,1.62,CameraModesCatalog.recommended(mode));
    }
    @Test void mountFollowsBodyPitchAndKeepsItsPositionAndLensAtEverySpeed() {
        for(int mode=36;mode<=38;mode++) {
            var settings=CameraModesCatalog.recommended(mode);
            var level=shot(mode,0,0,0);
            assertEquals(-settings.orbit(),level.pitch(),1e-9);
            assertEquals(shot(mode,30,0,0).pitch(),30-settings.orbit(),1e-9);
            assertEquals(4,level.x(),1e-9);
            assertEquals(101.62,level.y(),1e-9);
            assertEquals(8.4,level.z(),1e-9);
            assertEquals(level,shot(mode,0,5,0));
            assertFalse(settings.collision());
            assertEquals("FPV",CameraModesCatalog.group(mode));
        }
    }
    @Test void cameraTiltCouplesBankToYawAndBankReflectsSpeedAndTurnDirection() {
        var left=shot(36,0,1,-2);
        var right=shot(36,0,1,2);
        assertEquals(left.roll(),-right.roll(),1e-9);
        assertEquals(left.yaw(),-right.yaw(),1e-9);
        assertTrue(Math.abs(right.yaw())>5);
        assertTrue(Math.abs(shot(36,0,2,2).roll())>Math.abs(right.roll()));
        assertEquals(0,shot(36,0,0,30).roll(),1e-9);
        for(int mode=36;mode<=38;mode++) for(double pitch:new double[]{-90,-60,0,60,90}) for(double turn:new double[]{-90,0,90}) {
            var frame=shot(mode,pitch,3,turn);
            assertTrue(frame.valid());
            assertEquals(CameraModesCatalog.recommended(mode).fov(),frame.fov());
        }
    }
    @Test void recordedPitchAndSavedProfilesReachTheSameCameraCalculation() {
        var gson=new Gson();
        for(int mode=36;mode<=38;mode++) {
            var settings=CameraModesCatalog.recommended(mode);
            assertEquals(settings,gson.fromJson(gson.toJson(settings),BoatCam.Settings.class));
            var sample=new BoatSubjects.Sample(4,100,8,0,35,0,-1,1,2,null,null);
            assertEquals(CameraModeModel.shot(4,100,8,0,35,0,-1,1,2,2,1.62,settings),
                BoatCam.desired(sample,null,settings,40,16.0/9));
        }
    }
    @Test void headingAndRollStayContinuousAtStopAndAcrossFullRotations() {
        var settings=CameraModesCatalog.recommended(30);
        var a=CameraModeModel.shot(0,100,0,90,0,0,.0099,0,0,1.62,settings);
        var b=CameraModeModel.shot(0,100,0,90,0,0,.0101,0,0,1.62,settings);
        assertTrue(Math.hypot(a.x()-b.x(),a.z()-b.z())<.01);
        var before=new BoatCameraModel.Shot(0,0,0,179,0,359,90);
        var after=new BoatCameraModel.Shot(0,0,0,-179,0,1,90);
        var middle=BoatCameraModel.blend(before,after,.5);
        assertEquals(360,middle.roll(),1e-9);
        assertEquals(180,middle.yaw(),1e-9);
    }
}
