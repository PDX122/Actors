package pdx.dev.fbactors.menu;

import java.util.List;
import java.util.Locale;

public final class ReplayMenuModel {
    private ReplayMenuModel() {}

    public static List<ReplayBrowser.Entry> filter(List<ReplayBrowser.Entry> entries, String query) {
        String needle = query.strip().toLowerCase(Locale.ROOT);
        return entries.stream().filter(e -> e.name().toLowerCase(Locale.ROOT).contains(needle)).toList();
    }

    public static int rows(int height) {
        return Math.max(1, Math.min(8, (height - 180) / 28));
    }

    public static int pages(int count, int rows) {
        return Math.max(1, (int) (((long) count + rows - 1) / rows));
    }

    public static String duration(int seconds) {
        int value = Math.max(0, seconds);
        return String.format(Locale.ROOT, "%d:%02d", value / 60, value % 60);
    }
}
