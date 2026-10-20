package pdx.dev.fbactors.morph;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import pdx.dev.fbactors.util.FuzzySearch;


public final class MorphSearch {


    public static final int NO_MATCH = FuzzySearch.NO_MATCH;
    public static final int RANK_ID_EXACT = FuzzySearch.RANK_ID_EXACT;
    public static final int RANK_ID_PREFIX = FuzzySearch.RANK_ID_PREFIX;
    public static final int RANK_NAME_PREFIX = FuzzySearch.RANK_NAME_PREFIX;
    public static final int RANK_NAME_EXACT = FuzzySearch.RANK_NAME_EXACT;
    public static final int RANK_ID_CONTAINS = FuzzySearch.RANK_ID_CONTAINS;
    public static final int RANK_NAME_CONTAINS = FuzzySearch.RANK_NAME_CONTAINS;
    public static final int RANK_KIND = FuzzySearch.RANK_KIND;
    public static final int RANK_FUZZY = FuzzySearch.RANK_FUZZY;


    public static final int PAGE_SIZE = 24;


    public static final String DEFAULT_CATEGORY = "monster";


    private static final String[] KNOWN_CATEGORIES = {
        "humanoid", "animal", "monster", "projectile"};

    private MorphSearch() {
    }


    public static List<MorphDefinition> search(@Nullable MorphRegistry registry,
        @Nullable String query) {
        return search(registry, query, null, 0, PAGE_SIZE);
    }


    public static List<MorphDefinition> search(@Nullable MorphRegistry registry,
        @Nullable String query, @Nullable String category, int page, int pageSize) {
        return FuzzySearch.page(rank(registry, query, category), page, pageSize);
    }


    public static int pageCount(@Nullable MorphRegistry registry, @Nullable String query,
        @Nullable String category, int pageSize) {
        return FuzzySearch.pageCount(rank(registry, query, category).size(), pageSize);
    }


    public static int resultCount(@Nullable MorphRegistry registry, @Nullable String query,
        @Nullable String category) {
        return rank(registry, query, category).size();
    }

    private static List<MorphDefinition> rank(@Nullable MorphRegistry registry,
        @Nullable String query, @Nullable String category) {
        if (registry == null) {
            return List.of();
        }
        String needle = FuzzySearch.normalizeQuery(query);
        String wanted = category == null || category.isBlank()
            ? null : category.trim().toLowerCase(Locale.ROOT);

        List<MorphDefinition> all = registry.all();
        if (needle.isEmpty() && wanted == null) {


            return all;
        }


        List<int[]> keys = new ArrayList<>(all.size());
        List<MorphDefinition> matched = new ArrayList<>(all.size());
        for (int index = 0; index < all.size(); index++) {
            MorphDefinition morph = all.get(index);
            if (wanted != null && !wanted.equals(categoryOf(morph))) {
                continue;
            }
            if (!needle.isEmpty()) {
                int rank = FuzzySearch.rank(morph.id(), morph.displayName(), morph.kind(), needle);
                if (rank == NO_MATCH) {
                    continue;
                }
                keys.add(new int[] {rank, index});
            } else {
                keys.add(new int[] {RANK_ID_EXACT, index});
            }
            matched.add(morph);
        }


        List<Ranked> ranked = new ArrayList<>(matched.size());
        for (int i = 0; i < matched.size(); i++) {
            ranked.add(new Ranked(matched.get(i), keys.get(i)[0], keys.get(i)[1]));
        }
        ranked.sort(Comparator.comparingInt(Ranked::key));

        List<MorphDefinition> result = new ArrayList<>(ranked.size());
        for (Ranked entry : ranked) {
            result.add(entry.morph());
        }
        return result;
    }


    private record Ranked(MorphDefinition morph, int rank, int index) {

        int key() {
            return FuzzySearch.key(this.rank, this.index);
        }
    }

    public static String categoryOf(@Nullable MorphDefinition morph) {
        if (morph == null) {
            return DEFAULT_CATEGORY;
        }
        return categoryOf(morph.kind());
    }


    public static String categoryOf(@Nullable String kind) {
        if (kind == null) {
            return DEFAULT_CATEGORY;
        }
        String trimmed = kind.trim().toLowerCase(Locale.ROOT);
        return trimmed.isEmpty() ? DEFAULT_CATEGORY : trimmed;
    }


    public static boolean isKnownCategory(@Nullable String kind) {
        if (kind == null) {
            return false;
        }
        String want = kind.trim().toLowerCase(Locale.ROOT);
        for (String known : KNOWN_CATEGORIES) {
            if (known.equals(want)) {
                return true;
            }
        }
        return false;
    }


    public static List<String> knownCategories() {
        return List.of(KNOWN_CATEGORIES);
    }
}

