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

/**
 * Replays a take's recorded input through a real player and lets vanilla's own
 * movement and combat code decide where the actor ends up.
 *
 * <p>There is no custom damage arithmetic and no custom physics anywhere in this
 * class, and that is the whole design. A take that stored its performer's
 * position every tick threw away the cause of the position: two takes can agree
 * on every coordinate and still have arrived there by opposite routes, at
 * different speeds, with different momentum going into a collision. An earlier
 * version compensated with a hand-rolled A* to walk the actor home after a hit,
 * which had never seen the world the actor was actually in and so undid exactly
 * the fights whose outcome depended on where the actor finished.
 *
 * <p>Recording the input keeps the cause and lets the effect be recomputed by the
 * same code that computes it for a real player. Acceleration, friction, sprint
 * gating, water, ladders, elytra, collision, step-up, armour, resistance, knockback
 * and crits are then vanilla's problem rather than ours, which is the only way
 * they come out identical to a player standing there instead of an actor.
 *
 * <h2>The five phases</h2>
 *
 * <p>Each tick runs in a fixed order, and the order is load bearing rather than
 * incidental:
 *
 * <ol>
 *   <li><b>Prepare</b> &mdash; make sure the clone exists and is in the right
 *       dimension, and that the divergence latch has been consulted.
 *   <li><b>Baseline</b> &mdash; while the take is still authoritative, put the
 *       clone back onto the recorded frame <em>including vanilla's hidden state</em>.
 *   <li><b>Tick</b> &mdash; drive input in, then run one real entity tick, which is
 *       where {@code aiStep} and therefore collision live.
 *   <li><b>Actions</b> &mdash; fire the discrete things that happened this tick:
 *       swings, attacks, item use.
 *   <li><b>Publish</b> &mdash; capture the resulting state for the renderer.
 * </ol>
 *
 * <p>Each phase records the tick it last ran in, so that a phase which runs
 * twice for the same tick can be seen rather than silently integrating movement
 * twice. That is the failure this whole class used to have: {@code ActorManager}
 * and the clone's own tick both drove the body, the actor moved at double speed,
 * and nothing anywhere reported it.
 *
 * <p>A repeated take tick is deliberately <em>not</em> treated as that
 * programming error. Finishing a take seeks the playhead back to where the take
 * started, so the opening ticks are legitimately presented a second time, and
 * the viewer can scrub backwards at will. Throwing on a repeat took the server
 * thread down on the ordinary act of saving a take. The discontinuity handling
 * in {@link #prepare} already gives the right answer for it &mdash; rewind to a
 * boundary the recording describes and let the ticks re-derive forward &mdash;
 * so a repeat warns once and falls through to that.
 *
 * <h2>What the baseline restores, and what it must not restore twice</h2>
 *
 * <p>A take describes the same movement twice over. Every frame carries an
 * absolute rotation, and every input carries the delta that produced it. Every
 * frame also carries an absolute position, and no input carries the velocity
 * that goes with it, so velocity is the one quantity the baseline has to derive.
 *
 * <p>Applying both an absolute and its own delta is the defect that made actors
 * turn at twice the performer's rate and then snap on the tick the turn stopped.
 * The rule this class now follows is therefore that each quantity has exactly
 * one writer: the baseline owns absolute position, absolute rotation and
 * derived velocity, and nothing downstream re-adds the delta that produced
 * them. Look is the exception, because past divergence the baseline stops
 * running and the delta is all that is left of the performer's gaze.
 *
 * <p>Position and velocity have to agree, or the disagreement is re-injected
 * every tick. The baseline writes an absolute position while vanilla integrates
 * a velocity, and it is the velocity that decides where the body ends up
 * relative to it. Seeding the velocity the recording implies makes one tick of
 * vanilla physics carry the body from {@code F_n} to {@code F_{n+1}}; leaving
 * whatever the last tick happened to produce adds a spurious acceleration to
 * every visible transition, which is what "choppy" is.
 *
 * <h2>Baseline and divergence</h2>
 *
 * <p>A take is authoritative until something happens that the recording does not
 * account for, at which point the simulation becomes authoritative and stays
 * that way. The switch is <em>causal</em>: it is triggered by an event having a
 * real consequence, not by guessing from geometry. A previous version re-blended
 * the actor onto the recording once its pathfinder happened to arrive within half
 * a block, which undid fights whose result depended on where the actor ended up
 * and could not distinguish "the actor got back on track" from "the actor is
 * standing exactly where it was hit".
 *
 * <p>Divergence does not unlatch. An actor that has been genuinely hit is no
 * longer performing the take, and sliding it back onto the recorded path would
 * erase the consequence of the hit, which is the entire reason combat is worth
 * simulating.
 */
public final class InputSim {

    /**
     * How far the simulation may drift from the recording before the
     * discrepancy is worth logging rather than carrying silently.
     *
     * <p>Not a correction. Seeing a large value means something upstream is
     * wrong, and quietly nudging the actor back would hide exactly that. It
     * used to be declared and never compared against anything, which is the
     * same as not having a drift check at all.
     */
    public static final double DRIFT_REPORT = 4.0;

    /**
     * The number of consecutive ticks the actor is allowed to spend diverged
     * before we accept it as a new normal rather than an anomaly.
     *
     * <p>This is a diagnostic threshold, not a timeout. The previous code used a
     * limit like this as a <em>give up</em> timer: past it, the actor was
     * silently snapped back onto its recording in the middle of a knockback,
     * with no log line and no consequence other than a fight that quietly undid
     * itself. Logging it means a take whose actor is never landing anywhere
     * sensible gets reported instead of being covered up.
     */
    private static final int DRIFT_WARN_AFTER = 20 * 30;

    private final Actor actor;

    /** Tick at which the take stopped being authoritative, or -1. */
    private int divergedAtTick = -1;

    /** Ticks the sim has owned the body for, for the drift report. */
    private int physicalTicks;

    private int lastSimTick = Integer.MIN_VALUE;

    /** Cached per-tick input lookups; a take is sampled far more than it changes. */
    private int cachedInputTick = Integer.MIN_VALUE;
    @Nullable
    private ActorInput cachedInput;

    /**
     * The phases that have already run for the current tick.
     *
     * <p>Five separate sentinels rather than a bitmask so the failure message
     * can name the phase that repeated, which is the difference between "the
     * actor is broken" and "actions ran twice for tick 412".
     */
    private int lastPrepareTick = Integer.MIN_VALUE;
    private int lastBaselineTick = Integer.MIN_VALUE;
    private int lastActionTick = Integer.MIN_VALUE;
    private int lastPublishTick = Integer.MIN_VALUE;

    /** Whether this tick's health loss has already been turned into a hit. */
    private int lastMeasureTick = Integer.MIN_VALUE;

    /**
     * The tick a recorded hit has already been re-delivered on.
     *
     * <p>Guards {@link #replayStrike} the way the phase sentinels guard their
     * phases: a tick presented twice in a row must not shove twice. It is
     * deliberately an equality check rather than a "have we passed it" check, so
     * that the ordinary scrub - finishing a take seeks the playhead back to where
     * the take started - replays the shove rather than skipping it.
     */
    private int lastStrikeTick = Integer.MIN_VALUE;

    /** Health before this tick's simulation ran, for measuring damage. */
    private float healthBeforeTick = -1.0f;

    /** Whether the drift report has already fired for this divergence. */
    private boolean driftReported;

    /** Whether the baseline-fidelity report has already fired for this run. */
    private boolean baselineDriftReported;

    /** Whether the repeated-tick scrub warning has already fired for this run. */
    private boolean repeatWarned;

    InputSim(Actor actor) {
        this.actor = actor;
    }

    /**
     * Whether the simulation, rather than the recording, is currently
     * authoritative for this actor.
     */
    public boolean physical() {
        return this.divergedAtTick >= 0;
    }

    /** The tick divergence latched on, or -1 while still on the recording. */
    public int divergedAtTick() {
        return this.divergedAtTick;
    }

