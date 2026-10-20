package pdx.dev.fbactors.station;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import pdx.dev.fbactors.take.ActorTake;


public final class StationSummary {

    private StationSummary() {
    }


    public static String statusLine(List<ActorTake> takes, Set<UUID> selectedIds) {
        if (takes == null || takes.isEmpty()) {
            return "No actors in this replay";
        }
        int locked = 0;
        int muted = 0;
        int ticks = 0;
        for (ActorTake take : takes) {
            if (take.locked) {
                locked++;
            }
            if (!take.enabled) {
                muted++;
            }
            ticks += Math.max(0, take.length());
        }
        StringBuilder line = new StringBuilder();
        line.append(takes.size()).append(takes.size() == 1 ? " actor, " : " actors, ");
        line.append(String.format(java.util.Locale.ROOT, "%.1fs of footage", ticks / 20.0));
        if (locked > 0) {
            line.append(", ").append(locked).append(" locked");
        }
        if (muted > 0) {
            line.append(", ").append(muted).append(" muted");
        }
        int selected = selectedIds == null ? 0 : selectedIds.size();
        if (selected > 1) {
            line.append(", ").append(selected).append(" selected");
        }
        return line.toString();
    }
}

