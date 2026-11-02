package pdx.dev.fbactors.morph;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;


class MorphCompatibilityTest {

    @Test
    void playerSizedMorphsNeedNothing() {
        MorphDefinition zombie = Morph.registry().byId("zombie");
        assertEquals(MorphCompatibility.Requirements.NONE,
            MorphCompatibility.requirementsFor(zombie));
        assertTrue(MorphCompatibility.canApply(zombie, true));
        assertFalse(MorphCompatibility.canApply(zombie, false));
    }

    @Test
    void nullIsTreatedAsNeedingEverything() {
        assertEquals(MorphCompatibility.Requirements.RENDERER_ACCESSOR,
            MorphCompatibility.requirementsFor(null));
        assertFalse(MorphCompatibility.canApply(null, true));
    }

    @Test
    void availableMatchesCanApplyOverTheCatalogue() {
        MorphRegistry registry = Morph.registry();
        List<MorphDefinition> available = MorphCompatibility.available(registry);
        for (MorphDefinition morph : registry.all()) {
            assertEquals(MorphCompatibility.canApply(morph, true), available.contains(morph));
        }
        assertEquals(registry.all(), MorphCompatibility.available(registry));
    }

    @Test
    void nullRegistryAvailableIsEmpty() {
        assertTrue(MorphCompatibility.available(null).isEmpty());
    }

    @Test
    void allRequirementsListsBoth() {
        assertTrue(MorphCompatibility.allRequirements()
            .contains(MorphCompatibility.Requirements.NONE));
        assertTrue(MorphCompatibility.allRequirements()
            .contains(MorphCompatibility.Requirements.RENDERER_ACCESSOR));
    }

    @Test
    void reportIsSilentWhenNothingMissing() {
        assertFalse(MorphCompatibility.report(Morph.registry().byId("skeleton")));
    }
}