    public int lastSimTick() {
        return this.lastSimTick;
    }

    /**
     * Whether the take is still the thing describing this actor at a tick.
     *
     * <p>The single predicate that decides whether the recording or the
     * simulation owns the body, its health and its pose. Before divergence the
     * recording is truth; from the divergence tick onwards it is not, and
     * anything that writes recorded state over a diverged actor is erasing the
     * hit that caused the divergence.
     */
    public boolean usesRecordedBaseline(int takeTick) {
        return this.divergedAtTick < 0 || takeTick < this.divergedAtTick;
    }

    /**
     * Latches divergence for something that has to be simulated because the
     * recording cannot express it.
     *
     * <p>Once latched it does not unlatch. An actor that has been genuinely hit
     * is no longer performing the take, and sliding it back onto the recorded
     * path would erase the consequence of the hit, which is the entire reason
     * combat is worth simulating.
     */
    public void diverge(int takeTick) {
        if (this.divergedAtTick < 0) {
            this.divergedAtTick = takeTick;
            this.physicalTicks = 0;
            this.driftReported = false;
        }
    }

    /**
     * Drops every piece of simulation state.
     *
     * <p>Only for a genuine discontinuity, where the body is in a state that
     * cannot be reasoned about. The seek path calls
     * {@link #rewindToBoundary} instead, which keeps the latch, because
     * forgetting that an actor was struck is how a scrub silently un-kills it.
     */
    public void reset() {
        this.divergedAtTick = -1;
        this.physicalTicks = 0;
        this.lastSimTick = Integer.MIN_VALUE;
        this.cachedInputTick = Integer.MIN_VALUE;
        this.cachedInput = null;
        this.lastPrepareTick = Integer.MIN_VALUE;
        this.lastBaselineTick = Integer.MIN_VALUE;
        this.lastActionTick = Integer.MIN_VALUE;
        this.lastPublishTick = Integer.MIN_VALUE;
        this.lastMeasureTick = Integer.MIN_VALUE;
        this.healthBeforeTick = -1.0f;
        this.driftReported = false;
        this.baselineDriftReported = false;
        this.baselineDriftReported = false;
        this.repeatWarned = false;
        this.lastStrikeTick = Integer.MIN_VALUE;
        this.pendingCharge = -1.0f;
        this.pendingCrit = ActorHit.CRIT_NONE;
        this.strikeDamagePending = 0.0f;
        this.strikeImpulsePending = Vec3.ZERO;
        this.struckVelocityBefore = null;
    }

    // ---------------------------------------------------------------- phases

    /**
     * Phase one. Decides whether this tick is a continuation or a jump, and says
     * whether the recording or the simulation is in charge.
     *
     * <p>Must run first and exactly once per tick.
     *
     * @return true when the simulation owns the clone's transform this tick, in
     *     which case the caller must not snap the clone to the recorded frame
     */
    public boolean prepare(ServerLevel level, ActorClonePlayer clone, int takeTick) {
        if (this.lastPrepareTick == takeTick) {
            // A repeat is a scrub, not a programming error. Finishing a take
            // calls goToReplayTick back to where the take started, so the first ticks
            // of the take are legitimately presented a second time, and the viewer
            // can scrub backwards at will. Throwing here crashed the entire server
            // thread on the ordinary act of saving a take.
            //
            // The discontinuity handling a few lines below already treats a
            // repeated tick as a seek and rewinds to a boundary the recording
            // describes, which is exactly the right answer; this guard was firing
            // ahead of that logic and contradicting it. Warn once so a genuine
            // double-drive stays visible, then fall through and let the seek path work.
            if (!this.repeatWarned) {
                this.repeatWarned = true;
                pdx.dev.fbactors.FlashbackActors.LOGGER.warn(
                    "Actor '{}' re-entered take tick {}; treating it as a scrub",
                    name(), takeTick);
            }
        }

        // Health is diffed across the tick boundary rather than at the point of
        // impact. The strike lands part way through a server tick, after this
        // class has already run its phases for that tick, so the earliest place
        // the difference is visible is here on the following one. Measuring
        // inside the damage path would need a version-specific override of
        // `hurtServer`, whose signature and even name changed across this
        // project's targets; diffing is version-independent and catches damage
        // from sources that have no callback at all.
        measureDamage(clone);
        this.lastPrepareTick = takeTick;

        // A break in the tick sequence means a seek. Rewind and replay rather
        // than carrying whatever state the body was left in, so the same fight
        // comes back the same way. A tick that repeats or runs backwards is a
        // scrub, which is the same problem wearing a different hat.
        //
        // Only rewind on a genuine jump, not on a repeat. A repeat (same tick
        // presented twice) is what finishing a take does - it seeks back to
        // where the take started - and rewinding on it resets the actor's
        // position every time, which reads as a stutter. A jump of more than
        // a few ticks is a real seek and needs the rewind.
        int tickDelta = takeTick - this.lastSimTick;
        if (tickDelta < -2 || tickDelta > 5) {
            rewindToBoundary(level, clone, takeTick);
        }
        this.lastSimTick = takeTick;

        return !this.usesRecordedBaseline(takeTick);
    }

