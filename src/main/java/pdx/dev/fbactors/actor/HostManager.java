package pdx.dev.fbactors.actor;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.playback.ReplayPlayer;
import com.moulberry.flashback.playback.ReplayServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import pdx.dev.fbactors.FlashbackActors;
import pdx.dev.fbactors.compat.Compat;
import pdx.dev.fbactors.take.ActorHit;
import pdx.dev.fbactors.take.HostHits;
import pdx.dev.fbactors.take.HostStore;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;


public final class HostManager {


    private static final double VANILLA_HIT_KNOCKBACK = 0.4;


    private static final int MIN_HURT_DURATION = 10;


    @Nullable
    private static UUID loadedReplayId = null;


    private static HostHits hits = new HostHits();


    private static final Map<UUID, Integer> DIVERGED_AT = new HashMap<>();


    private static final Map<UUID, Integer> LAST_STRIKE_TICK = new HashMap<>();


    private static final Map<UUID, Vec3> DEATH_POS = new HashMap<>();


    private static int lastAppliedTick = Integer.MIN_VALUE;

    private HostManager() {
    }


    public static int hostTickFor(@Nullable Actor attacker, int attackerTakeTick) {
        int attackerOffset = attacker == null ? 0 : attacker.take.offsetTicks;
        return attackerTakeTick + attackerOffset;
    }


    public static boolean isHost(Entity target) {
        if (!(target instanceof Player)) {
            return false;
        }
        if (FlashbackActors.ACTORS.byUuid(target.getUUID()) != null) {
            return false;
        }
        return !viewerIds().contains(target.getUUID());
    }


    private static Set<UUID> viewerIds() {
        Set<UUID> ids = new HashSet<>();
        try {
            ReplayServer replay = Flashback.getReplayServer();
            if (replay != null) {
                for (ReplayPlayer viewer : replay.getReplayViewers()) {
                    if (viewer != null) {
                        ids.add(viewer.getUUID());
                    }
                }
            }
        } catch (RuntimeException e) {
            FlashbackActors.LOGGER.debug("Could not list replay viewers", e);
        }
        return ids;
    }


    public static boolean hasHitAt(UUID victim, int replayTick) {
        ensureLoaded(FlashbackActors.loadedReplayIdOrNull());
        return hits.hasHitAt(victim, replayTick);
    }


    public static void recordHit(UUID victim, ActorHit hit) {
        ensureLoaded(FlashbackActors.loadedReplayIdOrNull());
        if (hit.damage() <= 0.0f) {
            return;
        }
        if (hits.hasHitAt(victim, hit.tick())) {
            return;
        }
        hits.recordHit(victim, hit);
        UUID replayId = FlashbackActors.loadedReplayIdOrNull();
        if (replayId != null) {
            HostStore.save(replayId, hits);
        }
        FlashbackActors.LOGGER.info("Host '{}' struck for {} damage at replay tick {} (crit {})",
            victim, hit.damage(), hit.tick(), hit.crit());
    }


    public static void suppressStrike(UUID victim, int replayTick) {
        LAST_STRIKE_TICK.put(victim, replayTick);
    }


    public static void diverge(UUID victim, int replayTick) {
        DIVERGED_AT.putIfAbsent(victim, replayTick);
    }


    static boolean usesRecordedBaseline(UUID victim, int replayTick) {
        Integer diverged = DIVERGED_AT.get(victim);
        return diverged == null || replayTick < diverged;
    }


