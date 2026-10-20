package pdx.dev.fbactors.director;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;


final class DirectorMonitors {


    static final int MAX_MONITORS = 5;

    private final Set<UUID> monitors = new LinkedHashSet<>();


    boolean tryMonitor(UUID id) {
        if (id == null) {
            return false;
        }
        if (monitors.contains(id)) {
            return true;
        }
        if (monitors.size() >= MAX_MONITORS) {
            return false;
        }
        monitors.add(id);
        return true;
    }


    void unmonitor(UUID id) {
        monitors.remove(id);
    }


    boolean monitored(UUID id) {
        return monitors.contains(id);
    }


    int size() {
        return monitors.size();
    }


    List<UUID> ids() {
        return List.copyOf(monitors);
    }
}

