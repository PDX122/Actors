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
    @Test void racingShotsTrackTravelEvenWhenTheBoatSlidesSideways() {
        for(int mode:new int[]{14,15,16,17,18,20}) {
            var settings=CameraModesCatalog.recommended(mode);
            var a=CameraModeModel.shot(0,0,0,0,.8,.2,0,0,2.55,settings);
            var b=CameraModeModel.shot(0,0,0,90,.8,.2,0,0,2.55,settings);
            assertEquals(a,b,"Travel framing must not snap with body yaw: "+mode);
            assertTrue(CameraModeModel.shot(0,0,0,90,0,0,0,0,2.55,settings).valid());
        }
        assertTrue(shot(14,0).z()<0);
        assertTrue(shot(15,0).z()>0);
        assertEquals(0,shot(16,0).z(),1e-9);
        assertTrue(shot(16,0).y()<1);
    }
    @Test void mountedFramingUsesRiderHeightAndProfileShowsTheWholeMount() {
        var settings=CameraModesCatalog.recommended(19);
        var rider=CameraModeModel.shot(0,0,0,0,0,.2,0,0,2.55,settings);
        var walker=CameraModeModel.shot(0,0,0,0,0,.2,0,0,1.62,settings);
        assertEquals(.93,rider.y()-walker.y(),1e-9);
        assertTrue(Math.abs(shot(18,0).x())>=6);
        assertTrue(shot(17,0).y()>2.5);
    }
    @Test void newRevealsReachTheirEndAndOrbitKeepsMovingAfterItsRise() {
        for(int mode:new int[]{21,22}) {
            assertNotEquals(shot(mode,0),shot(mode,8));
            assertEquals(shot(mode,8),shot(mode,80));
            assertEquals(shot(mode,0),shot(mode,-10));
        }
        assertEquals(90,shot(22,8).pitch(),1e-6);
        assertTrue(shot(21,0).x()<0 && shot(21,8).x()>0);
        assertNotEquals(shot(23,8),shot(23,12));
        assertEquals(shot(23,8).y(),shot(23,12).y());
        for(var entry:CameraModesCatalog.ENTRIES) for(double speed:new double[]{0,.001,.3,4}) for(double time:new double[]{0,4,8,90}) {
            var frame=CameraModeModel.shot(0,0,0,179,speed,-speed,120,time,2.55,CameraModesCatalog.recommended(entry.mode()));
            assertTrue(frame.valid(),entry.id());
            assertTrue(frame.fov()>=(CameraModesCatalog.uav(entry.mode())?2:30) && frame.fov()<=110);
        }
    }
    @Test void wildShotsAnimateAndTimedDivesSettle() {
        for(int mode=24;mode<=29;mode++) {
            assertEquals(mode,CameraModesCatalog.recommended(mode).mode());
            assertEquals(shot(mode,0),shot(mode,-10));
            assertNotEquals(shot(mode,0),shot(mode,4));
        }
        assertEquals(96,shot(24,4).roll(),1e-9);
        assertTrue(shot(25,2).y()>shot(25,0).y());
        assertTrue(shot(26,2).x()>0);
        assertTrue(shot(27,2).x()>0);
        assertEquals(shot(28,2),shot(28,80));
        assertTrue(Math.abs(shot(28,2).z())<Math.abs(shot(28,0).z())*.25);
        assertEquals(shot(29,8),shot(29,80));
        assertTrue(shot(29,0).pitch()>70);
        assertTrue(shot(29,8).pitch()<0);
    }
    @Test void sideControlsRespondBelowTheBaseDistanceAndCanCrossToTheOtherSide() {
        for(int mode:new int[]{1,16,18}) {
            var base=CameraModesCatalog.recommended(mode);
            double previous=Double.NEGATIVE_INFINITY;
            for(double side:new double[]{-12,-4,0,1,4,12}) {
                var settings=new BoatCam.Settings(mode,base.distance(),base.height(),side,base.ahead(),base.orbit(),base.bank(),base.fov(),base.speedFov(),base.lag(),base.collision());
                var frame=CameraModeModel.shot(0,0,0,0,0,.1,0,0,1.62,settings);
                assertTrue(frame.x()>previous,"Side offset must respond throughout its range");
                previous=frame.x();
            }
        }
    }
}
