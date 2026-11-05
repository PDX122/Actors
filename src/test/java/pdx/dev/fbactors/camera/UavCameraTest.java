package pdx.dev.fbactors.camera;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UavCameraTest {
    private BoatCameraModel.Shot platform(int mode,double time) {
        return CameraModeModel.shot(0,64,0,0,0,0,0,time,1.62,CameraModesCatalog.recommended(mode));
    }
    @Test void jumpsMoveWithinTheGateWithoutLiftingTheAircraft() {
        for(int mode:new int[]{39,40}) {
            var settings=CameraModesCatalog.recommended(mode);
            var previous=platform(mode,0);
            var state=UavCameraModel.step(null,null,previous,0,65.16,0,Double.NaN,settings.lag());
            var baseline=state;
            double altitude=state.view().y(),largestOffset=0;
            for(int frame=1;frame<=240;frame++) {
                double time=frame/60.0;
                double jump=time<.6?1.25*Math.sin(Math.PI*time/.6):0;
                var desired=platform(mode,time);
                state=UavCameraModel.step(state,previous,desired,0,65.16+jump,0,20.0/60,settings.lag());
                baseline=UavCameraModel.step(baseline,previous,desired,0,65.16,0,20.0/60,settings.lag());
                previous=desired;
                assertEquals(altitude,state.view().y(),1e-9,"A jump must not change flight altitude");
                assertEquals(0,state.view().roll()); assertTrue(state.view().valid());
                var pixel=CameraModePreviewScene.project(state.view(),new CameraModePreviewScene.Point(0,65.16+jump,0),640,360);
                assertNotNull(pixel);
                var resting=CameraModePreviewScene.project(baseline.view(),new CameraModePreviewScene.Point(0,65.16,0),640,360);
                double offset=Math.abs(pixel.y()-resting.y());
                largestOffset=Math.max(largestOffset,offset);
                if(time>3) assertTrue(offset<.3,"Jump response should settle after landing");
            }
            assertTrue(largestOffset>3,"Jumping subject should move within the image instead of dragging the camera");
            assertTrue(largestOffset<50,"Tracker must keep a normal jump inside its gate");
        }
    }
    @Test void gimbalRespondsGraduallyAndScrubbingCutsToTheNewTarget() {
        var desired=platform(39,0);
        var state=UavCameraModel.step(null,null,desired,0,65.16,0,Double.NaN,.8);
        var first=UavCameraModel.step(state,desired,desired,0,66.4,0,20.0/60,.8);
        double raw=Math.abs(first.aim().pitch()-state.view().pitch());
        assertTrue(Math.abs(first.view().pitch()-state.view().pitch())<raw*.1);
        var seek=UavCameraModel.step(first,desired,desired,0,66.4,0,-20,.8);
        assertEquals(seek.aim().pitch(),seek.view().pitch());
        var paused=UavCameraModel.step(first,desired,desired,0,66.4,0,0,.8);
        assertEquals(first.view(),paused.view());
    }
    @Test void jumpingAndLandingLookConsistentAcrossExportFrameRates() {
        UavCameraModel.State reference=null;
        for(int fps:new int[]{24,60,144}) {
            var previous=platform(39,0);
            var state=UavCameraModel.step(null,null,previous,0,65.16,0,Double.NaN,.8);
            for(int i=1;i<=fps*2;i++) {
                double time=i/(double)fps;
                var desired=platform(39,time);
                double jump=time<.6?1.25*Math.sin(Math.PI*time/.6):0;
                state=UavCameraModel.step(state,previous,desired,0,65.16+jump,0,20.0/fps,.8);
                previous=desired;
            }
            if(reference!=null) {
                assertEquals(reference.view().x(),state.view().x(),.002);
                assertEquals(reference.view().pitch(),state.view().pitch(),.005);
                assertEquals(reference.view().yaw(),state.view().yaw(),.005);
            }
            reference=state;
        }
    }
}
