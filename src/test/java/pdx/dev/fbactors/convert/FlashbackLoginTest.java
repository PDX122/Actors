package pdx.dev.fbactors.convert;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FlashbackLoginTest {
    @Test void replacesFixedWidthEntityIdWithoutShiftingTheLoginFields() {
        byte[] packet={0x2b,0,0,0,7,1,2,3,4};
        byte[] result=FlashbackLogin.withPlayerId(packet,2_000_000_000);
        assertArrayEquals(new byte[]{0x2b,0x77,0x35,(byte)0x94,0,1,2,3,4},result);
        assertEquals(packet.length,result.length);
        assertEquals(7,packet[4]);
    }
    @Test void supportsMultiBytePacketIds() {
        byte[] packet={(byte)0x81,1,0,0,0,7,42};
        assertEquals(129,FlashbackLogin.packetId(packet));
        assertArrayEquals(new byte[]{(byte)0x81,1,0,0,0,9,42},FlashbackLogin.withPlayerId(packet,9));
    }
    @Test void rejectsTruncatedAndUnboundedHeaders() {
        assertThrows(IllegalArgumentException.class,()->FlashbackLogin.packetId(new byte[0]));
        assertThrows(IllegalArgumentException.class,()->FlashbackLogin.packetId(new byte[]{(byte)128,(byte)128,(byte)128,(byte)128,(byte)128}));
        assertThrows(IllegalArgumentException.class,()->FlashbackLogin.withPlayerId(new byte[]{1,0,0},9));
    }
}