    /**
     * Turns a health drop since the last tick into a recorded hit.
     *
     * <p>This is the only place a hit's damage enters a take, and it is measured
     * rather than predicted. Prediction would have to restate the charge curve,
     * the attack attribute, every enchantment on the weapon, the attacker's
     * potion effects, the target's armour, resistance and absorption, and the
     * damage-scaling modifier that grows with consecutive hits on the same
     * target. Vanilla has already done all of it, and re-reading the result is
     * both shorter and the only version that cannot drift out of date.
     */
    private void measureDamage(ActorClonePlayer clone) {
        float before = this.healthBeforeTick;
        float now = clone.getHealth();
        this.healthBeforeTick = now;

        // Consumed either way. A window that is never accounted for - because no
        // punch landed in it - must not carry its total into the next one and
        // silently discount a punch that did.
        float strikeDamage = this.strikeDamagePending;
        this.strikeDamagePending = 0.0f;
        Vec3 strikeImpulse = this.strikeImpulsePending;
        this.strikeImpulsePending = Vec3.ZERO;

        // Only damage the viewer dealt is written down, and this guard is what
        // makes a take's hit list stable across passes.
        //
        // A diff across a tick cannot tell one source from another, and two
        // sources reach it during ordinary playback: the viewer's punch, and the
        // swing of another actor's take being re-delivered on the tick it was
        // recorded on. The second is already described by this take - it is what
        // caused that swing to be re-run at all - so measuring it here recorded it
        // a second time, against the tick and the direction of the viewer's *last*
        // punch rather than against this hit. With no punch to attribute it to,
        // that tick was still the sentinel -1, which ActorHit clamps to zero:
        // every replayed fight deposited its damage at tick 0, the actor began the
        // take already hurt, and the tick it died on moved earlier every time the
        // take was played. The guard is the pending charge, which only a viewer's
        // strike sets, so damage the viewer dealt is still recorded exactly as
        // before and damage a replay re-delivered is left to the take.
        if (this.pendingCharge < 0.0f) {
            this.pendingCrit = ActorHit.CRIT_NONE;
            return;
        }

        // No baseline to compare against, or the punch did not land.
        if (before < 0.0f || now >= before) {
            // The charge belongs to one hit and to one hit only. Held over, it
            // would be attributed to whatever dropped the actor's health next.
            // The pre-hit velocity goes with it: without a landing there is no
            // impulse to measure, and carrying it into the next window would
            // credit the next hit with this one's shove. The crit flags go with
            // both, for the same reason: a whiffed crit is not a crit.
            this.pendingCharge = -1.0f;
            this.struckVelocityBefore = null;
            this.pendingCrit = ActorHit.CRIT_NONE;
            return;
        }

        // The punch's own share of the drop. A swing by another actor measured
        // its damage and filed it on its own tick, so counting it again here would
        // put two entries in the take for one hit and kill the actor early.
        float dealt = before - now - strikeDamage;
        if (dealt <= 0.0f) {
            // The whole of the drop belonged to that swing. There is nothing left
            // to record, and the charge goes with it, along with the pre-hit
            // velocity: the punch landed nothing, so it shoved nothing, and the
            // swing's own impulse is already filed on its own tick.
            this.pendingCharge = -1.0f;
            this.struckVelocityBefore = null;
            this.pendingCrit = ActorHit.CRIT_NONE;
            return;
        }

        float charge = this.pendingCharge;
        this.pendingCharge = -1.0f;

        // Direction is sampled from the body rather than stored on the way in,
        // because the knockback has already moved the target by the time this
        // runs and the recorded punch direction is where it was aimed, not where
        // it ended up.
        double dx = clone.getX() - this.lastStruckByX;
        double dz = clone.getZ() - this.lastStruckByZ;

        // The impulse is the change in velocity across the window, less
        // whatever swings out of other actors' takes already accounted for on
        // their own ticks. It carries both shoves vanilla applied - the base
        // one along the line to the victim and the sprint/enchantment one
        // along the attacker's facing - where the direction above only ever
        // described the first. A null baseline (a punch that landed before the
        // first publish, so nothing recorded the pre-hit velocity) measures
        // nothing rather than inventing a shove from a zero vector.
        Vec3 impulse = null;
        if (this.struckVelocityBefore != null) {
            impulse = clone.getDeltaMovement()
                .subtract(this.struckVelocityBefore).subtract(strikeImpulse);
        }
        this.struckVelocityBefore = null;

        // The crit flags vanilla raised for this exact strike, noted by the
        // mixin out of Player.crit/magicCrit. Consumed with the window, like
        // everything else in it.
        byte crit = this.pendingCrit;
        this.pendingCrit = ActorHit.CRIT_NONE;

        this.actor.take.recordHit(
            new ActorHit(this.lastMeasuredTick, dealt, dx, dz, charge, this.lastStruckBy,
                impulse, crit));

        // The shove this describes is already on the body, applied live a
        // moment ago, and the integration tick below would otherwise fire it
        // again. See suppressStrike.
        suppressStrike(this.lastMeasuredTick + 1);
        if (pdx.dev.fbactors.FlashbackActors.loadedReplayIdOrNull() != null) {
            pdx.dev.fbactors.take.TakeStore.saveThrottled(
                pdx.dev.fbactors.FlashbackActors.loadedReplayIdOrNull(), this.actor.take);
        }
        pdx.dev.fbactors.FlashbackActors.LOGGER.info(
            "Actor '{}' took {} damage at take tick {} (charge {}, crit {}, impulse {})",
            name(), dealt, this.lastMeasuredTick, charge, crit, impulse);
    }

    /**
     * Notes that the actor has been struck, from the attack callback.
     *
     * <p>Called from inside {@code Player.attack}, which is inside
     * {@code LivingEntity.hurt}, so by the time this runs vanilla has already
     * applied the damage. The latch has to happen here rather than at the next
     * tick because everything between here and then still believes the take is
     * authoritative, and would write the recorded position and health back over
     * the hit.
     *
     * @param velocityBefore the victim's velocity before the strike lands.
     *     The knockback is a change to this vector rather than an absolute,
     *     so the impulse cannot be measured without it, and the only moment
     *     it is still the pre-hit value is here, before the attack runs.
     */
    public void markStruck(int takeTick, float charge, ServerPlayer attacker,
                           Vec3 velocityBefore) {
  this.diverge(takeTick);
        this.pendingCharge = Mth.clamp(charge, 0.0f, 1.0f);
 this.lastMeasuredTick = takeTick;
        this.lastStruckByX = attacker.getX();
        this.lastStruckByZ = attacker.getZ();
this.lastStruckBy = attacker.getUUID();
        this.struckVelocityBefore = velocityBefore;
    }

    /**
     * The tick a pending hit should be recorded against.
     *
     * <p>Set by {@link #markStruck} and read one tick later by
     * {@link #measureDamage}.
     */
    private int lastMeasuredTick = -1;

    /** Charge of the strike awaiting measurement, or -1 when there is none. */
    private float pendingCharge = -1.0f;

    /**
     * Crit flags vanilla raised for the strike awaiting measurement.
     *
     * <p>Noted out of {@code Player.crit}/{@code magicCrit} by the damage
     * mixin, which runs inside vanilla's attack call - after
     * {@link #markStruck} latched the window and before
     * {@link #measureDamage} files it. Consumed and cleared with the window,
     * so a punch that lands nothing leaves no crit behind for the next one.
     */
    private byte pendingCrit = ActorHit.CRIT_NONE;

    /**
     * Notes that vanilla deemed the pending strike a crit.
     *
     * @param magic true for the enchanted {@code magicCrit} particles, false
     *     for the ordinary falling-strike {@code crit}
     */
    public void noteCrit(boolean magic) {
        this.pendingCrit |= magic ? ActorHit.CRIT_MAGIC : ActorHit.CRIT_HIT;
    }

    /**
     * Damage a swing by another actor dealt inside the window
     * {@link #measureDamage} is about to account for.
     *
     * <p>That window is a whole tick, and the diff taken across it cannot tell one
     * source from another. A punch from the viewer and a swing out of another
     * actor's take can both land inside it, and the swing has already been
     * measured and filed on its own tick by
     * {@link pdx.dev.fbactors.actor.ActorAttackResolver}. Without this the swing's
     * damage was counted a second time as part of the punch's, which is how a take
     * ends up killing its actor well before anything the recording describes.
     * Taking it off here leaves the punch's own share.
     */
    private float strikeDamagePending;

    /**
     * Notes damage a swing by another actor dealt in the current window.
     *
     * <p>Called with the drop {@code ActorAttackResolver} measured across its own
     * {@code attack} call - the same damage this class is about to see and
     * otherwise attribute to the viewer's punch.
     */
    public void noteStrikeDamage(float dealt) {
        if (dealt > 0.0f && Float.isFinite(dealt)) {
            this.strikeDamagePending += dealt;
        }
    }

    /**
     * Knockback a swing by another actor applied inside the window
     * {@link #measureDamage} is about to account for.
     *
     * <p>The same single-window problem as {@link #strikeDamagePending}, for
     * the impulse rather than the damage: the velocity diff taken across the
     * window cannot tell one shove from another, and the swing has already
     * measured and filed its own on its own tick. Without this the swing's
     * shove is counted a second time as part of the punch's, and the replayed
     * hit shoves twice as far as the recorded one did.
     */
    private Vec3 strikeImpulsePending = Vec3.ZERO;

    /**
     * Notes the impulse a swing by another actor applied in the current
     * window.
     *
     * <p>Called with the velocity change {@code ActorAttackResolver} measured
     * across its own {@code attack} call - the same impulse this class is
     * about to see and otherwise attribute to the viewer's punch.
     */
    public void noteStrikeImpulse(Vec3 impulse) {
        if (impulse != null) {
            this.strikeImpulsePending = this.strikeImpulsePending.add(impulse);
        }
    }

    /**
     * Suppresses the replay of a hit filed on this pass.
     *
     * <p>A hit is filed mid-pass - by {@link #measureDamage} in prepare, or by
     * {@code ActorAttackResolver} in actions - and the shove it describes is
     * already on the body, applied live by vanilla a moment ago. Without this
     * the integration tick below would look the hit up, find it, and apply it
     * a second time. Later passes rewind first, and the rewind clears the
     * latch, so the suppression lasts exactly as long as the pass that filed
     * it.
     *
     * @param takeTick the tick {@link #replayStrike} would fire the hit on
     */
    public void suppressStrike(int takeTick) {
        this.lastStrikeTick = takeTick;
    }

