package pdx.dev.fbactors.events;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

class BombEventsTest {
    @Test void nuclearDropNeedsOneLocationAndStartsHighAboveIt() {
        var c=new EventClip();c.kind=EventKind.NUCLEAR;c.launch=c.impact=new EventClip.Point(12,64,-30);c.end=c.kind.duration;
        assertTrue(c.valid());assertTrue(BombFlight.position(c,0,0).y()>c.impact.y()+175);
        assertEquals(c.impact.y()+2*BombFlight.size(c),BombFlight.position(c,0,c.kind.impact).y(),.0001);
        assertTrue(BombFlight.position(c,0,.10).y()>BombFlight.position(c,0,.15).y());
        var restored=new Gson().fromJson(new Gson().toJson(c),EventClip.class);restored.normalize();
        assertEquals(EventKind.NUCLEAR,restored.kind);assertTrue(restored.valid());assertEquals(c.dropHeight,restored.dropHeight);
        c.kind=EventKind.ICBM;assertFalse(c.valid());
    }
    @Test void carpetRunHasOrderedImpactsAndASpreadAroundOneMarker() {
        var c=new EventClip();c.kind=EventKind.CARPET;c.launch=c.impact=new EventClip.Point(0,64,0);c.end=c.kind.duration;
        assertTrue(c.valid());
        for(int i=1;i<c.bombs;i++) {
            assertTrue(BombFlight.impact(c,i)>BombFlight.impact(c,i-1));
            assertNotEquals(BombFlight.target(c,i),BombFlight.target(c,i-1));
        }
        var first=BombFlight.target(c,0);var last=BombFlight.target(c,c.bombs-1);
        assertTrue(first.z()<c.impact.z());assertTrue(last.z()>c.impact.z());
        c.heading=90;assertTrue(BombFlight.target(c,0).x()<0);assertTrue(BombFlight.target(c,c.bombs-1).x()>0);
        var copy=c.copy();c.bombs=24;c.spread=100;assertEquals(12,copy.bombs);assertEquals(72,copy.spread);
    }
    @Test void oldMissileSidecarsRemainMissilesAndInvalidParametersAreBounded() {
        var old=new Gson().fromJson("{\"start\":0,\"end\":600}",EventClip.class);old.normalize();
        assertEquals(EventKind.ICBM,old.kind);assertEquals(600,old.end);
        old.bombs=999;old.dropHeight=Float.NaN;old.volume=-1;old.normalize();
        assertEquals(24,old.bombs);assertEquals(180,old.dropHeight);assertEquals(0,old.volume);
    }
}
