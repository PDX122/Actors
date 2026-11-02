package pdx.dev.fbactors.actor;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.playback.ReplayServer;
import com.mojang.datafixers.util.Pair;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetEquipmentPacket;
import net.minecraft.world.item.ItemStack;
import pdx.dev.fbactors.take.ActorBlockEdit;
import pdx.dev.fbactors.take.ActorBlockEntityEdit;
import pdx.dev.fbactors.take.ActorContainerInteraction;
import pdx.dev.fbactors.take.ActorDeath;
import pdx.dev.fbactors.take.ActorEquip;
import pdx.dev.fbactors.fx.DeathFx;
import net.minecraft.world.entity.EquipmentSlot;
import pdx.dev.fbactors.take.ActorEvent;
import pdx.dev.fbactors.take.ActorFrame;
import pdx.dev.fbactors.take.ActorTake;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundRotateHeadPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityEvent;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import pdx.dev.fbactors.take.ActorEffect;
import pdx.dev.fbactors.take.ActorRide;
import pdx.dev.fbactors.take.ActorSound;
import pdx.dev.fbactors.take.ActorShot;
import pdx.dev.fbactors.take.ActorVitals;

import org.jetbrains.annotations.Nullable;
import java.util.UUID;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import pdx.dev.fbactors.FlashbackActors;
import pdx.dev.fbactors.compat.Compat;
import pdx.dev.fbactors.morph.Morph;
import pdx.dev.fbactors.morph.MorphApplier;
import pdx.dev.fbactors.morph.MorphDefinition;
import pdx.dev.fbactors.morph.MorphSelection;
import pdx.dev.fbactors.gear.Gear;
import pdx.dev.fbactors.gear.GearApplier;
import pdx.dev.fbactors.gear.GearLoadout;

public final class ActorManager {

    private static final int DEATH_FALL_TICKS = 6;

    private static final int GROUND_SEARCH_BLOCKS = 24;

    private static final Set<UUID> SHOT_REPEAT_WARNED = new HashSet<>();

    private static final Set<UUID> SPAWN_REPEAT_WARNED = new HashSet<>();

    private static final Map<UUID, GearLoadout> LAST_GEAR_SENT = new java.util.HashMap<>();

    public static void signalHit(ReplayServer server, ServerLevel level, Entity target) {
        signalHitPacket(server, level, target);
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
            SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    public static void signalHitPacket(ReplayServer server, ServerLevel level, Entity target) {
        ClientboundDamageEventPacket packet =
            new ClientboundDamageEventPacket(target, level.damageSources().generic());
        for (ServerPlayer viewer : server.getReplayViewers()) {
            viewer.connection.send(packet);
        }
    }

    private static void syncHead(ReplayServer server, ActorClonePlayer clone) {
        Packet<?> head = new ClientboundRotateHeadPacket(clone,
            (byte) Mth.floor(clone.getYHeadRot() * 256.0F / 360.0F));
        for (ServerPlayer viewer : server.getReplayViewers()) {
            viewer.connection.send(head);
        }
    }

    private static double groundBelow(ServerLevel level, double x, double y, double z) {
        BlockPos.MutableBlockPos pos =
            new BlockPos.MutableBlockPos(Mth.floor(x), Mth.floor(y), Mth.floor(z));
        for (int i = 0; i < GROUND_SEARCH_BLOCKS; i++) {
            pos.setY(Mth.floor(y) - i);
            if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()) {
                return pos.getY() + 1.0;
            }
        }
        return y;
    }

    private final List<Actor> actors = new java.util.concurrent.CopyOnWriteArrayList<>();

    public List<Actor> actors() {
        return Collections.unmodifiableList(this.actors);
    }

    public Actor add(ActorTake take) {
        Actor existing = byTakeId(take.id);
        if (existing != null) {
            return existing;
        }
        Actor actor = new Actor(take);
        this.actors.add(actor);
        return actor;
    }

    @Nullable
    public Actor byUuid(UUID entityId) {
        for (Actor actor : this.actors) {
            if (actor.clone() != null && actor.clone().getUUID().equals(entityId)) {
                return actor;
            }
        }

        return byTakeId(entityId);
    }

    @Nullable
    public Actor byTakeId(UUID takeId) {
        for (Actor actor : this.actors) {
            if (actor.take.id.equals(takeId)) {
                return actor;
            }
        }
        return null;
    }

    public void remove(Actor actor) {
        revertBlocks(actor);
        despawn(this.server, actor);
        this.actors.remove(actor);
    }

    public void clear() {
        for (Actor actor : this.actors) {
            revertBlocks(actor);
        }
        despawnAll();
        this.actors.clear();
    }

    private static void revertBlocks(Actor actor) {
        if (actor.take.blocks.isEmpty()) {
            return;
        }
        ReplayServer replay = Flashback.getReplayServer();
        if (replay == null) {
            return;
        }
        ServerLevel level = replay.getLevel(actor.dimensionKey());
        if (level != null) {
            BlockEdits.revert(level, actor.take.blocks);
        }
    }

    public void despawnAll() {
        for (Actor actor : this.actors) {
            despawn(this.server, actor);
        }
    }

