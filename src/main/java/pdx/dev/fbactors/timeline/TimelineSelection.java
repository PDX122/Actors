package pdx.dev.fbactors.timeline;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;


public final class TimelineSelection {

    private final Set<UUID> ids = new LinkedHashSet<>();


    public void select(UUID id) {
        ids.clear();
        if (id != null) {
            ids.add(id);
        }
    }


    public void toggle(UUID id) {
        if (id == null) {
            return;
        }
        if (!ids.remove(id)) {
            ids.add(id);
        }
    }


    public void add(UUID id) {
        if (id != null) {
            ids.add(id);
        }
    }


    public void remove(UUID id) {
        ids.remove(id);
    }


    public void clear() {
        ids.clear();
    }


    public boolean isEmpty() {
        return ids.isEmpty();
    }


    public int size() {
        return ids.size();
    }


    public boolean contains(UUID id) {
        return ids.contains(id);
    }


    public Set<UUID> ids() {
        return Collections.unmodifiableSet(ids);
    }
}

