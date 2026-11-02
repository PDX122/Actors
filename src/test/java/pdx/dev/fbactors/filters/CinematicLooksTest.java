package pdx.dev.fbactors.filters;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

class CinematicLooksTest {
    private static final List<String> IDS=List.of("cinema_35mm","noir","desert_chrome","moonlight");
    private LookSettings look(String id) { var result=new LookSettings(); assertTrue(result.add(id)); return result; }
    @Test void gradesPreserveAlphaHaveDistinctResultsAndSupportZeroStrength() {
        int[] source={0x00c88c50,0x80a0c0e0,0xff804020,0xffdddddd};
        var unique=new java.util.HashSet<Integer>();
        for(String id:IDS) {
            var settings=look(id);
            var output=LookCpuBake.bakePixels(source,4,1,settings,2);
            assertNotNull(output,id); assertTrue(unique.add(java.util.Arrays.hashCode(output)),id);
            for(int i=0;i<source.length;i++) assertEquals(source[i]>>>24,output[i]>>>24,id);
            var amount=FilterCatalog.params(id).stream().filter(p -> p.id().equals("Amount")).findFirst().orElseThrow();
            settings.parameter(id,amount,0);
            var neutral=LookCpuBake.bakePixels(source,4,1,settings,2);
            for(int i=0;i<source.length;i++) for(int shift:new int[]{0,8,16})
                assertEquals((source[i]>>>shift)&255,(neutral[i]>>>shift)&255,1,id);
        }
    }
    @Test void noirIsMonochromeAndMoonlightKeepsWarmPracticalLights() {
        var noir=LookCpuBake.bakePixels(new int[]{0xff2080d0},1,1,look("noir"),0);
        assertEquals(noir[0]&255,(noir[0]>>>8)&255); assertEquals(noir[0]&255,(noir[0]>>>16)&255);
        var moon=LookCpuBake.bakePixels(new int[]{0xffb0b0b0,0xff3070f0},2,1,look("moonlight"),0);
        assertTrue(((moon[0]>>>16)&255)>(moon[0]&255),"Neutral light becomes blue");
        assertTrue((moon[1]&255)>((moon[1]>>>16)&255),"Warm light remains warm");
    }
    @Test void filmGrainIsDeterministicAtAReplayTime() {
        int[] source=new int[1024]; java.util.Arrays.fill(source,0xff808080);
        var settings=look("cinema_35mm");
        var first=LookCpuBake.bakePixels(source,32,32,settings,2);
        assertArrayEquals(first,LookCpuBake.bakePixels(source,32,32,settings,2));
        assertFalse(java.util.Arrays.equals(first,LookCpuBake.bakePixels(source,32,32,settings,2.1f)));
    }
}
