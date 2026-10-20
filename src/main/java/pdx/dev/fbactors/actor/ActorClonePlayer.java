package pdx.dev.fbactors.actor;

import com.mojang.authlib.GameProfile;
import com.moulberry.flashback.playback.FlashbackFakePlayer;
import pdx.dev.fbactors.take.ActorFrame;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Explosion;
import pdx.dev.fbactors.compat.Compat;


public class ActorClonePlayer extends FlashbackFakePlayer {

    private static final int FLAG_FALL_FLYING = 7;


    private boolean physical;


    private boolean recorded = true;

    public ActorClonePlayer(MinecraftServer server, ServerLevel level,
                            GameProfile profile, ClientInformation clientInformation) {
        super(server, level, profile, clientInformation);
    }


    public void recorded(boolean recorded) {
        this.recorded = recorded;
    }


    @Override
    public void tick() {
        setOldPosAndRot();
        super.tick();
        doTick();


        Compat.tickSwing(this);
    }


    @Override
    public void doTick() {


        this.getFoodData().setFoodLevel(20);
        this.getFoodData().setSaturation(20.0f);


        super.doTick();
    }


    @Override
    public void awardStat(net.minecraft.stats.Stat<?> stat, int amount) {
        if (this.recorded) {
            return;
        }
        super.awardStat(stat, amount);
    }


    @Override
    public boolean isSpectator() {
        return false;
    }

    @Override
    public boolean isCreative() {
        return false;
    }


    @Override
    public boolean isPickable() {
        return true;
    }

    @Override
    public boolean isAttackable() {
        return true;
    }


    @Override
    public void crit(Entity target) {
        this.critStash |= pdx.dev.fbactors.take.ActorHit.CRIT_HIT;
        Compat.critAnimation(target, false);
    }

    @Override
    public void magicCrit(Entity target) {
        this.critStash |= pdx.dev.fbactors.take.ActorHit.CRIT_MAGIC;
        Compat.critAnimation(target, true);
    }


    @Override
    public boolean isPushable() {
        return !this.isRemoved();
    }


    @Override
    protected void markHurt() {

    }


    @Override
    protected void tickDeath() {
        this.deathTime++;
    }


    @Override
    public boolean ignoreExplosion(Explosion explosion) {
        return false;
    }


    private byte critStash = pdx.dev.fbactors.take.ActorHit.CRIT_NONE;


    byte takeCritStash() {
        byte out = this.critStash;
        this.critStash = pdx.dev.fbactors.take.ActorHit.CRIT_NONE;
        return out;
    }


    private boolean immunityLifted;


    private boolean savedInvulnerable;


    public void beginPlayerDamage() {
        if (this.immunityLifted) {
            return;
        }
        this.savedInvulnerable = this.getAbilities().invulnerable;
        this.immunityLifted = true;
        this.getAbilities().invulnerable = false;
    }


    public void endPlayerDamage() {
        if (!this.immunityLifted) {
            return;
        }
        this.immunityLifted = false;
        this.getAbilities().invulnerable = this.savedInvulnerable;
    }


    public void applyPoseState(ActorFrame frame) {
        Pose[] poses = Pose.values();
        byte ordinal = frame.pose();
        setPose(ordinal >= 0 && ordinal < poses.length ? poses[ordinal] : Pose.STANDING);

        setShiftKeyDown(frame.sneaking());
        setSprinting(frame.sprinting());
        setSharedFlag(FLAG_FALL_FLYING, frame.fallFlying());
        setOnGround(frame.onGround());
    }


    public void physical(boolean physical) {
        this.physical = physical;
    }

    public boolean physical() {
        return this.physical;
    }


    public void trackLastPosition() {
        this.xo = this.getX();
        this.yo = this.getY();
        this.zo = this.getZ();
    }


    public void syncEffectVisibility() {
        this.updateInvisibilityStatus();
    }
}