    /**
     * The victim's velocity before the pending strike landed, or null when
     * there is no pending strike or nothing captured it.
     *
     * <p>Set by {@link #markStruck} from the attack callback, which runs
     * before vanilla's attack call, and read one tick later by
     * {@link #measureDamage}. The knockback rewrites this vector in place,
     * so without the pre-hit value only the result is visible and the shove
     * itself cannot be recovered.
     */
    @Nullable
    private Vec3 struckVelocityBefore;

    /** Where the last strike came from, for the recorded direction. */
    private double lastStruckByX;
    private double lastStruckByZ;
    private UUID lastStruckBy;

    /**
     * Phase two. Puts the clone back onto the recorded frame, or leaves it alone
     * when the simulation has taken over.
     *
     * <p>Only runs for a tick where the recording is authoritative. Running it
     * against a diverged actor is the bug this guard exists for: it would restore
     * the recorded position and velocity on top of a knockback and send the actor
     * marching back into the punch that just landed.
     */
    public void applyBaseline(ActorClonePlayer clone, ActorFrame frame, int takeTick) {
        if (this.usesRecordedBaseline(takeTick)) {
            applyRecordedState(clone, frame, recordedVelocity(takeTick));
            this.lastBaselineTick = takeTick;
        }
    }

    /**
     * Re-delivers the shove of a hit the take says landed on this tick.
     *
     * <p>This is the piece that makes a fight replay instead of merely being
     * re-walked, and its absence is the whole of the difference between what the
     * viewer fought and what a second pass shows. A punch from the viewer is real
     * vanilla knockback on a real entity, and no part of the input track can
     * re-derive it: the input says what the actor tried to do, never what was
     * done to it. The take does carry the hit, though - the tick it landed on,
     * the damage it dealt and the direction it shoved in - so the reaction can be
     * simulated from it. Without this, the actor walks its recorded path straight
     * through the punch on every pass after the one it was punched on, stays
     * inside the reach of whoever was swinging at it, takes hits the recording
     * never described, and dies on a tick that belongs to no performance.
     *
     * <p>Called after {@link #applyBaseline} and before the tick, which is the
     * only ordering that works. The baseline writes the recorded position and
     * velocity, so the shove lands on a body standing exactly where the performer
     * stood when the punch landed, and the tick that follows integrates it the
     * way the performer's own tick did. Running it first would have the baseline
     * overwrite the shove; running it after the tick would delay it by one.
     *
     * <p>The shove is the hit's measured impulse where the take has one, and
     * the base shove where it does not. The impulse is the change in velocity
     * vanilla's own attack call applied when the hit was recorded - the base
     * {@code knockback(0.4, dx, dz)} along the line from the attacker to the
     * victim <em>plus</em> the extra {@code Player.attack} applies along the
     * attacker's own facing for the sprint bonus and the Knockback
     * enchantment - so adding it back reproduces the landing spot, where the
     * base shove alone replays a sprint hit in the wrong direction as well as
     * short. Hits recorded before impulses were measured carry none and fall
     * back to the base shove along the recorded direction, which is exact for
     * a standing, unenchanted punch and short for anything else.
     *
     * <p>An impulse hit fires a tick after the punch landed, because that is
     * when the shove started integrating live: the punch lands between ticks,
     * after the tick's baseline already ran, so the first step that carries
     * the shove is the next one. Firing it on the landing tick would skip a
     * baseline snap the performance ran and start the slide a tick early,
     * which reads as the actor teleporting into the punch. A legacy hit has
     * no integration tick to aim for - its take only ever knew the landing
     * tick - so it fires there, as it always did.
     *
     * <p>Firing also replays the impact, not just the slide. No vanilla attack
     * runs on a replay pass - a re-delivered swing plays its animation only -
     * so the damage number, the hurt flash and the invulnerability window the
     * live hit produced all have to be restored here. Without them the body
     * slides with full health and no feedback, which reads as the punch not
     * landing. Health comes from the take's own hit total, which is exactly
     * the number the live pass measured; it is written only on a firing tick,
     * so a live pass (whose shove is already on the body and whose strike is
     * suppressed) keeps vanilla's own health untouched. The hurt timers are
     * vanilla's own hit values and decay through the clone's own tick. The
     * sound and the client-side red flash ride on the damage packet, which the
     * caller sends when this returns true.
     *
     * @return true when a recorded hit fired on this tick and the caller owes
     *     it the damage packet and sound; false when a live punch owns this
     *     tick's feedback, or nothing fired at all
     */
    public boolean replayStrike(ActorClonePlayer clone, int takeTick) {
        if (this.lastStrikeTick == takeTick) {
            return false;
        }
        this.lastStrikeTick = takeTick;

        // See above: impulse hits live on the previous tick, legacy hits on
        // this one. A legacy hit and an impulse hit can share the moment when
        // a take mixes eras, so both fire, earliest punch first.
        ActorHit delayed = this.actor.take.hitAt(takeTick - 1);
        ActorHit impulseHit =
            delayed != null && delayed.impulse() != null ? delayed : null;
        ActorHit current = this.actor.take.hitAt(takeTick);
        ActorHit baseHit =
            current != null && current.impulse() == null ? current : null;
        if (impulseHit == null && baseHit == null) {
            return false;
        }

        // A punch the viewer threw on this very tick has already shoved the body
        // with the real thing, measured impulse included. Replaying the
        // recorded shove on top of it would double it. markStruck sets both of
        // these together and the next tick's measureDamage clears the charge,
        // so the pair is exactly "the punch that landed on this tick".
        if (this.pendingCharge >= 0.0f && this.lastMeasuredTick == takeTick) {
            return false;
        }

        // Latched before the shove, for the reason markStruck latches there: the
        // baseline must not write the recorded position over a body that has just
        // been hit, and from this tick onward the take must not own its health
        // either.
        this.diverge(takeTick);
        if (impulseHit != null) {
            // The measured change, added back onto whatever the body is
            // carrying. Applied as an add rather than through knockback() so
            // that knockback resistance - already accounted for in the
            // measurement - is not applied a second time.
            Vec3 impulse = impulseHit.impulse();
            clone.setDeltaMovement(clone.getDeltaMovement().add(impulse));
        }
        if (baseHit != null) {
            Compat.knockback(clone, VANILLA_HIT_KNOCKBACK, baseHit.dirX(), baseHit.dirZ());
        }

        // The impact. See the javadoc above for why health and the hurt timers
        // are replayed here rather than left to vanilla.
        clone.setHealth(this.actor.take.healthAt(takeTick));
        clone.hurtTime = MIN_HURT_DURATION;
        clone.hurtDuration = MIN_HURT_DURATION;

        // The crit visuals vanilla drew for the attacker's own client live.
        // A replay has no attacker client left, so without this the bonus
        // damage lands with no impact effect.
        if (impulseHit != null) {
            replayCrit(clone, impulseHit);
        }
        if (baseHit != null) {
            replayCrit(clone, baseHit);
        }
        return true;
    }

    /**
     * Replays a recorded hit's crit particles, if it had any.
     *
     * <p>Both flags are independent in vanilla - a hit can carry either - so
     * both are checked rather than treated as alternatives.
     */
    private static void replayCrit(ActorClonePlayer clone, ActorHit hit) {
        if (hit.isCritical()) {
            Compat.critAnimation(clone, false);
        }
        if (hit.isMagicCritical()) {
            Compat.critAnimation(clone, true);
        }
    }

    /**
     * The knockback vanilla applies to every melee hit that lands.
     *
     * <p>{@code LivingEntity.hurt}, which is where a punch is applied to the
     * victim, calls {@code knockback(0.4, dx, dz)} with the horizontal direction
     * from the attacker to the victim. This is that number, and
     * {@link ActorHit} records that direction.
     */
    private static final double VANILLA_HIT_KNOCKBACK = 0.4;

