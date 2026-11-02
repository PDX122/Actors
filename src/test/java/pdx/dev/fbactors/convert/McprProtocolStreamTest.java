package pdx.dev.fbactors.convert;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import com.google.gson.JsonObject;

class McprProtocolStreamTest {
    private static final int[] IDS={2,3,40,60};
    private McprReader.Recording source(byte[]... packets) {
        var ticks=new TreeMap<Integer,List<byte[]>>(); ticks.put(0,List.of(packets));
        return new McprReader.Recording(new JsonObject(),50,"26.2","QA",ticks,1);
    }
    @Test void separatesConfigurationFromPlayWithoutBorrowingAnotherWorldsLogin() {
        byte[] registry={7,11,12}, login={40,0,0,0,19}, play={7,88};
        var prepared=McprProtocolStream.prepare(source(new byte[]{2},registry,new byte[]{3},login,play),IDS);
        assertArrayEquals(login,prepared.login());
        assertArrayEquals(registry,prepared.configuration().getFirst());
        assertEquals(1,prepared.ticks().get(0).size());
        assertFalse(prepared.ticks().get(0).getFirst().configuration());
        assertArrayEquals(play,prepared.ticks().get(0).getFirst().payload());
    }
    @Test void tracksReconfigurationAndConsumesProtocolSwitchMarkers() {
        var prepared=McprProtocolStream.prepare(source(new byte[]{2},new byte[]{3},new byte[]{40},
            new byte[]{60},new byte[]{7,9},new byte[]{3},new byte[]{7,8}),IDS);
        var actions=prepared.ticks().get(0);
        assertEquals(2,actions.size()); assertTrue(actions.get(0).configuration()); assertFalse(actions.get(1).configuration());
    }
    @Test void rejectsTruncatedNegotiationAndMissingJoin() {
        assertThrows(IllegalArgumentException.class,()->McprProtocolStream.prepare(source(new byte[]{2},new byte[]{7}),IDS));
        assertThrows(IllegalArgumentException.class,()->McprProtocolStream.prepare(source(new byte[]{2},new byte[]{3},new byte[]{8}),IDS));
    }
    @Test void preservesTickBoundariesAfterInitialConfiguration() {
        var recording=source(new byte[]{2},new byte[]{3},new byte[]{40});
        recording.ticks().put(17,List.of(new byte[]{7,42}));
        var prepared=McprProtocolStream.prepare(recording,IDS);
        assertTrue(prepared.ticks().containsKey(17)); assertFalse(prepared.ticks().containsKey(0));
    }
}
