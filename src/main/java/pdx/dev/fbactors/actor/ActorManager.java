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

    /**
     * Takes whose shot phase has already complained about a repeated tick.
     *
     * <p>Held here rather than on {@link Actor} so the warn-once behaviour matches
     * what {@code InputSim} does with its own {@code repeatWarned} flag: one line
     * per actor per session, not one per scrub. A take id is used as the key
     * rather than an {@code Actor} so the entry does not pin a despawned actor's
     * clone in memory.
     */
    private static final Set<UUID> SHOT_REPEAT_WARNED = new HashSet<>();

    /**
     * Takes whose spawn phase has already complained about a repeated tick.
     * Same keying as SHOT_REPEAT_WARNED: take id, not Actor, so no pin.
     */
    private static final Set<UUID> SPAWN_REPEAT_WARNED = new HashSet<>();

    /**
     * The gear loadout last broadcast per take, so a wardrobe change made
     * mid-playback re-runs the equipment pass (and its broadcast) on the tick
     * it lands instead of waiting for the take's next recorded equipment
     * change. Keyed by take id rather than entity for the same reason
     * {@code SHOT_REPEAT_WARNED} is: the entry must not pin a despawned
     * clone, and it is dropped in {@link #despawn} with everything else.
     */
    private static final Map<UUID, GearLoadout> LAST_GEAR_SENT = new java.util.HashMap<>();

    public static void signalHit(ReplayServer server, ServerLevel level, Entity target) {
        signalHitPacket(server, level, target);
        level.playSound(null, target.getX(), target.getY(), target.getZ(),
            SoundEvents.PLAYER_HURT, SoundSource.PLAYERS, 1.0f, 1.0f);
    }

    /**
     * The damage packet half of {@link #signalHit}, without the generic hurt
     * sound.
     *
     * <p>Used when a replayed hit lands on a tick carrying its own recorded
     * hurt cue: the cue replays the specific sound vanilla chose (drown,
     * burn, freeze), and the generic hurt on top would double it. The packet
     * - the client's red flash - is still owed, because no vanilla attack
     * runs on a replay pass.
     */
    public static void signalHitPacket(ReplayServer server, ServerLevel level, Entity target) {
        ClientboundDamageEventPacket packet =
            new ClientboundDamageEventPacket(target, level.damageSources().generic());
        for (ServerPlayer viewer : server.getReplayViewers()) {
            viewer.connection.send(packet);
        }
    }

    /**
     * Puts the clone's head yaw on the wire for this tick.
     *
     * <p>Vanilla splits a player's orientation across two packets and the head
     * yaw travels on its own. The only thing that normally sends it is the
     * {@code ServerEntity} tracking the entity. The clone does have a tracker
     * (it joins the level through {@code placeNewPlayer}, which adds it), but
     * the head packet is cheap and this guarantees the head never pins at its
     * spawn orientation: without it the head would stay fixed for the whole
     * replay, and because a real player's head is moving almost constantly, one
     * that never does reads as a mannequin rather than as a person. The yaw is
     * packed the way vanilla packs it, one 256th of a turn per byte, because the
     * client unpacks it that way and a differently-scaled byte would spin it.
     *
     * <p>Body movement is deliberately NOT sent here. The tracker's own
     * relative-move and teleport packets already carry it, interpolated the way
     * vanilla interpolates every walking entity. Hand-sending a second movement
     * stream alongside the tracker double-applies each step on the client and
     * reads as back-and-forth bugging, which is exactly what a previous
     * revision of this method did.
     */
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

    private final List<Actor> actors = new ArrayList<>();

    public List<Actor> actors() {
        return Collections.unmodifiableList(this.actors);
    }

    public Actor add(ActorTake take) {
        Actor actor = new Actor(take);
        this.actors.add(actor);
        return actor;
    }

    /**
     * The actor whose clone wears this identity, or null.
     *
     * <p>Looked up by the clone's own UUID rather than by take id. The clone's
     * profile is built from the take id, so the two are the same value today, but
     * asking for the UUID an entity actually has is what combat needs: it arrives
     * as {@code Entity.getUUID()} from a raycast hit or a damage source, with no
     * knowledge of which take it belongs to. Relying on the two being equal is an
     * assumption about how the profile was constructed, and it is exactly the kind
     * that breaks silently if the profile is ever regenerated.
     */
    @Nullable
    public Actor byUuid(UUID entityId) {
        for (Actor actor : this.actors) {
            if (actor.clone() != null && actor.clone().getUUID().equals(entityId)) {
                return actor;
            }
        }
        // Fall back to the take id so a hit that landed before the clone spawned,
        // or on a take whose clone is mid-respawn, still resolves.
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
        if (server != null) {
            for (Map.Entry<BlockPos, Integer> outstanding : actor.outstandingMining()) {
                broadcast(server,
                    new ClientboundBlockDestructionPacket(outstanding.getValue(),
                        outstanding.getKey(), -1));
            }
        }
        // Off the vehicle before the body is discarded: a discarded passenger
        // leaves the vehicle holding a stale reference, and the next clone
        // spawned for this actor would find its boat already "full".
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

    /**
     * Discards all world entities this actor's playback spawned.
     *
     * <p>Best-effort: a stolen boat or missing UUID is skipped, never fatal.
     * Called on despawn so takes leaving the timeline do not litter the
     * replay world with orphaned boats and frames.
     */
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

    /**
     * Discards spawns whose tick is now after the playhead.
     *
     * <p>Scrub-backwards convergence: entities placed at ticks the playhead
     * left behind go away, entities at or before it stay (re-latch, not
     * re-spawn, is what makes back-and-forth scrubbing converge). Runs on
     * every tick, not just fresh ones, because a scrub can land on an
     * already-driven tick.
     */
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

    /**
     * When each actor last had a lifecycle line logged, so a hot loop names
     * itself a few times instead of burying the log.
     */
    private static final Map<UUID, Long> LAST_CYCLE_LOG = new java.util.HashMap<>();

    /**
     * Logs why a clone is being (re)spawned.
     *
     * <p>Respawns are rare in normal playback: the first tick an actor covers,
     * a scrub, a dimension change, a death played backwards. A respawn every
     * few ticks is never normal -- it removes and re-adds the entity on every
     * pass, which reads on screen as the actor blinking in and out -- and the
     * reason names the half that is churning. Throttled to one line per actor
     * per five seconds so the loop it reports cannot spam through it.
     */
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
        // Host damage re-applies after every actor - and after playback has
        // written the tick - so a host the fight reached keeps its
        // take-driven health, shove and death pose instead of having the
        // packets write the recording back over them. Runs with no actors too:
        // replaying host damage needs no cast. Untouched hosts cost one map
        // lookup each; see HostManager for the ownership rules.
        HostManager.applyTick(server, replayTick);
        pdx.dev.fbactors.voice.VoicePlayer.tick(replayTick,
            server.getReplayTick() == replayTick && this.lastAppliedTick == replayTick);
        this.lastAppliedTick = replayTick;
    }

    /**
     * The replay server from the last tick that ran. Block-break overlays live
     * on the client rather than the world, so they can only be withdrawn from a
     * server to hand packets to, including on the paths that tear an actor down
     * outside a tick.
     */
    @Nullable
    private ReplayServer server;

    private static void broadcast(ReplayServer server, Packet<?> packet) {
        for (ServerPlayer viewer : server.getReplayViewers()) {
            viewer.connection.send(packet);
        }
    }

    private int lastAppliedTick = Integer.MIN_VALUE;

    /**
     * What {@link Flashback#isExporting()} reported on the previous tick, so
     * the export diagnostics below fire once per transition rather than once
     * per tick.
     *
     * <p>Export is the one playback mode where the replay is paused and the
     * playhead is advanced a tick at a time while the server keeps ticking
     * underneath, which is exactly the shape that turns a latent
     * state-vs-render split into a visible one: the editor shows one thing
     * and the exported video shows another, and nothing in the per-tick log
     * says which half moved. Remembering the previous value here is what lets
     * the two transitions each log once. The field lives on the manager rather
     * than in a static because there is exactly one manager per session and a
     * static would leak the previous session's export state into the next
     * replay open, logging a phantom exit transition on the first tick.
     *
     * <p>Read and written only on the server tick thread inside
     * {@link #applyTick}, so no synchronisation is needed: the same thread
     * that mutates it is the only thread that ever reads it.
     */
    private boolean lastExporting = false;

    /**
     * Logs which half of the actor state breaks across an export, once per
     * export entry and once per export exit.
     *
     * <p>Why this exists at all: when an export renders actors without skins,
     * morphs, or gear that the editor plainly shows, there are two candidate
     * explanations and they point at different code. Either the live selection
     * maps drained (the editor rebuilt them empty on replay open, the file
     * restore never ran, the take ids shifted) and there is genuinely nothing
     * to render, or the maps are intact and the export render path never asked
     * for them (a client-side lookup that returns null off the export thread,
     * a render-state map keyed on an identity the exporter does not share).
     * The entry line names the map sizes while the export is starting, so the
     * next export log answers that question directly: empty maps mean a state
     * problem, intact maps mean a render-path problem.
     *
     * <p>Why on the transitions and not every tick: the replay stays paused
     * for the whole of an export, so a per-tick line would repeat the same
     * census hundreds of times, once per exported frame. One line on entry
     * and one line per actor on exit is the whole diagnosis at a constant
     * log cost.
     *
     * <p>Why the exit line carries a clone-vs-frame gap: the gap is computed
     * exactly the way {@link #logMovement} computes it, clone position minus
     * recorded frame position, so an export that moved bodies and an export
     * that lost cosmetics can be told apart in the same log. A gap near zero
     * with lost cosmetics says the bodies played back fine and only the
     * appearance link broke; a large gap says the simulation itself diverged
     * under export stepping.
     *
     * <p>Why everything is guarded: this method reads editor state, skin
     * maps, morph stores and clone positions that can each be null, empty, or
     * mid-teardown at the moment an export starts or stops. A diagnostic that
     * throws inside {@link #applyTick} would break playback in order to report
     * on it, which is the one outcome worse than no diagnostic. Every read
     * below is therefore null-tolerant and the whole probe sits inside a
     * try/catch that logs a warning and carries on.
     *
     * <p>Why entry also heals: the entry line once read {@code fileSkins=1}
     * with both live maps at zero, seconds after the pick that wrote them,
     * while the viewport showed the file skin correctly. Diagnosing the drain
     * without repairing it would cost the user one export per hypothesis, so
     * entry re-runs the spawn order ({@link ActorSkins#restoreFiles} then
     * {@link ActorSkins#applyDefault}) for actors missing an entry and reports
     * the before-and-after sizes with a {@code reappliedSkins} count. Entry
     * only, because the drain happens between the pick and the export, and a
     * per-tick rebuild would re-upload every texture every frame.
     *
     * <p>Why the exit line carries the worst gap of the whole export rather
     * than just the final tick: a body that leaves its frames mid-export and
     * is back on them by the end would read as correct on the final tick
     * alone. The per-tick maximum names the outlier frame instead.
     *
     * @param replayTick the replay tick being applied, logged so the entry
     *                   and exit lines can be correlated with the surrounding
     *                   playback log
     */
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
                    // Sizes stay at -1, which the line below reports as unknown.
                }
                int morphedTakes = -1;
                try {
                    morphedTakes = pdx.dev.fbactors.morph.Morph.store().size();
                } catch (Exception ignored) {
                    // Reported as unknown below.
                }
                int renderStates = -1;
                try {
                    renderStates =
                        pdx.dev.fbactors.morph.MorphRenderState.snapshot().size();
                } catch (Exception ignored) {
                    // Reported as unknown below.
                }
                EXPORT_MAX_GAP = 0.0;
                EXPORT_MAX_GAP_TICK = Integer.MIN_VALUE;
                EXPORT_MAX_GAP_ACTOR = null;
                int reappliedSkins = 0;
                try {
                    reappliedSkins = healExportSkins();
                } catch (Exception ignored) {
                    // The entry line below still reports the unhealed sizes.
                }
                try {
                    com.moulberry.flashback.state.EditorState healed =
                        com.moulberry.flashback.state.EditorStateManager.getCurrent();
                    if (healed != null) {
                        liveSkins = healed.skinOverride.size();
                        liveFileSkins = healed.skinOverrideFromFile.size();
                    }
                } catch (Exception ignored) {
                    // Sizes stay as first read; the line below says so.
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

    /**
     * Re-applies persisted file skins into the live override maps at export entry.
     *
     * <p>Why the export heals what the spawn path already restores: on 2026-10-14
     * an export started with {@code fileSkins=1} and both live override maps at
     * zero, eight seconds after the pick that wrote them, while the viewport
     * showed the file skin correctly. The persisted choices survived (they are
     * read from disk, not from the maps), but the live maps the exporter reads
     * did not, so the export rendered default skins. Re-running the exact spawn
     * order here - {@link ActorSkins#restoreFiles} then
     * {@link ActorSkins#applyDefault} - puts the choices back into whatever
     * state is current at export time, no matter which reopen, reload, or
     * dialog drained them in between.
     *
     * <p>Why entry-only and not every export tick: rebuilding a file skin
     * re-reads its PNG and re-uploads its texture, which per tick per actor
     * would turn the export into a slideshow for a different reason. The drain
     * this heals happens between the pick and the export, not during it, so
     * once per entry is the whole fix at a constant cost.
     *
     * <p>Why actors that already have an entry are skipped: without the check
     * every export would rebuild every skin it was about to render anyway,
     * paying the texture upload for maps that were never drained.
     *
     * @return how many actors gained an override entry they were missing
     */
    private int healExportSkins() {
        int reapplied = 0;
        try {
            ExportSkinProbe.arm();
        } catch (Exception ignored) {
            // The heal below still runs; only the client probe loses its trigger.
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
                // One actor's heal must never affect another's, and none of
                // this may reach the tick.
            }
        }
        return reapplied;
    }

    /**
     * Runs one actor through the tick, in the phase order
     * {@link InputSim} requires.

     *
     * <p>The order is prepare, baseline, step, actions, publish, and it is
     * enforced by throwing sentinels inside {@code InputSim} rather than by
     * convention here. Two orderings are genuinely wrong and both used to happen
     * silently: delivering a swing before the tick that walked the actor into
     * range, which made the swing miss, and stepping the body from both here and
     * the clone's own tick, which made it move at double speed.
     */
    /**
     * Fires the take's arrows for this tick, once per take tick.
     *
     * <p>Guarded on the same {@link Actor#lastAppliedTakeTick()} latch the mining
     * and event phases use, and for the same reason. That latch is what makes a
     * repeated take tick a scrub rather than a double drive: the playhead can be
     * presented on the same tick twice ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â once paused, once after a seek that
     * landed where it already was ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â and a projectile is the one kind of authored
     * action that cannot be idempotent. Re-running a swing replays an animation
     * and nothing else, but re-running a shot puts a second real arrow in the
     * world, and the first is already in flight. Warning rather than throwing
     * matches the surrounding phases, which treat a repeat as something to report
     * and then get on with.
     *
     * <p>Fired after the body has been stepped and before {@code syncPosition}, in
     * the same phase as {@code runActions}, because that is where "what happened
     * this tick" lives. See the call site for why the ordering within the phase is
     * what it is.
     */
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

    /**
     * Replays discrete uses as timed arm swings of the correct hand.
     *
     * <p>Idempotent animation only, so no scrub latch: re-running a swing
     * replays an animation and nothing else. World fallout (door state,
     * container open, mount) replays through its own track.
     */
    private static void fireUses(ServerLevel level, Actor actor, int takeTick) {
        if (!actor.take.hasUseAt(takeTick)) {
            return;
        }
        ActorClonePlayer clone = actor.clone();
        if (clone == null) {
            return;
        }
        for (pdx.dev.fbactors.take.ActorUse use : actor.take.usesAt(takeTick)) {
            Compat.swing(clone,
                use.offHand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        }
    }

    /**
     * Replays placed-entity spawns, once per take tick.
     *
     * <p>Latched like shots: a repeated tick is a scrub, not a new tick, and
     * re-running it must not put a second boat in the world. Spawned UUIDs
     * are tracked on the actor for scrub-backwards pruning and despawn
     * cleanup. Sounds are gated on forward pass only.
     */
    private static void fireSpawns(ServerLevel level, Actor actor, int takeTick) {
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
                }
            } catch (RuntimeException | LinkageError ex) {
                pdx.dev.fbactors.FlashbackActors.LOGGER.warn(
                    "Actor '{}' failed to spawn {} at take tick {}",
                    actor.take.name, spawn.entityId(), takeTick);
            }
        }
    }

    /**
     * Computes the take tick position for a given base tick and speed multiplier.
     *
     * <p>This is the single point where the speed multiplier is applied to
     * playback. The base tick is the take-relative position the actor would be
     * at under normal speed ({@code replayTick - offsetTicks}); multiplying it
     * by the speed multiplier yields the actual take tick to display.
     *
     * <p>For example, with a speed of 2.0 and a base tick of 5, this returns
     * 10.0 - the actor is shown at take tick 10, having advanced 10 ticks in
     * the 5 replay ticks since the offset. With a speed of 0.5 and a base tick
     * of 10, this returns 5.0 - the actor has advanced only 5 take ticks in
     * 10 replay ticks.
     *
     * <p>The result is a float so that callers can accumulate fractional
     * progress if needed. {@link #applyOne} casts to int, which naturally
     * floors the value and means a speed of 0.5 advances the actor one take
     * tick every two replay ticks.
     *
     * <p>The alternative - tracking a running accumulator on the actor - was
     * rejected because it introduces mutable playback state that must be
     * reset on despawn and clone respawn, and because the pure-function form
     * is trivially testable and cannot drift out of sync with the replay tick.
     *
     * @param baseTicks the take-relative tick without speed applied;
     *                  typically {@code replayTick - offsetTicks}
     * @param speed     the speed multiplier from
     *                  {@link ActorTake#getSpeedMultiplier()}
     * @return the actual take tick position as a float
     */
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

        // Heals a spawn-race drain within one tick: when the spawn-time
        // restore ran before the editor state was current, the live maps stay
        // empty all session and a synchronous single-frame capture renders
        // before the export-entry heal. Runs on fresh and repeat ticks alike
        // so either thread schedule heals before the renderer reads.
        ActorSkins.ensureLive(actor);

   int deathTick = actor.take.deathTick();
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
        // A body still in its corpse state on a tick the actor is alive is a
        // scrub that crossed a death backwards. The fall-over cannot be undone:
        // it is driven by the *viewer's* own deathTime, which only ever climbs
        // and which nothing on the wire can clear, so the only honest way back to
        // a standing body is a new one. Treated as a reason to respawn, exactly
        // like a removal or a dimension change.
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
      // File picks are restored before the default skin: the maps are rebuilt
      // empty on every replay open, and without this the file skins they held
      // die with the session that picked them. applyDefault below no-ops
      // once an override exists, so ordering here is the whole mechanism.
      ActorSkins.restoreFiles(actor);
      ActorSkins.applyDefault(actor);
        justSpawned = true;
        }

        // A take tick that has already been driven is a repeat, not a new tick.
        //
        // Flashback presents the same tick more than once. The replay is paused for
        // the whole of an export and advanced a tick at a time, the server keeps
        // ticking underneath a paused replay, and a seek that lands where the
        // playhead already was presents the tick again. Every other phase in this
        // method is already latched on the take tick for exactly that reason -
        // mining, containers, events, shots and the block edits all refuse to run
        // twice for one tick - and the body is the one that cannot: stepping it a
        // second time integrates the same knockback twice and carries the actor
        // somewhere the performer never was. That is the whole of the difference
        // between the fight as it plays in the editor and the fight an export
        // renders, so the latch has to cover the simulation as well.
        boolean freshTick = takeTick != actor.lastAppliedTakeTick();
        boolean simulated;
        if (freshTick) {
            simulated = actor.sim().prepare(level, clone, takeTick);

            // The baseline restores the recorded frame only while the take is still
            // authoritative. Once the actor has been struck this is skipped, which is
            // the whole point of the latch: writing the recorded position back on top
            // of a knockback would undo the hit that caused the divergence.
            actor.sim().applyBaseline(clone, frame, takeTick);

            // After the baseline and before the tick, which is the ordering
            // replayStrike documents: the baseline has just put the body on the frame
            // the performer was standing on, so a recorded hit shoves from the right
            // place, and the tick that follows integrates that shove the way the
            // performer's own tick did. This is what makes the second pass of a take
            // the same fight as the first - without it the actor walks straight
            // through every punch the take says it took, which is the desync between
            // what the viewer fought and what an export shows.
            //
            // A firing strike also owes the impact: no vanilla attack runs on a
            // replay pass, so the damage packet (the client's red flash) and the
            // hurt sound are sent here. A live punch on this same tick returns
            // false and keeps vanilla's own feedback, so nothing sounds twice.
            boolean struck = actor.sim().replayStrike(clone, takeTick);
            if (struck) {
                // The cue owns the sound when the take recorded this hit's
                // own hurt: the packet is still owed, the generic thud is not.
                if (hasRecordedHurt(actor, takeTick)) {
                    signalHitPacket(server, level, clone);
                } else {
                    signalHit(server, level, clone);
                }
            }

            // Before the tick, like the baseline: a mount has to be in place
            // before vanilla integrates, or the first riding tick simulates
            // on foot and the actor visibly steps beside the boat for a tick.
            // A dismount likewise releases the body before the tick moves it.
            applyRides(level, actor, takeTick);

            if (!actor.take.simulated()) {
                // A take with no input track is a legacy recording. It still plays
                // back exactly as recorded rather than being guessed at, but it still
                // needs the tick so its limbs animate and it accumulates fall distance.
                // The body belongs on the frame, like the simulated path's re-snap.
                // The limbs belong on the recording for the same reason they do over
                // in InputSim.step: the tick just integrated whatever displacement
                // this pass happened to simulate, and two passes reaching this tick
                // through different histories would otherwise stride differently on
                // the same frame. Guarded inside driveRecordedLimbs, so a diverged
                // legacy body keeps its live phase.
                clone.tick();
                if (actor.sim().usesRecordedBaseline(takeTick)) {
                    Compat.snapTo(clone, frame.x(), frame.y(), frame.z(),
                        frame.yRot(), frame.xRot());
                    actor.sim().driveRecordedLimbs(clone, takeTick);
                }
            } else {
                actor.sim().step(level, clone, takeTick);
            }

            // Push the tick's resulting velocity to the viewers when a recorded
            // hit fired above. Vanilla raises a sync flag on every knockback so
            // the tracker sends a motion packet with the shove; a replayed shove
            // is applied outside vanilla's knockback call, so without this the
            // client interpolates the slide across the tracker's background
            // cadence instead of extrapolating from the true velocity, which
            // reads as a steppy knockback. Sent after the tick so the packet
            // carries the integrated velocity, the way the tracker's own would.
            if (struck) {
                Compat.syncMotion(clone);
            }

            // Diagnostics only. Neither call changes behaviour; both exist so a log
            // from a failing session names the cause instead of leaving it to be seen.
            reportTakeShape(actor, takeTick);
            reportEmbedded(level, actor, clone, takeTick);
            actor.sim().runActions(clone, takeTick);

            // Immediately after the actions phase and before publish, for the same
            // reason runActions sits where it does: a shot has to leave a body that has
            // already been walked to where the performer stood. A phase earlier and the
            // arrow spawns from the previous tick's position; after publish and it
            // exists before the tick that moved the shooter was accounted for.
            fireShots(level, actor, clone, takeTick);
            fireSpawns(level, actor, takeTick);

            actor.sim().publish(clone, takeTick);
        } else {
            // The tick has already been driven, so the body is already where it
            // belongs. The only question worth answering again is which of the two
            // owns it, and that is a pure function of the take tick.
            simulated = !actor.sim().usesRecordedBaseline(takeTick);
        }

        if (!simulated && dead) {
    applyDeathFall(level, clone, frame, takeTick, deathTick);
        }

        syncHead(server, clone);
        applyHealth(actor, clone, takeTick, simulated, dead);
        if (dead) {
   clone.setPose(Pose.DYING);
}

   // The equipment pass runs on spawn, on a recorded change, and when the
   // gear loadout for the take changed since the last broadcast: a wardrobe
   // change made mid-playback would otherwise wait for the take's next
   // recorded change before reaching the viewers. The loadout lookup is one
   // map read per actor per tick, the same class of cost as the morph
   // selection lookup below.
   GearLoadout gear = Gear.loadoutFor(actor.take.id);
   if (justSpawned || actor.take.hasEquipmentChangeAt(takeTick)
       || !gear.equals(LAST_GEAR_SENT.get(actor.take.id))) {
            applyEquipment(server, clone, actor.take, takeTick, gear);
            LAST_GEAR_SENT.put(actor.take.id, gear);
      }

        // Morph runs here, after the take's own equipment, exactly where the
        // ordering contract on MorphApplier says it must: a quadruped's cleared
        // slots would otherwise be refilled before anyone sees them. Skipped
        // for the dead, whose DYING pose was written above and must win.
        if (!dead) {
            applyMorph(actor, clone);
        }

        // Block edits go here, after the body has been stepped for this tick.
        // Moving them earlier was tried and reverted: placing the world before
        // vanilla physics runs puts the clone inside the block it is about to stand
        // on, because the recorded frame for the placement tick is where the
        // performer was BEFORE the block existed, not on top of it. That embedded
        // the actor in geometry instead of a tick late. Getting towering right needs
        // the placement and the jump reconciled against each other, not one moved
        // arbitrarily ahead of the other.
        if (takeTick != actor.lastAppliedBlockTick()) {
            applyBlocks(level, actor, takeTick, clone);
            actor.markBlocksApplied(takeTick);
            applyBlockEntities(server, level, actor, takeTick);
        }


        // Scrub pruning runs on every tick, not just fresh ones: a seek can
        // land on an already-driven tick whose spawns are now in the future.
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
                // The cue owns the sound when the take recorded death's own:
                // a specific drown or burn death beats the generic thud, and
                // both together thud twice.
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

        // Last, so one packet carries every write this tick made -- health, the
        // death pose, the effects -- and outside the alive-only block above, so a
        // dead actor's health reaches the viewers and the client can start the
        // fall-over.
        pushEntityData(server, clone);
        logMovement(actor, clone, frame, takeTick, simulated, dead);
    }

    /** Whether an export is running now, set by the probe on every tick. */
    private static boolean EXPORT_ACTIVE = false;

    /** Largest clone-vs-frame gap seen on any export tick, reset at entry. */
    private static double EXPORT_MAX_GAP = 0.0;

    /** Take tick that gap was seen at, for the export exit line. */
    private static int EXPORT_MAX_GAP_TICK = Integer.MIN_VALUE;

    /** Actor that gap was seen on, for the export exit line. */
    private static String EXPORT_MAX_GAP_ACTOR = null;

    /** Last take tick each actor had a movement line logged for. */
    private static final Map<UUID, Integer> LAST_MOVE_LOG = new java.util.HashMap<>();

    /** Whether each actor was simulated on its last logged line. */
    private static final Map<UUID, Boolean> LAST_SIM_STATE = new java.util.HashMap<>();

    /**
     * Traces what the body is doing, once a second per actor.
     *
     * <p>One line per actor per twenty take ticks: where the clone is, where
     * the recording says it should be, how far apart those two are, how fast
     * the body is travelling, and who owns it. That is enough to separate the
     * four movement failure shapes without spamming: a frozen actor shows the
     * same position while the take tick advances; a teleporting one shows the
     * position jumping between lines; a diverged one shows the gap growing
     * while {@code sim} reads {@code sim}; a pose fight shows the pose
     * flipping between lines.
     *
     * <p>Take ticks pace it rather than wall time, so a paused replay logs one
     * line and goes quiet instead of filling the log with twenty identical
     * lines a second.
     */
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
        // >= rather than >: with MAX starting at 0.0 a strict comparison never
        // records an all-zero-gap export, which then exits claiming no frames
        // were tracked despite rendering every one of them.
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

    /** One decimal, so the trace reads as metres rather than noise. */
    private static String fmt(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    /**
     * Which health source owns this tick.
     *
     * <p>The take's recorded vitals and the hit-derived health model used to both
     * write the clone's health in the same tick, with the vitals applied second
     * because {@code applyConditioning} runs after the hit check. Vitals therefore
     * always won, and they are sampled on exactly the ticks the performer was
     * being punched, so the hit model was silently dead whenever it mattered.
     * Knockback and the visible health bar were driven by two different numbers.
     *
     * <p>Now there is one rule: while the take is authoritative the vitals are
     * the performance, and after divergence the simulation is, and nothing writes
     * recorded health over a body that has already taken a hit.
     */
    private static void applyHealth(Actor actor, ActorClonePlayer clone, int takeTick,
                                    boolean simulated, boolean dead) {
        if (!pdx.dev.fbactors.settings.FbSettings.combatEnabled()) {
            return;
        }

        // A body the take has called dead has to be dead on the wire, and this is
        // the only thing that makes it so. The viewer's client plays the fall-over
        // from the synced health and from nothing else, so an actor whose health
        // is still a live value while the take has already called it dead dies in
        // complete silence: the death sound plays and the body stays standing.
        //
        // Zero is also the only value that can be right here. The take derived the
        // death from its own hit total, so its own health model says zero too, and
        // the vitals samples are the *performer's* and are sampled on their ticks,
        // not the actor's -- they routinely stop short of the tick an actor was
        // killed on, or sit at full health through a fight the viewer fought.
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
        // No vitals sample for this tick and the actor is still performing, so
        // fall back to what the take's own hits add up to. Once diverged, the
        // clone's real health is correct and writing either of these over it would
        // heal the actor back up after every punch.
        if (!simulated) {
      clone.setHealth(actor.take.healthAt(takeTick));
        }
    }

    /**
     * Slides a dead actor down onto the ground it fell on.
     *
     * <p>Separate from the baseline because it has to run after the tick: the
     * body has to be at the death position for the ground search to mean
     * anything, and lerping it first would search from a height nothing is at.
     */
    private static void applyDeathFall(ServerLevel level, ActorClonePlayer clone,
                                       ActorFrame frame, int takeTick, int deathTick) {
        double posX = clone.getX();
        double posY = clone.getY();
        double posZ = clone.getZ();
        double groundY = groundBelow(level, posX, posY, posZ);
        double fall = Math.min(1.0, (takeTick - deathTick) / (double) DEATH_FALL_TICKS);
        clone.setPos(posX, posY + (groundY - posY) * fall, posZ);
    }


    /**
     * Logs the shape of a take once, the first time it is stepped.
     *
     * <p>Whether a take drives its actors from recorded input or from recorded
     * frames decides everything downstream, and until now that fact was only
     * visible by reading the file on disk. One line naming it in the log turns
     * "the actor stood still" from a guess into something checkable.
     */
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

    /**
     * Names the "actor is standing inside geometry" case by name.
     *
     * <p>Reported once per actor. {@code isAir} is the only predicate used because
     * a collision-shape query would be one more method that moved between targets,
     * and a false positive on a non-solid block costs one log line whereas a
     * missing one costs the whole diagnosis.
     */
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
    /**
     * Applies block-entity data changes - sign text, banner patterns, chest
     * contents, and any other tile data that lives inside a {@code BlockEntity}
     * rather than in the {@code BlockState}.
     *
     * <p>Block entities carry state that {@code BlockState} does not encode: a
     * sign's text, a banner's pattern list, a chest's contents. {@link ActorBlockEdit}
     * records the before/after {@code BlockState}, which says "this is an oak
     * sign" but not what the sign <em>says</em>. This method reads the latest
     * {@link ActorBlockEntityEdit} at or before the playhead for each position
     * and applies the recorded NBT snapshot to the block entity in the world.
     *
     * <p>Like {@link ActorBlockEntityEdit} itself, this is a desired-state
     * replay: the latest entry per position wins, and intermediate edits are
     * not replayed. A scrub backwards and forwards through a looting session
     * converges on the correct block-entity data without replaying every
     * intermediate step.
     *
     * <p>Called from {@link #applyOne} after {@link #applyBlocks}, because
     * block-entity edits are keyed on the same positions as block edits and
     * must be applied after the block state has been restored - a chest's
     * contents cannot be written to a chest that does not exist yet.
     *
     * @param server   the replay server, for broadcasting packets to viewers
     * @param level    the level the actor is in
     * @param actor    the actor whose take is being played back
     * @param takeTick the current take tick
     */
    private static void applyBlockEntities(ReplayServer server, ServerLevel level,
                                           Actor actor, int takeTick) {
        Map<BlockPos, ActorBlockEntityEdit> edits = actor.take.blockEntityAt(takeTick);
        if (edits.isEmpty()) {
            return;
        }

        BlockEdits.beginApply();
        try {
            for (ActorBlockEntityEdit edit : edits.values()) {
                // COMPAT: applyBlockEntityNbt applies the recorded NBT snapshot
                // to the block entity at the given position. This handles sign
                // text, banner patterns, chest contents, and any other block
                // entity data that isn't captured by BlockState. The type
                // discriminator lets the compat layer decide whether to bother
                // decoding the NBT at all.
                Compat.applyBlockEntityNbt(level, edit.pos(), edit.type(), edit.nbt());
            }
        } finally {
            BlockEdits.endApply();
        }
    }

    /**
     * Replays container interaction events - opening, closing, and slot
     * changes - for the current take tick.
     *
     * <p>Containers are block entities whose contents are not part of the
     * block state and are not captured by {@link ActorBlockEntityEdit}. That
     * record stores the <em>result</em> of a change (the full NBT after the
     * edit), but it cannot capture the <em>act</em> of opening or closing,
     * because opening and closing leave no trace in the block entity's NBT.
     * A chest that was opened and closed without a slot change is, from the
     * NBT's point of view, identical to one that was never touched.
     *
     * <p>This method reads every {@link ActorContainerInteraction} at exactly
     * the current take tick and replays each one:
     *
     * <ul>
     *   <li>{@code "open"} - sends the appropriate packet to viewers so they
     *       see the container GUI open.</li>
     *   <li>{@code "close"} - sends the appropriate packet to viewers so they
     *       see the container GUI close.</li>
     *   <li>{@code "slot_change"} - updates the container's contents at the
     *       recorded slot with the reconstructed item stack.</li>
     * </ul>
     *
     * <p>Container interactions are discrete events, not desired-state
     * snapshots, so this method is called once per take tick and replays
     * every interaction that happened on that tick. A performer can open a
     * chest, change a slot, and close it all on the same tick, and all three
     * events are replayed.
     *
     * @param server   the replay server, for broadcasting packets to viewers
     * @param level    the level the actor is in
     * @param actor    the actor whose take is being played back
     * @param takeTick the current take tick
     */
    private static void applyContainers(ReplayServer server, ServerLevel level,
                                        Actor actor, int takeTick) {
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
                    // COMPAT: openContainer sends the appropriate packet to
                    // viewers so they see the container GUI open. The packet
                    // includes the container type and position so the client
                    // can render the correct GUI.
                    Compat.openContainer(server, level, clone, interaction.pos());
                }
                case "close" -> {
                    // COMPAT: closeContainer sends the appropriate packet to
                    // viewers so they see the container GUI close.
                    Compat.closeContainer(server, clone);
                }
                case "slot_change" -> {
                    // Reconstruct the full ItemStack from the recorded NBT,
                    // then update the container's contents at the given slot.
                    // COMPAT: itemStackFromNbt reconstructs the full ItemStack
                    // including enchantments, custom names, and other components
                    // from the recorded NBT.
                    ItemStack stack = Compat.itemStackFromNbt(level,
                        interaction.itemId(), interaction.count(), interaction.nbt());
                    // COMPAT: updateContainerSlot updates the container's
                    // contents at the given slot with the given item stack.
                    // This writes to the block entity's NBT directly.
                    Compat.updateContainerSlot(level, interaction.pos(),
                        interaction.slot(), stack);
                }
            }
        }
    }


    /**
     * Replays ride transitions - mounts and dismounts - for the current take tick.
     *
     * <p>Called before the simulation step so a mount is in place while
     * vanilla integrates: the vehicle's own tick reads its passenger's input,
     * which {@link pdx.dev.fbactors.actor.InputSim} has just written, so a
     * clone driving a boat steers it the way the performer did. Runs under
     * the fresh-tick latch like every other discrete phase, so a repeated
     * tick (a scrub landing where the playhead already was) never boards twice.
     *
     * <p>The vehicle is the same world entity the performer rode, resolved by
     * UUID: the replay world still holds it, so no spawn is needed. When it is
     * gone (cleared by a scrub, removed by another take), the mount is skipped
     * with a warning rather than invented: boarding the wrong boat reads worse
     * than standing on the dock, and the baseline keeps the body on the
     * recorded frames either way. Spawning a replacement vehicle is future
     * work documented in {@code docs/spawn-track-design.md}.
     */
    private static void applyRides(ServerLevel level, Actor actor, int takeTick) {
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


    /**
     * Replays the take's recorded sound cues for the current take tick.
     *
     * <p>Each cue is resolved back to its {@code SoundEvent} through the
     * per-target shim and rebroadcast at its recorded position, volume,
     * pitch and source. Runs under the playback apply-guard so a take being
     * recorded on the same server never files another actor's replayed cues
     * as its own performance. Unknown sounds (a mod sound missing on this
     * install) are skipped, never fatal: one silent cue beats a dead tick.
     *
     * <p>Like every other discrete phase this runs once per take tick under
     * the fresh-tick latch, so scrubs never stack the same thud twice.
     *
     * <p>Three cue families are owned by other phases and skipped here:
     * lid sounds at a container being opened or closed (the container pass
     * plays those), hurt sounds on a tick the actor swings or is struck
     * (the live strike or the replayed-hit feedback owns those), and death
     * sounds on the death tick (the death pass owns that). Without the
     * carve-outs every one of them would play twice.
     */
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


    /**
     * Whether a cue's audio belongs to another playback phase.
     *
     * <p>Lid sounds at a container with an open/close event this tick belong
     * to the container pass; hurt sounds on a tick with a swing or a
     * recorded hit belong to the strike feedback; death sounds on the death
     * tick belong to the death pass. Matching is deliberately narrow (id
     * substrings, same tick, nearby) so a place thud landing next to a chest
     * still plays.
     */
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

    /** Whether the take recorded death's own sound on this tick. */
    private static boolean hasRecordedDeath(Actor actor, int takeTick) {
        for (ActorSound cue : actor.take.soundsAt(takeTick)) {
            if (cue.soundId() != null && isDeathSound(cue.soundId())) {
                return true;
            }
        }
        return false;
    }

    /** Whether the take recorded this hit's own hurt sound on either firing tick. */
    private static boolean hasRecordedHurt(Actor actor, int takeTick) {
        // An impulse hit fires a tick after it landed (see replayStrike), so
        // both the landing tick and the firing tick are checked.
        for (int tick : new int[] { takeTick, takeTick - 1 }) {
            for (ActorSound cue : actor.take.soundsAt(tick)) {
                if (cue.soundId() != null && isHurtSound(cue.soundId())) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Whether a container opened or closed within a couple blocks this tick. */
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


/**
 * Puts the clone's active effects, health and absorption where the performance
 * had them.
 *
 * <p>Effects are a desired state rather than a delta: the clone is made to
 * match the take's set for this tick, which is idempotent and so survives the
 * playhead jumping without having to replay every add and remove.
 *
 * <p>Effects go first, and that ordering is load bearing.
 * {@code Attributes.MAX_ABSORPTION} defaults to zero and is only raised by the
 * {@code absorption} effect's own attribute modifier, so
 * {@code setAbsorptionAmount} before the effect has been added clamps a
 * golden apple's four points of absorption straight back to zero.
 */
private static void applyConditioning(ReplayServer server, ActorClonePlayer clone,
                                      ActorTake take, int takeTick, boolean simulated) {
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

        // Health is written only while the take is authoritative. Running this
        // against a diverged actor is the same bug applyHealth documents: the
        // vitals are the *performer's* samples, so writing them over a clone that
        // has since been punched heals it straight back up, and the knockback and
        // the visible health bar then disagree about whether the hit happened.
        // Once diverged the damage pipeline owns health, and this must not touch
        // it.
        if (!simulated) {
            ActorVitals vitals = take.vitalsAt(takeTick);
            if (vitals != null) {
                clone.setHealth(vitals.health());
                clone.setAbsorptionAmount(vitals.absorption());
            }
        }

        // The effect map was mutated rather than assigned, so the synced
        // particle list is still stale and has to be pushed by hand.
        clone.syncEffectVisibility();
    }

    /**
     * Puts the clone's synced entity data on the wire.
     *
     * <p>Health, absorption, pose and the effect swirl all travel as synced
     * entity data, and a clone's own connection is a black hole, so nothing puts
     * them on the wire on its own. {@code packDirty} only costs anything when
     * something above actually changed, which makes this the cheapest correct
     * place to send. It answers null rather than an empty list when nothing is
     * dirty, and a take that neither took damage nor changed effects is the
     * common case, so that cannot be treated as "empty".
     *
     * <p>Runs whether or not the actor is alive, and that is the whole of the
     * death animation. The viewer's client decides a body has died from the
     * synced health and from nothing else; only then does its own
     * {@code deathTime} start climbing and its renderer tip the body over. This
     * used to live inside {@link #applyConditioning}, which is skipped for a dead
     * actor, so an actor died in complete silence on the wire: the death sound
     * played and the body stayed standing.
     */
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

        // Gear overrides land here, after the recorded equipment and before
        // the broadcast, so the packet below carries what the viewers must
        // actually see. GearApplier writes server-side state only; this
        // broadcast is the one its javadoc defers to. Reading the packet back
        // off the clone (rather than the recorded loadout) is what lets the
        // overrides travel: with no gear stored the slots hold exactly what
        // was just written, so the no-gear broadcast is unchanged.
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

    /**
     * Writes the actor's morph onto the clone, if it has one.
     *
     * <p>Called after {@link #applyEquipment} on the same tick, per the
     * ordering contract: the applier is idempotent, so running it every tick
     * is a no-op when nothing changed, and running it here (rather than from
     * its own hook) is what keeps a quadruped's cleared slots cleared.
     *
     * <p>An un-morphed actor still passes through {@link MorphApplier#apply}
     * with a null definition rather than skipping it, because that is the call
     * that tears down a morph that was removed mid-session. It is cheap when
     * there is nothing to tear down.
     */
    private static void applyMorph(Actor actor, ActorClonePlayer clone) {
        if (!pdx.dev.fbactors.settings.FbSettings.morphEnabled()) {
            MorphApplier.apply(clone, null, null);
            return;
        }
        MorphSelection selection = Morph.selectionFor(actor.take.id);
        if (selection == null || !selection.isMorphed()) {
            MorphApplier.apply(clone, null, null);
            return;
        }
        MorphDefinition definition = Morph.definitionFor(actor.take.id);
        MorphApplier.apply(clone, definition, selection);
    }

/**
     * Fires the cosmetic half of this tick's events.
     *
     * <p>The {@code ATTACK} case deliberately does nothing beyond the swing. The
     * strike itself is delivered by {@link InputSim#runActions}, which runs a
     * few lines earlier in the same tick and calls vanilla's {@code Player.attack}
     * against the recorded target. It used to be handled here too, by sending a
     * damage event packet at the target, which flashed it red and played the hurt
     * sound without applying a single point of damage: the target stood there
     * taking a convincing-looking beating and came out at full health.
 *
 * <p>The {@code HURT} case is the same story and is now unreachable, because
 * nothing records a {@code HURT} event: a take records the swing that caused a
 * hit, and the hit's damage is measured off the clone afterwards rather than
 * announced in advance. It is kept so that a take written by a build that did
 * record one still shows something, which is the recoverable half of the problem.
 */
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
                case SWING_MAIN -> Compat.swing(clone, InteractionHand.MAIN_HAND);
                case SWING_OFF -> Compat.swing(clone, InteractionHand.OFF_HAND);
                case USE_ITEM -> Compat.swing(clone, InteractionHand.MAIN_HAND);
case ATTACK -> Compat.swing(clone, InteractionHand.MAIN_HAND);
            }
        }
    }
}
