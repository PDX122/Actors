package pdx.dev.fbactors.take;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;


public final class HostHits {


    private final Map<UUID, List<ActorHit>> hits = new LinkedHashMap<>();


    public Set<UUID> victims() {
        return Collections.unmodifiableSet(this.hits.keySet());
    }


    public boolean isEmpty() {
        return this.hits.isEmpty();
    }


    public void clear() {
        this.hits.clear();
    }


    public void recordHit(UUID victim, ActorHit hit) {
        List<ActorHit> list = this.hits.computeIfAbsent(victim, id -> new ArrayList<>());
        int index = list.size();
        while (index > 0 && list.get(index - 1).tick() > hit.tick()) {
            index--;
        }
        while (index > 0 && list.get(index - 1).tick() == hit.tick()
            && java.util.Objects.equals(list.get(index - 1).target(), hit.target())) {
            list.remove(index - 1);
            index--;
        }
        list.add(index, hit);
    }


    @Nullable
    public ActorHit hitAt(UUID victim, int replayTick) {
        List<ActorHit> list = this.hits.get(victim);
        if (list == null) {
            return null;
        }
        for (ActorHit hit : list) {
            if (hit.tick() == replayTick) {
                return hit;
            }
            if (hit.tick() > replayTick) {
                break;
            }
        }
        return null;
    }


    public boolean hasHitAt(UUID victim, int replayTick) {
        return hitAt(victim, replayTick) != null;
    }


    public float cumulativeAt(UUID victim, int replayTick) {
        List<ActorHit> list = this.hits.get(victim);
        if (list == null) {
            return 0.0f;
        }
        float total = 0.0f;
        for (ActorHit hit : list) {
            if (hit.tick() <= replayTick) {
                total += hit.damage();
            } else {
                break;
            }
        }
        return total;
    }


    public float healthAt(UUID victim, int replayTick, float maxHealth) {
        float health = maxHealth - cumulativeAt(victim, replayTick);
        if (health < 0.0f) {
            return 0.0f;
        }
        if (health > maxHealth) {
            return maxHealth;
        }
        return health;
    }


    public int deathTick(UUID victim, float maxHealth) {
        List<ActorHit> list = this.hits.get(victim);
        if (list == null) {
            return -1;
        }
        float health = maxHealth;
        for (ActorHit hit : list) {
            health -= hit.damage();
            if (health <= 0.0f) {
                return hit.tick();
            }
        }
        return -1;
    }


    public List<ActorHit> hitsFor(UUID victim) {
        List<ActorHit> list = this.hits.get(victim);
        return list == null ? Collections.emptyList() : Collections.unmodifiableList(list);
    }


    public void sortHits() {
        for (Map.Entry<UUID, List<ActorHit>> entry : this.hits.entrySet()) {
            List<ActorHit> list = entry.getValue();
            list.sort(Comparator.comparingInt(ActorHit::tick));
            List<ActorHit> collapsed = new ArrayList<>(list.size());
            int lastTick = Integer.MIN_VALUE;
            UUID lastTarget = null;
            boolean hasLast = false;
            for (ActorHit hit : list) {
                if (hasLast && hit.tick() == lastTick
                    && java.util.Objects.equals(hit.target(), lastTarget)) {
                    continue;
                }
                collapsed.add(hit);
                lastTick = hit.tick();
                lastTarget = hit.target();
                hasLast = true;
            }
            if (collapsed.size() != list.size()) {
                entry.setValue(collapsed);
            }
        }
    }
}

