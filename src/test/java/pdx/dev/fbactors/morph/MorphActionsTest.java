package pdx.dev.fbactors.morph;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.UUID;

class MorphActionsTest {
    @Test void humanoidsOfferSeatedAndLocomotionActionsWithoutGivingEveryMobASitPose() {
        for(String body:List.of("zombie","husk","parched","drowned","skeleton","piglin")) {
            assertTrue(MorphPoses.choices(body).containsAll(List.of("idle","walking","running","crouching","sitting")),body);
            assertEquals("sitting",MorphPoses.normalize(body,"sitting"));
        }
        assertEquals("auto",MorphPoses.normalize("creeper","sitting"));
        assertEquals("auto",MorphPoses.normalize("wolf","relaxing"));
        assertEquals(List.of("auto"),MorphPoses.choices("custom_0123456789abcdef0123456789abcdef"));
        assertTrue(MorphPoses.speed("running")>MorphPoses.speed("walking"));
        assertEquals(0,MorphPoses.speed("idle"));
    }
    @Test void renderOnlySeatingDoesNotLeakIntoOtherActions() {
        class State implements MorphActionAccess {
            String action;
            public void fbactors$action(String value) { action=value; }
            public String fbactors$action() { return action; }
        }
        var state=new State();
        assertFalse(MorphActionAccess.seated(state));
        state.fbactors$action("sitting"); assertTrue(MorphActionAccess.seated(state));
        state.fbactors$action("walking"); assertFalse(MorphActionAccess.seated(state));
        state.fbactors$action("auto"); assertFalse(MorphActionAccess.seated(state));
        assertFalse(MorphActionAccess.seated(new Object()));
    }
    @Test void actionsTravelInDragPayloadsAndTimelineClips() {
        var registry=new MorphRegistry(); UUID actor=UUID.randomUUID();
        for(String action:List.of("idle","walking","running","crouching","sitting")) {
            var clip=MorphLibraryPresets.create("zombie/"+action,actor,40,0,500,false,registry);
            assertNotNull(clip,action); assertEquals(action,clip.pose);
            var timeline=new MorphTimeline(); timeline.clips.add(clip);
            var fallback=MorphSelection.of(actor,"cat");
            assertEquals(action,timeline.evaluate(actor,40,fallback).pose());
            assertEquals(fallback,timeline.evaluate(actor,clip.end,fallback));
        }
    }
    @Test void actionSelectionsSurviveDiskAndBundleImport(@org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        var store=new MorphStore();
        for(String action:List.of("idle","walking","running","crouching","sitting")) {
            UUID actor=UUID.randomUUID(),imported=UUID.randomUUID();
            store.set(actor,MorphSelection.of(actor,"zombie").withPose(action));
            store.saveAll(directory);
            var loaded=new MorphStore(); loaded.load(directory);
            assertEquals(action,loaded.selectionFor(actor).pose());
            assertTrue(loaded.importSelection(imported.toString(),store.exportSelection(actor)));
            assertEquals(action,loaded.selectionFor(imported).pose());
        }
    }
}