    /**
     * The performer's velocity at the start of this tick, derived from the take.
     *
     * <p>A frame stores a position and no velocity, but a position without its
     * own velocity is not a state: it says where the performer was, not how fast
     * they were going when they got there. Vanilla reads {@code deltaMovement}
     * on every tick &mdash; {@code moveRelative} rotates the input axes against
     * the body's facing, friction scales it, gravity accumulates into it &mdash;
     * so a clone whose recorded position has just been written over the top of
     * an unrelated leftover velocity is a body standing at {@code F_n} while
     * carrying momentum belonging to some earlier tick. The two disagree, and
     * the disagreement is injected into visible motion as a one-tick jerk at
     * every input transition, because vanilla's acceleration and friction then
     * have to spend several ticks re-converging on a velocity the baseline
     * overwrites again the moment they get close.
     *
     * <p>The velocity is the backward difference {@code F_n - F_{n-1}}: the
     * displacement the performer covered over the tick which ended at this
     * frame. Together with the recorded input axes, that is the entire input
     * state vanilla needs, so the tick that follows reproduces the performer's
     * acceleration and friction instead of starting from wherever the clone
     * happened to be. It makes the absolute position and the velocity agree:
     * the clone is at {@code F_n} <em>and</em> travelling at the rate the
     * performer was travelling when they reached {@code F_n}, which is the only
     * pairing from which one tick of vanilla physics lands on {@code F_{n+1}}.
     * The two must never disagree, because one is authoritative and the other
     * is not; a mismatch cannot accumulate, but it is re-injected as a
     * correction on every single tick, which is precisely what reads as chop.
     *
     * <p>Deriving this rather than storing it is deliberate. Takes already on
     * disk carry no velocity, and the record shapes are not ours to change;
     * {@code F_n - F_{n-1}} is the same quantity, recoverable from data every
     * take already has, and for a body that is snapped to an absolute position
     * every tick anyway it is exactly as accurate as a stored field.
     *
     * <p>Seeding velocity on this path is safe with respect to knockback, which
     * is the objection the previous version raised by leaving velocity alone on
     * the grounds that it "lets a clone keep sliding out of a knockback on the
     * very tick the baseline runs". That tick cannot exist: a knockback is what
     * causes divergence, {@link #markStruck} latches it, and
     * {@link #usesRecordedBaseline} is false from that tick onward. There is no
     * tick on which a knockback and a baseline coexist, so nothing here eats
     * one, and {@link #rewindToBoundary} still clears velocity outright across a
     * genuine discontinuity.
     *
     * <p>{@code frameAt} clamps at both ends of the frame list, so the first
     * frame of a take differences against itself and yields {@link Vec3#ZERO}.
     * That is the honest answer: there is no earlier sample, so as far as the
     * recording knows the performer was stationary, and seeding zero lets the
     * first tick accelerate away from rest exactly as the performer did.
     */
    private Vec3 recordedVelocity(int takeTick) {
        ActorFrame here = this.actor.take.frameAt(takeTick);
        ActorFrame before = this.actor.take.frameAt(takeTick - 1);
        if (here == null || before == null) {
            return Vec3.ZERO;
        }

        Vec3 velocity = new Vec3(
            here.x() - before.x(),
            here.y() - before.y(),
            here.z() - before.z());

        // Only ever trips on a corrupt or spliced frame track. Vanilla's own
        // terminal velocities stay well inside this, so it cannot clip a
        // legitimate sprint, an elytra dive or a long fall, but it does stop a
        // one-frame discontinuity from launching the actor across the level on
        // the tick before the baseline hauls it back.
        double speed = velocity.length();
        if (speed > MAX_RECORDED_SPEED) {
            return velocity.scale(MAX_RECORDED_SPEED / speed);
        }
        return velocity;
    }

    /**
     * Largest per-tick displacement a take is allowed to imply before the
     * derived velocity is treated as corrupt rather than as movement.
     */
    private static final double MAX_RECORDED_SPEED = 10.0;

    /**
     * Phase three. Drives this tick's input onto the clone and runs one real
     * entity tick.
     *
     * <p>The ordering inside here is load bearing. Input has to be in place
     * before the tick, because {@code aiStep} reads it. The previous position has
     * to be recorded before the tick as well, because that is where
     * {@code calculateEntityAnimation} derives the limb swing from; a clone with a
     * stale {@code xOld} reports standing still while it is visibly moving, which
     * is the classic sliding-feet artefact.
     *
     * <p>This runs whether or not the actor has diverged, which is the single
     * biggest behavioural change from the previous version. Only running it after
     * divergence meant a clone on the recorded baseline was teleported each tick
     * and never ticked at all: no {@code aiStep}, no collision, no gravity, no
     * friction, no limb animation, no fall distance. The clone's own documentation
     * already explained that skipping {@code doTick} was "the real reason the
     * physics looked wrong", and then only took that fix for the post-divergence
     * case.
     *
     * <p>{@code tick()} is driven here rather than by the level on purpose. The
     * clone is registered as a player but deliberately never added to the
     * level's entity list, so nothing else would call it, and adding it would
     * integrate its movement twice.
     */
    public void step(ServerLevel level, ActorClonePlayer clone, int takeTick) {
        if (!this.usesRecordedBaseline(takeTick)) {
            this.physicalTicks++;
            reportDrift(level, clone, takeTick);
        }

        ActorInput input = inputAt(takeTick);
        // Input first, then the tick: aiStep reads these fields.
        applyLook(clone, takeTick, input);
        applyMovement(clone, input);
        applyHeld(clone, input);

        clone.tick();
        clone.physical(!this.usesRecordedBaseline(takeTick));

        // Guard against the clone being dragged toward a stale interpolation
        // target after the baseline has written the position.
        //
        // LivingEntity.getInterpolation() is non-null server-side, and
        // LivingEntity.aiStep() runs getInterpolation().interpolate() at the top
        // of the tick whenever isInterpolating(), which moves the entity toward
        // that handler's stored target. Nothing primes it for an untracked clone
        // today -- the clone is never in the level's entity list, so there is no
        // ServerEntity to drive it -- but interpolate() also calls cancel() on
        // itself once it runs out of steps, and a handler that had been primed
        // by anything at all would pull the body away from the recording after
        // every baseline write and re-apply the offset the next tick. That is a
        // silent tug-of-war producing exactly the per-tick jitter this class
        // exists to remove, and it would be invisible: no packet is wrong, no
        // counter moves, nothing throws.
        //
        // Placed after clone.tick() rather than before applyBaseline because
        // interpolation is applied *inside* the tick. Cancelling beforehand would
        // leave the current tick's pull intact and only protect the next one;
        // cancelling here means the position the caller goes on to read is the
        // one the tick produced, with no tug left on it. The baseline is the
        // other candidate position and cancelling there would do nothing at all,
        // because the tick runs after it and would re-apply the pull.
        //
        // A no-op on 1.21.1 and 1.21.4, where the shim is an empty method because
        // those versions have no entity interpolation to cancel.
        Compat.cancelInterpolation(clone);

        if (this.usesRecordedBaseline(takeTick)) {
            // The tick moved the body, but on this tick the recording is still
            // the authority, so put it back. What is kept is the vanilla state
            // the tick itself advanced, because vanilla will not advance it again
            // and a zeroed invulnerability window means an actor standing in a
            // wall of another actor's swing eats hits it should have blocked.
            restoreTimers(clone, input);
            checkBaselineFidelity(clone, takeTick);
            snapToRecorded(clone, takeTick);
            driveRecordedLimbs(clone, takeTick);
        }
    }

    /**
     * Puts the body back onto the tick's recorded frame after the tick ran.
     *
     * <p>The tick above is not wasted: it advanced the limb swing, the
     * collision state, the fall distance and every timer vanilla owns, and all
     * of that is kept. Only the position is put back, because on an
     * authoritative tick the recording owns it. This is what makes jumps land
     * exactly where the performer jumped: a re-simulated arc is at the mercy
     * of every input, effect and ground flag lining up, while the frame states
     * the answer outright. Past divergence there is no frame to return to, so
     * this never runs there and the integrated position stands.
     */

