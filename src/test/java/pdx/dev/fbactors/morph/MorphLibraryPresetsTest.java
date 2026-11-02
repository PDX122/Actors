package pdx.dev.fbactors.morph;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;

class MorphLibraryPresetsTest {
    private final MorphRegistry registry=new MorphRegistry();
    @Test void everyCardRoundTripsAndUnsupportedPayloadsAreRejected() {
        for(String body:registry.ids()) for(var preset:MorphLibraryPresets.entries(body,registry)) {
            assertEquals(preset,MorphLibraryPresets.decode(preset.payload(),registry));
            assertTrue(MorphPoses.choices(preset.body()).contains(preset.pose()));
        }
        for(String bad:new String[]{"cat/unknown","wolf/relaxing","creeper/sitting","cat/sitting/extra","","x".repeat(100)})
            assertNull(MorphLibraryPresets.decode(bad,registry),bad);
        assertNull(MorphLibraryPresets.decode(null,registry));
    }
    @Test void dropUsesTheReceivingActorAndThePointerTime() {
        UUID receiving=UUID.randomUUID();
        var clip=MorphLibraryPresets.create("cat/relaxing",receiving,240,200,800,false,registry);
        assertNotNull(clip); assertEquals(receiving,clip.actor);
        assertEquals("cat",clip.morph); assertEquals("relaxing",clip.pose);
        assertEquals(240,clip.start); assertEquals(340,clip.end);
        var timeline=new MorphTimeline(); timeline.clips.add(clip);
        assertNull(timeline.active(receiving,239.99));
        assertEquals("relaxing",timeline.evaluate(receiving,240,null).pose());
        assertNull(timeline.active(receiving,340));
    }
    @Test void dropsStayInsideTrimmedTakesAndCannotOverflow() {
        UUID actor=UUID.randomUUID();
        var early=MorphLibraryPresets.create("cat/sitting",actor,-50,100,400,false,registry);
        assertEquals(100,early.start); assertEquals(200,early.end);
        var late=MorphLibraryPresets.create("parrot/standing",actor,395,100,400,false,registry);
        assertEquals(395,late.start); assertEquals(400,late.end);
        var overflow=MorphLibraryPresets.create("cat/auto",actor,Integer.MAX_VALUE,0,100_000_000,false,registry);
        assertEquals(99_999_999,overflow.start); assertEquals(100_000_000,overflow.end);
        assertNull(MorphLibraryPresets.create("cat/sitting",actor,0,400,400,false,registry));
    }
    @Test void lockedAndMissingActorsCannotReceivePoseClips() {
        assertNull(MorphLibraryPresets.create("cat/sitting",UUID.randomUUID(),10,0,100,true,registry));
        assertNull(MorphLibraryPresets.create("cat/sitting",null,10,0,100,false,registry));
    }
    @Test void libraryAcceptsEveryRegisteredBodyIncludingCustomAndModdedIds() {
        registry.register(MorphDefinition.of("custom_0123456789abcdef0123456789abcdef","Imported creature"));
        registry.register(MorphDefinition.of("example:cave/beast","Cave beast"));
        for(String id:new String[]{"zombie","creeper","custom_0123456789abcdef0123456789abcdef","example:cave/beast"}) {
            var preset=new MorphLibraryPresets.Preset(id,"auto");
            assertEquals(preset,MorphLibraryPresets.decode(preset.payload(),registry));
            var clip=MorphLibraryPresets.create(preset.payload(),UUID.randomUUID(),0,0,500,false,registry);
            assertNotNull(clip); assertEquals(id,clip.morph);
            assertEquals("auto",clip.pose);
        }
        assertNull(MorphLibraryPresets.decode("missing_mod:creature/auto",registry));
    }
    @Test void aPlayerCardRestoresTheOriginalPlayerBodyForItsClip() {
        registry.register(MorphDefinition.player());
        UUID actor=UUID.randomUUID();
        var clip=MorphLibraryPresets.create("player/auto",actor,20,0,500,false,registry);
        var timeline=new MorphTimeline(); timeline.clips.add(clip);
        var fallback=MorphSelection.of(actor,"cat");
        assertFalse(timeline.evaluate(actor,20,fallback).isMorphed());
        assertEquals(fallback,timeline.evaluate(actor,clip.end,fallback));
    }
}
