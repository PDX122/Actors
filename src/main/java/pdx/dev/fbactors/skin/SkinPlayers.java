package pdx.dev.fbactors.skin;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class SkinPlayers {
    private SkinPlayers() {}
    public static <T> void exclude(Set<UUID> excluded, java.util.Collection<T> rows, java.util.function.Function<T,UUID> identity) {

        for (T row : new java.util.ArrayList<>(rows)) {
            if (row == null) continue;
            UUID id = identity.apply(row);
            if (id != null) excluded.add(id);
        }
    }
    public static boolean visible(UUID id, String name, Set<UUID> excluded, String query) {
        if (id == null || excluded.contains(id) || name == null || name.isBlank()) return false;
        String label = name.trim();
        if (label.equalsIgnoreCase("Replay Viewer")) return false;
        return label.toLowerCase(Locale.ROOT).contains(query.trim().toLowerCase(Locale.ROOT));
    }
}