    /**
     * Warns once when a baseline-authoritative tick lands away from the take.
     *
     * <p>On this path the tick starts from the recorded frame, velocity and
     * inputs, so one tick of vanilla physics should land on the next frame to
     * within millimetres. A miss here is the simulation disagreeing with the
     * recording - a jump that did not fire, an effect that arrived a tick
     * late, a ground flag that lied - and it is silent otherwise: the next
     * tick re-snaps to the recording, so the failure reads as a one-tick
     * judder (or a floaty arc, if it happens on every takeoff) with nothing
     * in the log pointing at it. This names the tick, the distance and whether
     * jump was held, which is what a jump-arc bug report needs.
     */
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

    /**
     * Writes the tick's recorded frame back over the integrated position.
     *
     * <p>Null-safe on a missing frame: past the last recorded tick there is
     * nothing to return to, and the integrated position is the honest answer.
     */
    private void snapToRecorded(ActorClonePlayer clone, int takeTick) {
        pdx.dev.fbactors.take.ActorFrame frame = this.actor.take.frameAt(takeTick);
        if (frame == null) {
            return;
        }
        // Same vehicle-first ordering as applyRecordedState: the post-tick
        // re-snap must not leave the clone and its vehicle disagreeing.
        Entity ridden = clone.getVehicle();
        if (ridden != null) {
            Compat.snapTo(ridden, frame.x(), frame.y(), frame.z(),
                ridden.getYRot(), ridden.getXRot());
        }
        Compat.snapTo(clone, frame.x(), frame.y(), frame.z(), frame.yRot(), frame.xRot());
    }

    /**
     * Overwrites the tick's live limb phase with the take's recorded one.
     *
     * <p>The tick above integrated the limb swing from whatever displacement
     * it happened to simulate, and that displacement depends on the tick
     * history: a viewport that scrubbed to this tick and an export that
     * walked straight through it simulated different steps and accumulated
     * different phases, while standing on the same snapped frame. That is the
     * whole of the legs-together versus legs-split-wide disagreement at an
     * identical position. The take's own prefix sums describe the
     * performer's actual stride, so writing them here - after the tick ran
     * and after {@link #snapToRecorded} put the body back - makes the phase a
     * pure function of the take tick: the same tick always renders the same
     * limbs on both paths, by construction rather than by convergence.
     *
     * <p>Baseline only, and that guard is the entire safety case. Past
     * divergence the simulation owns the body and its live integration is
     * left untouched, so a knockback slide keeps whatever phase the slide
     * earned. Corpses are skipped for the same reason a diverged body is: a
     * dead clone's tick decays its swing toward rest from live movement, and
     * freezing it at the last recorded phase would hold its legs mid-stride
     * through the fall-over. Both early-outs leave the integrated values in
     * place rather than zeroing them, because the tick just ran and its
     * values are the honest live answer where the recording is not
     * authoritative.
     *
     * <p>Both the legacy stride ({@code Entity.walkDist}/{@code walkDistO} on the
     * 1.21.x targets, read by the cape layer, the hand bob and the sprint gate)
     * and the walk state the player renderer actually poses the limbs from
     * ({@code walkAnimation.position} and {@code speed} on every target, read by
     * {@code LivingEntityRenderer} into {@code HumanoidModel.setupAnim}) are
     * written, because driving one without the other would fix the legs while
     * leaving the cape a tick history behind. All field access goes through
     * {@link Compat}, which is the only layer that may name version-varying
     * entity fields - and where a target removed the field outright (26.x
     * dropped the stride accumulators), that half of the shim is a no-op.
     */
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

    /**
     * Phase four. Fires the discrete things that happened on this tick.
     *
     * <p>Separate from {@link #step} because a swing has to be delivered after
     * the tick that carried the body into range, not before. Delivering it
     * inside the tick runs the attack against where the actor used to be, which
     * is a miss for every swing that walked into range on that same tick.
     */
    public void runActions(ActorClonePlayer clone, int takeTick) {
        if (this.lastActionTick == takeTick) {
            // Same reasoning as prepare: a repeated take tick is a scrub, not a double
            // drive, and re-running the action is correct for the new pass. Hits are
            // authored events replayed as a hurt animation rather than accumulated
            // damage, so presenting the tick again cannot double the damage.
            if (!this.repeatWarned) {
                this.repeatWarned = true;
                pdx.dev.fbactors.FlashbackActors.LOGGER.warn(
                    "Actor '{}' re-ran actions for take tick {}; treating it as a scrub",
                    name(), takeTick);
            }
        }
        this.lastActionTick = takeTick;

        ActorEvent attack = this.actor.take.attackAt(takeTick);
        if (attack == null) {
            return;
        }

        ActorInput input = inputAt(takeTick);
        // Restore the charge the performer had at the moment of the swing. The
        // baseline does not do this, because the swing can arrive several ticks
        // after the charge was sampled and a charge that has since decayed would
        // land a fraction of what the performer got.
        restoreAttackCharge(clone, input == null ? 1.0f : input.attackStrength());

        Entity struck = ActorAttackResolver.resolve(clone, attack.target());
        if (struck != null) {
            ActorAttackResolver.strike(clone, struck, takeTick);
        } else {
            // The swing still plays for a whiff. An actor whose recorded swing
            // missed and which now swings at nothing reads as having lost its
            // target, and a take cannot say whether the recorded miss was against
            // a wall or into empty air. Compat because 26.3 replaced
            // LivingEntity.swing with swingAndResetAttackStrength.
            Compat.swing(clone, InteractionHand.MAIN_HAND);
        }
    }

    /**
     * Phase five. Publishes the tick's result for the renderer.
     */
    public void publish(ActorClonePlayer clone, int takeTick) {
        if (this.lastPublishTick == takeTick) {
            // Same reasoning as prepare: a repeated tick is a scrub. Publishing again just
        // re-captures health for the tick being presented, which is what we want.
        if (!this.repeatWarned) {
            this.repeatWarned = true;
            pdx.dev.fbactors.FlashbackActors.LOGGER.warn(
                "Actor '{}' re-published take tick {}; treating it as a scrub",
                name(), takeTick);
        }
        }
        this.lastPublishTick = takeTick;

        // Health is captured before the tick runs so a loss across the tick can
        // be attributed. See measureDamage for why that is done here rather than
        // by hooking the damage entry point.
        this.healthBeforeTick = clone.getHealth();
        this.lastMeasureTick = takeTick;
    }

    // ------------------------------------------------------------- the rewind

    /**
     * Restores the body to the last tick before divergence after a seek.
     *
     * <p>The previous version's javadoc claimed this "rewinds to the last tick
     * before divergence and replays forward". It did neither: it teleported the
     * clone onto the <em>recorded</em> frame at the divergence tick and returned.
     * So after a scrub a diverged actor was put back on its take's path, which
     * destroyed the divergence and, with it, the hit that caused it. Every seek
     * quietly undid the fight.
     *
     * <p>Replaying the intervening ticks properly is only sound when the take
     * says what happened, which it does not: a real hit is not in the input
     * track. What is sound is to restore the body to the last state we can
     * vouch for and let the remaining ticks re-derive forward from the recorded
     * baseline, which is what this does. {@link ActorManager} is responsible for
     * making sure the boundary it lands on is one the recording describes.
     */
    private void rewindToBoundary(ServerLevel level, ActorClonePlayer clone, int takeTick) {
        // Keep the latch. Forgetting that an actor was struck is exactly the bug
        // this is fixing, and a rewind is a change of viewpoint, not a change of
        // what happened.
        this.lastPrepareTick = Integer.MIN_VALUE;
        this.lastBaselineTick = Integer.MIN_VALUE;
        this.lastActionTick = Integer.MIN_VALUE;
        this.lastPublishTick = Integer.MIN_VALUE;
        this.lastMeasureTick = Integer.MIN_VALUE;
        this.lastStrikeTick = Integer.MIN_VALUE;

        int from = this.divergedAtTick < 0
            ? takeTick
            : Math.min(this.divergedAtTick, takeTick);
        ActorFrame frame = this.actor.take.frameAt(from);
        if (frame == null) {
            return;
        }

        Compat.snapTo(clone, frame.x(), frame.y(), frame.z(), frame.yRot(), frame.xRot());

        // The same velocity the recorded position implies, not zero. This used to
        // clear it outright, on the reasoning that a body teleported somewhere it
        // has no business being should not arrive carrying momentum. That
        // reasoning describes the discontinuity, not the rewind target: the clone
        // is being put back on a frame the take actually describes, and the
        // velocity that belongs at that frame is the recorded one. Zeroing here
        // meant the first tick after every scrub and every seek began from rest,
        // so an actor that scrubbed mid-sprint visibly accelerated from a
        // standstill on the way back up to speed -- the same start-of-take ramp
        // that seeding at the baseline was meant to remove, reproduced on every
        // scrub instead of only the first one.
        clone.setDeltaMovement(recordedVelocity(from));
        clone.setOnGround(frame.onGround());
        clone.trackLastPosition();
        this.physicalTicks = 0;
    }

