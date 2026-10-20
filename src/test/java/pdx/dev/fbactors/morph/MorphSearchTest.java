package pdx.dev.fbactors.morph;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;


class MorphSearchTest {

    private static MorphRegistry registry() {
        return new MorphRegistry();
    }

    @Test
    void blankQueryReturnsCatalogueOrder() {
        MorphRegistry registry = registry();
        List<MorphDefinition> all = registry.all();
        assertEquals(all, MorphSearch.search(registry, null, null, 0, 1000));
        assertEquals(all, MorphSearch.search(registry, "   ", null, 0, 1000));
    }

    @Test
    void exactIdBeatsPrefixBeatsContains() {
        MorphRegistry registry = registry();
        List<MorphDefinition> ranked =
            MorphSearch.search(registry, "creeper", null, 0, 1000);
        assertFalse(ranked.isEmpty());
        assertEquals("creeper", ranked.get(0).id());

        List<MorphDefinition> prefixed =
            MorphSearch.search(registry, "zombie", null, 0, 1000);
        assertFalse(prefixed.isEmpty());
        assertEquals("zombie", prefixed.get(0).id());
    }

    @Test
    void categoryFilterRestricts() {
        MorphRegistry registry = registry();
        List<MorphDefinition> animals =
            MorphSearch.search(registry, null, "animal", 0, 1000);
        assertFalse(animals.isEmpty());
        for (MorphDefinition morph : animals) {
            assertEquals("animal", MorphSearch.categoryOf(morph));
        }
        assertEquals(animals.size(),
            MorphSearch.resultCount(registry, null, "animal"));
    }

    @Test
    void unknownCategoryMatchesNothing() {
        assertTrue(MorphSearch.search(registry(), "creeper", "no-such-cat", 0, 24).isEmpty());
    }

    @Test
    void pagingIsDefensive() {
        MorphRegistry registry = registry();
        assertTrue(MorphSearch.search(registry, null, null, -5, 24).size() <= 24);
        assertTrue(MorphSearch.search(registry, null, null, 10_000, 24).isEmpty());
        assertTrue(MorphSearch.pageCount(registry, null, null, 24) >= 1);
        int total = MorphSearch.resultCount(registry, null, null);
        assertEquals(registry.size(), total);
    }

    @Test
    void fuzzyFindsSlimeFromSlm() {
        List<MorphDefinition> rows = MorphSearch.search(registry(), "slm", null, 0, 1000);
        assertTrue(rows.stream().anyMatch(m -> m.id().equals("slime")));
    }

    @Test
    void knownCategoriesAreFixedTabs() {
        List<String> cats = MorphSearch.knownCategories();
        assertTrue(cats.contains("monster"));
        assertTrue(cats.contains("animal"));
        assertTrue(cats.contains("humanoid"));
        assertFalse(MorphSearch.isKnownCategory("undead"));
        assertEquals(MorphSearch.DEFAULT_CATEGORY, MorphSearch.categoryOf((String) null));
    }

    @Test
    void nullRegistrySearchesNothing() {
        assertTrue(MorphSearch.search(null, "creeper", null, 0, 24).isEmpty());
        assertEquals(0, MorphSearch.resultCount(null, "creeper", null));
    }
}

