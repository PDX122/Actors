package pdx.dev.fbactors.filters;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ActorEffectPlanTest {
    @Test void spatialEffectsUseTheLitActorWhileGradesKeepTheirMaterials() {
        for (String id : new String[]{"ascii", "tessera", "digital_glitch", "hologram", "arcade", "halftone", "neon_wire", "pixel_sort", "codec_smear", "signal_rot", "bitcrush"}) {
            var look = new LookSettings(); look.add(id);
            assertTrue(ActorEffectPlan.usesScreen(look), id);
            assertTrue(ActorEffectPlan.material(look).filters.isEmpty(), id);
        }
        var grade = new LookSettings(); grade.add("ember"); grade.exposure = .7f;
        assertFalse(ActorEffectPlan.usesScreen(grade));
        assertEquals(grade.filters, ActorEffectPlan.material(grade).filters);
        assertEquals(.7f, ActorEffectPlan.material(grade).exposure);
    }

    @Test void mixedStacksAndGradingAreAppliedOnceInTheirOriginalOrder() {
        var look = new LookSettings(); look.add("ember"); look.add("ascii"); look.add("tessera");
        look.exposure = 1.5f; look.intensity = .4f;
        var material = ActorEffectPlan.material(look);
        assertTrue(material.enabled);
        assertTrue(material.filters.isEmpty());
        assertEquals(0, material.exposure);
        assertEquals(1, material.intensity);
        assertEquals(java.util.List.of("ember", "ascii", "tessera"), look.filters);
        assertEquals(1.5f, look.exposure);
        assertEquals(.4f, look.intensity);
        assertFalse(ActorEffectPlan.usesScreen(null));
    }
}
