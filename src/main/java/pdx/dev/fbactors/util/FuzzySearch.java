package pdx.dev.fbactors.util;

import java.util.List;
import java.util.Locale;


public final class FuzzySearch {


    public static final int RANK_COUNT = 8;


    public static final int RANK_ID_EXACT = 0;
    public static final int RANK_ID_PREFIX = 1;
    public static final int RANK_NAME_EXACT = 2;
    public static final int RANK_NAME_PREFIX = 3;
    public static final int RANK_ID_CONTAINS = 4;
    public static final int RANK_NAME_CONTAINS = 5;
    public static final int RANK_KIND = 6;
    public static final int RANK_FUZZY = 7;


    public static final int NO_MATCH = -1;

    private FuzzySearch() {
    }


    public static String normalizeQuery(String query) {
        return query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    }


    public static String lower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }


    public static String shortId(String id) {
        if (id == null) {
            return "";
        }
        int colon = id.indexOf(':');
        return colon < 0 ? id : id.substring(colon + 1);
    }


    public static boolean isSubsequence(String needle, String haystack) {
        if (needle.isEmpty()) {
            return true;
        }
        int found = 0;
        for (int i = 0; i < haystack.length() && found < needle.length(); i++) {
            if (haystack.charAt(i) == needle.charAt(found)) {
                found++;
            }
        }
        return found == needle.length();
    }


    public static int rank(String id, String name, String kind, String needle) {
        if (needle == null || needle.isEmpty()) {
            return RANK_ID_EXACT;
        }
        String lcId = lower(id);
        String lcName = lower(name);


        String shortId = shortId(lcId);

        if (lcId.equals(needle)) {
            return RANK_ID_EXACT;
        }
        if (lcId.startsWith(needle) || shortId.startsWith(needle)) {
            return RANK_ID_PREFIX;
        }
        if (lcName.equals(needle)) {
            return RANK_NAME_EXACT;
        }
        if (lcName.startsWith(needle)) {
            return RANK_NAME_PREFIX;
        }
        if (lcId.contains(needle) || shortId.contains(needle)) {
            return RANK_ID_CONTAINS;
        }
        if (lcName.contains(needle)) {
            return RANK_NAME_CONTAINS;
        }


        if (lower(kind).equals(needle)) {
            return RANK_KIND;
        }
        return isSubsequence(needle, lcName) || isSubsequence(needle, shortId)
            || isSubsequence(needle, lcId) ? RANK_FUZZY : NO_MATCH;
    }


    public static int key(int rank, int index) {
        return rank * RANK_COUNT + index;
    }


    public static int pageCount(int total, int pageSize) {
        if (pageSize <= 0) {
            return 1;
        }
        return Math.max(1, (total + pageSize - 1) / pageSize);
    }


    public static int clampPage(int page, int pageCount) {
        return Math.min(Math.max(0, page), Math.max(0, pageCount - 1));
    }


    public static <T> List<T> page(List<T> ranked, int page, int pageSize) {
        int size = Math.max(1, pageSize);
        int from = Math.max(0, page) * size;
        if (from >= ranked.size()) {
            return List.of();
        }
        return List.copyOf(ranked.subList(from, Math.min(ranked.size(), from + size)));
    }
}

