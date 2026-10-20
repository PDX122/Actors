package pdx.dev.fbactors.skin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.Nullable;
import pdx.dev.fbactors.FlashbackActors;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;


public final class SkinHistory {


    public record HistoryEntry(String skinName, String url, long timestamp) {


        public static final Comparator<HistoryEntry> NEWEST_FIRST =
            Comparator.comparingLong(HistoryEntry::timestamp).reversed();
    }


    public record History(String profileId, String profileName, List<HistoryEntry> entries) {


        public History {
            entries = entries == null ? List.of() : List.copyOf(entries);
        }


        public static History empty(String profileId, String profileName) {
            return new History(profileId, profileName, List.of());
        }


        public boolean isEmpty() {
            return this.entries.isEmpty();
        }

        public int size() {
            return this.entries.size();
        }


        @Nullable
        public HistoryEntry newest() {
            return this.entries.isEmpty() ? null : this.entries.get(0);
        }
    }

    @FunctionalInterface
    public interface Fetcher {


        History fetch(SkinId id) throws Exception;
    }


    private static final Fetcher UNAVAILABLE = id ->
        unavailable(id.value() + " (no history provider is installed)");


    private static volatile Fetcher fetcher = UNAVAILABLE;

    private SkinHistory() {
    }


    public static void setFetcher(@Nullable Fetcher newFetcher) {
        fetcher = newFetcher == null ? UNAVAILABLE : newFetcher;
    }


    public static Fetcher fetcher() {
        return fetcher;
    }

    public static History unavailable(String reason) {
        return new History(null, reason == null ? "unavailable" : reason, List.of());
    }


    public static History fetch(@Nullable SkinId id) {
        if (id == null) {
            return unavailable("no account was given");
        }
        if (id.isBuiltin() || id.isUsername()) {


            return unavailable(id.value() + " is not a UUID-backed account");
        }

        try {
            History history = fetcher.fetch(id);
            return history == null ? unavailable("the history provider returned nothing")
                : history;
        } catch (Exception e) {
            FlashbackActors.LOGGER.warn("Could not fetch the skin history for {}", id.value(), e);
            return unavailable(id.value() + ": " + e.getMessage());
        }
    }

    public static History parse(String json) {
        if (json == null || json.isBlank()) {
            return History.empty(null, null);
        }

        JsonObject root;
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (parsed == null || !parsed.isJsonObject()) {
                FlashbackActors.LOGGER.warn("Skin history response was not a JSON object");
                return History.empty(null, null);
            }
            root = parsed.getAsJsonObject();
        } catch (RuntimeException e) {

            FlashbackActors.LOGGER.warn("Could not parse a skin history response", e);
            return History.empty(null, null);
        }

        String profileId = canonicalId(text(root, "id", null));
        String profileName = text(root, "name", null);


        JsonElement skins = root.get("skins");

        Map<String, HistoryEntry> byName = new LinkedHashMap<>();
        if (skins != null && skins.isJsonArray()) {

            for (JsonElement element : skins.getAsJsonArray()) {
                if (element.isJsonObject()) {
                    addEntry(byName, element.getAsJsonObject());
                }
            }
        } else if (skins != null && skins.isJsonObject()) {

            addEntry(byName, skins.getAsJsonObject());
        } else {

            addEntry(byName, root);
        }

        List<HistoryEntry> entries = new ArrayList<>(byName.values());

        entries.sort(HistoryEntry.NEWEST_FIRST.thenComparing(HistoryEntry::skinName));

        return new History(profileId, profileName, entries);
    }


    private static void addEntry(Map<String, HistoryEntry> byName, JsonObject object) {
        String name = text(object, "name", null);
        String url = text(object, "url", null);
        if (name == null || name.isBlank() || url == null || url.isBlank()) {
            return;
        }

        HistoryEntry entry = new HistoryEntry(name, url, timestamp(object));

        HistoryEntry existing = byName.get(name);


        if (existing == null || entry.timestamp() > existing.timestamp()) {
            byName.put(name, entry);
        }
    }


    private static long timestamp(JsonObject object) {
        JsonElement element = object.get("timestamp");
        if (element == null || !element.isJsonPrimitive()) {
            return 0L;
        }

        long value;
        try {
            value = element.getAsLong();
        } catch (RuntimeException e) {


            return 0L;
        }

        if (value <= 0L) {
            return 0L;
        }


        return value < 1_000_000_000_000L ? value * 1000L : value;
    }


    @Nullable
    private static String canonicalId(@Nullable String raw) {
        if (raw == null) {
            return null;
        }
        SkinId id = SkinId.tryParse(raw);
        return id == null ? raw : id.value();
    }


    @Nullable
    private static String text(JsonObject object, String key, @Nullable String fallback) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return fallback;
        }
        if (element.isJsonPrimitive()) {
            return element.getAsString();
        }
        return fallback;
    }
}