    private static void despawn(@Nullable ReplayServer server, Actor actor) {
        BoatPhysics.forget(actor.take.id);
        if (server != null) {
            for (Map.Entry<BlockPos, Integer> outstanding : actor.outstandingMining()) {
                broadcast(server,
                    new ClientboundBlockDestructionPacket(outstanding.getValue(),
                        outstanding.getKey(), -1));
            }
        }

        ActorClonePlayer riding = actor.clone();
        if (riding != null && riding.getVehicle() != null) {
            try {
                riding.stopRiding();
            } catch (RuntimeException | LinkageError ignored) {
            }
        }
        MiningAnimation.clear(actor);
        MorphApplier.forget(actor.clone());
        GearApplier.forget(actor.clone());
        LAST_GEAR_SENT.remove(actor.take.id);
        discardSpawns(server, actor);
        actor.despawn();
    }

    private static void discardSpawns(@Nullable ReplayServer server, Actor actor) {
        if (actor.spawnedEntities().isEmpty()) {
            return;
        }
        if (server != null) {
            for (UUID id : new java.util.ArrayList<>(actor.spawnedEntities().keySet())) {
                try {
                    ServerLevel level = server.getLevel(actor.dimensionKey());
                    if (level == null) {
                        continue;
                    }
                    Entity e = Compat.entityByUuid(level, id);
                    if (e != null && !e.isRemoved()) {
                        e.discard();
                    }
                } catch (RuntimeException | LinkageError ignored) {
                }
            }
        }
        actor.spawnedEntities().clear();
        SPAWN_REPEAT_WARNED.remove(actor.take.id);
    }

