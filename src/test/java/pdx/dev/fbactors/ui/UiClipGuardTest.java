package pdx.dev.fbactors.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UiClipGuardTest {
    @Test void zeroHeightCrashCaseSkipsScissorAndDrawThenRecovers() {
        var guard=new UiClipGuard();
        assertFalse(guard.draw(6,1));
        assertFalse(guard.scissor(112,0)); assertFalse(guard.draw(6,1));
        assertTrue(guard.scissor(112,64)); assertTrue(guard.draw(6,1));
        assertFalse(guard.scissor(0,64)); assertFalse(guard.draw(6,1));
        assertFalse(guard.scissor(112,-1)); assertFalse(guard.draw(6,1));
        assertTrue(guard.scissor(1,1)); assertTrue(guard.draw(6,1));
        assertFalse(guard.draw(0,1)); assertFalse(guard.draw(6,0));
    }
}
