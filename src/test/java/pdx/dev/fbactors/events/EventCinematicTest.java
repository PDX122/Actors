package pdx.dev.fbactors.events;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;
import pdx.dev.fbactors.camera.CameraExport;

class EventCinematicTest {
    @Test void coverSeparationHasNoCameraCutAndNaturalEventsSurviveSave() {
        var c=new EventClip();
        var before=EventCameraModel.shot(c,(MissileFlight.SHROUD-.000001)*c.end,16./9,null);
        var after=EventCameraModel.shot(c,(MissileFlight.SHROUD+.000001)*c.end,16./9,null);
        assertTrue(Math.hypot(Math.hypot(after.x()-before.x(),after.z()-before.z()),after.y()-before.y())<1);
        assertEquals(before.fov(),after.fov(),.01);assertEquals(before.yaw(),after.yaw(),.1);
        for(var kind:EventKind.LIBRARY) {
            c.kind=kind;c.end=kind.duration;c.impact=c.launch.add(kind.twoLocations?95:0,0,0);
            var loaded=new Gson().fromJson(new Gson().toJson(c),EventClip.class);loaded.normalize();assertTrue(loaded.valid());assertEquals(kind,loaded.kind);
            assertTrue(loaded.active(1));
            for(double p:new double[]{0,.25,.5,.9}) {
                var shot=EventCameraModel.shot(loaded,p*loaded.end,16./9,null);
                assertTrue(Double.isFinite(shot.x()+shot.y()+shot.z()+shot.yaw()+shot.pitch()));
            }
        }
        c.kind=EventKind.CARPET;assertFalse(c.active(1));assertFalse(java.util.Arrays.asList(EventKind.LIBRARY).contains(EventKind.CARPET));
    }
    @Test void eventCameraSupportsScrubbingStylesScalesAndPortraitExports() {
        var c=new EventClip();c.kind=EventKind.NUCLEAR;c.end=560;c.eventCam=true;c.launch=c.impact=new EventClip.Point(0,64,0);
        for(float size:new float[]{.25f,1,4})for(double aspect:new double[]{9./16,16./9})for(int style=0;style<3;style++) {
            c.blast=size;c.cameraStyle=style;
            for(int tick=0;tick<c.end;tick+=7) {
                var shot=EventCameraModel.shot(c,tick,aspect,c.impact.add(100,0,20));assertTrue(shot.valid());
                assertEquals(shot,EventCameraModel.shot(c,tick,aspect,c.impact.add(100,0,20)));
            }
        }
        c.cameraStyle=2;var restored=new Gson().fromJson(new Gson().toJson(c),EventClip.class);restored.normalize();
        assertTrue(restored.eventCam);assertEquals(2,restored.copy().cameraStyle);
        var track=new EventTimeline();track.clips.add(c);assertEquals(new CameraExport.Range(0,560),CameraExport.events(track,-1,-1));
        c.enabled=false;assertEquals(new CameraExport.Range(-1,-1),CameraExport.events(track,-1,-1));
    }
    @Test void pressureIsBoundedDeterministicAndRespondsToDistance() {
        var near=EventPressure.synthesize(1,0);var far=EventPressure.synthesize(1,3);
        assertArrayEquals(near,EventPressure.synthesize(1,0));assertEquals(16*EventPressure.RATE,near.length);
        long highNear=0,highFar=0;int maximum=0;
        for(int i=1;i<near.length;i++) {
            maximum=Math.max(maximum,Math.abs((int)near[i]));
            highNear+=Math.abs(near[i]-near[i-1]);highFar+=Math.abs(far[i]-far[i-1]);
        }
        assertTrue(maximum>5000 && maximum<32767);assertTrue(highFar<highNear);
        assertEquals(1,EventPressure.delay(343),.00001);assertEquals(8,EventPressure.delay(100000));
        assertEquals("events/generated/pressure_3_3",EventPressure.key(100,100000));
    }
    @Test void spaceFlightAndFollowCameraStayContinuousAcrossStagingAndApogee() {
        var c=new EventClip();c.end=1200;c.cameraStyle=3;
        var previous=MissileFlight.sample(c,0);var camera=EventCameraModel.shot(c,0,16./9,null);
        for(int i=1;i<2000;i++) {
            double tick=i/2000.*c.end;
            var next=MissileFlight.sample(c,tick);
            var view=EventCameraModel.shot(c,tick,16./9,null);
            if(next.progress()<MissileFlight.IMPACT-.002) {
                var a=previous.direction();var b=next.direction();
                assertTrue(a.x()*b.x()+a.y()*b.y()+a.z()*b.z()>.99,"Abrupt flight-axis turn at "+next.progress());
                assertTrue(Math.hypot(Math.hypot(camera.x()-view.x(),camera.z()-view.z()),camera.y()-view.y())<200,"Camera teleported during flight");
            }
            previous=next;camera=view;
        }
    }
    @Test void removedMapsFallBackAndClockJitterCannotRecrossStageCuts() {
        var c=new EventClip();c.end=360;c.eventCam=true;c.ending=2;
        var track=new EventTimeline();track.clips.add(c);
        var custom=c.copy();custom.end=800;track.clips.add(custom);
        var locked=c.copy();locked.locked=true;track.clips.add(locked);
        assertTrue(track.upgradeTiming());assertEquals(1200,c.end);assertEquals(800,custom.end);assertEquals(360,locked.end);
        assertFalse(track.upgradeTiming());assertEquals(1200,track.end(437));
        var loaded=new Gson().fromJson(new Gson().toJson(c),EventClip.class);loaded.normalize();
        assertEquals(0,loaded.ending);assertEquals(2,EventEnding.NAMES.length);
        loaded.ending=3;assertTrue(EventEnding.active(loaded,.70));
        assertEquals(360.2,EventFrameClock.sample(360.2,360,359.9,360,false,false));
        assertEquals(359.9,EventFrameClock.sample(360.2,360,359.9,359,false,false));
        assertEquals(359.9,EventFrameClock.sample(360.2,360,359.9,360,true,false));
        assertEquals(359.9,EventFrameClock.sample(360.2,360,359.9,360,false,true));
        assertEquals(200,EventFrameClock.sample(360.2,360,200,360,false,false));
        for(double cut:EventCameraModel.FLIGHT_CUTS) {
            double before=cut*c.end+.01;
            double after=EventFrameClock.sample(before,(int)before,before-.02,(int)before,false,false);
            assertEquals(EventCameraModel.shot(c,before,16./9,null),EventCameraModel.shot(c,after,16./9,null));
        }
    }
}
