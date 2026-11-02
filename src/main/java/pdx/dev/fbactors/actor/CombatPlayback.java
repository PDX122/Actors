package pdx.dev.fbactors.actor;

import pdx.dev.fbactors.take.ActorHit;
import java.util.List;
import java.util.UUID;

public final class CombatPlayback {
    private CombatPlayback() {}
    public static List<ActorHit> at(List<ActorHit> hits, int tick) {
        return hits.stream().filter(hit -> hit.impulse()!=null ? (long)hit.tick()+1==tick : hit.tick()==tick).toList();
    }
    public static boolean alreadyAuthored(List<ActorHit> hits,int tick,UUID attacker,UUID victim) {
        return hits.stream().anyMatch(hit->hit.tick()==tick && (hit.target()==null
            || hit.target().equals(attacker) || hit.target().equals(victim)));
    }
}
