package pdx.dev.fbactors.actor;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import pdx.dev.fbactors.compat.Compat;
import pdx.dev.fbactors.swing.DamageStateAccess;
import pdx.dev.fbactors.swing.SwapPenaltyAccess;
import pdx.dev.fbactors.take.ActorEvent;
import pdx.dev.fbactors.take.ActorFrame;
import pdx.dev.fbactors.take.ActorHit;
import pdx.dev.fbactors.take.ActorInput;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public final class InputSim {

    public static final double DRIFT_REPORT = 4.0;

    private static final int DRIFT_WARN_AFTER = 20 * 30;

    private final Actor actor;

    private int divergedAtTick = -1;
    private boolean requestedGlide;

    private int physicalTicks;

    private int lastSimTick = Integer.MIN_VALUE;

    private int cachedInputTick = Integer.MIN_VALUE;
    @Nullable
    private ActorInput cachedInput;

    private int lastPrepareTick = Integer.MIN_VALUE;
    private int lastBaselineTick = Integer.MIN_VALUE;
    private int lastActionTick = Integer.MIN_VALUE;
    private int lastPublishTick = Integer.MIN_VALUE;

    private boolean driftReported;

    private boolean baselineDriftReported;

    private boolean repeatWarned;

    InputSim(Actor actor) {
        this.actor = actor;
    }

    public boolean physical() {
        return this.divergedAtTick >= 0;
    }

    public int divergedAtTick() {
        return this.divergedAtTick;
    }

    public int lastSimTick() {
        return this.lastSimTick;
    }

    public boolean usesRecordedBaseline(int takeTick) {
        return this.divergedAtTick < 0 || takeTick < this.divergedAtTick;
    }

    public void diverge(int takeTick) {
        if (this.divergedAtTick < 0) {
            this.divergedAtTick = takeTick;
            this.physicalTicks = 0;
            this.driftReported = false;
        }
    }

    public void reset() {
        this.divergedAtTick = -1;
        this.requestedGlide = false;
        actor.combat().reset();
        this.physicalTicks = 0;
        this.lastSimTick = Integer.MIN_VALUE;
        this.cachedInputTick = Integer.MIN_VALUE;
        this.cachedInput = null;
        this.lastPrepareTick = Integer.MIN_VALUE;
        this.lastBaselineTick = Integer.MIN_VALUE;
        this.lastActionTick = Integer.MIN_VALUE;
        this.lastPublishTick = Integer.MIN_VALUE;
        this.driftReported = false;
        this.baselineDriftReported = false;
        this.baselineDriftReported = false;
        this.repeatWarned = false;
    }

    public boolean prepare(ServerLevel level, ActorClonePlayer clone, int takeTick) {
        if (this.lastPrepareTick == takeTick) {

            if (!this.repeatWarned) {
                this.repeatWarned = true;
                pdx.dev.fbactors.FlashbackActors.LOGGER.warn(
                    "Actor '{}' re-entered take tick {}; treating it as a scrub",
                    name(), takeTick);
            }
        }

        this.lastPrepareTick = takeTick;

        int tickDelta = takeTick - this.lastSimTick;
        if (tickDelta < -2 || tickDelta > 5) {
            actor.combat().seek();
            if (!actor.combat().restoreBefore(clone,takeTick)) rewindToBoundary(level, clone, takeTick);
        }
        this.lastSimTick = takeTick;

        return !this.usesRecordedBaseline(takeTick);
    }

    public void applyBaseline(ActorClonePlayer clone, ActorFrame frame, int takeTick) {
        if (this.usesRecordedBaseline(takeTick)) {
            applyRecordedState(clone, frame, recordedVelocity(takeTick), this.actor.take.vehicleAt(takeTick));
            this.lastBaselineTick = takeTick;
        }
    }

    public boolean replayStrike(ActorClonePlayer clone, int takeTick) {
        return actor.combat().replay(clone, takeTick);
    }

    private Vec3 recordedVelocity(int takeTick) {

        var motion = pdx.dev.fbactors.settings.FbSettings.settings().slidingMomentum
            ? this.actor.take.momentumAt(takeTick) : null;
        if (motion != null && motion.finite()) return new Vec3(motion.x(), motion.y(), motion.z());
        ActorFrame here = this.actor.take.frameAt(takeTick);
        ActorFrame before = this.actor.take.frameAt(takeTick - 1);
        if (here == null || before == null) {
            return Vec3.ZERO;
        }

        Vec3 velocity = new Vec3(
            here.x() - before.x(),
            here.y() - before.y(),
            here.z() - before.z());

        double speed = velocity.length();
        if (speed > MAX_RECORDED_SPEED) {
            return velocity.scale(MAX_RECORDED_SPEED / speed);
        }
        return velocity;
    }

    private static final double MAX_RECORDED_SPEED = 10.0;

    public void step(ServerLevel level, ActorClonePlayer clone, int takeTick) {
        if (!this.usesRecordedBaseline(takeTick)) {
            this.physicalTicks++;
            reportDrift(level, clone, takeTick);
        }

        ActorInput input = inputAt(takeTick);
        float oldHead = clone.getYHeadRot(), oldBody = clone.yBodyRot;

        applyLook(clone, takeTick, input);
        applyMovement(clone, input);
        var flightFrame = actor.take.frameAt(takeTick);
        boolean wantsGlide = flightFrame != null && flightFrame.fallFlying();
        if (pdx.dev.fbactors.settings.FbSettings.settings().flightPlayback
            && !this.usesRecordedBaseline(takeTick) && wantsGlide && !requestedGlide && !clone.isFallFlying())
            clone.tryToStartFallFlying();
        requestedGlide = wantsGlide && (requestedGlide || clone.isFallFlying());
        applyHeld(clone, input);

        if (!this.usesRecordedBaseline(takeTick)) BoatPhysics.step(clone.getVehicle());
        clone.tick();
        if (!this.usesRecordedBaseline(takeTick) && clone.getVehicle() != null) {
            Vec3 seat = clone.getVehicle().getPassengerRidingPosition(clone);
            clone.setPos(seat.x, seat.y, seat.z);
            BoatPhysics.seat(clone, oldHead, oldBody);
        }
        clone.physical(!this.usesRecordedBaseline(takeTick));

        Compat.cancelInterpolation(clone);

        if (this.usesRecordedBaseline(takeTick)) {

            restoreTimers(clone, input);
            checkBaselineFidelity(clone, takeTick);
            snapToRecorded(clone, takeTick);
            driveRecordedLimbs(clone, takeTick);
        }
    }

    private static final double BASELINE_DRIFT_WARN_BLOCKS = 0.5;

    private void checkBaselineFidelity(ActorClonePlayer clone, int takeTick) {
        if (this.baselineDriftReported) {
            return;
        }
        ActorFrame next = this.actor.take.frameAt(takeTick + 1);
        if (next == null) {
            return;
        }
        double dx = clone.getX() - next.x();
        double dy = clone.getY() - next.y();
        double dz = clone.getZ() - next.z();
        double drift = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (drift <= BASELINE_DRIFT_WARN_BLOCKS) {
            return;
        }
        this.baselineDriftReported = true;
        ActorInput input = inputAt(takeTick);
        pdx.dev.fbactors.FlashbackActors.LOGGER.warn(
            "Actor '{}' left the recording at take tick {} (off by {} blocks, jumping {})",
            name(), takeTick, String.format(java.util.Locale.ROOT, "%.2f", drift),
            input != null && input.jump());
    }

    private void snapToRecorded(ActorClonePlayer clone, int takeTick) {
        pdx.dev.fbactors.take.ActorFrame frame = this.actor.take.frameAt(takeTick);
        if (frame == null) {
            return;
        }
        frame = ActorVehicles.riderFrame(clone, frame, this.actor.take.vehicleAt(takeTick));

        Entity ridden = clone.getVehicle();
        if (ridden != null) {
            Vec3 seatOffset = ridden.getPassengerRidingPosition(clone).subtract(ridden.position());
            Compat.snapTo(ridden, frame.x() - seatOffset.x, frame.y() - seatOffset.y,
                frame.z() - seatOffset.z, ridden.getYRot(), ridden.getXRot());
        }
        Compat.snapTo(clone, frame.x(), frame.y(), frame.z(), frame.yRot(), frame.xRot());
    }

    public void driveRecordedLimbs(ActorClonePlayer clone, int takeTick) {
        if (!this.usesRecordedBaseline(takeTick)) {
            return;
        }
        if (clone.isDeadOrDying()) {
            return;
        }
        Compat.setWalkDist(clone, this.actor.take.walkDistAt(takeTick),
            this.actor.take.walkDistOAt(takeTick));
        Compat.setWalkAnimation(clone, this.actor.take.walkSpeedOldAt(takeTick),
            this.actor.take.walkSpeedAt(takeTick), this.actor.take.walkPositionAt(takeTick));
    }

    public void runActions(ActorClonePlayer clone, int takeTick) {
        if (this.lastActionTick == takeTick) {

            if (!this.repeatWarned) {
                this.repeatWarned = true;
                pdx.dev.fbactors.FlashbackActors.LOGGER.warn(
                    "Actor '{}' re-ran actions for take tick {}; treating it as a scrub",
                    name(), takeTick);
            }
        }
        this.lastActionTick = takeTick;

        ActorInput input = inputAt(takeTick);
        for (ActorEvent attack : this.actor.take.eventsAt(takeTick)) {
            if (attack.type() != ActorEvent.Type.ATTACK) continue;
            restoreAttackCharge(clone, attack.attackStrength() >= 0.0f
                ? attack.attackStrength() : input == null ? 1.0f : input.attackStrength());
            if (attack.fallDistance() >= 0.0) {
                Compat.setFallDistance(clone, (float) attack.fallDistance());
                clone.setSprinting(attack.sprinting());
            }
            Entity struck = ActorAttackResolver.resolve(clone, attack.target());
            if (struck != null) ActorAttackResolver.strike(clone, struck, takeTick);
            else {
                Actor victim=pdx.dev.fbactors.FlashbackActors.ACTORS.byUuid(attack.target());
                if(victim!=null)victim.combat().reject(takeTick+actor.take.offsetTicks-victim.take.offsetTicks);
            }

        }
    }

    public void publish(ActorClonePlayer clone, int takeTick) {
        if (this.lastPublishTick == takeTick) {

        if (!this.repeatWarned) {
            this.repeatWarned = true;
            pdx.dev.fbactors.FlashbackActors.LOGGER.warn(
                "Actor '{}' re-published take tick {}; treating it as a scrub",
                name(), takeTick);
        }
        }
        this.lastPublishTick = takeTick;
        actor.combat().capture(clone,takeTick);

    }

    private void rewindToBoundary(ServerLevel level, ActorClonePlayer clone, int takeTick) {

        this.lastPrepareTick = Integer.MIN_VALUE;
        this.lastBaselineTick = Integer.MIN_VALUE;
        this.lastActionTick = Integer.MIN_VALUE;
        this.lastPublishTick = Integer.MIN_VALUE;

        int from = this.divergedAtTick < 0
            ? takeTick
            : Math.min(this.divergedAtTick, takeTick);
        ActorFrame frame = this.actor.take.frameAt(from);
        if (frame == null) {
            return;
        }

        Compat.snapTo(clone, frame.x(), frame.y(), frame.z(), frame.yRot(), frame.xRot());

        clone.setDeltaMovement(recordedVelocity(from));
        clone.setOnGround(frame.onGround());
        clone.trackLastPosition();
        this.physicalTicks = 0;
    }

    private static void applyRecordedState(ActorClonePlayer clone, ActorFrame frame,
                                           Vec3 velocity, pdx.dev.fbactors.take.ActorVehicleFrame vehiclePose) {
        frame = ActorVehicles.riderFrame(clone, frame, vehiclePose);

        Entity vehicle = clone.getVehicle();
        if (vehicle != null) {
            Vec3 seatOffset = vehicle.getPassengerRidingPosition(clone).subtract(vehicle.position());
            Compat.snapTo(vehicle, frame.x() - seatOffset.x, frame.y() - seatOffset.y,
                frame.z() - seatOffset.z, vehicle.getYRot(), vehicle.getXRot());
        }
        double snapDx = clone.getX() - frame.x();
        double snapDy = clone.getY() - frame.y();
        double snapDz = clone.getZ() - frame.z();
        double snapDist = Math.sqrt(snapDx * snapDx + snapDy * snapDy + snapDz * snapDz);
        if (snapDist > 2.0) {
            logSnap(clone, frame, snapDist);
        }
        Compat.snapTo(clone, frame.x(), frame.y(), frame.z(), frame.yRot(), frame.xRot());

        clone.setYHeadRot(frame.yHeadRot());

        clone.setYBodyRot(frame.yRot());

        clone.setXRot(Mth.clamp(frame.xRot(), -90.0f, 90.0f));

        clone.applyPoseState(frame);

        clone.setDeltaMovement(velocity);

        Compat.setFallDistance(clone, ActorFallDistance.infer(frame));
    }

    private static final java.util.Map<UUID, Long> LAST_SNAP_LOG = new java.util.HashMap<>();

    private static void logSnap(ActorClonePlayer clone, ActorFrame frame, double dist) {
        UUID id = clone.getUUID();
        long now = System.currentTimeMillis();
        Long last = LAST_SNAP_LOG.get(id);
        if (last != null && now - last < 5000) {
            return;
        }
        LAST_SNAP_LOG.put(id, now);
        pdx.dev.fbactors.FlashbackActors.LOGGER.warn(
            "Actor baseline snapped {}m in one tick: body was ({},{},{}) recording says ({},{},{})",
            String.format(java.util.Locale.ROOT, "%.1f", dist),
            String.format(java.util.Locale.ROOT, "%.1f", clone.getX()),
            String.format(java.util.Locale.ROOT, "%.1f", clone.getY()),
            String.format(java.util.Locale.ROOT, "%.1f", clone.getZ()),
            String.format(java.util.Locale.ROOT, "%.1f", frame.x()),
            String.format(java.util.Locale.ROOT, "%.1f", frame.y()),
            String.format(java.util.Locale.ROOT, "%.1f", frame.z()));
    }

    private static void restoreTimers(ActorClonePlayer clone, @Nullable ActorInput input) {
        if (input == null) {
            return;
        }
        if (clone.hurtTime > 0 && clone.hurtDuration < MIN_HURT_DURATION) {

            clone.hurtDuration = MIN_HURT_DURATION;
        }
    }

    private static final int MIN_HURT_DURATION = 10;

    private static void restoreAttackCharge(ActorClonePlayer clone, float recordedStrength) {
        float strength = Mth.clamp(recordedStrength, 0.0f, 1.0f);
        float delay = Math.max(1.0f, clone.getCurrentItemAttackStrengthDelay());

        int ticks = strength >= FULLY_CHARGED
            ? FULLY_CHARGED_TICKS
            : Math.max(0, Math.round(delay * strength - 0.5f));
        ((DamageStateAccess) clone).fbactors$setAttackStrengthTicker(ticks);

        if (clone instanceof SwapPenaltyAccess) {
            ((SwapPenaltyAccess) clone).fbactors$setItemSwapTicker(SwapPenaltyAccess.NO_PENALTY);
        }
    }

    private static final float FULLY_CHARGED = 0.999f;

    private static final int FULLY_CHARGED_TICKS = 1000;

    @Nullable
    private ActorInput inputAt(int takeTick) {
        if (takeTick == this.cachedInputTick) {
            return this.cachedInput;
        }
        ActorInput input = this.actor.take.inputAt(takeTick);
        this.cachedInputTick = takeTick;
        this.cachedInput = input;
        return input;
    }

    private void applyLook(ActorClonePlayer clone, int takeTick, @Nullable ActorInput input) {
        if (input == null || this.usesRecordedBaseline(takeTick)) {
            return;
        }
        float yawStep = Mth.clamp(input.yawDelta(), -MAX_TURN_PER_TICK, MAX_TURN_PER_TICK);
        float pitchStep = Mth.clamp(input.pitchDelta(), -MAX_TURN_PER_TICK, MAX_TURN_PER_TICK);
        if (yawStep != 0.0f) {
            clone.setYRot(clone.getYRot() + yawStep);
        }
        if (pitchStep != 0.0f) {
            clone.setXRot(Mth.clamp(clone.getXRot() + pitchStep, -90.0f, 90.0f));
        }
    }

    private static void applyMovement(ActorClonePlayer clone, @Nullable ActorInput input) {
        if (input == null) {
            clone.xxa = 0.0f;
            clone.zza = 0.0f;
            clone.setShiftKeyDown(false);
            Compat.setJumpHeld(clone, false);
            clone.setSprinting(false);
            applyFlying(clone, false);
            return;
        }

        clone.xxa = input.strafeAxis();
        clone.zza = input.forwardAxis();
        clone.setShiftKeyDown(input.sneak());
        Compat.setJumpHeld(clone, input.jump());

        clone.setSprinting(input.sprint());

        applyFlying(clone, input.flying());
    }

    private static void applyFlying(ActorClonePlayer clone, boolean flying) {
        if (clone.getAbilities().flying == flying && clone.getAbilities().mayfly == flying) {
            return;
        }
        clone.getAbilities().flying = flying;
        clone.getAbilities().mayfly = flying;
        clone.onUpdateAbilities();
    }

    private static void applyHeld(ActorClonePlayer clone, @Nullable ActorInput input) {
        if (input == null) {
            if (clone.isUsingItem()) {
                try {
                    clone.stopUsingItem();
                } catch (Exception | LinkageError ignored) {
                }
            }
            return;
        }
        Compat.setSelectedSlot(clone, input.selectedSlot());
        if (clone.isDeadOrDying()) {
            if (clone.isUsingItem()) {
                try {
                    clone.stopUsingItem();
                } catch (Exception | LinkageError ignored) {
                }
            }
            return;
        }
        boolean wantUse = input.useHeld();
        net.minecraft.world.InteractionHand wantHand = input.useOffhand()
            ? net.minecraft.world.InteractionHand.OFF_HAND
            : net.minecraft.world.InteractionHand.MAIN_HAND;
        try {
            if (wantUse) {
                net.minecraft.world.item.ItemStack held;
                try {
                    held = clone.getItemInHand(wantHand);
                } catch (Exception | LinkageError e) {
                    held = net.minecraft.world.item.ItemStack.EMPTY;
                }
                if (held == null || held.isEmpty()) {
                    if (clone.isUsingItem()) {
                        clone.stopUsingItem();
                    }
                    return;
                }
                if (clone.isUsingItem()) {
                    net.minecraft.world.InteractionHand current;
                    try {
                        current = clone.getUsedItemHand();
                    } catch (Exception | LinkageError e) {
                        current = wantHand;
                    }
                    if (current != wantHand) {
                        clone.stopUsingItem();
                        clone.startUsingItem(wantHand);
                    } else {
                        net.minecraft.world.item.ItemStack using;
                        try {
                            using = clone.getUseItem();
                        } catch (Exception | LinkageError e) {
                            using = null;
                        }
                        if (using == null || using.isEmpty()
                            || !net.minecraft.world.item.ItemStack.isSameItem(using, held)) {
                            clone.stopUsingItem();
                            clone.startUsingItem(wantHand);
                        }
                    }
                } else {
                    clone.startUsingItem(wantHand);
                }
            } else if (clone.isUsingItem()) {
                ItemActionReplay.release(clone);
            }
        } catch (Exception | LinkageError ignored) {

        }
    }

    private static final float MAX_TURN_PER_TICK = 180.0f;

    private void reportDrift(ServerLevel level, ActorClonePlayer clone, int takeTick) {
        if (this.driftReported || this.physicalTicks < DRIFT_WARN_AFTER) {
            return;
        }
        ActorFrame frame = this.actor.take.frameAt(takeTick);
        if (frame == null) {
            return;
        }
        double dx = clone.getX() - frame.x();
        double dy = clone.getY() - frame.y();
        double dz = clone.getZ() - frame.z();
        double drift = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (drift <= DRIFT_REPORT) {
            return;
        }
        this.driftReported = true;
        pdx.dev.fbactors.FlashbackActors.LOGGER.info(
            "Actor '{}' has diverged {} blocks from its recording after {} ticks"
                + " (diverged at take tick {}); it stays simulated from here",
            this.actor.take.name, drift, this.physicalTicks, this.divergedAtTick);
    }

    private String name() {
        return this.actor.take.name == null ? this.actor.take.id.toString() : this.actor.take.name;
    }
}
