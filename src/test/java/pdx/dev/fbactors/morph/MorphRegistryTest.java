package pdx.dev.fbactors.morph;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;


class MorphRegistryTest {

    @Test
    void builtInsAreUniqueWellFormedData() {
        List<MorphDefinition> all = MorphRegistry.builtIns();
        assertTrue(all.size() >= 60);
        Set<String> ids = new HashSet<>();
        for (MorphDefinition morph : all) {
            assertNotNull(morph.id());
            assertFalse(morph.id().isBlank());
            assertTrue(ids.add(morph.id()), "duplicate morph id " + morph.id());
            assertNotNull(morph.displayName());
            assertFalse(morph.displayName().isBlank());
            assertNotNull(morph.kind());
            assertNotNull(morph.stance());
            assertTrue(Float.isFinite(morph.scale()) && morph.scale() > 0.0f);
            assertTrue(Double.isFinite(morph.hitboxWidth()) && morph.hitboxWidth() > 0.0);
            assertTrue(Float.isFinite(morph.hitboxHeight()) && morph.hitboxHeight() > 0.0f);
            assertTrue(Double.isFinite(morph.eyeHeight()));
        }
    }

    @Test
    void loadBearingCatalogueValues() {
        MorphRegistry registry = new MorphRegistry();
        assertEquals(MorphStance.HUMANOID, registry.byId("zombie").stance());
        assertEquals(1.08f, registry.byId("zombie").scale());
        assertEquals(MorphStance.CRAWLING, registry.byId("creeper").stance());
        assertEquals(0.94f, registry.byId("creeper").scale());
        assertEquals(MorphStance.FLYING, registry.byId("bat").stance());
        assertTrue(registry.byId("bat").elytraCapable());
        assertEquals(MorphStance.QUADRUPED, registry.byId("chicken").stance());
        assertTrue(registry.byId("chicken").quadruped());
        assertEquals(0.39f, registry.byId("chicken").scale());
        assertEquals(MorphStance.UPRIGHT_TALL, registry.byId("enderman").stance());
        assertEquals(1.61f, registry.byId("enderman").scale());
        assertEquals(MorphStance.UPRIGHT_TALL, registry.byId("warden").stance());
        assertEquals(2.9f, registry.byId("warden").hitboxHeight());
        assertEquals(MorphStance.FLYING, registry.byId("elder_guardian").stance());
        assertEquals(2.97f, registry.byId("elder_guardian").hitboxHeight());
        assertEquals("warden", MorphRegistry.entityIdFor("warden"));
        assertEquals("happy_ghast", MorphRegistry.entityIdFor("happy_ghast"));
        assertEquals("parched", MorphRegistry.entityIdFor("parched"));
        assertNull(registry.byId("no-such-mob"));
        assertNull(registry.byId(null));
    }

    @Test
    void scaleMatchesStoredHeight() {


        for (MorphDefinition morph : MorphRegistry.builtIns()) {
            float drawn = morph.scale() * 1.8f;
            assertEquals(morph.hitboxHeight(), drawn, 0.05f,
                "morph " + morph.id() + " draws " + drawn + " blocks tall");
        }
    }

    @Test
    void noBossCategory() {
        for (MorphDefinition morph : MorphRegistry.builtIns()) {
            assertNotEquals("boss", morph.kind(), "boss morph " + morph.id());
        }
        assertFalse(MorphSearch.knownCategories().contains("boss"));
        assertFalse(MorphSearch.isKnownCategory("boss"));
    }

    @Test
    void entityAliasesRenameOnly() {
        assertEquals("snow_golem", MorphRegistry.entityIdFor("snowman"));
        assertEquals("wolf", MorphRegistry.entityIdFor("dog"));
        assertEquals("silverfish", MorphRegistry.entityIdFor("snake"));
        assertEquals("creeper", MorphRegistry.entityIdFor("creeper"));
        assertNull(MorphRegistry.entityIdFor(null));
    }

    @Test
    void userPackShadowsBuiltInsAndResetRestores(@TempDir Path dir) throws Exception {
        MorphRegistry registry = new MorphRegistry();
        int builtIns = registry.size();
        Files.writeString(dir.resolve("creeper.json"),
            "{\"version\":1,\"id\":\"creeper\",\"displayName\":\"Mine\",\"kind\":\"monster\","
                + "\"scale\":2.0,\"hitboxWidth\":0.6,\"hitboxHeight\":1.7,\"eyeHeight\":1.4,"
                + "\"rideOffset\":0.0,\"stance\":\"CRAWLING\",\"elytraCapable\":false,"
                + "\"swimmer\":false,\"quadruped\":false}",
            StandardCharsets.UTF_8);
        assertEquals(1, registry.load(dir));
        assertEquals(builtIns, registry.size());
        assertEquals("Mine", registry.byId("creeper").displayName());
        assertEquals(2.0f, registry.byId("creeper").scale());
        registry.reset();
        assertEquals("Creeper", registry.byId("creeper").displayName());
    }

    @Test
    void storeFileIsSkippedNotWarnedAbout(@TempDir Path dir) throws Exception {
        MorphRegistry registry = new MorphRegistry();
        Files.writeString(dir.resolve("selections.json"),
            "{\"version\":1,\"actors\":[]}", StandardCharsets.UTF_8);
        assertEquals(0, registry.load(dir));
        assertEquals(MorphRegistry.builtIns().size(), registry.size());
    }

    @Test
    void brokenFilesCostOneMorphNotTheFolder(@TempDir Path dir) throws Exception {
        MorphRegistry registry = new MorphRegistry();
        Files.writeString(dir.resolve("a-broken.json"),
            "{\"version\":1,\"id\":\"broken\",\"stance\":\"NO_SUCH_STANCE\"}",
            StandardCharsets.UTF_8);
        Files.writeString(dir.resolve("z-good.json"),
            "{\"version\":1,\"id\":\"good\",\"displayName\":\"Good\",\"kind\":\"monster\","
                + "\"scale\":1.0,\"hitboxWidth\":0.6,\"hitboxHeight\":1.8,\"eyeHeight\":1.62,"
                + "\"rideOffset\":0.0,\"stance\":\"HUMANOID\",\"elytraCapable\":false,"
                + "\"swimmer\":false,\"quadruped\":false}",
            StandardCharsets.UTF_8);
        assertEquals(1, registry.load(dir));
        assertNotNull(registry.byId("good"));
        assertNull(registry.byId("broken"));
    }

    @Test
    void missingDirectoryIsNotAnError(@TempDir Path dir) {
        assertEquals(0, new MorphRegistry().load(dir.resolve("does-not-exist")));
    }
}

