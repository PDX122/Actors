package pdx.dev.fbactors.convert;
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class McprReplayPacketsTest {
    private McprReplayPackets filter() {return new McprReplayPackets(Map.of(7,"minecraft:ping"),Map.of(7,"minecraft:add_entity",8,"minecraft:move_entity_pos_rot",9,"minecraft:entity_position_sync",10,"minecraft:player_chat"));}
    private byte[] spawn() throws IOException {
        var b=new ByteArrayOutputStream();var out=new DataOutputStream(b);
        McprReplayPackets.writeVarInt(out,7);McprReplayPackets.writeVarInt(out,301);out.writeLong(0);out.writeLong(0);McprReplayPackets.writeVarInt(out,1);
        out.writeDouble(-12.25);out.writeDouble(64);out.writeDouble(8.125);out.writeByte(0);out.writeByte(0);return b.toByteArray();
    }
    @Test void networkPingDoesNotDropSameNumericPlayPacket() throws Exception {
        var f=filter();assertNull(f.packet(true,new byte[]{7,0,0,0,1}));byte[] spawn=spawn();assertArrayEquals(spawn,f.packet(false,spawn));
    }
    @Test void relativeMovementBecomesAbsoluteWithoutLosingTickPositionOrRotation() throws Exception {
        var f=filter();f.packet(false,spawn());var b=new ByteArrayOutputStream();var out=new DataOutputStream(b);
        McprReplayPackets.writeVarInt(out,8);McprReplayPackets.writeVarInt(out,301);out.writeShort(2048);out.writeShort(0);out.writeShort(-4096);out.writeByte(64);out.writeByte(-32);out.writeBoolean(true);
        var in=new DataInputStream(new ByteArrayInputStream(f.packet(false,b.toByteArray())));
        assertEquals(9,McprReplayPackets.readVarInt(in));assertEquals(301,McprReplayPackets.readVarInt(in));
        assertEquals(-11.75,in.readDouble());assertEquals(64,in.readDouble());assertEquals(7.125,in.readDouble());in.skipNBytes(24);
        assertEquals(90,in.readFloat());assertEquals(-45,in.readFloat());assertTrue(in.readBoolean());
    }
    @Test void unsupportedOrMalformedPacketStopsConversionBeforeWriting() {
        assertThrows(IllegalArgumentException.class,()->filter().packet(false,new byte[]{10}));
        assertThrows(IllegalArgumentException.class,()->filter().packet(false,new byte[]{7}));
        assertThrows(IllegalArgumentException.class,()->filter().packet(false,new byte[]{99}));
    }
    @Test void restoresLocalPlayerProfileAndInitialCameraAnchorWithoutSessionTrailer() throws Exception {
        var f=new McprReplayPackets(Map.of(),Map.of(11,"minecraft:player_position",9,"minecraft:entity_position_sync"));
        var b=new ByteArrayOutputStream();var out=new DataOutputStream(b);
        out.writeByte(2);out.writeLong(123);out.writeLong(456);out.writeByte(2);out.writeBytes("QA");out.writeByte(0);out.writeLong(999);out.writeLong(999);
        f.loginProfile(b.toByteArray());f.gameLogin(new byte[]{40,0,0,1,45});
        b.reset();out.writeByte(11);out.writeByte(1);out.writeDouble(-22.5);out.writeDouble(72);out.writeDouble(9);
        out.writeDouble(0);out.writeDouble(0);out.writeDouble(0);out.writeFloat(90);out.writeFloat(15);out.writeInt(0);
        assertNull(f.packet(false,b.toByteArray()));
        var in=new DataInputStream(new ByteArrayInputStream(f.localPlayer()));
        assertEquals(123,in.readLong());assertEquals(456,in.readLong());assertEquals(-22.5,in.readDouble());assertEquals(72,in.readDouble());assertEquals(9,in.readDouble());
        assertEquals(15,in.readFloat());assertEquals(90,in.readFloat());assertEquals(90,in.readFloat());in.skipNBytes(24);
        assertEquals(123,in.readLong());assertEquals(456,in.readLong());assertEquals(2,in.readByte());assertEquals('Q',in.readByte());assertEquals('A',in.readByte());
        assertEquals(0,in.readByte());assertEquals(0,in.readByte());assertEquals(-1,in.read());
    }
}