    private static void pruneSpawns(ServerLevel level, Actor actor, int takeTick) {
        if (actor.spawnedEntities().isEmpty()) {
            return;
        }
        java.util.Iterator<Map.Entry<UUID, Integer>> it =
            actor.spawnedEntities().entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> entry = it.next();
            if (entry.getValue() > takeTick) {
                try {
                    Entity e = Compat.entityByUuid(level, entry.getKey());
                    if (e != null && !e.isRemoved()) {
                        e.discard();
                    }
                } catch (RuntimeException | LinkageError ignored) {
                }
                it.remove();
            }
        }
    }

    private static final Map<UUID, Long> LAST_CYCLE_LOG = new java.util.HashMap<>();

    private static void logCycle(Actor actor, String what, String reason, int takeTick) {
        long now = System.currentTimeMillis();
        Long last = LAST_CYCLE_LOG.get(actor.take.id);
        if (last != null && now - last < 5000) {
            return;
        }
        LAST_CYCLE_LOG.put(actor.take.id, now);
        FlashbackActors.LOGGER.info("Actor '{}' {} ({} at take tick {})",
            actor.displayName(), what, reason, takeTick);
    }

    public boolean anySolo() {
        for (Actor actor : this.actors) {
            if (actor.solo) {
                return true;
            }
        }
        return false;
    }

    public void applyTick(ReplayServer server, int replayTick) {
        this.server = server;
        reportExportTransition(replayTick);
        boolean soloing = anySolo();
        for (Actor actor : this.actors) {
            applyOne(server, actor, replayTick, soloing);
        }

        HostManager.applyTick(server, replayTick);
        pdx.dev.fbactors.voice.VoicePlayer.tick(replayTick,
            server.getReplayTick() == replayTick && this.lastAppliedTick == replayTick);
        this.lastAppliedTick = replayTick;
    }

    @Nullable
    private ReplayServer server;

    private static void broadcast(ReplayServer server, Packet<?> packet) {
        for (ServerPlayer viewer : server.getReplayViewers()) {
            viewer.connection.send(packet);
        }
    }

    private int lastAppliedTick = Integer.MIN_VALUE;

    private boolean lastExporting = false;

    private void reportExportTransition(int replayTick) {
        boolean exporting;
        try {
            exporting = Flashback.isExporting();
        } catch (Exception e) {
            return;
        }
        boolean entered = exporting && !this.lastExporting;
        boolean exited = !exporting && this.lastExporting;
        this.lastExporting = exporting;
        EXPORT_ACTIVE = exporting;
        if (!entered && !exited) {
            return;
        }
        try {
            if (entered) {
                int takes = this.actors.size();
                int fileSkins = ActorSkins.fileSkinCount();
                int liveSkins = -1;
                int liveFileSkins = -1;
                try {
                    com.moulberry.flashback.state.EditorState editorState =
                        com.moulberry.flashback.state.EditorStateManager.getCurrent();
                    if (editorState != null) {
                        liveSkins = editorState.skinOverride.size();
                        liveFileSkins = editorState.skinOverrideFromFile.size();
                    }
                } catch (Exception ignored) {

                }
                int morphedTakes = -1;
                try {
                    morphedTakes = pdx.dev.fbactors.morph.Morph.store().size();
                } catch (Exception ignored) {

                }
                int renderStates = -1;
                try {
                    renderStates =
                        pdx.dev.fbactors.morph.MorphRenderState.snapshot().size();
                } catch (Exception ignored) {

                }
                EXPORT_MAX_GAP = 0.0;
                EXPORT_MAX_GAP_TICK = Integer.MIN_VALUE;
                EXPORT_MAX_GAP_ACTOR = null;
                int reappliedSkins = 0;
                try {
                    reappliedSkins = healExportSkins();
                } catch (Exception ignored) {

                }
                try {
                    com.moulberry.flashback.state.EditorState healed =
                        com.moulberry.flashback.state.EditorStateManager.getCurrent();
                    if (healed != null) {
                        liveSkins = healed.skinOverride.size();
                        liveFileSkins = healed.skinOverrideFromFile.size();
                    }
                } catch (Exception ignored) {

                }
                FlashbackActors.LOGGER.info(
                    "Export started at replay tick {}: takes={} fileSkins={} "
                        + "liveSkinOverride={} liveSkinOverrideFromFile={} "
                        + "morphedTakes={} renderStates={} reappliedSkins={}",
                    replayTick, takes, fileSkins, liveSkins, liveFileSkins,
                    morphedTakes, renderStates, reappliedSkins);
                return;
            }
            for (Actor actor : this.actors) {
                try {
                    ActorClonePlayer clone = actor.clone();
                    if (clone == null) {
                        FlashbackActors.LOGGER.info(
                            "Export ended at replay tick {}: actor '{}' has no clone "
                                + "(despawned during export)",
                            replayTick, actor.displayName());
                        continue;
                    }
                    int lastTick = actor.lastAppliedTakeTick();
                    pdx.dev.fbactors.take.ActorFrame frame = actor.take.frameAt(lastTick);
                    if (frame == null) {
                        FlashbackActors.LOGGER.info(
                            "Export ended at replay tick {}: actor '{}' has no frame "
                                + "at take tick {} (out of range)",
                            replayTick, actor.displayName(), lastTick);
                        continue;
                    }
                    double dx = clone.getX() - frame.x();
                    double dy = clone.getY() - frame.y();
                    double dz = clone.getZ() - frame.z();
                    double gap = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    boolean baseline;
                    try {
                        baseline = actor.sim().usesRecordedBaseline(lastTick);
                    } catch (Exception e) {
                        FlashbackActors.LOGGER.info(
                            "Export ended at replay tick {}: actor '{}' gap={}m "
                                + "state=unknown (baseline check failed)",
                            replayTick, actor.displayName(), fmt(gap));
                        continue;
                    }
                    FlashbackActors.LOGGER.info(
                        "Export ended at replay tick {}: actor '{}' gap={}m "
                            + "state={} at take tick {}",
                        replayTick, actor.displayName(), fmt(gap),
                        baseline ? "baseline" : "diverged", lastTick);
                } catch (Exception e) {
                    FlashbackActors.LOGGER.warn(
                        "Export exit diagnostic failed for an actor at replay tick {}",
                        replayTick, e);
                }
            }
            FlashbackActors.LOGGER.info(
                "Export ended at replay tick {}: worst clone-vs-frame gap={}m{}",
                replayTick, fmt(EXPORT_MAX_GAP),
                EXPORT_MAX_GAP_ACTOR == null ? " (no export frames tracked)"
                    : " on actor '" + EXPORT_MAX_GAP_ACTOR + "' at take tick "
                        + EXPORT_MAX_GAP_TICK);
        } catch (Exception e) {
            FlashbackActors.LOGGER.warn(
                "Export transition diagnostic failed at replay tick {}", replayTick, e);
        }
    }

    private int healExportSkins() {
        int reapplied = 0;
        try {
            ExportSkinProbe.arm();
        } catch (Exception ignored) {

        }
        for (Actor actor : this.actors) {
            try {
                com.moulberry.flashback.state.EditorState editorState =
                    com.moulberry.flashback.state.EditorStateManager.getCurrent();
                if (editorState == null) {
                    return reapplied;
                }
                if (editorState.skinOverride.containsKey(actor.take.id)
                    || editorState.skinOverrideFromFile.containsKey(actor.take.id)) {
                    continue;
                }
                ActorSkins.restoreFiles(actor);
                ActorSkins.applyDefault(actor);
                if (editorState.skinOverride.containsKey(actor.take.id)
                    || editorState.skinOverrideFromFile.containsKey(actor.take.id)) {
                    reapplied++;
                }
            } catch (Exception ignored) {

            }
        }
        return reapplied;
    }

    private static void fireShots(ServerLevel level, Actor actor, ActorClonePlayer clone,
                                 int takeTick) {
        List<ActorShot> shots = actor.take.shotsAt(takeTick);
        if (shots.isEmpty()) {
            return;
        }

        if (takeTick == actor.lastAppliedTakeTick()) {
            if (SHOT_REPEAT_WARNED.add(actor.take.id)) {
                pdx.dev.fbactors.FlashbackActors.LOGGER.warn(
                    "Actor '{}' re-fired {} arrow(s) for take tick {}; treating it as a scrub",
                    actor.take.name, shots.size(), takeTick);
            }
            return;
        }

        int fired = 0;
        for (ActorShot shot : shots) {
            if (ActorArrows.fire(level, clone, shot)) {
                fired++;
            }
        }
        pdx.dev.fbactors.FlashbackActors.LOGGER.info(
            "Actor '{}' fired {}/{} recorded arrow(s) at take tick {} (speed {})",
            actor.take.name, fired, shots.size(), takeTick,
            shots.get(0).power());
    }

    private static void fireUses(ServerLevel level, Actor actor, int takeTick) {
        if (!actor.take.hasUseAt(takeTick)) {
            return;
        }
        ActorClonePlayer clone = actor.clone();
        if (clone == null) {
            return;
        }
        for (pdx.dev.fbactors.take.ActorUse use : actor.take.usesAt(takeTick)) {
            ItemActionReplay.use(level, clone, use, !actor.sim().usesRecordedBaseline(takeTick));
            Compat.swing(clone,
                use.offHand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        }
    }

    private static void fireSpawns(ServerLevel level, Actor actor, int takeTick) {
        if (!actor.sim().usesRecordedBaseline(takeTick)) return;
        if (!actor.take.hasSpawnAt(takeTick)) {
            return;
        }
        if (takeTick == actor.lastAppliedTakeTick()) {
            if (SPAWN_REPEAT_WARNED.add(actor.take.id)) {
                pdx.dev.fbactors.FlashbackActors.LOGGER.warn(
                    "Actor '{}' re-spawned {} entit(ies) for take tick {}; treating it as a scrub",
                    actor.take.name, actor.take.spawnsAt(takeTick).size(), takeTick);
            }
            return;
        }
        for (pdx.dev.fbactors.take.ActorSpawn spawn : actor.take.spawnsAt(takeTick)) {
            try {
                Entity e = Compat.spawnEntityFromRecord(level, spawn);
                if (e != null) {
                    actor.trackSpawn(e.getUUID(), takeTick);
                    ActorWorldSimulation.track(e);
                }
            } catch (RuntimeException | LinkageError ex) {
                pdx.dev.fbactors.FlashbackActors.LOGGER.warn(
                    "Actor '{}' failed to spawn {} at take tick {}",
                    actor.take.name, spawn.entityId(), takeTick);
            }
        }
    }

    private static float advanceTicks(int baseTicks, float speed) {
        return baseTicks * speed;
    }

    private void applyOne(ReplayServer server, Actor actor, int replayTick, boolean soloing) {
        if (!actor.enabled() || (soloing && !actor.solo)) {
            logCycle(actor, "despawned", "disabled-or-soloed", replayTick);
            despawn(server, actor);
            return;
        }

        int takeTick = (int) advanceTicks(actor.takeTickFor(replayTick),
            actor.take.getSpeedMultiplier());
        if (!actor.take.coversTick(takeTick)) {
            logCycle(actor, "despawned", "out-of-range", takeTick);
            despawn(server, actor);
            return;
        }

   ServerLevel level = server.getLevel(actor.dimensionKey());
        if (level == null) {
logCycle(actor, "despawned", "no-level", takeTick);
despawn(server, actor);
          return;
        }

        ActorSkins.ensureLive(actor);

        ActorVitals recordedVitals=actor.take.vitalsAt(takeTick);
        if(actor.clone()!=null && actor.clone().getHealth()<=0)actor.combat().declareDeath(takeTick);
        if(actor.sim().usesRecordedBaseline(takeTick) && recordedVitals!=null && recordedVitals.health()<=0)
            actor.combat().declareDeath(takeTick);
        int deathTick=actor.combat().deathTick();
        for(ActorDeath authored:actor.take.deaths)
            if(deathTick<0 || authored.tick()<deathTick)deathTick=authored.tick();
        boolean dead = deathTick >= 0 && takeTick >= deathTick;

     if (dead && takeTick > deathTick + ActorTake.DEATH_LINGER_TICKS) {
logCycle(actor, "despawned", "dead-past-linger deathTick=" + deathTick, takeTick);
   despawn(server, actor);
            return;
        }

        ActorFrame frame = actor.take.frameAt(dead ? deathTick : takeTick);
        if (frame == null) {
            logCycle(actor, "despawned", "no-frame", takeTick);
            despawn(server, actor);
            return;
        }

        ActorClonePlayer clone = actor.clone();
        boolean justSpawned = false;

        boolean corpseBeforeRevival = clone != null && !dead && clone.deathTime > 0;
        if (clone == null || clone.isRemoved() || clone.level() != level
            || corpseBeforeRevival) {
 logCycle(actor, "respawned", clone == null ? "no-clone"
     : clone.isRemoved() ? "removed"
     : clone.level() != level ? "level-changed" : "corpse-revival", takeTick);
 despawn(server, actor);
  clone = ActorClone.spawn(server, level, actor.profile(), frame);
            actor.setClone(clone);
      if (clone == null) {
    return;
       }

      ActorSkins.restoreFiles(actor);
      ActorSkins.applyDefault(actor);
        justSpawned = true;
        }

        boolean freshTick = takeTick != actor.lastAppliedTakeTick();
        boolean simulated;
        if (freshTick) {
            simulated = actor.sim().prepare(level, clone, takeTick);
            ActorVehicles.apply(level, actor, takeTick, frame);

            actor.sim().applyBaseline(clone, frame, takeTick);
            ActorTerrain.react(level, actor, takeTick);
            ActorEnvironment.react(level, actor, takeTick);

            boolean struck = actor.sim().replayStrike(clone, takeTick);
            if (struck) {

                if (hasRecordedHurt(actor, takeTick)) {
                    signalHitPacket(server, level, clone);
                } else {
                    signalHit(server, level, clone);
                }
            }

            fireSpawns(level, actor, takeTick);
            applyRides(level, actor, takeTick);

            if (!actor.take.simulated()) {

                float oldHead = clone.getYHeadRot(), oldBody = clone.yBodyRot;
                if (!actor.sim().usesRecordedBaseline(takeTick)) BoatPhysics.step(clone.getVehicle());
                clone.tick();
                if (!actor.sim().usesRecordedBaseline(takeTick)) BoatPhysics.seat(clone, oldHead, oldBody);
                if (actor.sim().usesRecordedBaseline(takeTick)) {
                    Compat.snapTo(clone, frame.x(), frame.y(), frame.z(),
                        frame.yRot(), frame.xRot());
                    actor.sim().driveRecordedLimbs(clone, takeTick);
                }
            } else {
                actor.sim().step(level, clone, takeTick);
            }

            if (struck) {
                Compat.syncMotion(clone);
            }

            reportTakeShape(actor, takeTick);
            reportEmbedded(level, actor, clone, takeTick);
            actor.sim().runActions(clone, takeTick);

            fireShots(level, actor, clone, takeTick);

            actor.sim().publish(clone, takeTick);
        } else {

            simulated = !actor.sim().usesRecordedBaseline(takeTick);
        }

        ActorVehicles.apply(level, actor, takeTick, frame);
        simulated = !actor.sim().usesRecordedBaseline(takeTick);
        if(actor.combat().deathTick()>=0 && takeTick>=actor.combat().deathTick()) {
            deathTick=actor.combat().deathTick(); dead=true;
        }
        if (!simulated && dead) {
    applyDeathFall(level, clone, frame, takeTick, deathTick);
        }

        if (!pdx.dev.fbactors.settings.FbSettings.settings().actorPacketMovement) {
            syncHead(server, clone);
        }
        applyHealth(actor, clone, takeTick, simulated, dead);
        if (dead) {
   clone.setPose(Pose.DYING);
}

   GearLoadout gear = Gear.loadoutFor(actor.take.id);
   if (justSpawned || actor.take.hasEquipmentChangeAt(takeTick)
       || !gear.equals(LAST_GEAR_SENT.get(actor.take.id))) {
            applyEquipment(server, clone, actor.take, takeTick, gear);
            LAST_GEAR_SENT.put(actor.take.id, gear);
      }

        if (!dead) {
            applyMorph(actor, clone);
        }

        if (takeTick != actor.lastAppliedBlockTick()) {
            applyBlocks(level, actor, takeTick, clone);
            actor.markBlocksApplied(takeTick);
            applyBlockEntities(server, level, actor, takeTick);
        }

        pruneSpawns(level, actor, takeTick);

        if (takeTick != actor.lastAppliedTakeTick()) {
            MiningAnimation.update(server, actor, takeTick);
            applyContainers(server, level, actor, takeTick);
            applySounds(level, actor, takeTick);
            fireEvents(server, level, actor, takeTick);
            fireUses(level, actor, takeTick);

            if (!dead) {
                applyConditioning(server, clone, actor.take, takeTick, simulated);
            }

            if (dead && takeTick == deathTick) {
                FlashbackActors.LOGGER.info(
                    "Actor '{}' died at take tick {}: health {} pose {} deathTime {} diverged {}",
                    actor.take.name, takeTick, clone.getHealth(), clone.getPose(),
                    clone.deathTime, simulated);
                level.broadcastEntityEvent(clone, EntityEvent.DEATH);

                if (!hasRecordedDeath(actor, takeTick)) {
                    level.playSound(null, clone.getX(), clone.getY(), clone.getZ(),
                    SoundEvents.PLAYER_DEATH, SoundSource.PLAYERS, 1.0f, 1.0f);
                }

                ActorDeath authored = actor.take.deathAt(deathTick);
                if (authored != null) {
         DeathFx.play(level, clone, authored.kind());
    }
  }

    actor.markApplied(takeTick);
        }

        pushEntityData(server, clone);
        logMovement(actor, clone, frame, takeTick, simulated, dead);
    }

    private static boolean EXPORT_ACTIVE = false;

    private static double EXPORT_MAX_GAP = 0.0;

    private static int EXPORT_MAX_GAP_TICK = Integer.MIN_VALUE;

    private static String EXPORT_MAX_GAP_ACTOR = null;

    private static final Map<UUID, Integer> LAST_MOVE_LOG = new java.util.HashMap<>();

    private static final Map<UUID, Boolean> LAST_SIM_STATE = new java.util.HashMap<>();

    private static void logMovement(Actor actor, ActorClonePlayer clone, ActorFrame frame,
                                    int takeTick, boolean simulated, boolean dead) {
        UUID id = actor.take.id;
        Boolean wasSim = LAST_SIM_STATE.get(id);
        if (wasSim == null || wasSim != simulated) {
            LAST_SIM_STATE.put(id, simulated);
            FlashbackActors.LOGGER.info("Actor '{}' {}", actor.displayName(),
                simulated ? "diverged from the recording at take tick " + takeTick
                    : "back on the recorded path at take tick " + takeTick);
        }
        double dx = clone.getX() - frame.x();
        double dy = clone.getY() - frame.y();
        double dz = clone.getZ() - frame.z();
        double gap = Math.sqrt(dx * dx + dy * dy + dz * dz);

        if (EXPORT_ACTIVE && gap >= EXPORT_MAX_GAP) {
            EXPORT_MAX_GAP = gap;
            EXPORT_MAX_GAP_TICK = takeTick;
            EXPORT_MAX_GAP_ACTOR = actor.displayName();
        }
        Integer last = LAST_MOVE_LOG.get(id);
        if (last != null && takeTick - last < 20) {
            return;
        }
        LAST_MOVE_LOG.put(id, takeTick);
        net.minecraft.world.phys.Vec3 vel = clone.getDeltaMovement();
        FlashbackActors.LOGGER.info(
            "Actor '{}' t={} pos=({},{},{}) frame=({},{},{}) gap={}m "
                + "vel=({},{},{}) ground={} pose={} sim={} dead={}",
            actor.displayName(), takeTick,
            fmt(clone.getX()), fmt(clone.getY()), fmt(clone.getZ()),
            fmt(frame.x()), fmt(frame.y()), fmt(frame.z()), fmt(gap),
            fmt(vel.x), fmt(vel.y), fmt(vel.z), clone.onGround(), clone.getPose(),
            simulated ? "sim" : "rec", dead);
    }

    private static String fmt(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private static void applyHealth(Actor actor, ActorClonePlayer clone, int takeTick,
                                    boolean simulated, boolean dead) {
        if (!pdx.dev.fbactors.settings.FbSettings.combatEnabled()) {
            return;
        }

        if (dead) {
            clone.setHealth(0.0f);
            clone.setAbsorptionAmount(0.0f);
            return;
        }

        ActorVitals vitals = actor.take.vitalsAt(takeTick);
        if (vitals != null && !simulated) {
  clone.setHealth(vitals.health());
            clone.setAbsorptionAmount(vitals.absorption());
            return;
        }

    }

    private static void applyDeathFall(ServerLevel level, ActorClonePlayer clone,
                                       ActorFrame frame, int takeTick, int deathTick) {
        double posX = clone.getX();
        double posY = clone.getY();
        double posZ = clone.getZ();
        double groundY = groundBelow(level, posX, posY, posZ);
        double fall = Math.min(1.0, (takeTick - deathTick) / (double) DEATH_FALL_TICKS);
        clone.setPos(posX, posY + (groundY - posY) * fall, posZ);
    }

    private static void reportTakeShape(Actor actor, int takeTick) {
        if (actor.reportedShape || takeTick != 0) {
            return;
        }
        actor.reportedShape = true;
        pdx.dev.fbactors.FlashbackActors.LOGGER.info(
            "Actor '{}' playing: startTick={} frames={} inputs={} blockEdits={}"
                + " driving={}",
            actor.displayName(), actor.take.startTick, actor.take.frames.size(),
            actor.take.inputs.size(), actor.take.blocks.size(),
            actor.take.simulated() ? "input" : "frames");
    }

    private static void reportEmbedded(ServerLevel level, Actor actor,
            ActorClonePlayer clone, int takeTick) {
        if (actor.reportedEmbedded) {
            return;
        }
        BlockPos feet = BlockPos.containing(clone.getX(), clone.getY(), clone.getZ());
        if (level.getBlockState(feet).isAir()) {
            return;
        }
        actor.reportedEmbedded = true;
        pdx.dev.fbactors.FlashbackActors.LOGGER.warn(
            "Actor '{}' is inside {} at take tick {} ({}, {}, {})",
            actor.displayName(), level.getBlockState(feet).getBlock().getName().toString(),
            takeTick, clone.getX(), clone.getY(), clone.getZ());
    }

    private static void applyBlocks(ServerLevel level, Actor actor, int takeTick,
                                   ActorClonePlayer clone) {
        if (!actor.sim().usesRecordedBaseline(takeTick)) {

            for (ActorBlockEdit edit : actor.take.blocks) {
                if (edit.tick() == actor.take.recordedTick(takeTick) && "minecraft:air".equals(edit.after())
                    && ItemActionReplay.canReachBlock(level, clone, edit.pos())) clone.gameMode.destroyBlock(edit.pos());
            }
            return;
        }
        List<ActorBlockEdit> edits = actor.take.blocks;
        if (edits.isEmpty()) {
            return;
        }

        int last = actor.lastAppliedBlockTick();
        boolean jumped = takeTick < last || takeTick - last > 1;
        int from = jumped ? Integer.MIN_VALUE : last;

        BlockEdits.beginApply();
        try {
            java.util.List<pdx.dev.fbactors.take.ActorSound> cues =
                actor.take.soundsAt(takeTick);
            for (ActorBlockEdit edit : edits) {
                if (edit.tick() > actor.take.recordedTick(takeTick)) {
                    break;
                }
                if (edit.tick() <= from) {
                    continue;
                }
                BlockEdits.apply(level, edit, !jumped, clone, cues);
            }
        } finally {
            BlockEdits.endApply();
        }
    }

    private static void applyBlockEntities(ReplayServer server, ServerLevel level,
                                           Actor actor, int takeTick) {
        if (!actor.sim().usesRecordedBaseline(takeTick)) return;
        Map<BlockPos, ActorBlockEntityEdit> edits = actor.take.blockEntityAt(takeTick);
        if (edits.isEmpty()) {
            return;
        }

        BlockEdits.beginApply();
        try {
            for (ActorBlockEntityEdit edit : edits.values()) {

                Compat.applyBlockEntityNbt(level, edit.pos(), edit.type(), edit.nbt());
            }
        } finally {
            BlockEdits.endApply();
        }
    }

    private static void applyContainers(ReplayServer server, ServerLevel level,
                                        Actor actor, int takeTick) {
        if (!actor.sim().usesRecordedBaseline(takeTick)) return;
        List<ActorContainerInteraction> interactions = actor.take.containerAt(takeTick);
        if (interactions.isEmpty()) {
            return;
        }

        ActorClonePlayer clone = actor.clone();
        if (clone == null) {
            return;
        }

        for (ActorContainerInteraction interaction : interactions) {
            switch (interaction.action()) {
                case "open" -> {

                    Compat.openContainer(server, level, clone, interaction.pos());
                }
                case "close" -> {

                    Compat.closeContainer(server, clone);
                }
                case "slot_change" -> {

                    ItemStack stack = Compat.itemStackFromNbt(level,
                        interaction.itemId(), interaction.count(), interaction.nbt());

                    Compat.updateContainerSlot(level, interaction.pos(),
                        interaction.slot(), stack);
                }
            }
        }
    }

    private static void applyRides(ServerLevel level, Actor actor, int takeTick) {
        if (!actor.sim().usesRecordedBaseline(takeTick)) return;
        if (!actor.take.hasRideAt(takeTick)) {
            return;
        }
        ActorClonePlayer clone = actor.clone();
        if (clone == null) {
            return;
        }
        for (ActorRide ride : actor.take.ridesAt(takeTick)) {
            if (ride.mount()) {
                if (ride.vehicleUuid() == null) {
                    continue;
                }
                Entity vehicle = Compat.entityByUuid(level, ride.vehicleUuid());
                if (vehicle == null || vehicle.isRemoved()) {
                    FlashbackActors.LOGGER.warn(
                        "Actor '{}' cannot board {} at take tick {}: vehicle gone",
                        actor.displayName(), ride.vehicleType(), takeTick);
                    continue;
                }
                if (clone.getVehicle() != vehicle) {
                    clone.startRiding(vehicle);
                }
            } else if (clone.getVehicle() != null) {
                clone.stopRiding();
            }
        }
    }

    private static void applySounds(ServerLevel level, Actor actor, int takeTick) {
        List<ActorSound> cues = actor.take.soundsAt(takeTick);
        if (cues.isEmpty()) {
            return;
        }
        BlockEdits.beginApply();
        try {
            for (ActorSound cue : cues) {
                if (cue.soundId() == null || ownedElsewhere(actor, takeTick, cue)) {
                    continue;
                }
                net.minecraft.sounds.SoundEvent sound;
                try {
                    sound = pdx.dev.fbactors.compat.CompatSounds.resolveSound(cue.soundId());
                } catch (RuntimeException | LinkageError e) {
                    continue;
                }
                if (sound == null) {
                    continue;
                }
                net.minecraft.sounds.SoundSource source;
                try {
                    source = cue.source() == null
                        ? net.minecraft.sounds.SoundSource.BLOCKS
                        : net.minecraft.sounds.SoundSource.valueOf(cue.source());
                } catch (RuntimeException | LinkageError e) {
                    source = net.minecraft.sounds.SoundSource.BLOCKS;
                }
                try {
                    level.playSound(null, cue.x(), cue.y(), cue.z(),
                        sound, source, cue.volume(), cue.pitch());
                } catch (RuntimeException | LinkageError ignored) {
                }
            }
        } finally {
            BlockEdits.endApply();
        }
    }

    private static boolean ownedElsewhere(Actor actor, int takeTick, ActorSound cue) {
        String id = cue.soundId();
        if (isLidSound(id) && containerTransitionNearby(actor, takeTick, cue)) {
            return true;
        }
        if (isHurtSound(id)
            && (actor.take.attackAt(takeTick) != null
                || actor.take.hitAt(takeTick) != null
                || actor.take.hitAt(takeTick - 1) != null)) {
            return true;
        }
        return isDeathSound(id) && actor.take.deathTick() == takeTick;
    }

    private static boolean isLidSound(String id) {
        return id.contains("chest") || id.contains("shulker") || id.contains("barrel");
    }

    private static boolean isHurtSound(String id) {
        return id.endsWith(".hurt") || id.endsWith("_hurt");
    }

    private static boolean isDeathSound(String id) {
        return id.endsWith(".death") || id.endsWith("_death");
    }

    private static boolean hasRecordedDeath(Actor actor, int takeTick) {
        for (ActorSound cue : actor.take.soundsAt(takeTick)) {
            if (cue.soundId() != null && isDeathSound(cue.soundId())) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasRecordedHurt(Actor actor, int takeTick) {

        for (int tick : new int[] { takeTick, takeTick - 1 }) {
            for (ActorSound cue : actor.take.soundsAt(tick)) {
                if (cue.soundId() != null && isHurtSound(cue.soundId())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean containerTransitionNearby(Actor actor, int takeTick, ActorSound cue) {
        for (ActorContainerInteraction interaction : actor.take.containerAt(takeTick)) {
            String action = interaction.action();
            if (!"open".equals(action) && !"close".equals(action)) {
                continue;
            }
            double dx = cue.x() - (interaction.x() + 0.5);
            double dy = cue.y() - (interaction.y() + 0.5);
            double dz = cue.z() - (interaction.z() + 0.5);
            if (dx * dx + dy * dy + dz * dz <= 6.25) {
                return true;
            }
        }
        return false;
    }

private static void applyConditioning(ReplayServer server, ActorClonePlayer clone,
                                      ActorTake take, int takeTick, boolean simulated) {
        if (simulated) { clone.syncEffectVisibility(); return; }
        Map<String, ActorEffect> wanted = take.effectSetAt(takeTick);
        for (Holder<MobEffect> active : List.copyOf(clone.getActiveEffectsMap().keySet())) {
            if (!wanted.containsKey(Compat.effectId(active))) {
                clone.removeEffect(active);
            }
        }
        for (ActorEffect effect : wanted.values()) {
            Holder<MobEffect> holder = Compat.effectHolder(effect.effectId());
            if (holder == null) {
                continue;
            }
            MobEffectInstance current = clone.getEffect(holder);
            if (current != null && current.getAmplifier() == effect.amplifier()) {
                continue;
            }
            clone.addEffect(new MobEffectInstance(holder,
                Math.max(1, effect.duration()), effect.amplifier(), false, false, true));
        }

        if (!simulated) {
            ActorVitals vitals = take.vitalsAt(takeTick);
            if (vitals != null) {
                clone.setHealth(vitals.health());
                clone.setAbsorptionAmount(vitals.absorption());
            }
        }

        clone.syncEffectVisibility();
    }

    private static void pushEntityData(ReplayServer server, ActorClonePlayer clone) {
        List<SynchedEntityData.DataValue<?>> changed = clone.getEntityData().packDirty();
        if (changed != null && !changed.isEmpty()) {
            broadcast(server, new ClientboundSetEntityDataPacket(clone.getId(), changed));
        }
    }

private static void applyEquipment(ReplayServer server, ActorClonePlayer clone,
                                       ActorTake take, int takeTick, GearLoadout gear) {
        EquipmentSlot[] slots = EquipmentSlot.values();
        ActorEquip[] loadout = take.equipmentAt(takeTick, slots.length);

        for (EquipmentSlot slot : slots) {
            int index = slot.ordinal();
            ActorEquip equip = index < loadout.length ? loadout[index] : null;
            ItemStack stack = equip != null
                ? Compat.itemStackFromNbt(clone.level(), equip.itemId(), equip.count(), equip.nbt())
                : ItemStack.EMPTY;
            clone.setItemSlot(slot, stack);
        }

        GearApplier.apply(clone, gear);

        List<Pair<EquipmentSlot, ItemStack>> changes = new ArrayList<>();
        for (EquipmentSlot slot : slots) {
            changes.add(Pair.of(slot, clone.getItemBySlot(slot)));
        }

        ClientboundSetEquipmentPacket packet =
            new ClientboundSetEquipmentPacket(clone.getId(), changes);
        for (ServerPlayer viewer : server.getReplayViewers()) {
            viewer.connection.send(packet);
        }
    }

    private static void applyMorph(Actor actor, ActorClonePlayer clone) {
        if (!pdx.dev.fbactors.settings.FbSettings.morphEnabled()) {
            MorphApplier.apply(clone, null, null);
            return;
        }
        MorphSelection selection = Morph.effectiveSelection(actor.take.id);
        if (selection == null || !selection.isMorphed()) {
            MorphApplier.apply(clone, null, null);
            return;
        }
        MorphDefinition definition = Morph.definitionFor(actor.take.id);
        MorphApplier.apply(clone, definition, selection);
    }

    private static void fireEvents(ReplayServer server, ServerLevel level, Actor actor, int takeTick) {
        ActorClonePlayer clone = actor.clone();
        if (clone == null) {
            return;
        }

        for (ActorEvent event : actor.take.eventsAt(takeTick)) {
            switch (event.type()) {
                case HURT -> {
                    if (pdx.dev.fbactors.settings.FbSettings.combatEnabled()) {
                        signalHit(server, level, clone);
                    }
                }
                case SWING_MAIN -> Compat.replaySwing(clone, InteractionHand.MAIN_HAND, event);
                case SWING_OFF -> Compat.replaySwing(clone, InteractionHand.OFF_HAND, event);
                case USE_ITEM -> Compat.swing(clone, InteractionHand.MAIN_HAND);
case ATTACK -> {
                    if (actor.take.eventsAt(takeTick).stream().noneMatch(e -> e.type() == ActorEvent.Type.SWING_MAIN))
                        Compat.swing(clone, InteractionHand.MAIN_HAND);
                }
            }
        }
    }
}
