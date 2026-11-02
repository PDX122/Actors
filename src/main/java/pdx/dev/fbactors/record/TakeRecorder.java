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
    private final WorldActionCapture worldActions = new WorldActionCapture();

    public boolean capturesWorldPosition(int x, int y, int z) {
        return take != null && worldActions.contains(x, y, z, currentTick);
    }

    public boolean isPerformer(java.util.UUID id) { return viewer != null && viewer.getUUID().equals(id); }

    public static Entity soundCause(Entity except, Entity target) {
        Entity source = target instanceof Projectile projectile ? projectile.getOwner() : target;
        return source != null && FlashbackActors.RECORDER.isPerformer(source.getUUID()) ? source : except;
    }

    private static final double CAPTURE_REACH = 6.0;
    private static final double CAPTURE_REACH_SQUARED = CAPTURE_REACH * CAPTURE_REACH;

    private static final int MAX_EFFECT_TICKS = 20 * 60 * 60;

    @Nullable
    private ActorTake take;

    @Nullable
    private ServerPlayer viewer;

    private int currentTick;
    private boolean lastSwinging;

    private String[] lastEquipIds;
    private int[] lastEquipCounts;

    private String[] lastEquipNbt;

    private int lastMiningStage = -1;
    private int lastMiningX;
    private int lastMiningY;
    private int lastMiningZ;
    private boolean lastMiningSwinging;

    @Nullable
    private UUID lastVehicleUuid;

    private float lastHealth = Float.NaN;
    private float lastAbsorption = Float.NaN;
    private Map<String, int[]> lastEffects;

    private float lastYaw;
    private float lastPitch;

    private final Set<Integer> seenArrowIds = new HashSet<>();

    private Set<Integer> liveArrowIds = new HashSet<>();

    private boolean preparing;

    public boolean isRecording() {
        return this.take != null && !preparing;
    }

    @Nullable
    public ActorTake current() {
        return this.take;
    }

    public int currentTick() {
        return this.currentTick;
    }

    public void prepare(ServerPlayer viewer, int replayTick, String name) {
        preparing = true;
        start(viewer, replayTick, name);
    }

    public void start(ServerPlayer viewer, int replayTick, String name) {
        ActorTake prepared = preparing ? this.take : null;
        boolean enteringPreparation = preparing && prepared == null;
        preparing = enteringPreparation;
        worldActions.clear();
        ServerLevel level = viewer.level() instanceof ServerLevel sl ? sl : null;
        String dimension = Compat.dimensionId(level);

        this.take = new ActorTake(UUID.randomUUID(), name, dimension, replayTick);
        if (prepared != null) {

            this.take.blocks.addAll(prepared.blocks);
            this.take.blockEntities.addAll(prepared.blockEntities);
            for (var spawn : prepared.spawns) {
                UUID entityId = pdx.dev.fbactors.actor.ActorEntityIdentity.read(spawn.nbt());
                Entity entity = level == null || entityId == null ? null : Compat.entityByUuid(level, entityId);
                if (entity != null && !entity.isRemoved()) {
                    this.take.spawns.add(new pdx.dev.fbactors.take.ActorSpawn(replayTick,
                        spawn.entityId(), entity.getX(), entity.getY(), entity.getZ(),
                        entity.getYRot(), entity.getXRot(), Compat.entityNbt(entity)));
                }
            }
        }
        pdx.dev.fbactors.voice.VoiceSettings.applyTo(this.take);
        pdx.dev.fbactors.morph.MorphSettings.applyTo(this.take);
        pdx.dev.fbactors.morph.MorphSelection selection =
            pdx.dev.fbactors.morph.Morph.selectionFor(this.take.id);

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

        this.seenArrowIds.clear();
        this.liveArrowIds = new HashSet<>();

        if (!preparing && this.take.voiceEnabled) {
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
        if (dx * dx + dy * dy + dz * dz > CAPTURE_REACH_SQUARED
            && !recorder.worldActions.contains(pos.getX(), pos.getY(), pos.getZ(), recorder.currentTick)) {
            return;
        }

        recorder.worldActions.note(pos.getX(), pos.getY(), pos.getZ(), recorder.currentTick);

        take.blocks.add(new ActorBlockEdit(recorder.currentTick,
            pos.getX(), pos.getY(), pos.getZ(),
            BlockEdits.serialize(before), BlockEdits.serialize(newState)));

        if (!newState.isAir() && Compat.hasBlockEntity(level, pos)) {

            String type = blockEntityType(level, newState);
            captureBlockEntity(level, pos, type);
        }
    }

    private static String blockEntityType(Level level, BlockState state) {
        var key = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return key != null ? key.getPath() : "unknown";
    }

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

        if (nbt == null || nbt.isEmpty()) {
            return;
        }

        if (isDuplicateBlockEntity(take, pos, nbt)) {
            return;
        }
        take.blockEntities.add(new ActorBlockEntityEdit(
            recorder.currentTick, pos.getX(), pos.getY(), pos.getZ(), type, nbt));
    }

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
        if (!own && !correlatedAction(take, recorder.currentTick, viewer, x, y, z)
            && !recorder.worldActions.contains((int)Math.floor(x), (int)Math.floor(y), (int)Math.floor(z), recorder.currentTick)) {
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

    private static final double SOUND_CORRELATION_BLOCKS = 8.0;

    private static boolean correlatedAction(ActorTake take, int tick, ServerPlayer viewer,
                                            double x, double y, double z) {
        double dx = x - viewer.getX();
        double dy = y - viewer.getY();
        double dz = z - viewer.getZ();
        if (dx * dx + dy * dy + dz * dz
            > SOUND_CORRELATION_BLOCKS * SOUND_CORRELATION_BLOCKS) {
            return false;
        }
        if (hasAt(take.uses, tick) || hasAt(take.blocks, tick)) {
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
            if (entry instanceof pdx.dev.fbactors.take.ActorUse e) {
                t = e.tick();
            } else if (entry instanceof ActorBlockEdit e) {
                t = e.tick();
            } else if (entry instanceof ActorContainerInteraction e) {
                t = e.tick();
            } else if (entry instanceof ActorBlockEntityEdit e) {
                t = e.tick();
            } else if (entry instanceof pdx.dev.fbactors.take.ActorRide e) {
                t = e.tick();
            } else if (entry instanceof ActorShot e) {

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

        var movement = pdx.dev.fbactors.actor.PerformerMovement.forPlayer(viewer.getUUID());
        if (movement != null) {
            var velocity = movement.velocity();
            take.momentum.add(new pdx.dev.fbactors.take.ActorMomentum(replayTick, velocity.x, velocity.y, velocity.z));
        }
        sampleInput(viewer, replayTick);

        this.lastSwinging = swinging;

        sampleEquipment(viewer, replayTick);
        sampleVitals(viewer, replayTick);
        sampleEffects(viewer, replayTick);
        sampleArrows(viewer, replayTick);
        sampleRides(viewer, replayTick);
    }

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

        var movement = pdx.dev.fbactors.actor.PerformerMovement.forPlayer(viewer.getUUID());
        take.inputs.add(new ActorInput(
            replayTick,
            movement == null ? viewer.zza : movement.forward(),
            movement == null ? viewer.xxa : movement.strafe(),
            movement == null ? Compat.jumpHeld(viewer) : movement.jump(),
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

                continue;
            }

            record(projectile, viewer, take, replayTick);
        }

        this.liveArrowIds = present;

        this.seenArrowIds.retainAll(present);
    }

    public static void captureProjectile(Entity entity) {
        TakeRecorder recorder = FlashbackActors.RECORDER;
        if (!recorder.isRecording() || !(entity instanceof Projectile projectile)
            || !(entity.level() instanceof ServerLevel level) || recorder.viewer == null
            || projectile.getOwner() != recorder.viewer) return;
        Class<?> arrow = arrowClass(level, recorder.viewer);
        if (arrow != null && arrow.isInstance(projectile) && recorder.seenArrowIds.add(entity.getId())) {
            record(projectile, recorder.viewer, recorder.take, recorder.currentTick);
        }
    }

    @Nullable
    private static Class<?> arrowClass(ServerLevel level, LivingEntity shooter) {
        if (ARROW_CLASS != null) {
            return ARROW_CLASS == NONE ? null : ARROW_CLASS;
        }
        try {
            if (Items.ARROW instanceof ArrowItem arrowItem) {
                ItemStack arrow = new ItemStack(Items.ARROW);
                ARROW_CLASS = arrowItem.createArrow(level, arrow, shooter, arrow).getClass().getSuperclass();
            } else {

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

    private static final Class<?> NONE = ActorArrows.class;

    private static Class<?> ARROW_CLASS;

    private static void record(Projectile arrow, ServerPlayer viewer, ActorTake take,
                               int replayTick) {
        Vec3 velocity = arrow.getDeltaMovement();
        double speed = velocity.length();

        ItemStack held = viewer.getMainHandItem();
        String itemId = held.isEmpty() ? null : ActorItems.idOf(held);

        take.shots.add(new ActorShot(
            replayTick - take.startTick,
            velocity.x,
            velocity.y,
            velocity.z,
            (float) speed,
            itemId,

            null, net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(arrow.getType()).toString(),
            Compat.entityNbt(arrow)));

        FlashbackActors.LOGGER.info(
            "Actor '{}' loosed an arrow at take tick {} (speed {})",
            take.name, replayTick - take.startTick, speed);
    }

    private void sampleRides(ServerPlayer viewer, int replayTick) {
        ActorTake take = this.take;
        if (take == null) {
            return;
        }

        Entity vehicle = viewer.getVehicle();
        if (vehicle != null) {
            var pose = new pdx.dev.fbactors.take.ActorVehicleFrame(replayTick,
                vehicle.getUUID(), vehicleTypeId(vehicle), vehicle.getX(), vehicle.getY(), vehicle.getZ(),
                vehicle.getYRot(), vehicle.getXRot());
            var movement = pdx.dev.fbactors.actor.PerformerMovement.forPlayer(viewer.getUUID());
            if (movement != null && vehicle.getUUID().equals(movement.vehicle())) {
                var velocity = movement.vehicleVelocity();
                pose = pose.withMotion(velocity.x, velocity.y, velocity.z,
                    movement.leftPrevious(), movement.left(), movement.rightPrevious(), movement.right(),
                    movement.leftPaddle(), movement.rightPaddle());
            }
            take.vehicles.add(pose);

            if (!java.util.Objects.equals(vehicle.getUUID(), this.lastVehicleUuid)
                && take.spawns.stream().noneMatch(spawn -> vehicle.getUUID().equals(
                    pdx.dev.fbactors.actor.ActorEntityIdentity.read(spawn.nbt())))) {
                take.spawns.add(new pdx.dev.fbactors.take.ActorSpawn(replayTick, vehicleTypeId(vehicle),
                    vehicle.getX(), vehicle.getY(), vehicle.getZ(), vehicle.getYRot(), vehicle.getXRot(),
                    Compat.entityNbt(vehicle)));
            }
        }
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

    public static void captureSwing(LivingEntity player, net.minecraft.world.InteractionHand hand) {
        captureSwing(player, hand, null, -1);
    }

    public static void captureSwing(LivingEntity player, net.minecraft.world.InteractionHand hand,
                                    String animation, int duration) {
        TakeRecorder recorder = FlashbackActors.RECORDER;
        if (!recorder.isRecording() || player != recorder.viewer) return;
        ActorEvent.Type type = hand == net.minecraft.world.InteractionHand.OFF_HAND
            ? ActorEvent.Type.SWING_OFF : ActorEvent.Type.SWING_MAIN;
        for (ActorEvent event : recorder.take.events) {
            if (event.tick() == recorder.currentTick && event.type() == type) return;
        }
        recorder.take.events.add(new ActorEvent(recorder.currentTick, type, null,
            -1.0f, -1.0, false, animation, duration));
    }

    public void recordAttack(UUID target, int replayTick, float strength, double fallDistance, boolean sprinting) {
        if (this.take != null) {
            this.take.events.add(new ActorEvent(replayTick, ActorEvent.Type.ATTACK, target, strength, fallDistance, sprinting));
        }
    }

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
        var stack = viewer.getItemInHand(offHand ? net.minecraft.world.InteractionHand.OFF_HAND : net.minecraft.world.InteractionHand.MAIN_HAND);
        take.uses.add(pdx.dev.fbactors.take.ActorUse.block(
            recorder.currentTick, pos.getX(), pos.getY(), pos.getZ(), offHand)
            .withItem(ActorItems.idOf(stack), Compat.itemStackNbt(level, stack)));
    }

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
        var stack = viewer.getItemInHand(offHand ? net.minecraft.world.InteractionHand.OFF_HAND : net.minecraft.world.InteractionHand.MAIN_HAND);
        take.uses.add(pdx.dev.fbactors.take.ActorUse.air(recorder.currentTick, offHand)
            .withItem(ActorItems.idOf(stack), Compat.itemStackNbt(level, stack)));
    }

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

    public static boolean canCaptureSpawn(ServerLevel level, Entity entity) {
        TakeRecorder recorder = FlashbackActors.RECORDER;
        ActorTake take = recorder.take;
        ServerPlayer viewer = recorder.viewer;
        if (entity == null || take == null || viewer == null || BlockEdits.isApplying()) {
            return false;
        }
        if (viewer.level() != level) {
            return false;
        }
        String entityId;
        try {
            var key = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                .getKey(entity.getType());
            entityId = key == null ? null : key.toString();
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
        boolean spawnEgg = entity instanceof LivingEntity && take.uses.stream().anyMatch(use ->
            use.tick() == recorder.currentTick && use.itemId() != null && use.itemId().endsWith("_spawn_egg"));
        if (entityId == null || (!isSpawnTrackId(entityId) && !spawnEgg)) return false;
        double dx = entity.getX() - viewer.getX();
        double dy = entity.getY() - viewer.getY();
        double dz = entity.getZ() - viewer.getZ();
        if (dx * dx + dy * dy + dz * dz > 64.0) {
            return false;
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
            return false;
        }
        return true;
    }

    public static void captureSpawn(ServerLevel level, Entity entity) {
        if (!canCaptureSpawn(level, entity)) return;
        TakeRecorder recorder = FlashbackActors.RECORDER;
        ActorTake take = recorder.take;
        String entityId = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
        pdx.dev.fbactors.actor.ActorWorldSimulation.track(entity);
        String nbt = Compat.entityNbt(entity);
        take.spawns.add(new pdx.dev.fbactors.take.ActorSpawn(
            recorder.currentTick, entityId,
            entity.getX(), entity.getY(), entity.getZ(),
            entity.getYRot(), entity.getXRot(), nbt));
    }

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
        preparing = false;
        this.take = null;
        this.viewer = null;
        this.lastSwinging = false;
        this.lastYaw = 0.0f;
        this.lastPitch = 0.0f;
        this.lastHealth = Float.NaN;
        this.lastAbsorption = Float.NaN;
        this.lastEffects = new HashMap<>();
        this.lastVehicleUuid = null;

        this.seenArrowIds.clear();
        this.liveArrowIds = new HashSet<>();

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
