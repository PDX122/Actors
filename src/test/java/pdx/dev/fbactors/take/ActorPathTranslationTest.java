package pdx.dev.fbactors.take;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ActorPathTranslationTest {
    @Test void draggingPreservesTheRecordedMotionAndPose() {
        var first=new ActorFrame(1,64,2,45,20,50,(byte)3,(byte)7);
        var next=new ActorFrame(2.25,64.42,2.5,50,10,55,(byte)1,(byte)5);
        var original=List.of(first,next);
        var moved=ActorPathTranslation.translate(original,3,-2,4);
        assertEquals(first.x()+3,moved.getFirst().x());
        assertEquals(first.y()-2,moved.getFirst().y());
        assertEquals(next.x()-first.x(),moved.get(1).x()-moved.getFirst().x());
        assertEquals(next.y()-first.y(),moved.get(1).y()-moved.getFirst().y(),1e-10);
        assertEquals(next.flags(),moved.get(1).flags());
        assertEquals(next.yHeadRot(),moved.get(1).yHeadRot());
        assertEquals(original,ActorPathTranslation.translate(moved,-3,2,-4));
    }
    @Test void invalidDragCannotCorruptTheTake() {
        assertThrows(IllegalArgumentException.class,()->ActorPathTranslation.translate(List.of(),Double.NaN,0,0));
    }
}
