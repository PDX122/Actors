package pdx.dev.fbactors.gear;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import pdx.dev.fbactors.util.FuzzySearch;


public final class GearSearch {

    private GearSearch() {
    }


    public record Entry(String id, String name, boolean armour) {

        public Entry {
            id = id == null ? "" : id.trim();
            name = name == null || name.isBlank() ? id : name.trim();
        }


        public String shortId() {
            return FuzzySearch.shortId(this.id);
        }
    }


    public record Page(List<Entry> entries, int page, int pageCount, int total) {

        public Page {
            entries = List.copyOf(entries);
        }


        public boolean hasMore() {
            return this.page + 1 < this.pageCount;
        }


        public boolean hasPrevious() {
            return this.page > 0;
        }


        static Page empty() {
            return new Page(List.of(), 0, 1, 0);
        }
    }


    public static final class Index {


        private final List<Entry>[] byRank;

        private final int total;
        private final int armourCount;

        @SuppressWarnings("unchecked")
        private Index(List<Entry> unique) {
            this.byRank = (List<Entry>[]) new List<?>[FuzzySearch.RANK_COUNT];
            for (int i = 0; i < FuzzySearch.RANK_COUNT; i++) {
                this.byRank[i] = new ArrayList<>();
            }

            int armour = 0;
            for (Entry entry : unique) {


                this.byRank[FuzzySearch.RANK_ID_EXACT].add(entry);
                if (entry.armour()) {
                    armour++;
                }
            }


            for (List<Entry> bucket : this.byRank) {
                bucket.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
            }
            this.total = unique.size();
            this.armourCount = armour;
        }


        public int size() {
            return this.total;
        }


        public int armourCount() {
            return this.armourCount;
        }


        public static Index empty() {
            return new Index(List.of());
        }


        public Page search(String query, boolean armourOnly, int page, int pageSize) {
            String needle = FuzzySearch.normalizeQuery(query);
            boolean filtering = !needle.isEmpty();
            boolean paging = pageSize > 0;


            int firstIndex = paging ? Math.max(0, page) * pageSize : 0;
            int want = paging ? pageSize : Integer.MAX_VALUE;

            List<Entry> collected = new ArrayList<>();
            int matched = 0;

            if (!filtering) {


                for (Entry entry : this.byRank[FuzzySearch.RANK_ID_EXACT]) {
                    if (armourOnly && !entry.armour()) {
                        continue;
                    }
                    if (matched >= firstIndex && collected.size() < want) {
                        collected.add(entry);
                    }
                    matched++;
                }
            } else {


                for (int rank = 0; rank < FuzzySearch.RANK_COUNT; rank++) {
                    for (Entry entry : this.byRank[rank]) {
                        if (armourOnly && !entry.armour()) {
                            continue;
                        }
                        if (FuzzySearch.rank(entry.id(), entry.name(), null, needle) != rank) {
                            continue;
                        }
                        if (matched >= firstIndex && collected.size() < want) {
                            collected.add(entry);
                        }
                        matched++;
                    }
                }
            }

            if (matched == 0) {
                return Page.empty();
            }
            int pageCount = FuzzySearch.pageCount(matched, paging ? pageSize : 0);
            int safePage = paging ? FuzzySearch.clampPage(page, pageCount) : 0;
            return new Page(collected, safePage, pageCount, matched);
        }


        public static Index of(List<Entry> entries) {
            if (entries == null || entries.isEmpty()) {
                return empty();
            }
            Map<String, Entry> unique = new LinkedHashMap<>();
            for (Entry entry : entries) {
                if (entry != null && !entry.id().isEmpty()) {
                    unique.put(entry.id(), entry);
                }
            }
            return new Index(new ArrayList<>(unique.values()));
        }
    }
}