    // ------------------------------------------------------------- the baseline

    /**
     * Puts a clone onto the recorded frame, with a velocity that agrees with it.
     *
     * <p>Rotation here is <em>absolute</em>: {@code frame.yRot()} is the yaw the
     * performer was facing at the end of this tick, and it is written straight
     * in. That makes this the single authority for look on the recorded path,
     * which is the whole of why {@link #applyLook} no longer touches yaw or
     * pitch while the recording is authoritative. It used to add the recorded
     * delta on top of this absolute, so a steady 10 deg/tick turn rotated the
     * head 20 deg/tick and the tick the turn stopped rotated it by the residual
     * delta and then snapped. Both numbers were already in hand and the head
     * was being given each of them twice.
     *
     * <p>The rejected alternative is the delta-accumulating scheme: seed the
     * clone with the <em>previous</em> tick's yaw here and let {@code applyLook}
     * add the delta, so the two compose to the right answer. It was rejected
     * because it makes the head's position depend on the take being contiguous
     * and on every prior tick having been simulated correctly, whereas the frame
     * states the answer outright. A scrub, a dropped frame or one repeated tick
     * in the input track leaves the head a delta away from where the recording
     * says it is, and because that is a rotation and not a position it stays
     * visible for the rest of the replay as the actor looking slightly the wrong
     * way. The absolute form cannot compound: replaying the same tick twice
     * lands on the same angle, which is the property the delta form of look is
     * meant to have.
     *
     * <p>Position and velocity are written together because they are one state.
     * Writing the position alone leaves a body standing at {@code F_n} carrying
     * whatever momentum the previous tick's integration happened to leave, and
     * the recorded axes then integrate against the wrong starting velocity. See
     * {@link #recordedVelocity} for why the two must never disagree.
     */
    private static void applyRecordedState(ActorClonePlayer clone, ActorFrame frame,
                                           Vec3 velocity) {
        // A riding clone's position belongs to its vehicle: vanilla re-seats
        // the passenger from the vehicle every tick, so writing the frame
        // onto the clone alone fights the vehicle and the two rubber-band.
        // Snap the vehicle to the recorded position first; the clone snap
        // below then agrees with where the vehicle carries it.
        Entity vehicle = clone.getVehicle();
        if (vehicle != null) {
            Compat.snapTo(vehicle, frame.x(), frame.y(), frame.z(),
                vehicle.getYRot(), vehicle.getXRot());
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

        // Body yaw is written from the same absolute as the head, so the two
        // agree at the moment the baseline runs. This is not a pin: vanilla does
        // not leave it alone. LivingEntity.tick() derives a body-yaw *target*
        // from the direction the body actually travelled this tick (atan2 of
        // getZ() - zo against getX() - xo) and eases yBodyRot toward it by 0.3
        // per tick, clamped to getMaxHeadRotationRelativeToBody() of the entity
        // yaw. So this sets where the torso starts each tick, and vanilla eases
        // it toward where the body is going, which on a turn is the direction
        // the performer turned toward.
        //
        // The rejected alternative was advancing body yaw by the look delta in
        // applyLook so head and body moved by the same step. That fights
        // vanilla: the body-yaw ease runs inside the tick and immediately pulls
        // the torso back toward the travel direction, so a delta written there
        // is discarded the same tick and only the head visibly moves. It is also
        // wrong on its own terms, because a clone walking sideways or
        // strafing has a torso genuinely out of line with its head, and forcing
        // the two equal destroyed that.
        clone.setYBodyRot(frame.yRot());

        // Absolute, like the yaw. Clamped because a recorded pitch is a real
        // player's pitch and vanilla will not render outside [-90, 90]; an
        // unclamped write leaves the head inside out.
        clone.setXRot(Mth.clamp(frame.xRot(), -90.0f, 90.0f));

        clone.applyPoseState(frame);

        // The velocity belonging to the position just written. Not zeroed, and
        // not left over: see recordedVelocity.
        clone.setDeltaMovement(velocity);

        // Fall distance is a boolean in disguise: vanilla's fall check only asks
        // whether the body is descending and not on the ground. Reconstructing it
        // from the recorded flags is what makes an actor dropped off a ledge take
        // fall damage exactly when the performer did, and conversely stops an
        // actor that was not falling from being shoved by a fall check left over
        // from the tick that ran before the rewind.
        Compat.setFallDistance(clone, ActorFallDistance.infer(frame));
    }

    /** When each clone last had a snap logged for, so a bad stretch cannot spam. */
    private static final java.util.Map<UUID, Long> LAST_SNAP_LOG = new java.util.HashMap<>();

    /**
     * Logs a baseline snap that moved the body suspiciously far in one tick.
     *
     * <p>The baseline restores the recorded position every fresh tick, so a
     * small snap is the ordinary cost of a knockback or a scrub: the body is
     * somewhere the recording never put it and gets put back. A snap past two
     * metres in a single tick is never ordinary movement -- nothing the
     * performer can do covers that in a fiftieth of a second -- so it means a
     * pop the viewer will see as a teleport. Throttled to one line per clone
     * per five seconds.
     */
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

    /**
     * Puts vanilla's hidden damage state back after a tick the simulation ran on
     * the recorded baseline.
     *
     * <p>These are the fields vanilla will not touch again on its own, so a tick
     * that runs and then gets rewound to the recording leaves them describing the
     * simulation rather than the performance. Left alone, the actor's hurt
     * animation stops halfway, and its i-frame window expires early enough that
     * the next punch lands a fraction early.
     */
    private static void restoreTimers(ActorClonePlayer clone, @Nullable ActorInput input) {
        if (input == null) {
            return;
        }
        if (clone.hurtTime > 0 && clone.hurtDuration < MIN_HURT_DURATION) {
            // Vanilla's own floor. An actor whose hurt timer was running while
            // the baseline rewound its position would otherwise have its red
            // flash cut off mid-animation.
            clone.hurtDuration = MIN_HURT_DURATION;
        }
    }

    /**
     * How long the hurt animation runs when nothing better is known.
     *
     * <p>Vanilla's value, which {@code hurtDuration} is initialised to and which
     * {@code hurt} sets on every hit.
     */
    private static final int MIN_HURT_DURATION = 10;

    /**
     * Restores the attacker's charge so a replayed swing lands at the strength
     * the performer swung with.
     *
     * <p>{@code attackStrengthTicker} counts <em>down</em> from
     * {@code getCurrentItemAttackStrengthDelay()} to zero, and the damage scales
     * by {@code 0.2 + charge^2 * 0.8} while it does. Inverting that is the whole
     * trick: the recorded charge is turned back into a number of ticks left to
     * run, and the swing reads it the same way it would on the client.
     *
     * <p>Deliberately uses the actor's own weapon delay rather than a stored
     * tick count. The recorded charge is a fraction, and that fraction means a
     * different number of ticks for a sword and for an axe; converting it against
     * the wrong delay throws the swing's strength out by the ratio between them.
     */
    private static void restoreAttackCharge(ActorClonePlayer clone, float recordedStrength) {
        float strength = Mth.clamp(recordedStrength, 0.0f, 1.0f);
        int delay = (int) Math.max(1.0f, clone.getCurrentItemAttackStrengthDelay());

        int ticks = strength >= FULLY_CHARGED
            ? FULLY_CHARGED_TICKS
            : Math.max(0, Math.round(delay * strength - 0.5f));
        ((DamageStateAccess) clone).fbactors$setAttackStrengthTicker(ticks);

        // A swap penalty would otherwise apply to a swing that was recorded
        // before the swap. Absent entirely before 1.21.11, so this is a no-op on
        // the older targets rather than a version test at every call site.
        if (clone instanceof SwapPenaltyAccess) {
            ((SwapPenaltyAccess) clone).fbactors$setItemSwapTicker(SwapPenaltyAccess.NO_PENALTY);
        }
    }

    /**
     * Charge at or above which the swing is treated as fully wound up.
     *
     * <p>Not exactly 1: the last fraction of a percent is below what
     * {@code getAttackStrengthScale} can resolve into a distinct tick count, and
     * mapping it through the delay would round down to a slightly weak swing for
     * a swing that was, for every practical purpose, full.
     */
    private static final float FULLY_CHARGED = 0.999f;

    /**
     * Ticker value meaning "fully charged".
     *
     * <p>A sentinel rather than the real delay, for the same reason as
     * {@link SwapPenaltyAccess#NO_PENALTY}: any value above the longest possible
     * recharge is indistinguishable from charged, and hard-coding one keeps this
     * independent of which item the actor happens to be holding.
     */
    private static final int FULLY_CHARGED_TICKS = 1000;

    // ------------------------------------------------------------- the input

    /**
     * The recorded input for a tick, or null when the take has none.
     *
     * <p>Deliberately not interpolated. A missing tick means the performer held
     * nothing, and inventing a half-pressed key would make an actor drift.
     */
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

    /**
     * Applies the recorded look change, but only once the baseline has stopped.
     *
     * <p>{@code TakeRecorder} records both an absolute rotation on every frame
     * and a per-tick delta on every input, and they are the same movement
     * described twice. {@link #applyRecordedState} writes the absolute, so while
     * the recording is authoritative this is a no-op: adding the delta as well
     * gave the head {@code yaw_n + (yaw_n - yaw_{n-1})} instead of
     * {@code yaw_n}, which is a doubled turn rate on every take with any head
     * movement and a snap on the tick the turn stopped. Applying both is the
     * mistake, and the fix is to apply one.
     *
     * <p>The delta is still needed once the take stops being authoritative. Past
     * divergence there is no baseline and therefore no absolute rotation, so the
     * recorded delta is the only remaining description of where the performer
     * was looking. An actor that has been knocked off its path keeps its head on
     * the performance rather than freezing on whatever it was facing when the
     * hit landed, which is what happens if this is simply deleted.
     *
     * <p>Clamped to a single tick's worth, as before, because a recorded delta
     * is the difference between two sampled ticks and the clamp only trips on
     * corrupt data. Body yaw is deliberately not advanced here: see the note in
     * {@link #applyRecordedState} on why writing it from a look delta is both
     * undone by vanilla and wrong for a body that is not facing where it walks.
     */
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

    /**
     * Puts the recorded input into the fields vanilla's movement code reads.
     *
     * <p>{@code xxa}/{@code zza} are the entity's local movement axes and are
     * what {@code moveRelative} rotates by yaw, so they are set from the recorded
     * axes rather than from a world-space direction.
     *
     * <p>The recorded values are written straight through, with no rescaling.
     * They were read off the client after it had already applied the {@code 0.98}
     * input scale, the sneak speed and the item-use slowdown, and applied its
     * diagonal correction. Doing any of that again here would apply it twice and
     * make the actor slower than the performer on a slow surface and faster on a
     * fast one.
     */
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

        // Sprint is not simply the sprint key. Vanilla only allows it when the
        // performer is moving forward and is not impeded, and honouring the
        // recorded boolean blindly would let an actor sprint sideways or sprint
        // while swimming.
        boolean wantsSprint = input.sprint() && input.forward() && !input.sneak();
        clone.setSprinting(wantsSprint && canSprint(clone));

        applyFlying(clone, input.flying());
    }

