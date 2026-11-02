package pdx.dev.fbactors.camera;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.playback.ReplayServer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import pdx.dev.fbactors.FlashbackActors;
import java.util.UUID;

public final class BoatSubjects {
    private record Motion(Object replay, UUID body, int tick, double x, double y, double z,
                          double yaw, double vx, double vz, double turn) {}
    private static final java.util.concurrent.ConcurrentMap<UUID,Motion> motion = new java.util.concurrent.ConcurrentHashMap<>();
    private static volatile UUID watched, watchedRival;
    public record Sample(double x, double y, double z, double yaw, double vx, double vz,
                         double turn, Level level, Entity entity) {}
    private BoatSubjects() {}
    public static void watch(UUID target, UUID rival) {
        if (!java.util.Objects.equals(watched,target) || !java.util.Objects.equals(watchedRival,rival)) motion.clear();
        watched=target; watchedRival=rival;
    }

    public static void capture(ReplayServer replay) {
        capture(replay,watched); capture(replay,watchedRival);
    }
    private static void capture(ReplayServer replay, UUID id) {
        if (id==null) return;
        for (var level:replay.getAllLevels()) {
            Entity driver=level.getPlayerByUUID(id);
            if (driver==null) continue;
            Entity body=driver.getVehicle()==null?driver:driver.getVehicle();
            int tick=replay.getReplayTick(); var before=motion.get(id);
            var v=body.getDeltaMovement(); double vx=v.x,vz=v.z,turn=0;
            if (before!=null && before.replay()==replay && before.body().equals(body.getUUID())) {
                if (tick==before.tick() && body.getX()==before.x() && body.getY()==before.y() && body.getZ()==before.z()) {
                    vx=before.vx(); vz=before.vz(); turn=before.turn();
                } else if (tick==before.tick() || tick==before.tick()+1) {
                    vx=body.getX()-before.x(); vz=body.getZ()-before.z();
                    turn=BoatCameraModel.angle(before.yaw(),body.getYRot(),1)-before.yaw();
                }
            }
            motion.put(id,new Motion(replay,body.getUUID(),tick,body.getX(),body.getY(),body.getZ(),body.getYRot(),vx,vz,turn));
            return;
        }
    }
    public static double clock() {
        var replay = Flashback.getReplayServer();
        if (replay == null) return Double.NaN;
        var export=Flashback.EXPORT_JOB;
        return CameraExport.replayTick(replay.getPartialReplayTick(),Flashback.isExporting() && export!=null,
            export==null?0:export.getSettings().startTick(),export==null?0:export.getCurrentTickDouble());
    }
    public static Sample client(UUID id, double tick) {
        var mc = Minecraft.getInstance();
        var replay = Flashback.getReplayServer();
        if (id == null || mc.level == null || replay == null) return null;
        Entity player = mc.level.getPlayerByUUID(id);
        if (player == null || player == mc.player) return null;
        float fraction = replay.replayPaused && !Flashback.isExporting() ? 1 : (float)(tick - Math.floor(tick));
        return sample(player, id, tick, fraction, true);
    }
    public static Sample server(ReplayServer replay, UUID id, double tick) {
        if (id == null) return null;
        for (var level : replay.getAllLevels()) {
            Entity player = level.getPlayerByUUID(id);
            if (player != null) return sample(player, id, tick, 1, false);
        }
        return null;
    }
    private static Sample sample(Entity player, UUID id, double tick, float partial, boolean client) {
        Entity body = player.getVehicle() == null ? player : player.getVehicle();
        Vec3 pos = client ? body.getPosition(partial) : body.position();
        Vec3 velocity = body.getPosition(1).subtract(body.getPosition(0));
        double yaw = client ? BoatCameraModel.angle(body.yRotO, body.getYRot(), partial) : body.getYRot();
        double turn = BoatCameraModel.angle(body.yRotO, body.getYRot(), 1) - body.yRotO;
        var published=motion.get(id);
        if (published!=null && published.replay()==Flashback.getReplayServer() && published.body().equals(body.getUUID())
            && Math.abs(published.tick()-tick)<=1.01) {
            velocity=new Vec3(published.vx(),0,published.vz()); turn=published.turn();
        }
        var actor = FlashbackActors.ACTORS.byUuid(id);
        if (actor != null) {
            double speed = actor.take.getSpeedMultiplier();
            double local = (tick - actor.take.offsetTicks) * speed;
            int at = (int)Math.floor(local);
            if (!actor.enabled() || !actor.take.coversTick(at)) return null;
            if (actor.sim().usesRecordedBaseline(at)) {
                var a = actor.take.vehicleAt(at);
                var b = actor.take.vehicleAt(at + 1);
                if (a != null && body.getUUID().equals(a.uuid())) {
                    double f = local - Math.floor(local);
                    if (b != null && a.uuid().equals(b.uuid())) {
                        pos = new Vec3(a.x()+(b.x()-a.x())*f, a.y()+(b.y()-a.y())*f, a.z()+(b.z()-a.z())*f);
                        var prev = actor.take.vehicleAt(at - 1);
                        double[] v = BoatCameraModel.blendedVelocity(prev, a, b, f);
                        velocity = new Vec3(v[0], v[1], v[2]).scale(speed);
                        yaw = BoatCameraModel.angle(a.yaw(), b.yaw(), f);
                        turn = BoatCameraModel.blendedTurn(prev, a, b, f) * speed;
                    } else {
                        pos = new Vec3(a.x(), a.y(), a.z());
                        yaw = a.yaw();
                        if (a.hasMomentum()) velocity = new Vec3(a.velocityX(), a.velocityY(), a.velocityZ()).scale(speed);
                    }
                }
            }
        }
        return new Sample(pos.x, pos.y, pos.z, yaw, velocity.x, velocity.z, turn, body.level(), body);
    }
    public static double eyeHeight(Sample sample) {
        var entity=sample.entity();
        if(entity==null) return 1.62;
        var rider=entity.getFirstPassenger();
        double eye=rider==null?entity.getEyeHeight():rider.getY()-entity.getY()+rider.getEyeHeight();
        return Double.isFinite(eye)?Math.max(.3,Math.min(8,eye)):1.62;
    }
    public static BoatCameraModel.Shot avoid(BoatCameraModel.Shot shot, Sample a, Sample b) {
        Vec3 aim = b == null ? new Vec3(a.x(), a.y()+CameraModeModel.aimHeight(eyeHeight(a)), a.z())
            : new Vec3((a.x()+b.x())/2, (a.y()+b.y()+CameraModeModel.aimHeight(eyeHeight(a))+CameraModeModel.aimHeight(eyeHeight(b)))/2, (a.z()+b.z())/2);
        Vec3 camera = new Vec3(shot.x(), shot.y(), shot.z());
        var hit = a.level().clip(new ClipContext(aim, camera, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, a.entity()));
        if (hit.getType() == HitResult.Type.MISS) return shot;
        camera = hit.getLocation().add(aim.subtract(camera).normalize().scale(.25));
        return CameraModeModel.lookAt(camera.x,camera.y,camera.z,aim.x,aim.y,aim.z,shot.roll(),shot.fov());
    }
}
