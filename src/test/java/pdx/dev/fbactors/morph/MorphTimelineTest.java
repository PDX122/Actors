package pdx.dev.fbactors.morph;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import com.google.gson.Gson;

class MorphTimelineTest {
    @Test void malformedSavedClipsAreFilteredAndBounded() {
        var track=new MorphTimeline(); track.clips.add(null);
        var invalid=new MorphTimeline.Clip(); invalid.actor=UUID.randomUUID(); invalid.morph=null; track.clips.add(invalid);
        for(int n=0;n<300;n++) {
            var c=new MorphTimeline.Clip(); c.actor=UUID.randomUUID(); c.start=-10; c.end=Integer.MIN_VALUE; c.pose=null;
            track.clips.add(c);
        }
        track.normalize(); assertEquals(256,track.clips.size());
        for(var c:track.clips) { assertEquals(0,c.start); assertEquals(1,c.end); assertEquals("auto",c.pose); }
    }
    @Test void crouchingUsesNativeSitOnlyInAutoMode() {
        assertEquals("sitting",MorphPoses.resolve("auto",true,true));
        assertEquals("auto",MorphPoses.resolve("auto",false,true));
        assertEquals("auto",MorphPoses.resolve("auto",true,false));
        assertEquals("relaxing",MorphPoses.resolve("relaxing",true,true));
        assertTrue(MorphPoses.choices("dog").contains("sitting"));
    }
    @Test void bodyPoseOverridesRestoreDefaultInGapsAndAfterSeeking() {
        var actor=UUID.randomUUID(); var other=UUID.randomUUID();
        var base=MorphSelection.of(actor,"wolf").withScale(2).withAlpha(.7f);
        var track=new MorphTimeline(); var c=new MorphTimeline.Clip();
        c.actor=actor; c.start=20; c.end=40; c.morph="cat"; c.pose="relaxing"; track.clips.add(c);
        assertSame(base,track.evaluate(actor,19.99,base));
        var changed=track.evaluate(actor,20,base);
        assertEquals("cat",changed.morphId()); assertEquals("relaxing",changed.pose());
        assertEquals(2,changed.scale()); assertEquals(.7f,changed.alpha());
        assertSame(base,track.evaluate(other,25,base));
        assertSame(base,track.evaluate(actor,40,base));
        assertEquals("cat",track.evaluate(actor,22,base).morphId());
        c.enabled=false; assertSame(base,track.evaluate(actor,22,base));
        assertSame(base,track.evaluate(actor,Double.NaN,base));
    }
    @Test void overlapsPlayerClipsAndSavedCopies() {
        UUID actor=UUID.randomUUID(); var track=new MorphTimeline();
        var a=new MorphTimeline.Clip(); a.actor=actor; a.start=0; a.end=100; a.morph="cat"; a.pose="sitting";
        var b=new MorphTimeline.Clip(); b.actor=actor; b.start=30; b.end=50; b.morph="player";
        track.clips.add(a); track.clips.add(b);
        var fallback=MorphSelection.of(actor,"wolf");
        assertFalse(track.evaluate(actor,30,fallback).isMorphed());
        assertEquals("cat",track.evaluate(actor,50,fallback).morphId());
        var gson=new Gson(); var saved=gson.fromJson(gson.toJson(track),MorphTimeline.class); saved.normalize();
        assertEquals("sitting",saved.active(actor,20).pose);
        var snapshot=track.copy(); a.morph="wolf"; assertEquals("cat",snapshot.find(a.id).morph);
    }
}
