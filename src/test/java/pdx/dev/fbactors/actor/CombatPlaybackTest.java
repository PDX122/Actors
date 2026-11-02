package pdx.dev.fbactors.actor;

import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import pdx.dev.fbactors.take.ActorHit;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class CombatPlaybackTest {
    @Test void oneRecordedAttackerDoesNotSwallowAnotherAttackersLiveHit() {
        var first=java.util.UUID.randomUUID();
        var second=java.util.UUID.randomUUID();
        var victim=java.util.UUID.randomUUID();
        var hit=new ActorHit(10,4,1,0,1,first,new Vec3(.4,.4,0),(byte)0);
        assertTrue(CombatPlayback.alreadyAuthored(List.of(hit),10,first,victim));
        assertFalse(CombatPlayback.alreadyAuthored(List.of(hit),10,second,victim));
        assertFalse(CombatPlayback.alreadyAuthored(List.of(hit),11,first,victim));
    }
    @Test void mixedOldAndMeasuredHitsIntegrateAtTheCorrectBoundary() {
        ActorHit measured=new ActorHit(10,4,1,0,1,null,new Vec3(.4,.4,0),(byte)0);
        ActorHit old=ActorHit.legacy(11,2,0,1);
        ActorHit future=new ActorHit(11,3,1,0,1,null,new Vec3(.8,.4,0),(byte)0);
        List<ActorHit> hits=List.of(measured,old,future);
        assertEquals(List.of(),CombatPlayback.at(hits,10));
        assertEquals(List.of(measured,old),CombatPlayback.at(hits,11));
        assertEquals(List.of(future),CombatPlayback.at(hits,12));
        assertEquals(List.of(measured,old),CombatPlayback.at(hits,11));
    }
    @Test void damageFreeWindImpulseIsStillAnEvent() {
        ActorHit wind=new ActorHit(20,0,1,0,1,null,new Vec3(0,1.2,0),(byte)0);
        assertEquals(List.of(wind),CombatPlayback.at(List.of(wind),21));
    }
}
