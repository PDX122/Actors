package pdx.dev.fbactors.camera;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.util.UUID;
import com.google.gson.Gson;

class BoatTimelineTest {
    private BoatTimeline.Clip clip(int start, int end) {
        var c = new BoatTimeline.Clip(); c.start=start; c.end=end; c.target=UUID.randomUUID();
        c.settings=new BoatCam.Settings(0,4,2,0,1,20,0,70,0,0,false);
        return c;
    }
    @Test void boundariesMuteOverlapAndSave() {
        var track=new BoatTimeline(); var a=clip(20,60); var b=clip(40,50);
        track.clips.add(a); track.clips.add(b);
        assertNull(track.active(19.99)); assertSame(a,track.active(20));
        assertSame(b,track.active(40)); assertSame(b,track.active(49.99));
        assertSame(a,track.active(50)); assertNull(track.active(60));
        b.enabled=false; assertSame(a,track.active(45));
        track.enabled=false; assertNull(track.active(45));
        track.enabled=true;
        var gson=new Gson(); var saved=gson.fromJson(gson.toJson(track),BoatTimeline.class);
        saved.normalize(); assertEquals(a.target,saved.active(30).target);
        assertEquals(a.settings,saved.active(30).settings);
        assertNull(track.active(Double.NaN));
    }
    @Test void duplicateRetainsShotAndSkipsOccupiedRanges() {
        var track=new BoatTimeline(); var a=clip(20,40); var b=clip(40,70);
        track.clips.add(a); track.clips.add(b);
        var copy=track.duplicate(a.id);
        assertNotEquals(a.id,copy.id); assertEquals(70,copy.start); assertEquals(90,copy.end);
        assertEquals(a.target,copy.target); assertEquals(a.settings,copy.settings);
        var snapshot=track.copy(); copy.start=80;
        assertEquals(70,snapshot.find(copy.id).start);
        track.locked=true; assertNull(track.duplicate(a.id));
    }
    @Test void invalidShotsAreRejectedBeforeCameraApplication() {
        assertFalse(new BoatCameraModel.Shot(Double.NaN,0,0,0,0,0,70).valid());
        assertFalse(new BoatCameraModel.Shot(0,0,0,Double.POSITIVE_INFINITY,0,0,70).valid());
        assertFalse(new BoatCameraModel.Shot(0,0,0,0,0,0,Float.NaN).valid());
        assertFalse(new BoatCameraModel.Shot(0,0,0,0,0,0,180).valid());
        assertTrue(new BoatCameraModel.Shot(0,0,0,0,0,0,70).valid());
    }
    @Test void invalidCameraValuesAndDuplicateIdsCannotReachPlayback() {
        var settings=new BoatCam.Settings(999,Double.NaN,Double.POSITIVE_INFINITY,999,-1,999,-99,0,999,Double.NaN,false);
        assertEquals(CameraModesCatalog.ENTRIES.size()-1,settings.mode()); assertEquals(4,settings.distance()); assertEquals(2,settings.height());
        assertEquals(12,settings.side()); assertEquals(0,settings.ahead()); assertEquals(90,settings.orbit());
        assertEquals(0,settings.bank()); assertEquals(30,settings.fov()); assertEquals(20,settings.speedFov()); assertEquals(0,settings.lag());
        var track=new BoatTimeline(); var a=clip(0,10); var b=clip(10,20); b.id=a.id;
        track.clips.add(a); track.clips.add(b); track.normalize(); assertNotEquals(a.id,b.id);
        for(int n=0;n<300;n++) track.clips.add(clip(0,10));
        track.normalize(); assertEquals(256,track.clips.size());
    }
    @Test void normalizeRepairsInvalidRangesAndMissingClips() {
        var track=new BoatTimeline(); var c=clip(-4,-1); track.clips.add(c); track.clips.add(null);
        track.normalize(); assertEquals(1,track.clips.size());
        assertEquals(0,c.start); assertEquals(1,c.end);
    }
}
