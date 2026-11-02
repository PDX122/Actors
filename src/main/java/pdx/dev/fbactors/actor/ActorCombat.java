package pdx.dev.fbactors.actor;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import pdx.dev.fbactors.FlashbackActors;
import pdx.dev.fbactors.compat.Compat;
import pdx.dev.fbactors.take.ActorHit;
import pdx.dev.fbactors.take.TakeStore;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ActorCombat {
    public record DamageStart(int tick, float health, Vec3 velocity, UUID source, float charge, double dx, double dz) {}
    private record State(Vec3 position, Vec3 velocity, float health, float absorption, boolean grounded) {}
    private final Actor actor;
    private final Map<Integer, State> history = new HashMap<>();
    private final Set<Integer> liveIntegrationTicks = new HashSet<>();
    private final Set<Integer> authorizedContacts = new HashSet<>();
    private DamageStart pending;
    private float damage;
    private byte crit;
    private Vec3 previousVelocity = Vec3.ZERO;
    private int lastReplayTick = Integer.MIN_VALUE;
    private int deathTick = -1;
    private boolean replaying;
    public ActorCombat(Actor actor) { this.actor=actor; }

    private int tick() {
        return (int)((FlashbackActors.replayTick()-actor.take.offsetTicks)*actor.take.getSpeedMultiplier());
    }
    public DamageStart beginDamage(ActorClonePlayer clone, DamageSource source) {
        Entity attacker=source.getEntity();
        UUID sourceId=attacker==null?null:attacker.getUUID();
        if(sourceId!=null && FlashbackActors.RECORDER.isPerformer(sourceId) && FlashbackActors.RECORDER.current()!=null)
            sourceId=FlashbackActors.RECORDER.current().id;
        return new DamageStart(tick(),clone.getHealth(),clone.getDeltaMovement(),sourceId,
            attacker instanceof Player player?player.getAttackStrengthScale(0.5f):1.0f,
            attacker==null?0:clone.getX()-attacker.getX(),attacker==null?1:clone.getZ()-attacker.getZ());
    }
    public void finishDamage(ActorClonePlayer clone, DamageStart start, boolean accepted) {
        if (replaying || !accepted || start==null) return;
        float dealt=Math.max(0,start.health()-clone.getHealth());
        if (dealt>0) {
            if(pending==null)pending=start;
            else if(damage==0)pending=new DamageStart(start.tick(),start.health(),pending.velocity(),start.source(),start.charge(),start.dx(),start.dz());
            damage+=dealt;
            actor.sim().diverge(start.tick());
            liveIntegrationTicks.add(start.tick()+1);
        }
    }
    public void impulse(ActorClonePlayer clone) {
        if (replaying) return;
        if (pending==null) pending=new DamageStart(tick(),clone.getHealth(),previousVelocity,null,1,0,1);
        actor.sim().diverge(tick());
        liveIntegrationTicks.add(tick()+1);
    }
    public void noteCrit(boolean magic) { crit |= magic?ActorHit.CRIT_MAGIC:ActorHit.CRIT_HIT; }
    public void flush(ActorClonePlayer clone) {
        if (pending==null) return;
        Vec3 impulse=clone.getDeltaMovement().subtract(pending.velocity());
        if (FlashbackActors.RECORDER.isRecording() && (damage>0 || impulse.lengthSqr()>1.0e-12)) {
            actor.take.recordHit(new ActorHit(pending.tick(),damage,pending.dx(),pending.dz(),pending.charge(),
                pending.source(),impulse,crit));
            if (FlashbackActors.loadedReplayIdOrNull()!=null)
                TakeStore.saveThrottled(FlashbackActors.loadedReplayIdOrNull(),actor.take);
        }
        Compat.syncMotion(clone);
        if(clone.getHealth()<=0)declareDeath(pending.tick());
        pending=null; damage=0; crit=ActorHit.CRIT_NONE;
    }
    public void suppress(int integrationTick) { liveIntegrationTicks.add(integrationTick); }
    public boolean replay(ActorClonePlayer clone,int tick) {
        if (tick==lastReplayTick) return false;
        lastReplayTick=tick;
        if (liveIntegrationTicks.contains(tick)) return false;
        var hits=CombatPlayback.at(actor.take.hits,tick);
        if(hits.isEmpty())return false;
        boolean hurt=false;
        replaying=true;
        try {
            for(ActorHit hit:hits) {
                if (!contactStillValid(clone,hit)) continue;
                actor.sim().diverge(tick);
                if(hit.impulse()!=null)clone.setDeltaMovement(clone.getDeltaMovement().add(hit.impulse()));
                else Compat.knockback(clone,0.4,hit.dirX(),hit.dirZ());
                if(hit.damage()>0) {
                    clone.setHealth(Math.max(0,clone.getHealth()-hit.damage()));
                    if(clone.getHealth()<=0)declareDeath(tick);
                    clone.hurtTime=10; clone.hurtDuration=10; hurt=true;
                }
                if(hit.isCritical())Compat.critAnimation(clone,false);
                if(hit.isMagicCritical())Compat.critAnimation(clone,true);
            }
        } finally { replaying=false; }
        if(!hurt)Compat.syncMotion(clone);
        return hurt;
    }
    private boolean contactStillValid(ActorClonePlayer victim,ActorHit hit) {
        if(hit.target()==null)return true;
        Actor source=FlashbackActors.ACTORS.byUuid(hit.target());
        if(source==null || source==actor)return true;
        int worldTick=(int)(hit.tick()/actor.take.getSpeedMultiplier())+actor.take.offsetTicks;
        int sourceTick=(int)((worldTick-source.take.offsetTicks)*source.take.getSpeedMultiplier());
        boolean melee=source.take.eventsAt(sourceTick).stream().anyMatch(event->
            event.type()==pdx.dev.fbactors.take.ActorEvent.Type.ATTACK && victim.getUUID().equals(event.target()));
        if(!melee)return true;
        return authorizedContacts.contains(hit.tick());
    }
    public void authorize(int hitTick) { authorizedContacts.add(hitTick); }
    public int deathTick() { return deathTick; }
    public void declareDeath(int tick) { if(deathTick<0)deathTick=tick; }
    public void reject(int hitTick) { authorizedContacts.remove(hitTick); }
    public void seek() { lastReplayTick=Integer.MIN_VALUE; liveIntegrationTicks.clear(); }
    public void translate(double x,double y,double z) {
        history.replaceAll((tick,state)->new State(state.position().add(x,y,z),state.velocity(),
            state.health(),state.absorption(),state.grounded()));
    }
    public void capture(ActorClonePlayer clone,int tick) {
        previousVelocity=clone.getDeltaMovement();
        if(actor.sim().physical()) {
            if(history.size()>20000)history.clear();
            history.put(tick,new State(clone.position(),clone.getDeltaMovement(),clone.getHealth(),clone.getAbsorptionAmount(),clone.onGround()));
        }
    }
    public boolean restoreBefore(ActorClonePlayer clone,int tick) {
        State state=history.get(tick-1);
        if(state==null)return false;
        clone.setPos(state.position().x,state.position().y,state.position().z);
        clone.setDeltaMovement(state.velocity()); clone.setHealth(state.health());
        if(state.health()>0)deathTick=-1;
        clone.setAbsorptionAmount(state.absorption()); clone.setOnGround(state.grounded());
        lastReplayTick=Integer.MIN_VALUE;
        liveIntegrationTicks.clear();
        return true;
    }
    public void reset() {
        pending=null; damage=0; crit=0; previousVelocity=Vec3.ZERO;
        deathTick=-1;
        history.clear(); liveIntegrationTicks.clear(); authorizedContacts.clear(); lastReplayTick=Integer.MIN_VALUE;
    }
}
