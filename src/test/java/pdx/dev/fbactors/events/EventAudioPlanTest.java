package pdx.dev.fbactors.events;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Arrays;
import pdx.dev.fbactors.camera.CameraExport;

class EventAudioPlanTest {
    @Test void onlySuppliedIcbmSoundsAreScheduledAndMutedEventsStaySilent() {
        var c=new EventClip();
        for(var phase:EventAudioPlan.PHASES) {
            assertTrue(Arrays.asList(EventAudioPlan.FILES).contains(phase.name()));
            double p=(phase.start()+phase.end())/2;
            assertTrue(phase.active(c,p));assertTrue(phase.level(p)>0);
            assertFalse(phase.active(c,phase.end()));
            for(var kind:EventKind.LIBRARY)if(kind!=EventKind.ICBM) {var other=c.copy();other.kind=kind;assertFalse(phase.active(other,p));}
        }
        c.sound=false;for(var phase:EventAudioPlan.PHASES)assertFalse(phase.active(c,(phase.start()+phase.end())/2));
        assertEquals("detonation_close",EventAudioPlan.impact(100));assertEquals("detonation_distant",EventAudioPlan.impact(1000));
        assertArrayEquals(new EventKind[]{EventKind.ICBM,EventKind.NUCLEAR,EventKind.METEOR,EventKind.AIRBURST},EventKind.LIBRARY);
    }
    @Test void visualEventsFinishPastFootageWithoutTakingOverTheCameraRange() {
        var track=new EventTimeline();var c=new EventClip();c.start=20;c.end=1200;track.clips.add(c);
        assertEquals(1200,track.end(437));assertEquals(new CameraExport.Range(100,300),CameraExport.events(track,100,300));
        assertEquals(new CameraExport.Range(-1,-1),CameraExport.events(track,-1,-1));
        c.eventCam=true;assertEquals(1200,track.end(437));assertEquals(new CameraExport.Range(20,1200),CameraExport.events(track,100,300));
        c.enabled=false;assertEquals(437,track.end(437));
        for(var kind:EventKind.values())if(!kind.available()) {
            c.kind=kind;c.enabled=true;c.impact=c.launch.add(95,0,0);
            assertFalse(c.active(30));assertEquals(437,track.end(437));
        }
    }
    @Test void exportCreditKeepsOriginalTextAndNeverDuplicates() {
        var lines=new ArrayList<Object>();ExportCredits.add(lines,"Exported Frames: 10/60");ExportCredits.add(lines,"Hold [ESC] to cancel");
        assertEquals(java.util.List.of("Exported Frames: 10/60","Hold [ESC] to cancel","","Flashback Actors"),lines);
        ExportCredits.add(lines,"Hold [ESC] to cancel");assertEquals(1,lines.stream().filter("Flashback Actors"::equals).count());
    }
}
