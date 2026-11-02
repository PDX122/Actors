package pdx.dev.fbactors.morph;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.UUID;

class MorphPoseSelectionTest {
    @Test void onlySupportedNativePosesAreOfferedAndSaved() {
        assertTrue(MorphPoses.choices("cat").containsAll(List.of("auto","standing","sitting","relaxing","idle","walking","running")));
        assertEquals(MorphPoses.choices("cat"),MorphPoses.choices("minecraft:cat"));
        for(String body:List.of("wolf","dog","parrot")) {
            assertTrue(MorphPoses.choices(body).contains("standing"));
            assertEquals("auto",MorphPoses.normalize(body,"relaxing"));
        }
        assertTrue(MorphPoses.choices("creeper").contains("walking"));
        assertFalse(MorphPoses.choices("creeper").contains("sitting"));
    }
    @Test void forcedStandingAndRelaxingOverrideRecordedCrouching() {
        assertEquals("standing",MorphPoses.resolve("standing",true,true));
        assertEquals("relaxing",MorphPoses.resolve("relaxing",true,true));
        assertEquals("sitting",MorphPoses.resolve("auto",true,true));
        assertEquals("auto",MorphPoses.resolve("auto",true,false));
    }
    @Test void previewUsesChosenPoseAndRetainsNativePoseDemosInAuto() {
        assertEquals("relaxing",MorphPoses.preview("cat","relaxing","Sitting"));
        assertEquals("standing",MorphPoses.preview("cat","standing","Sitting"));
        assertEquals("sitting",MorphPoses.preview("cat","auto","Sitting"));
        assertEquals("standing",MorphPoses.preview("cat","auto","Standing"));
        assertEquals("auto",MorphPoses.preview("creeper","auto","Sitting"));
    }
    @Test void timelinePoseChangesRestoreTheSavedDefaultAfterScrubbing() {
        UUID actor=UUID.randomUUID();
        var base=MorphSelection.of(actor,"cat").withPose("standing");
        var timeline=new MorphTimeline(); var clip=new MorphTimeline.Clip();
        clip.actor=actor; clip.start=40; clip.end=60; clip.morph="cat"; clip.pose="relaxing";
        timeline.clips.add(clip);
        assertEquals("standing",timeline.evaluate(actor,39.9,base).pose());
        assertEquals("relaxing",timeline.evaluate(actor,40,base).pose());
        assertEquals("relaxing",timeline.evaluate(actor,59.99,base).pose());
        assertEquals("standing",timeline.evaluate(actor,60,base).pose());
        assertEquals("relaxing",timeline.evaluate(actor,45,base).pose());
    }
}
