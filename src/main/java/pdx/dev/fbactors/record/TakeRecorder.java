package pdx.dev.fbactors.record;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import pdx.dev.fbactors.FlashbackActors;
import pdx.dev.fbactors.actor.ActorArrows;
import pdx.dev.fbactors.actor.ActorItems;
import pdx.dev.fbactors.actor.BlockEdits;
import pdx.dev.fbactors.take.ActorBlockEdit;
import pdx.dev.fbactors.take.ActorBlockEntityEdit;
import pdx.dev.fbactors.take.ActorContainerInteraction;
import pdx.dev.fbactors.take.ActorEffect;
import pdx.dev.fbactors.take.ActorEquip;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ArrowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import pdx.dev.fbactors.take.ActorEvent;
import pdx.dev.fbactors.take.ActorFrame;
import pdx.dev.fbactors.take.ActorInput;
import pdx.dev.fbactors.take.ActorMining;
import pdx.dev.fbactors.take.ActorMiningState;
import pdx.dev.fbactors.take.ActorShot;
import pdx.dev.fbactors.take.ActorVitals;
import pdx.dev.fbactors.compat.Compat;
import pdx.dev.fbactors.take.ActorTake;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class TakeRecorder {

    private static final double CAPTURE_REACH = 6.0;
    private static final double CAPTURE_REACH_SQUARED = CAPTURE_REACH * CAPTURE_REACH;

    /** Longest effect worth storing, an hour of ticks. */
    private static final int MAX_EFFECT_TICKS = 20 * 60 * 60;

    @Nullable
    private ActorTake take;

    @Nullable
    private ServerPlayer viewer;

    private int currentTick;
    private boolean lastSwinging;

    private String[] lastEquipIds;
    private int[] lastEquipCounts;
    /**
     * The serialised components last recorded per equipment slot, alongside
     * {@link #lastEquipIds} and {@link #lastEquipCounts}.
     *
     * <p>Without this the change check is id-and-count only, so a component
     * edit that keeps both - re-enchanting the same sword, renaming it,
     * drinking half a potion stack - records nothing and the take keeps the
     * old components for the rest of the performance.
     */
    private String[] lastEquipNbt;

    /**
     * The crack stage and position of the last {@link ActorMiningState} recorded,
     * and whether the performer's arm was swinging on that tick.
     *
     * <p>These exist so {@link #captureDestroyProgress} can tell whether the
     * mining state has actually changed since the last broadcast. Vanilla sends
     * a {@code destroyBlockProgress} packet on every crack-stage transition and
     * a negative one to clear the overlay, but it does <em>not</em> send one
     * on every tick - the crack overlay is static between broadcasts. Without
     * a remembered previous state, the recorder would either record a
     * duplicate snapshot on every broadcast of the same stage (inflating the
     * take with identical entries playback would have to skip) or record
     * nothing on the ticks where vanilla sent no packet at all (leaving the
     * crack overlay frozen between broadcasts, which is the stutter
     * {@link ActorMiningState} was introduced to eliminate).
     *
     * <p>The fields are primitive rather than a {@code BlockPos} because
     * {@code ActorMiningState} stores the position as three {@code int}s -
     * the same reason {@link ActorBlockEntityEdit} does - and keeping them as
     * primitives here avoids unpacking and repacking a {@code BlockPos} on
     * every broadcast just to compare it.
     */
    private int lastMiningStage = -1;
    private int lastMiningX;
    private int lastMiningY;
    private int lastMiningZ;
    private boolean lastMiningSwinging;

    /**
     * The vehicle the performer was riding on the previous sampled tick.
     *
     * <p>Compared by UUID rather than by reference: the entity object is
     * re-resolved every tick and must not be held, while the UUID is stable
     * for the vehicle's lifetime and is what playback uses to find the same
     * boat. Null when the performer was on foot.
     */
    @Nullable
    private UUID lastVehicleUuid;

    private float lastHealth = Float.NaN;
    private float lastAbsorption = Float.NaN;
    private Map<String, int[]> lastEffects;

    private float lastYaw;
    private float lastPitch;

    /**
     * Arrow ids already recorded this take, so an arrow is stored once and only
     * once.
     *
     * <p>Identity rather than "is there a shot at this tick": an arrow persists
     * for many ticks after it is fired, so a tick-indexed test would either record
     * the same arrow on every tick of its flight or need an arbitrary lifetime
     * cut-off that drops slow arrows. Entity ids are unique for a session's
     * lifetime, so a set is exact.
     *
     * <p>Pruned every tick against the ids still present in the level. Without
     * that this grows for the whole take - a long recording firing regularly adds
     * thousands of integers nothing would ever release - and a recorder that is
     * stopped and started repeatedly accumulates across takes.
     */
    private final Set<Integer> seenArrowIds = new HashSet<>();

    /**
     * Arrow ids found in the level on the previous tick.
     *
     * <p>Separate from {@link #seenArrowIds} because the two answer different
     * questions. This one is the "did not exist before" test and is rebuilt from
     * the level each tick, so it only ever holds what is currently alive; the
     * other is the "have I already recorded it" memory, which has to outlive the
     * despawn or a re-spawned id would record a second time.
     */
    private Set<Integer> liveArrowIds = new HashSet<>();

    public boolean isRecording() {
        return this.take != null;
    }

    @Nullable
    public ActorTake current() {
        return this.take;
    }

    public int currentTick() {
        return this.currentTick;
    }

    public void start(ServerPlayer viewer, int replayTick, String name) {
        ServerLevel level = viewer.level() instanceof ServerLevel sl ? sl : null;
        String dimension = Compat.dimensionId(level);

        this.take = new ActorTake(UUID.randomUUID(), name, dimension, replayTick);
        pdx.dev.fbactors.voice.VoiceSettings.applyTo(this.take);
        pdx.dev.fbactors.morph.MorphSettings.applyTo(this.take);
        pdx.dev.fbactors.morph.MorphSelection selection =
            pdx.dev.fbactors.morph.Morph.selectionFor(this.take.id);
        // Live preview: the performer sees their own morph while recording,
        // rendered by the same per-version model swap the viewers get. Pose and
        // equipment stay the performer's own - only the drawn model changes -
        // so nothing about the take being recorded is affected.
        if (selection != null && selection.isMorphed()
            && pdx.dev.fbactors.settings.FbSettings.morphEnabled()) {
            pdx.dev.fbactors.morph.MorphRenderState.set(viewer.getUUID(),
                new pdx.dev.fbactors.morph.MorphRenderInfo(
                    selection.morphId(), selection.scale(), selection.alpha()));
        }
        this.viewer = viewer;
        this.currentTick = replayTick;
        this.lastSwinging = false;
        this.lastYaw = viewer.getYRot();
        this.lastPitch = viewer.getXRot();

        int slots = EquipmentSlot.values().length;
        this.lastEquipIds = new String[slots];
        this.lastEquipCounts = new int[slots];
        this.lastEquipNbt = new String[slots];

        Arrays.fill(this.lastEquipIds, null);
        this.lastHealth = Float.NaN;
        this.lastAbsorption = Float.NaN;
        this.lastEffects = new HashMap<>();
        this.lastVehicleUuid = null;

        // Cleared rather than carried over: ids from a previous take describe
        // entities that no longer exist, and keeping them would suppress the
        // first shots of this one whenever the entity id counter had not moved
        // past them.
        this.seenArrowIds.clear();
        this.liveArrowIds = new HashSet<>();

        // Only open the microphone when this actor actually wants voice, and
        // only for as long as the take is running.
        if (this.take.voiceEnabled) {
            pdx.dev.fbactors.voice.MicCapture.start(
                this.take.voiceDevice, this.take.voiceActivation, this.take.voiceThreshold,
                pdx.dev.fbactors.settings.FbSettings.settings().micGain);
        }
    }

    public static void captureBlockChange(Level level, BlockPos pos, BlockState newState) {
        TakeRecorder recorder = FlashbackActors.RECORDER;
        ActorTake take = recorder.take;
        ServerPlayer viewer = recorder.viewer;
        if (take == null || viewer == null || BlockEdits.isApplying()) {
            return;
        }
        if (viewer.level() != level) {
            return;
        }

        BlockState before = level.getBlockState(pos);
        if (before == newState) {
            return;
        }

        double dx = pos.getX() + 0.5 - viewer.getX();
        double dy = pos.getY() + 0.5 - viewer.getY();
        double dz = pos.getZ() + 0.5 - viewer.getZ();
        if (dx * dx + dy * dy + dz * dz > CAPTURE_REACH_SQUARED) {
            return;
        }

        take.blocks.add(new ActorBlockEdit(recorder.currentTick,
            pos.getX(), pos.getY(), pos.getZ(),
            BlockEdits.serialize(before), BlockEdits.serialize(newState)));

        // After a block is placed, check if the new block has a block entity
        // (sign, banner, chest, etc). If so, capture its NBT so the take
        // can reproduce the block entity's state - text on a sign, contents
        // of a chest, pattern on a banner - not just the block shape.
        if (!newState.isAir() && Compat.hasBlockEntity(level, pos)) {
            // COMPAT: needs Compat.hasBlockEntity(Level, BlockPos)
            String type = blockEntityType(level, newState);
            captureBlockEntity(level, pos, type);
        }
    }

    /**
     * Derives a short type discriminator for a block entity from its block
     * state, for use in {@link ActorBlockEntityEdit}.
     *
     * <p>The discriminator is the path component of the block's registry name
     * - {@code "oak_sign"} for an oak sign, {@code "chest"} for a chest - which
     * is unique per block type and stable across Minecraft versions. It is
     * deliberately a {@code String} and not an enum: the set of block entity
     * types is open-ended (mods add their own), and an enum would either have
     * to be updated in lockstep with vanilla or silently drop unknown types.
     *
     * <p>The lookup goes through {@code level.registryAccess()} rather than
     * {@code BuiltInRegistries.BLOCK} directly because the registry access is
     * the same object vanilla itself uses for block-entity lookups, so a
     * block entity placed by a mod with a custom registry entry resolves
     * correctly. The key is fetched with {@code getKey} rather than
     * {@code getResourceKey} because {@code getKey} returns the registry name
     * directly and is stable across all nine targets, whereas
     * {@code getResourceKey} returns a {@code ResourceKey} whose type changed
     * at 1.21.11.
     *
     * <p>{@code var} is used for the key because the return type of
     * {@code Registry.getKey} is {@code ResourceLocation} on 1.21.1 through
     * 1.21.10 and {@code Identifier} on 1.21.11 and 26.x. Using {@code var}
     * avoids naming a type that does not exist on every target, and the
     * only method called on it - {@code getPath()} - is identical on both.
     */
    private static String blockEntityType(Level level, BlockState state) {
        var key = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return key != null ? key.getPath() : "unknown";
    }

    /**
     * Captures the full NBT of a block entity at the given position.
     *
     * <p>Block entities - signs, banners, chests, shulker boxes, furnaces,
     * brewing stands, and the rest - carry state that lives <em>inside</em>
     * the block entity object, not in the block state. A sign's text, a
     * banner's pattern list, a chest's contents: none of these survive a
     * {@code BlockState} round-trip, because {@code BlockState} only describes
     * the <em>shape</em> of the block. Without this capture, playback would
     * place the correct block but lose everything the performer put into it.
     *
     * <p>The NBT is stored as a serialised string rather than a typed record
     * for the same reason {@link ActorBlockEntityEdit} stores it as a string:
     * the set of block entity types is open-ended (mods add their own), the
     * schema changes between Minecraft versions, and a typed record would be
     * a permanent treadmill of updates. The string is opaque to this project
     * - written by vanilla's codec at capture time and read back by the same
     * codec at playback time - so the take format never needs to know what a
     * sign's {@code front_text} or a chest's {@code Items} tag looks like.
     *
     * <p>The {@code type} discriminator is recorded alongside the NBT so
     * that a reader can decide whether to bother decoding the tag at all.
     * A playback path that only cares about chests can skip every sign edit
     * without parsing a single NBT string, and a debugging tool can filter
     * by type without understanding the payload.
     *
     * <p>This method is called from {@link #captureBlockChange} after a block
     * is placed, not from a mixin directly. The block entity may not exist
     * yet at the moment {@code setBlock} returns - vanilla creates it in the
     * same tick but potentially after the block state change - so the
     * {@code Compat.blockEntityNbt} call returns null if the block entity
     * is not yet available and the capture is silently skipped. The next
     * block-entity edit the performer makes (placing a sign, opening a chest
     * which marks it dirty) will trigger a fresh capture.
     */
    public static void captureBlockEntity(Level level, BlockPos pos, String type) {
        TakeRecorder recorder = FlashbackActors.RECORDER;
        ActorTake take = recorder.take;
        ServerPlayer viewer = recorder.viewer;
        if (take == null || viewer == null || BlockEdits.isApplying()) {
            return;
        }
        if (viewer.level() != level) {
            return;
        }

        String nbt = Compat.blockEntityNbt(level, pos);
        // COMPAT: needs Compat.blockEntityNbt(Level, BlockPos)
        if (nbt == null || nbt.isEmpty()) {
            return;
        }

        if (isDuplicateBlockEntity(take, pos, nbt)) {
            return;
        }
        take.blockEntities.add(new ActorBlockEntityEdit(
            recorder.currentTick, pos.getX(), pos.getY(), pos.getZ(), type, nbt));
    }

    /**
     * Re-captures a block entity whose NBT changed without a {@code setBlock}.
     *
     * <p>Sign text, banner patterns and chest contents edited in place never
     * touch {@code setBlock}, so neither {@code captureBlockChange} nor the
     * before/after {@code setBlock} mixin sees them. Called from the
     * {@code BlockEntity.setChanged} mixin after vanilla marks the tile dirty.
     * Deduped against the last entry for the same position so furnace ticks
     * and double-hooks do not flood the take.
     */
    public static void captureBlockEntityRefresh(Level level, BlockPos pos) {
        TakeRecorder recorder = FlashbackActors.RECORDER;
        ActorTake take = recorder.take;
        ServerPlayer viewer = recorder.viewer;
        if (take == null || viewer == null || BlockEdits.isApplying()) {
            return;
        }
        if (viewer.level() != level) {
            return;
        }
        if (!Compat.hasBlockEntity(level, pos)) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        String type = blockEntityType(level, state);
        captureBlockEntity(level, pos, type);
    }

    private static boolean isDuplicateBlockEntity(ActorTake take, BlockPos pos, String nbt) {
        for (int i = take.blockEntities.size() - 1; i >= 0; i--) {
            ActorBlockEntityEdit edit = take.blockEntities.get(i);
            if (edit.x() == pos.getX() && edit.y() == pos.getY() && edit.z() == pos.getZ()) {
                return nbt.equals(edit.nbt());
            }
        }
        return false;
    }

    /**
     * A block-break progress broadcast for the recording player. Vanilla sends
     * one of these per crack stage and a negative one to wipe the overlay, so
     * this is a verbatim transcript of the mining animation the performer saw
     * rather than something reconstructed from the block edit that follows it.
     */
    public static void captureDestroyProgress(ServerLevel level, int id, BlockPos pos,
                                              int progress) {
        TakeRecorder recorder = FlashbackActors.RECORDER;
        ActorTake take = recorder.take;
        ServerPlayer viewer = recorder.viewer;
        if (take == null || viewer == null || BlockEdits.isApplying()) {
            return;
        }
        if (viewer.level() != level || viewer.getId() != id) {
            return;
        }

        take.mining.add(new ActorMining(recorder.currentTick, pos.immutable(), progress));

        // Also record a continuous mining state snapshot. ActorMining is a log
        // of discrete progress broadcasts; ActorMiningState is the playback
        // snapshot that drives the crack overlay and arm animation between
        // broadcasts. Without it, the crack would jump from one stage to the
        // next with no interpolation, and the arm would never swing on ticks
        // where vanilla sent no progress event.
        boolean swinging = Compat.isSwinging(viewer);
        if (progress != recorder.lastMiningStage
            || pos.getX() != recorder.lastMiningX
            || pos.getY() != recorder.lastMiningY
            || pos.getZ() != recorder.lastMiningZ
            || swinging != recorder.lastMiningSwinging) {
            recorder.lastMiningStage = progress;
            recorder.lastMiningX = pos.getX();
            recorder.lastMiningY = pos.getY();
            recorder.lastMiningZ = pos.getZ();
            recorder.lastMiningSwinging = swinging;
            take.miningStates.add(new ActorMiningState(
                recorder.currentTick, pos.getX(), pos.getY(), pos.getZ(),
                progress, swinging));
        }
    }

    /**
     * Records that the performer opened a container at the given position.
     *
     * <p>Containers - chests, shulker boxes, furnaces, brewing stands, hoppers,
     * dispensers, and the rest - are block entities whose contents are not part
     * of the block state and are not captured by {@link ActorBlockEntityEdit}.
     * That record stores the <em>result</em> of a change (the full NBT after the
     * edit), but it cannot capture the <em>act</em> of opening or closing,
     * because opening and closing leave no trace in the block entity's NBT. A
     * chest that was opened and closed without a slot change is, from the NBT's
     * point of view, identical to one that was never touched. Yet the open/close
     * transition is exactly what a viewer sees: the lid animates, the GUI
     * appears, the sound plays. Without recording it, playback would show a
     * chest's contents changing with no visible cause - items sliding in and
     * out of a chest that never opened.
     *
     * <p>This method is called from a mixin on the container-open packet, not
     * from {@link #captureBlockChange}. The mixin fires when the performer
     * opens a container GUI, which is the moment the lid animation starts and
     * the sound plays. The position is the container's block position, which
     * the mixin extracts from the packet.
     *
     * <p>No Compat method is needed for the recording itself - the event is
     * self-contained, carrying only the position and the action discriminator.
     * The {@code Compat.isContainer} check is used by the mixin to decide
     * whether to call this method, not by the recorder.
     */
    public static void captureContainerOpen(ServerLevel level, BlockPos pos) {
        TakeRecorder recorder = FlashbackActors.RECORDER;
        ActorTake take = recorder.take;
        ServerPlayer viewer = recorder.viewer;
        if (take == null || viewer == null || BlockEdits.isApplying()) {
            return;
        }
        if (viewer.level() != level) {
            return;
        }

        take.containers.add(new ActorContainerInteraction(
            recorder.currentTick, pos.getX(), pos.getY(), pos.getZ(),
            "open", -1, null, 0, null));
    }

    /**
     * Records that the performer closed a container at the given position.
     *
     * <p>The same argument as {@link #captureContainerOpen} applies: closing
     * a container leaves no trace in the block entity's NBT, so without this
     * event playback would show a chest GUI that never closes. The event is
     * self-contained - it carries only the position and the action
     * discriminator - and is called from a mixin on the container-close
     * packet.
     *
     * <p>Open and close are separate methods rather than one method with an
     * {@code action} parameter because the two events are triggered by
     * different mixin hooks (the open packet and the close packet) and have
     * no shared payload beyond the position. A single method with a boolean
     * would force every call site to pass a meaningless {@code slot},
     * {@code itemId}, {@code count}, and {@code nbt} - all null or zero - just
     * to satisfy a shared signature. Separate methods keep each call site
     * honest about what it knows.
     */
    public static void captureContainerClose(ServerLevel level, BlockPos pos) {
        TakeRecorder recorder = FlashbackActors.RECORDER;
        ActorTake take = recorder.take;
        ServerPlayer viewer = recorder.viewer;
        if (take == null || viewer == null || BlockEdits.isApplying()) {
            return;
        }
        if (viewer.level() != level) {
            return;
        }

        take.containers.add(new ActorContainerInteraction(
            recorder.currentTick, pos.getX(), pos.getY(), pos.getZ(),
            "close", -1, null, 0, null));
    }

    /**
     * Records that the performer changed a slot within a container.
     *
     * <p>Slot changes are the third type of container interaction, alongside
     * open and close. Unlike open and close, a slot change carries a payload:
     * the slot index, the item id and count that were placed or removed, and
     * the full NBT of the item stack. The NBT is captured for the same reason
     * {@link #sampleEquipment} captures it: an item id alone cannot
     * distinguish a diamond sword from a Sharpness V diamond sword, and a take
     * that loses enchantments is a take that lies about what the performer
     * was holding.
     *
     * <p>The {@code itemId} and {@code count} are the <em>result</em> of the
     * slot change, not the delta. This is the same desired-state philosophy as
     * {@link ActorBlockEntityEdit}: playback applies the latest known state
     * rather than replaying every intermediate drag, so a scrub backwards and
     * forwards through a looting session converges on the correct contents.
     *
     * <p>This method is called from a mixin on the container slot-change
     * packet. The mixin extracts the position, slot index, and item stack
     * from the packet and passes them here. The {@code Compat.itemStackNbt}
     * call serialises the item's components - enchantments, custom names,
     * attribute modifiers - so the take can reproduce the exact item the
     * performer placed or removed.
     *
     * <p>The {@code stack} parameter is the <em>resulting</em> stack in the
     * slot after the change, not the stack the performer placed. This is
     * because the mixin fires after vanilla has applied the change, so the
     * slot already contains the new stack. A slot that was emptied by the
     * change has an empty stack, which is recorded as {@code itemId = null},
     * {@code count = 0}, {@code nbt = null} - the same convention
     * {@link ActorEquip#isEmpty()} uses.
     */
    /**
     * Records a sound broadcast the performer's actions caused.
     *
     * <p>Called from the {@code Level.playSound} mixin for every broadcast on
     * every overload. Attribution is causal: the excluded-player being the
     * performer is exact (vanilla excludes the causer because their client
     * predicts the sound), and anything else must land within
     * {@link #SOUND_CORRELATION_BLOCKS} on the same tick as a recorded action
     * of the performer's - a block edit, a container event, a mount, a shot -
     * which is how a null-player door click or chest lid is told apart from a
     * mob growling nearby. The world's own sounds replay themselves and are
     * never filed.
     *
     * <p>Delegation doubles are collapsed: on 1.21.1/1.21.4 the Player-first
     * overloads funnel into the Entity ones, so both mixin hooks fire for one
     * broadcast. An entry identical to the last in every field is the same
     * broadcast arriving twice, not two broadcasts.
     *
     * @param except the player vanilla excluded from the broadcast, or null
     * @param x,y,z where the sound plays
     * @param sound what vanilla chose to play
     */
    public static void capturePlaySound(Level level, @Nullable Entity except,
                                        double x, double y, double z,
                                        net.minecraft.sounds.SoundEvent sound,
                                        net.minecraft.sounds.SoundSource source,
                                        float volume, float pitch) {
        TakeRecorder recorder = FlashbackActors.RECORDER;
        ActorTake take = recorder.take;
        ServerPlayer viewer = recorder.viewer;
        if (take == null || viewer == null || BlockEdits.isApplying()) {
            return;
        }
        if (viewer.level() != level || sound == null) {
            return;
        }

        boolean own = except != null && except.getUUID().equals(viewer.getUUID());
        if (!own && !correlatedAction(take, recorder.currentTick, viewer, x, y, z)) {
            return;
        }

        String id = soundKey(sound);
        if (id == null || id.isEmpty()) {
            return;
        }
        String sourceName;
        try {
            sourceName = source == null ? null : source.name();
        } catch (RuntimeException | LinkageError e) {
            sourceName = null;
        }

        // Delegation double: same tick, same sound, same place, same voice.
        if (!take.sounds.isEmpty()) {
            pdx.dev.fbactors.take.ActorSound last =
                take.sounds.get(take.sounds.size() - 1);
            if (last.tick() == recorder.currentTick
                && id.equals(last.soundId())
                && last.x() == x && last.y() == y && last.z() == z
                && last.volume() == volume && last.pitch() == pitch) {
                return;
            }
        }

        take.sounds.add(new pdx.dev.fbactors.take.ActorSound(
            recorder.currentTick, id, x, y, z, volume, pitch, sourceName));
    }

    /**
     * How far from the performer a null-player sound may be and still count
     * as theirs, in blocks. A door clicks at the hand; a mob growls anywhere.
     * Eight blocks comfortably covers every interaction reach while excluding
     * anything the performer could not have touched.
     */
    private static final double SOUND_CORRELATION_BLOCKS = 8.0;

    /**
     * Whether the performer did anything this tick that a sound at this spot
     * could belong to.
     *
     * <p>Walks the action tracks rather than a flag so hook order never
     * matters: packets (which trigger sounds) and the per-tick sample (which
     * records shots and rides) arrive in no guaranteed order within a tick,
     * and a boolean set by one and read by the other would depend on who ran
     * first. The lists are tiny and sounds are infrequent, so the scan costs
     * nothing.
     */
    private static boolean correlatedAction(ActorTake take, int tick, ServerPlayer viewer,
                                            double x, double y, double z) {
        double dx = x - viewer.getX();
        double dy = y - viewer.getY();
        double dz = z - viewer.getZ();
        if (dx * dx + dy * dy + dz * dz
            > SOUND_CORRELATION_BLOCKS * SOUND_CORRELATION_BLOCKS) {
            return false;
        }
        if (hasAt(take.blocks, tick)) {
            return true;
        }
        if (hasAt(take.containers, tick)) {
            return true;
        }
        if (hasAt(take.blockEntities, tick)) {
            return true;
        }
        if (hasAt(take.rides, tick)) {
            return true;
        }
        if (hasAt(take.shots, tick)) {
            return true;
        }
        if (hasAt(take.mining, tick)) {
            return true;
        }
        if (hasAt(take.equipment, tick)) {
            return true;
        }
        if (hasAt(take.vitals, tick)) {
            return true;
        }
        // Shots live in take-relative frame indices, not absolute ticks, so
        // they are converted rather than compared raw: frame i fires at
        // take tick startTick + i wherever the clip sits.
        int frame = tick - take.startTick;
        for (ActorShot shot : take.shots) {
            if (shot.tick() == frame) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAt(java.util.List<?> entries, int tick) {
        for (Object entry : entries) {
            int t;
            if (entry instanceof ActorBlockEdit e) {
                t = e.tick();
            } else if (entry instanceof ActorContainerInteraction e) {
                t = e.tick();
            } else if (entry instanceof ActorBlockEntityEdit e) {
                t = e.tick();
            } else if (entry instanceof pdx.dev.fbactors.take.ActorRide e) {
                t = e.tick();
            } else if (entry instanceof ActorShot e) {
                // Shots are take-relative frame indices, not absolute ticks;
                // they cannot corroborate a sound on their own. Skipped
                // deliberately rather than compared across coordinate systems.
                continue;
            } else if (entry instanceof ActorMining m) {
                t = m.tick();
            } else if (entry instanceof ActorEquip e) {
                t = e.tick();
            } else if (entry instanceof ActorVitals v) {
                t = v.tick();
            } else {
                continue;
            }
            if (t == tick) {
                return true;
            }
        }
        return false;
    }

    /**
     * The registry id of a sound event, e.g. {@code "minecraft:block.wood.place"}.
     *
     * <p>{@code var} because the key type is {@code ResourceLocation} up to
     * 1.21.10 and {@code Identifier} after; only {@code toString()} is read.
     */
    @Nullable
    private static String soundKey(net.minecraft.sounds.SoundEvent sound) {
        try {
            var key = net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT
                .getKey(sound);
            return key == null ? null : key.toString();
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }

    public static void captureContainerSlot(ServerLevel level, BlockPos pos,
                                            int slot, ItemStack stack) {        TakeRecorder recorder = FlashbackActors.RECORDER;
        ActorTake take = recorder.take;
        ServerPlayer viewer = recorder.viewer;
        if (take == null || viewer == null || BlockEdits.isApplying()) {
            return;
        }
        if (viewer.level() != level) {
            return;
        }

        String itemId = stack.isEmpty() ? null : ActorItems.idOf(stack);
        int count = stack.isEmpty() ? 0 : stack.getCount();
        String nbt = stack.isEmpty() ? null : Compat.itemStackNbt(level, stack);
        // COMPAT: needs Compat.itemStackNbt(Level, ItemStack)

        take.containers.add(new ActorContainerInteraction(
            recorder.currentTick, pos.getX(), pos.getY(), pos.getZ(),
            "slot_change", slot, itemId, count, nbt));
    }

    public void sample(ServerPlayer viewer, int replayTick) {
        ActorTake take = this.take;
        if (take == null) {
            return;
        }
        this.currentTick = replayTick;

        int expected = take.startTick + take.frames.size();
        if (replayTick < expected) {
            return;
        }
        while (take.frames.size() < replayTick - take.startTick && !take.frames.isEmpty()) {
            take.frames.add(take.frames.get(take.frames.size() - 1));
        }

        boolean swinging = Compat.isSwinging(viewer);
        byte flags = ActorFrame.packFlags(
            viewer.isShiftKeyDown(),
            viewer.isSprinting(),
            viewer.onGround(),
            swinging,
            viewer.isFallFlying()
        );

        take.frames.add(new ActorFrame(
            viewer.getX(), viewer.getY(), viewer.getZ(),
            viewer.getYRot(), viewer.getXRot(), viewer.getYHeadRot(),
            (byte) viewer.getPose().ordinal(),
            flags
        ));

        sampleInput(viewer, replayTick);

        if (swinging && !this.lastSwinging) {
            take.events.add(ActorEvent.swing(replayTick, false));
        }
        this.lastSwinging = swinging;

        sampleEquipment(viewer, replayTick);
        sampleVitals(viewer, replayTick);
        sampleEffects(viewer, replayTick);
        sampleArrows(viewer, replayTick);
        sampleRides(viewer, replayTick);
    }

    /**
     * Records what the performer pressed this tick.
     *
     * <p>Look is stored as the change since the previous tick rather than as an
     * absolute angle, so replaying a turn applies exactly the rotation that was
     * performed. An absolute target would be a feedback loop that compounds
     * every time the actor is re-simulated.
     *
     * <p>The movement axes come from the server player's own {@code zza} and
     * {@code xxa} rather than from key presses. Those fields already carry the
     * client's {@code 0.98} input scale, the sneak speed and the item-use
     * slowdown, already diagonal-corrected, because the client computed all of
     * that before the packet was ever sent. Reading the keys instead would mean
     * re-deriving the same arithmetic here and hoping it matches, and it used to:
     * the sign of each axis was recorded as a boolean, so a performer at a third
     * of full deflection was stored as if holding the key flat out and the actor
     * came out running at full speed for the whole take.
     *
     * <p>{@code attackStrength} is sampled every tick rather than only on the
     * tick a swing fired, because it decays continuously after a swing and the
     * value on the swing's own tick is the one that has to be restored later.
     */
    private void sampleInput(ServerPlayer viewer, int replayTick) {
        ActorTake take = this.take;
        if (take == null) {
            return;
        }

        float yawDelta = net.minecraft.util.Mth.wrapDegrees(viewer.getYRot() - this.lastYaw);
        float pitchDelta = viewer.getXRot() - this.lastPitch;
        this.lastYaw = viewer.getYRot();
        this.lastPitch = viewer.getXRot();

        boolean using = viewer.isUsingItem();
        boolean offhand = false;
        if (using) {
            try {
                offhand = viewer.getUsedItemHand() == net.minecraft.world.InteractionHand.OFF_HAND;
            } catch (Exception | LinkageError ignored) {
                offhand = false;
            }
        }

        take.inputs.add(new ActorInput(
            replayTick,
            viewer.zza,
            viewer.xxa,
            Compat.jumpHeld(viewer),
            viewer.isShiftKeyDown(),
            viewer.isSprinting(),
            yawDelta,
            pitchDelta,
            (byte) Compat.selectedSlot(viewer),
            using,
            Compat.isSwinging(viewer),
            viewer.getAttackStrengthScale(0.5f),
            viewer.getAbilities().flying,
            offhand
        ));
    }

    /**
     * Records the arrows the viewer fired this tick, at the speed vanilla gave them.
     *
     * <p>The arrow is found by diffing the level's projectiles against the set
     * from the previous tick, not by watching for a release. A diff is what makes
     * this exact: there is no charge-up window to guess the end of, no crossbow
     * versus bow distinction to draw, and an arrow that spawned on the same tick
     * as a block break is caught whether or not the two are causally related.
     *
     * <p>What is stored is {@code arrow.getDeltaMovement()} - the vector vanilla
     * itself computed from the performer's draw and the bow's own multipliers -
     * split into a direction and a magnitude. Nothing here re-derives a launch
     * speed from a charge curve, and that is the whole point: a charge curve is
     * version-specific and enchantment-specific, so a take recorded on one
     * Minecraft version and played back on another would fly a different distance
     * with no way to tell from the file. See {@link ActorShot} for the full
     * argument.
     *
     * <p>Ownership is checked against the recording viewer rather than assumed
     * from position, so an arrow another player loosed into the scene is not
     * recorded as this actor's. Only arrows are considered, so a snowball or an
     * ender pearl thrown during a take is left alone rather than being replayed as
     * a shot on a bow tick.
     *
     * <p>The arrow is one tick old by the time this sees it, because the sample
     * runs after the level has ticked. That is a deliberate trade: reading
     * velocity on the spawn tick would need a mixin into the projectile's
     * constructor, and a projectile's own first tick applies gravity and drag
     * before it is ever visible to a per-tick sampler. The measured speed is
     * therefore one gravity step below the muzzle value, which is the velocity a
     * spectator watching the performer actually sees the arrow travel at.
     */
    private void sampleArrows(ServerPlayer viewer, int replayTick) {
        ActorTake take = this.take;
        if (take == null || !(viewer.level() instanceof ServerLevel level)) {
            return;
        }

        Class<?> arrowClass = arrowClass(level, viewer);
        if (arrowClass == null) {
            return;
        }

        Set<Integer> present = new HashSet<>();
        for (Entity entity : level.getAllEntities()) {
            if (!(entity instanceof Projectile projectile) || !arrowClass.isInstance(projectile)) {
                continue;
            }

            present.add(entity.getId());

            Entity owner = projectile.getOwner();
            if (owner == null || !owner.getUUID().equals(viewer.getUUID())) {
                continue;
            }
            if (this.liveArrowIds.contains(entity.getId())
                || !this.seenArrowIds.add(entity.getId())) {
                // Already alive last tick, or already recorded. An id only enters
                // seenArrowIds once it has been considered here, so this covers
                // both the second tick of an arrow's life and a re-spawned id.
                continue;
            }

            record(projectile, viewer, take, replayTick);
        }

        // Rebuilt, not added to: the previous tick's set is exactly the set of
        // arrows whose arrival has already been decided, so replacing it is what
        // makes the diff correct, and it drops despawned ids for free.
        this.liveArrowIds = present;

        // Pruned against what is still alive so a long take cannot grow this
        // without bound. The ids are boxed integers in a hash set, so the cost is
        // proportional to the arrows in flight, not to the length of the take.
        this.seenArrowIds.retainAll(present);
    }

    /**
     * The concrete arrow class, discovered by asking vanilla's arrow item to build one.
     *
     * <p>{@code AbstractArrow} cannot be named in shared source. It is
     * {@code net.minecraft.world.entity.projectile.AbstractArrow} up to 1.21.10
     * and {@code net.minecraft.world.entity.projectile.arrow.AbstractArrow} from
     * 1.21.11 onward, so a direct reference compiles on part of the supported
     * range and fails on the rest, and so does a direct {@code instanceof}. The
     * class is therefore obtained by value instead of by name: {@link ArrowItem}
     * has a stable package on every target, so asking it for an arrow yields an
     * instance whose class is the right one on this version without a single
     * version-specific token in this file.
     *
     * <p>The rejected alternatives were reflection on a class name and a duck-typed
     * mixin interface on {@code AbstractArrow}. Reflection resolves by string and
     * breaks silently on a rename, which is the failure this project cares most
     * about avoiding; a duck interface would work but adds a mixin and a field to
     * every arrow in the game to answer a question the item registry already knows
     * the answer to.
     *
     * <p>The probe is built and discarded rather than added to the level, so it
     * costs one unadded entity once per session and can never be seen, hit or
     * ticked. The result is cached because the class cannot change while the game
     * is running.
     */
    @Nullable
    private static Class<?> arrowClass(ServerLevel level, LivingEntity shooter) {
        if (ARROW_CLASS != null) {
            return ARROW_CLASS == NONE ? null : ARROW_CLASS;
        }
        try {
            if (Items.ARROW instanceof ArrowItem arrowItem) {
                ItemStack arrow = new ItemStack(Items.ARROW);
                ARROW_CLASS = arrowItem.createArrow(level, arrow, shooter, arrow).getClass();
            } else {
                // Vanilla cannot produce this, but recording nothing is the correct
                // response if it ever did, rather than recording every projectile
                // in the level as if it were a bow shot.
                ARROW_CLASS = NONE;
                FlashbackActors.LOGGER.debug(
                    "No ArrowItem on this version, so arrows won't record (vanilla can't do this)");
            }
        } catch (RuntimeException | LinkageError failure) {
            ARROW_CLASS = NONE;
            FlashbackActors.LOGGER.debug("Cannot resolve the arrow class: {}", failure.toString());
        }
        return ARROW_CLASS == NONE ? null : ARROW_CLASS;
    }

    /** Sentinel meaning "looked, and there is no arrow class on this version". */
    private static final Class<?> NONE = ActorArrows.class;

    private static Class<?> ARROW_CLASS;

    /**
     * Writes one arrow into the take, in take ticks like every other measured event.
     */
    private static void record(Projectile arrow, ServerPlayer viewer, ActorTake take,
                               int replayTick) {
        Vec3 velocity = arrow.getDeltaMovement();
        double speed = velocity.length();

        ItemStack held = viewer.getMainHandItem();
        String itemId = held.isEmpty() ? null : ActorItems.idOf(held);

        // Take tick, not replay tick, for the reason ActorTake.shots documents: a
        // shot belongs to the body that loosed it and has to move with it when the
        // clip is dragged along the timeline.
        take.shots.add(new ActorShot(
            replayTick - take.startTick,
            velocity.x,
            velocity.y,
            velocity.z,
            (float) speed,
            itemId,
            // Deliberately null. The arrow is one tick old and has met nothing; a
            // target recorded now would be invented, and inventing one is exactly
            // what ActorHit's javadoc rejects for melee.
            null));

        FlashbackActors.LOGGER.info(
            "Actor '{}' loosed an arrow at take tick {} (speed {})",
            take.name, replayTick - take.startTick, speed);
    }

    /**
     * Records the performer mounting or dismounting a vehicle this tick.
     *
     * <p>Sampled from {@code getVehicle()} rather than hooked into
     * {@code startRiding}: the riding call's signature is version-specific
     * while the getter is stable on every target, and a per-tick sample is
     * exact for a state that only changes on discrete ticks. Compared by
     * UUID so a vehicle swap on one tick (boat to horse) files a dismount
     * and a mount in order rather than one confused entry.
     *
     * <p>No reference to the entity is held: the take keeps the UUID and the
     * type id string, which is everything playback needs to find the same
     * vehicle again.
     */
    private void sampleRides(ServerPlayer viewer, int replayTick) {
        ActorTake take = this.take;
        if (take == null) {
            return;
        }

        Entity vehicle = viewer.getVehicle();
        UUID now = vehicle == null ? null : vehicle.getUUID();
        if (java.util.Objects.equals(now, this.lastVehicleUuid)) {
            return;
        }
        this.lastVehicleUuid = now;

        if (vehicle == null) {
            take.rides.add(pdx.dev.fbactors.take.ActorRide.dismount(replayTick));
        } else {
            take.rides.add(pdx.dev.fbactors.take.ActorRide.mount(
                replayTick, vehicleTypeId(vehicle), now));
        }
    }

    /**
     * The registry id of a vehicle's entity type, e.g. {@code "minecraft:oak_boat"}.
     *
     * <p>{@code var} because the key type is {@code ResourceLocation} up to
     * 1.21.10 and {@code Identifier} after; only {@code toString()} is called,
     * which is identical on both. "unknown" when unresolvable rather than
     * null, so a mount entry always names something.
     */
    private static String vehicleTypeId(Entity vehicle) {
        try {
            var key = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                .getKey(vehicle.getType());
            return key == null ? "unknown" : key.toString();
        } catch (RuntimeException | LinkageError e) {
            return "unknown";
        }
    }

    private void sampleVitals(ServerPlayer viewer, int replayTick) {        ActorTake take = this.take;
        if (take == null) {
            return;
        }

        // Health moves in fractions of a heart and absorption in half hearts,
        // so rounding to two decimals keeps the take sparse without dropping a
        // step the viewer could see.
        float health = rounded(viewer.getHealth());
        float absorption = rounded(viewer.getAbsorptionAmount());
        if (health == this.lastHealth && absorption == this.lastAbsorption) {
            return;
        }

        this.lastHealth = health;
        this.lastAbsorption = absorption;
        take.vitals.add(new ActorVitals(replayTick, health, absorption));
    }

    private static float rounded(float value) {
        return Math.round(value * 100.0f) / 100.0f;
    }

    private void sampleEffects(ServerPlayer viewer, int replayTick) {
        ActorTake take = this.take;
        if (take == null) {
            return;
        }

        // Only the identity of each effect is compared. Remaining duration ticks
        // down every tick, so including it would add an entry per effect per
        // tick, and the clone never decrements them anyway.
        Map<String, int[]> now = new HashMap<>();
        for (MobEffectInstance active : viewer.getActiveEffects()) {
            String id = Compat.effectId(active.getEffect());
            if (!id.isEmpty()) {
                now.put(id, new int[] {active.getAmplifier(),
                    Math.min(active.getDuration(), MAX_EFFECT_TICKS)});
            }
        }

        if (sameEffects(this.lastEffects, now)) {
            return;
        }

        for (Map.Entry<String, int[]> entry : now.entrySet()) {
            int[] after = entry.getValue();
            take.effects.add(new ActorEffect(replayTick, entry.getKey(),
                after[0], after[1]));
        }
        this.lastEffects = now;
    }

    private static boolean sameEffects(Map<String, int[]> before, Map<String, int[]> after) {
        if (before.size() != after.size()) {
            return false;
        }
        for (Map.Entry<String, int[]> entry : after.entrySet()) {
            int[] previous = before.get(entry.getKey());
            if (previous == null || previous[0] != entry.getValue()[0]) {
                return false;
            }
        }
        return true;
    }

    private void sampleEquipment(ServerPlayer viewer, int replayTick) {
        ActorTake take = this.take;
        if (take == null || this.lastEquipIds == null) {
            return;
        }

        for (EquipmentSlot slot : EquipmentSlot.values()) {
            int index = slot.ordinal();
            if (index >= this.lastEquipIds.length) {
                continue;
            }

            ItemStack stack = viewer.getItemBySlot(slot);
            String id = ActorItems.idOf(stack);
            int count = stack.isEmpty() ? 0 : stack.getCount();
            String nbt = stack.isEmpty() ? null : Compat.itemStackNbt(viewer.level(), stack);
            // COMPAT: needs Compat.itemStackNbt(Level, ItemStack)

            if (id.equals(this.lastEquipIds[index]) && count == this.lastEquipCounts[index]
                && java.util.Objects.equals(nbt, this.lastEquipNbt[index])) {
                continue;
            }
            this.lastEquipIds[index] = id;
            this.lastEquipCounts[index] = count;
            this.lastEquipNbt[index] = nbt;
            take.equipment.add(new ActorEquip(replayTick, (byte) index, id, count, nbt));
        }
    }

    public void recordAttack(UUID target, int replayTick) {
        if (this.take != null) {
            this.take.events.add(ActorEvent.attack(replayTick, target));
        }
    }

    /**
     * Records a discrete block right-click.
     *
     * <p>Called from the use-item packet mixin at HEAD: intent, not outcome.
     * The client's arm still swung even when vanilla later rejects the use,
     * so recording matches what spectators saw. Dedups consecutive identical
     * uses on the same tick - holding right-click on some blocks sends one
     * packet per click plus repeats.
     */
    public static void captureUseBlock(Level level, BlockPos pos, boolean offHand) {
        TakeRecorder recorder = FlashbackActors.RECORDER;
        ActorTake take = recorder.take;
        ServerPlayer viewer = recorder.viewer;
        if (take == null || viewer == null || BlockEdits.isApplying()) {
            return;
        }
        if (viewer.level() != level) {
            return;
        }
        if (!take.uses.isEmpty()) {
            pdx.dev.fbactors.take.ActorUse last = take.uses.get(take.uses.size() - 1);
            if (last.tick() == recorder.currentTick
                && last.kind() == pdx.dev.fbactors.take.ActorUse.Kind.BLOCK
                && last.x() == pos.getX() && last.y() == pos.getY() && last.z() == pos.getZ()
                && last.offHand() == offHand) {
                return;
            }
        }
        take.uses.add(pdx.dev.fbactors.take.ActorUse.block(
            recorder.currentTick, pos.getX(), pos.getY(), pos.getZ(), offHand));
    }

    /**
     * Records a discrete air right-click (eat, bow draw, pearl throw).
     */
    public static void captureUseAir(Level level, boolean offHand) {
        TakeRecorder recorder = FlashbackActors.RECORDER;
        ActorTake take = recorder.take;
        ServerPlayer viewer = recorder.viewer;
        if (take == null || viewer == null || BlockEdits.isApplying()) {
            return;
        }
        if (viewer.level() != level) {
            return;
        }
        if (!take.uses.isEmpty()) {
            pdx.dev.fbactors.take.ActorUse last = take.uses.get(take.uses.size() - 1);
            if (last.tick() == recorder.currentTick
                && last.kind() == pdx.dev.fbactors.take.ActorUse.Kind.AIR
                && last.offHand() == offHand) {
                return;
            }
        }
        take.uses.add(pdx.dev.fbactors.take.ActorUse.air(recorder.currentTick, offHand));
    }

    /**
     * Records a discrete entity right-click. Null target means attack, which
     * is already covered by the attack callback path - skip to avoid doubles.
     */
    public static void captureUseEntity(Level level, @Nullable UUID target, boolean offHand) {
        if (target == null) {
            return;
        }
        TakeRecorder recorder = FlashbackActors.RECORDER;
        ActorTake take = recorder.take;
        ServerPlayer viewer = recorder.viewer;
        if (take == null || viewer == null || BlockEdits.isApplying()) {
            return;
        }
        if (viewer.level() != level) {
            return;
        }
        if (!take.uses.isEmpty()) {
            pdx.dev.fbactors.take.ActorUse last = take.uses.get(take.uses.size() - 1);
            if (last.tick() == recorder.currentTick
                && last.kind() == pdx.dev.fbactors.take.ActorUse.Kind.ENTITY
                && target.equals(last.target())
                && last.offHand() == offHand) {
                return;
            }
        }
        take.uses.add(pdx.dev.fbactors.take.ActorUse.entity(
            recorder.currentTick, target, offHand));
    }

    /**
     * Records a performer-placed entity seen entering the world.
     *
     * <p>Called from the addFreshEntity hook with the real entity: measured
     * position, facing, full NBT. Attribution comes from recency - a use in
     * the last 3 ticks by the recording viewer within 8 blocks - not from the
     * entity itself, which carries no placer. Uncorrelated spawns are not
     * recorded; recording the wrong thing is worse than recording nothing.
     */
    public static void captureSpawn(ServerLevel level, Entity entity) {
        TakeRecorder recorder = FlashbackActors.RECORDER;
        ActorTake take = recorder.take;
        ServerPlayer viewer = recorder.viewer;
        if (take == null || viewer == null || BlockEdits.isApplying()) {
            return;
        }
        if (viewer.level() != level) {
            return;
        }
        String entityId;
        try {
            var key = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                .getKey(entity.getType());
            entityId = key == null ? null : key.toString();
        } catch (RuntimeException | LinkageError e) {
            return;
        }
        if (entityId == null || !isSpawnTrackId(entityId)) {
            return;
        }
        double dx = entity.getX() - viewer.getX();
        double dy = entity.getY() - viewer.getY();
        double dz = entity.getZ() - viewer.getZ();
        if (dx * dx + dy * dy + dz * dz > 64.0) {
            return;
        }
        boolean recentUse = false;
        for (int i = take.uses.size() - 1; i >= 0; i--) {
            pdx.dev.fbactors.take.ActorUse use = take.uses.get(i);
            if (recorder.currentTick - use.tick() > 3) {
                break;
            }
            recentUse = true;
            break;
        }
        if (!recentUse) {
            return;
        }
        String nbt = Compat.entityNbt(entity);
        take.spawns.add(new pdx.dev.fbactors.take.ActorSpawn(
            recorder.currentTick, entityId,
            entity.getX(), entity.getY(), entity.getZ(),
            entity.getYRot(), entity.getXRot(), nbt));
    }

    /**
     * Whether this registry id is one of the six spawn-track families.
     *
     * <p>Matched on the path so boat/minecart variants need no schema change:
     * any {@code *_boat}, {@code *_raft}, or path containing
     * {@code minecart} qualifies, plus the four exact singles. Unknown or
     * modded ids degrade to unrecorded, never to a broken take.
     */
    public static boolean isSpawnTrackId(String entityId) {
        if (entityId == null || entityId.isEmpty()) {
            return false;
        }
        int slash = entityId.indexOf(':');
        String path = slash >= 0 ? entityId.substring(slash + 1) : entityId;
        if (path.equals("armor_stand") || path.equals("painting")
            || path.equals("item_frame") || path.equals("glow_item_frame")
            || path.equals("end_crystal")) {
            return true;
        }
        if (path.endsWith("_boat") || path.endsWith("_chest_boat")
            || path.endsWith("_raft") || path.endsWith("_chest_raft")
            || path.contains("boat") || path.contains("raft")) {
            return true;
        }
        return path.contains("minecart");
    }

    @Nullable
    public ActorTake stop() {
        pdx.dev.fbactors.voice.VoiceBuffer.finish();
        pdx.dev.fbactors.voice.MicCapture.stop();
        if (this.viewer != null) {
            pdx.dev.fbactors.morph.MorphRenderState.clear(this.viewer.getUUID());
        }
        ActorTake finished = this.take;
        this.take = null;
        this.viewer = null;
        this.lastSwinging = false;
        this.lastYaw = 0.0f;
        this.lastPitch = 0.0f;
        this.lastHealth = Float.NaN;
        this.lastAbsorption = Float.NaN;
        this.lastEffects = new HashMap<>();
        this.lastVehicleUuid = null;
        // Same reasoning as start(): the ids describe entities from a take that
        // has ended, and carrying them into the next one would suppress its first
        // shots whenever the entity id counter had not moved past them.
        this.seenArrowIds.clear();
        this.liveArrowIds = new HashSet<>();
        // Mining state is cleared for the same reason: a crack stage or position
        // from a previous take describes a block that no longer exists, and
        // carrying it into the next one would suppress the first mining state
        // snapshot of that take whenever the performer happened to mine the
        // same block at the same stage.
        this.lastMiningStage = -1;
        this.lastMiningX = 0;
        this.lastMiningY = 0;
        this.lastMiningZ = 0;
        this.lastMiningSwinging = false;
        if (finished == null || finished.frames.isEmpty()) {
            return null;
        }
        return finished;
    }
}
