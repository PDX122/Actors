package pdx.dev.fbactors.camera;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BoatBattleCameraTest {
    @Test void wideningGapAndPortraitVideoBothPullCameraBack() {
        var close=BoatCameraModel.battle(-1,60,0,1,60,0,0,6,2,80,16d/9);
        var wide=BoatCameraModel.battle(-20,60,0,20,60,0,0,6,2,80,16d/9);
        var portrait=BoatCameraModel.battle(-20,60,0,20,60,0,0,6,2,80,9d/16);
        assertTrue(wide.z()<close.z());
        assertTrue(portrait.z()<wide.z());
    }
    @Test void bothBoatsFitTheProjectionAtDifferentHeadingsHeightsAndAspects() {
        for(double yaw:new double[]{0,45,90,179,-179,270})
            for(double aspect:new double[]{16d/9,9d/16,1}) {
                var s=BoatCameraModel.battle(-10,60,-20,16,66,22,yaw,6,2,70,aspect);
                for(double[] boat:new double[][]{{-10,60,-20},{16,66,22}}) {

                    for(double dx:new double[]{-1,1}) for(double dz:new double[]{-1,1})
                        assertInside(s,boat[0]+dx,boat[1]+.6,boat[2]+dz,aspect);
                }
            }
    }
    private void assertInside(BoatCameraModel.Shot s,double x,double y,double z,double aspect) {
        double yaw=Math.toRadians(s.yaw()),pitch=Math.toRadians(s.pitch());
        double dx=x-s.x(),dy=y-s.y(),dz=z-s.z();
        double right=dx*Math.cos(yaw)+dz*Math.sin(yaw);
        double flat=-dx*Math.sin(yaw)+dz*Math.cos(yaw);
        double forward=flat*Math.cos(pitch)-dy*Math.sin(pitch);
        double up=flat*Math.sin(pitch)+dy*Math.cos(pitch);
        double vertical=Math.tan(Math.toRadians(s.fov())/2);
        assertTrue(forward>0);
        assertTrue(Math.abs(right/forward)<vertical*aspect);
        assertTrue(Math.abs(up/forward)<vertical);
    }
    @Test void coincidentBoatsProduceAFiniteStableShot() {
        var s=BoatCameraModel.battle(1,60,2,1,60,2,45,6,2,80,1);
        assertTrue(Double.isFinite(s.x())); assertTrue(Double.isFinite(s.pitch()));
        assertEquals(45,s.yaw()); assertEquals(0,s.roll());
    }
}
