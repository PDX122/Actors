package pdx.dev.fbactors.actor;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class ActorEntityIdentityTest {
    @Test void preservesSignedUuidWordsUsedByRideEvents() {
        for (UUID id : new UUID[] {new UUID(0, 0), new UUID(-1, -1),
            new UUID(Long.MIN_VALUE, Long.MAX_VALUE), UUID.randomUUID()}) {
            assertEquals(id, ActorEntityIdentity.read(ActorEntityIdentity.write(id)));
        }
    }

    @Test void readsUuidInsideFullVanillaEntitySnapshot() {
        UUID id = new UUID(0x80000000ffffffffL, 0x7fffffff00000001L);
        assertEquals(id, ActorEntityIdentity.read("{Motion:[0.0d,0.0d,0.0d], UUID: [I; -2147483648, -1, 2147483647, 1], Invulnerable:0b}"));
    }

    @Test void oldOrInvalidSnapshotsHaveNoIdentity() {
        assertNull(ActorEntityIdentity.read(null));
        assertNull(ActorEntityIdentity.read(""));
        assertNull(ActorEntityIdentity.read("{UUID:[I;999999999999,0,0,0]}"));
    }
}
