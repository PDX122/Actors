package pdx.dev.fbactors.input;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EscapeRunPolicyTest {
    @Test void escapeDuringFootageDoesNotFinishTheRun() {
        assertFalse(EscapeRunPolicy.mayFinish(true,false,0,600));
        assertFalse(EscapeRunPolicy.mayFinish(true,false,300,600));
        assertFalse(EscapeRunPolicy.mayFinish(true,false,599,600));
    }
    @Test void escapeAtTheEndFinishesARecording() {
        assertTrue(EscapeRunPolicy.mayFinish(true,false,600,600));
        assertTrue(EscapeRunPolicy.mayFinish(true,false,601,600));
    }
    @Test void inventoryAndOtherScreensKeepEscapeEvenAtTheEnd() {
        assertFalse(EscapeRunPolicy.mayFinish(true,true,600,600));
        assertFalse(EscapeRunPolicy.mayFinish(true,true,300,600));
    }
    @Test void setupPrepCountdownAndOrdinaryReplayAreNotRecording() {
        assertFalse(EscapeRunPolicy.mayFinish(false,false,600,600));
    }
    @Test void absentOrEmptyFootageIsNotAFinishedRecording() {
        assertFalse(EscapeRunPolicy.mayFinish(true,false,0,0));
        assertFalse(EscapeRunPolicy.mayFinish(true,false,-1,600));
    }
}
