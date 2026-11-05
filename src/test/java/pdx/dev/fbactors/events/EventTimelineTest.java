package pdx.dev.fbactors.events;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.Gson;
import java.util.List;
import org.junit.jupiter.api.Test;

class EventTimelineTest {
    @Test void flightIsDeterministicContinuousAndStretchesWithTheClip() {
        var c=new EventClip();c.start=100;c.end=700;
        assertFalse(c.active(99));assertTrue(c.active(100));assertFalse(c.active(700));assertFalse(c.active(Double.NaN));
        assertEquals(c.launch,MissileFlight.position(c,0));assertEquals(c.impact,MissileFlight.position(c,MissileFlight.IMPACT));
        var initial=MissileFlight.sample(c,100);assertTrue(initial.direction().y()>.99);
        var reentry=MissileFlight.sample(c,100+600*(MissileFlight.REENTRY+.05));assertTrue(reentry.direction().y()<0);
        var middle=MissileFlight.sample(c,100+600*.45);
        // Sample arbitrary later/earlier times; the same tick must reproduce the same image state.
        MissileFlight.sample(c,680);MissileFlight.sample(c,120);assertEquals(middle,MissileFlight.sample(c,370));
        var stretched=c.copy();stretched.start=1000;stretched.end=2200;
        assertEquals(middle,MissileFlight.sample(stretched,1540));
        assertEquals(0,MissileFlight.sample(c,100+600*(MissileFlight.IMPACT-.01)).explosion());
        assertTrue(MissileFlight.sample(c,100+600*(MissileFlight.IMPACT+.10)).explosion()>0);
        EventClip.Point previous=MissileFlight.position(c,0);
        for(int i=1;i<=1000;i++) {var next=MissileFlight.position(c,i/1000.);assertTrue(next.valid());assertTrue(next.distance(previous)<MissileFlight.height(c)*.012);previous=next;}
    }
    @Test void defaultPacingAcceleratesOffThePadAndKeepsTheCoastUnpowered() {
        assertEquals(1200,new EventClip().end);
        assertTrue(MissileFlight.IMPACT*MissileFlight.DEFAULT_TICKS/20.<=44);
        double early=MissileFlight.fraction(.06)-MissileFlight.fraction(.04);
        double boost=MissileFlight.fraction(.16)-MissileFlight.fraction(.14);
        double terminal=MissileFlight.fraction(.71)-MissileFlight.fraction(.69);
        assertTrue(boost>early*3);assertTrue(terminal>boost);
        assertTrue(MissileFlight.SEPARATION<MissileFlight.BURNOUT && MissileFlight.BURNOUT<MissileFlight.SHROUD && MissileFlight.SHROUD<MissileFlight.REENTRY);
        assertTrue(MissileFlight.position(new EventClip(),.45).y()>6000,"Coast must genuinely leave the local scene for the cinematic space view");
        double previous=0;
        for(int i=0;i<=1000;i++) {double value=MissileFlight.fraction(i/1000.);assertTrue(value>=previous && value<=1);previous=value;}
    }
    @Test void launchLeavesVerticallyThenBanksIntoACurvedDownrangeClimb() {
        var c=new EventClip();c.launch=new EventClip.Point(0,0,0);c.impact=new EventClip.Point(95,0,0);
        var early=MissileFlight.sample(c,MissileFlight.IGNITION*c.end);
        var boost=MissileFlight.sample(c,.12*c.end);
        assertTrue(early.direction().y()>.99);
        assertTrue(boost.position().x()>100,"Boost should make a visible downrange gravity turn");
        assertTrue(boost.position().z()>20,"The climb should bank smoothly instead of staying on a line");
        assertTrue(boost.direction().x()>.05 && boost.direction().y()>.85);
        assertEquals(c.launch,MissileFlight.position(c,0));assertEquals(c.impact,MissileFlight.position(c,MissileFlight.IMPACT));
    }
    @Test void replaySidecarRoundTripsAndNormalizesInvalidDataWithoutAliasingSnapshots() {
        var timeline=new EventTimeline();var c=new EventClip();c.locked=true;c.enabled=false;c.scale=2;c.blast=3;c.start=20;c.end=300;
        timeline.clips.add(c);var gson=new Gson();
        var restored=gson.fromJson(gson.toJson(timeline),EventTimeline.class);restored.normalize();var loaded=restored.find(c.id);
        assertEquals(c.launch,loaded.launch);assertEquals(c.impact,loaded.impact);assertTrue(loaded.locked);assertFalse(loaded.enabled);assertEquals(3,loaded.blast);
        var snapshot=timeline.copy();c.launch=new EventClip.Point(1,2,3);c.end=500;
        assertNotEquals(c.launch,snapshot.find(c.id).launch);assertEquals(300,snapshot.find(c.id).end);
        var invalid=new EventClip();invalid.impact=new EventClip.Point(Double.NaN,1,1);
        var duplicate=loaded.copy();duplicate.start=Integer.MAX_VALUE;duplicate.end=-1;duplicate.scale=Float.NaN;
        restored.clips.addAll(List.of(invalid,duplicate));restored.clips.add(null);restored.normalize();
        assertEquals(2,restored.clips.size());assertNotEquals(restored.clips.get(0).id,restored.clips.get(1).id);
        assertTrue(duplicate.end-duplicate.start>=20);assertEquals(1,duplicate.scale);
        var same=new EventClip();same.impact=same.launch;assertFalse(same.valid());
    }
}