    /**
     * Puts the recorded creative-flight state onto the clone's abilities.
     *
     * <p>Creative flight is the one movement mode that does not live in the
     * input axes: {@code Player.travel} branches on {@code abilities.flying} to
     * choose flight physics over ground physics, and no combination of
     * {@code xxa}/{@code zza}/jump can imply it. It is therefore written here,
     * from the recorded flag, and this is its only writer. The clone's
     * {@code doTick} used to clear it every tick, which deleted recorded flight
     * outright; that clear is gone and this is what replaces it.
     *
     * <p>{@code mayfly} is written to match rather than left at the spawn value.
     * {@code GameType.CREATIVE.updatePlayerAbilities} turns it on, so a clone
     * spawned into creative starts flagged as able to fly even though the
     * performer may only ever have walked; a recording of someone on the ground
     * should not leave the clone a flier, and vanilla's own glide and climb
     * checks read the flag directly. Writing the pair together keeps them
     * consistent, and {@code onUpdateAbilities} is called only on an actual
     * change so the clone's black-hole connection is not handed a packet every
     * tick for nothing.
     *
     * <p>A null input means the take has no record at all for this tick, and the
     * honest reading of that is "not flying": leaving the previous value would
     * let one recorded flying tick latch flight on for the rest of the take.
     */
    private static void applyFlying(ActorClonePlayer clone, boolean flying) {
        if (clone.getAbilities().flying == flying && clone.getAbilities().mayfly == flying) {
            return;
        }
        clone.getAbilities().flying = flying;
        clone.getAbilities().mayfly = flying;
        clone.onUpdateAbilities();
    }

    /**
     * The held, non-positional inputs, which have no effect until something
     * consumes them.
     *
     * <p>Selected slot is restored first so a later use starts with the item
     * the performer was actually holding. Item use (spyglass, bow draw,
     * shield block, eating) is then mirrored: a held use starts the clone's
     * own use with the recorded hand, a released use stops it. Vanilla owns
     * the countdown once started, so this only drives the edges and lets the
     * tick integrate the rest - the same way movement axes are written for
     * vanilla to consume rather than simulated here.
     */
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
                clone.stopUsingItem();
            }
        } catch (Exception | LinkageError ignored) {
            // A hand the target does not know is a render miss, not a tick
            // failure: the body still walks the take without the spyglass pose.
        }
    }

    /**
     * The subset of vanilla's sprint gate that matters for a clone. Food and
     * effects are not modelled here, so this covers the movement conditions.
     */
    private static boolean canSprint(ActorClonePlayer clone) {
        if (clone.isPassenger() || clone.isSwimming() || clone.isInWater()) {
            return false;
        }
        if (clone.isShiftKeyDown() || !clone.onGround()) {
            return false;
        }
        return !clone.isInLava();
    }

    /**
     * Largest look change applied in a single tick. A recorded delta is a
     * difference between samples and is already bounded by a real mouse or
     * controller, so this only trips on corrupt data.
     */
    private static final float MAX_TURN_PER_TICK = 180.0f;

    // ---------------------------------------------------------------- drift

    /**
     * Compares the simulated body against the recorded frame and says so when it
     * is far away.
     *
     * <p>Only ever warns. A large value means the two disagree, which is a fact
     * worth knowing, and it is emphatically not an instruction to move the actor:
     * correcting it here would undo divergence by smearing it and leave the
     * original fault in place but invisible.
     */
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