    public static void applyTick(ReplayServer server, int replayTick) {
        if (!pdx.dev.fbactors.settings.FbSettings.hostDamageEnabled()) {
            return;
        }
        ensureLoaded(FlashbackActors.loadedReplayIdOrNull());
        if (hits.isEmpty()) {
            lastAppliedTick = replayTick;
            return;
        }
        if (replayTick < lastAppliedTick) {
            onScrub(replayTick);
        }
        lastAppliedTick = replayTick;

        Set<UUID> viewers = viewerIds();
        for (UUID victim : hits.victims()) {
            if (viewers.contains(victim)) {
                continue;
            }
            Entity entity = entityOf(server, victim);
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }
            applyOne(server, victim, living, replayTick);
        }
    }


    private static void onScrub(int replayTick) {
        DIVERGED_AT.entrySet().removeIf(entry -> entry.getValue() > replayTick);
        LAST_STRIKE_TICK.entrySet().removeIf(entry -> entry.getValue() > replayTick);
    }

    private static void applyOne(ReplayServer server, UUID victim, LivingEntity living,
                                 int replayTick) {
        float maxHealth = living.getMaxHealth();
        int deathTick = hits.deathTick(victim, maxHealth);
        boolean dead = deathTick >= 0 && replayTick >= deathTick;

        if (dead) {
            holdCorpse(server, victim, living, replayTick, deathTick);
            return;
        }
        DEATH_POS.remove(victim);

        if (usesRecordedBaseline(victim, replayTick)) {
            return;
        }


        living.setHealth(hits.healthAt(victim, replayTick, maxHealth));

        if (LAST_STRIKE_TICK.getOrDefault(victim, Integer.MIN_VALUE) == replayTick) {

            return;
        }


        ActorHit delayed = hits.hitAt(victim, replayTick - 1);
        ActorHit impulseHit =
            delayed != null && delayed.impulse() != null ? delayed : null;
        ActorHit current = hits.hitAt(victim, replayTick);
        ActorHit baseHit =
            current != null && current.impulse() == null ? current : null;
        if (impulseHit == null && baseHit == null) {
            return;
        }
        LAST_STRIKE_TICK.put(victim, replayTick);
        diverge(victim, replayTick);

        if (impulseHit != null) {

            Vec3 impulse = impulseHit.impulse();
            living.setDeltaMovement(living.getDeltaMovement().add(impulse));
        }
        if (baseHit != null) {
            Compat.knockback(living, VANILLA_HIT_KNOCKBACK, baseHit.dirX(), baseHit.dirZ());
        }

        living.setHealth(hits.healthAt(victim, replayTick, maxHealth));
        living.hurtTime = MIN_HURT_DURATION;
        living.hurtDuration = MIN_HURT_DURATION;

        if (impulseHit != null) {
            replayCrit(living, impulseHit);
        }
        if (baseHit != null) {
            replayCrit(living, baseHit);
        }

        if (living.level() instanceof ServerLevel level) {
            ActorManager.signalHit(server, level, living);
            Compat.syncMotion(living);
        }
    }


    private static void holdCorpse(ReplayServer server, UUID victim, LivingEntity living,
                                   int replayTick, int deathTick) {
        Vec3 pos = DEATH_POS.get(victim);
        if (pos == null) {
            DEATH_POS.put(victim, living.position());
        } else {
            living.setPos(pos.x, pos.y, pos.z);
        }
        living.setHealth(0.0f);
        living.setAbsorptionAmount(0.0f);
        living.setPose(Pose.DYING);
        if (replayTick == deathTick && living.level() instanceof ServerLevel level) {
            FlashbackActors.LOGGER.info("Host '{}' died at replay tick {}", victim, replayTick);
            level.broadcastEntityEvent(living, EntityEvent.DEATH);
            level.playSound(null, living.getX(), living.getY(), living.getZ(),
                SoundEvents.PLAYER_DEATH, SoundSource.PLAYERS, 1.0f, 1.0f);
            ActorManager.signalHit(server, level, living);
        }
    }


    private static void replayCrit(LivingEntity living, ActorHit hit) {
        if (hit.isCritical()) {
            Compat.critAnimation(living, false);
        }
        if (hit.isMagicCritical()) {
            Compat.critAnimation(living, true);
        }
    }


    @Nullable
    private static Entity entityOf(ReplayServer server, UUID id) {
        try {
            for (ServerLevel level : server.getAllLevels()) {
                Entity found = Compat.entityByUuid(level, id);
                if (found != null && !found.isRemoved()
                    && !(found instanceof ServerPlayer viewer && isViewer(server, viewer))) {
                    return found;
                }
            }
            return null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static boolean isViewer(ReplayServer server, ServerPlayer player) {
        try {
            for (ReplayPlayer viewer : server.getReplayViewers()) {
                if (viewer != null && viewer.getUUID().equals(player.getUUID())) {
                    return true;
                }
            }
        } catch (RuntimeException e) {

        }
        return false;
    }


    private static void ensureLoaded(@Nullable UUID replayId) {
        if (Objects.equals(loadedReplayId, replayId)) {
            return;
        }
        loadedReplayId = replayId;
        DIVERGED_AT.clear();
        LAST_STRIKE_TICK.clear();
        DEATH_POS.clear();
        lastAppliedTick = Integer.MIN_VALUE;
        hits = replayId == null ? new HostHits() : HostStore.load(replayId);
    }
}

