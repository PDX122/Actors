package pdx.dev.fbactors.camera;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.keyframe.handler.MinecraftKeyframeHandler;
import com.moulberry.flashback.state.EditorStateManager;
import net.minecraft.client.Minecraft;
import org.joml.Vector3d;
import pdx.dev.fbactors.FlashbackActors;
import pdx.dev.fbactors.actor.ActorMode;
import pdx.dev.fbactors.settings.FbSettings;

public final class BoatCam {
    public static final String[] MODES = CameraModesCatalog.names();
    private static boolean enabled, applying, timelineOwned;
    private static java.util.UUID activeClip, mountedSubject, mountedBody, sampledTarget;
    private static Object session;
    private static Object renderExport;
    private static BoatCameraModel.Shot shot, saved, rendered, previousDesired;
    private static UavCameraModel.State uav;
    private static Settings sampledSettings;
    private static double lastTick = Double.NaN, startedTick;
    private static boolean savedFov, savedRoll;
    private static float savedFovAmount, savedRollAmount;
    private static String status = "Choose a player or actor.";
    private BoatCam() {}
    public static void open() { BoatMenu.open(0); }
    public static boolean ownsCamera() {
        if(pdx.dev.fbactors.events.EventCam.applying())return false;
        if(pdx.dev.fbactors.events.EventCam.ownsCamera())return true;
        if (applying || ActorMode.isActive()) return false;

        boolean active = BoatStudioState.get().timeline.active(BoatSubjects.clock()) != null;
        if (!active && timelineOwned) stop();
        return active || enabled && !timelineOwned;
    }
    private static void start() { start(BoatStudioState.get().target); }
    private static void start(java.util.UUID target) {
        var mc = Minecraft.getInstance();
        var state = EditorStateManager.getCurrent();
        if (target == null || mc.player == null || Flashback.getReplayServer() == null || state == null) return;
        if (!enabled) {
            saved = new BoatCameraModel.Shot(mc.player.getX(), mc.player.getY(), mc.player.getZ(), mc.player.getYRot(), mc.player.getXRot(), 0, 0);
            savedFov = state.replayVisuals.overrideFov;
            savedFovAmount = state.replayVisuals.overrideFovAmount;
            savedRoll = state.replayVisuals.overrideRoll;
            savedRollAmount = state.replayVisuals.overrideRollAmount;
        }
        session = Flashback.getReplayServer();
        enabled = true;
        startedTick = BoatSubjects.clock();
        reset();
    }
    public static void stop() {
        if (!enabled) return;
        enabled = false;
        timelineOwned = false; activeClip = null;
        var mc = Minecraft.getInstance();
        var state = EditorStateManager.getCurrent();
        if (session == Flashback.getReplayServer() && mc.player != null && saved != null) {
            if (!ActorMode.isActive()) {
                pdx.dev.fbactors.compat.Compat.snapTo(mc.player, saved.x(), saved.y(), saved.z(), (float)saved.yaw(), (float)saved.pitch());
                pdx.dev.fbactors.compat.Compat.cancelInterpolation(mc.player);
            }
            if (state != null) {
                state.replayVisuals.overrideFov = savedFov;
                state.replayVisuals.overrideFovAmount = savedFovAmount;
                state.replayVisuals.overrideRoll = savedRoll;
                state.replayVisuals.overrideRollAmount = savedRollAmount;
            }
        }
        reset();
    }
    public static void restartShot() { startedTick=BoatSubjects.clock(); reset(); }
    public static void reset() { shot = null; rendered = null; previousDesired = null; uav = null; sampledSettings = null; mountedSubject = mountedBody = null; lastTick = Double.NaN; }
    public static boolean hides(java.util.UUID entity) {
        return rendered!=null && entity!=null && (entity.equals(mountedSubject) || entity.equals(mountedBody));
    }

    public static BoatCameraModel.Shot renderedShot() {
        var event=pdx.dev.fbactors.events.EventCam.renderedShot();if(event!=null)return event;
        return enabled && !ActorMode.isActive() && session==Flashback.getReplayServer()?rendered:null;
    }
    public record Settings(int mode, double distance, double height, double side, double ahead,
                           double orbit, double bank, double fov, double speedFov, double lag, boolean collision) {
        public Settings {
            mode = Math.max(0, Math.min(MODES.length - 1, mode));
            distance = finite(distance, .5, CameraModesCatalog.uav(mode)?256:24, 4);
            height = finite(height, .3, CameraModesCatalog.uav(mode)?256:12, 2);
            side = finite(side, -12, 12, 0);
            ahead = finite(ahead, 0, 12, 0);
            orbit = finite(orbit, -90, 90, 20);
            bank = finite(bank, 0, CameraModesCatalog.fpv(mode)?85:30, 0);
            fov = finite(fov, CameraModesCatalog.uav(mode)?2:30, 110, 70);
            speedFov = finite(speedFov, 0, 20, 0);
            lag = finite(lag, 0, 1, 0);
        }
        private static double finite(double value, double min, double max, double fallback) {
            return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
        }
    }
    public static Settings snapshot() {
        var s = FbSettings.settings();
        return new Settings(s.boatCamMode, s.boatCamDistance, s.boatCamHeight, s.boatCamSide,
            s.boatCamLookAhead, s.boatCamOrbitSpeed, s.boatCamBank, s.boatCamFov,
            s.boatCamSpeedFov, s.boatCamSmoothing, s.boatCamCollision);
    }
    public static BoatCameraModel.Shot desired(BoatSubjects.Sample a, BoatSubjects.Sample b,
                                              Settings s, double tick, double aspect) {
        if (s.mode() == 7) {
            if (b == null || b.level() != a.level()) return null;
            double vx = a.vx()+b.vx(), vz = a.vz()+b.vz();
            double heading = Math.hypot(vx, vz) > .01 ? Math.toDegrees(Math.atan2(-vx, vz)) : a.yaw();
            return BoatCameraModel.battle(a.x(),a.y()+CameraModeModel.aimHeight(BoatSubjects.eyeHeight(a))-.6,a.z(),b.x(),b.y()+CameraModeModel.aimHeight(BoatSubjects.eyeHeight(b))-.6,b.z(),heading,
                s.distance(),s.height(),s.fov(),aspect);
        }
        double altitude=CameraModesCatalog.uav(s.mode())?BoatSubjects.groundHeight(a):a.y();
        return CameraModeModel.shot(a.x(),altitude,a.z(),a.yaw(),a.pitch(),a.vx(),a.vy(),a.vz(),a.turn(),tick/20,
            BoatSubjects.eyeHeight(a),s);
    }
    public static BoatCameraModel.Shot smooth(BoatCameraModel.Shot before, BoatCameraModel.Shot next,
                                             double delta, double lag) {
        if (before == null || !Double.isFinite(delta) || delta < 0 || delta > 5 || lag <= 0) return next;
        return delta > 0 ? BoatCameraModel.blend(before,next,1-Math.exp(-delta/(20*Math.max(.001,lag)))) : before;
    }
    public static BoatCameraModel.Shot smooth(BoatCameraModel.Shot before, BoatCameraModel.Shot from,
                                             BoatCameraModel.Shot next, double delta, double lag) {
        if (from == null || before == null || !Double.isFinite(delta) || delta < 0 || delta > 5 || lag <= 0) return next;
        if (delta == 0) return before;
        // Integrate the moving target over the frame, so damping does not change with export FPS.
        double step = delta/(20*Math.max(.001,lag));
        double alpha = -Math.expm1(-step);
        double weight = step < .001 ? .5+step/12 : 1/alpha-1/step;
        return BoatCameraModel.blend(before,BoatCameraModel.blend(from,next,weight),alpha);
    }
    public static void clientTick() {
        var state = BoatStudioState.get();
        BoatStudioState.flush(false);
        var clip = state.timeline.active(BoatSubjects.clock());
        BoatSubjects.watch(BoatSubjects.resolve(clip == null ? state.target : clip.target), BoatSubjects.resolve(clip == null ? state.rival : clip.rival));
    }
    public static void renderFrame() {
        rendered=null; mountedSubject=mountedBody=null;
        if(pdx.dev.fbactors.events.EventCam.renderFrame())return;
        Object export=Flashback.isExporting()?Flashback.EXPORT_JOB:null;
        if(renderExport!=export) { renderExport=export; reset(); }
        var studio = BoatStudioState.get();
        double tick = BoatSubjects.clock();
        var clip = studio.timeline.active(tick);
        if (clip == null && timelineOwned) { stop(); timelineOwned = false; activeClip = null; }
        if (clip != null && !ActorMode.isActive()) {
            if (!enabled) start(clip.target);
            timelineOwned = enabled;
            if (!clip.id.equals(activeClip)) { activeClip = clip.id; reset(); }
        }
        if (!enabled) return;
        var mc = Minecraft.getInstance();
        var replay = Flashback.getReplayServer();
        if (replay == null || replay != session || ActorMode.isActive() || mc.player == null || mc.level == null) { stop(); return; }
        var state = BoatStudioState.get();
        var target=BoatSubjects.resolve(clip==null?state.target:clip.target);
        if(!java.util.Objects.equals(sampledTarget,target)) { sampledTarget=target; reset(); }
        var a = BoatSubjects.client(target,tick);
        var b = BoatSubjects.client(clip == null ? state.rival : clip.rival,tick);
        if (a == null) { status = "Waiting for the subject at this playhead."; reset(); return; }
        var settings = clip == null ? snapshot() : clip.settings;
        if(!CameraModesCatalog.available(settings.mode())) { stop();return; }
        if (!settings.equals(sampledSettings)) { reset(); sampledSettings = settings; }
        double shotTick=CameraModesCatalog.timed(settings.mode())?tick-(clip==null?startedTick:clip.start):tick;
        var next = desired(a,b,settings,shotTick,aspect());
        if (next == null) { status = "Two subjects must be present in the same dimension."; reset(); return; }
        if (!next.valid()) { status = "Waiting for valid subject motion."; reset(); return; }
        if (previousDesired != null && Math.hypot(Math.hypot(next.x()-previousDesired.x(),next.z()-previousDesired.z()),next.y()-previousDesired.y()) > 64) {
            reset(); sampledSettings=settings;
        }
        if(CameraModesCatalog.uav(settings.mode())) {
            uav=UavCameraModel.step(uav,previousDesired,next,a.x(),a.y()+CameraModeModel.aimHeight(BoatSubjects.eyeHeight(a)),a.z(),tick-lastTick,settings.lag());
            shot=uav.view();
        } else {
            var damped = smooth(shot,previousDesired,next,tick-lastTick,settings.lag());
            shot = CameraModesCatalog.fpv(settings.mode()) || CameraModesCatalog.pov(settings.mode())
                ? new BoatCameraModel.Shot(next.x(),next.y(),next.z(),damped.yaw(),damped.pitch(),damped.roll(),next.fov())
                : damped;
        }
        previousDesired = next;
        lastTick = tick;
        var view = settings.collision() && !CameraModesCatalog.fpv(settings.mode()) ? BoatSubjects.avoid(shot,a,settings.mode()==7?b:null) : shot;
        if (!view.valid()) { status = "Waiting for a valid camera position."; reset(); return; }
        rendered=view;
        if(CameraModesCatalog.pov(settings.mode()) || CameraModesCatalog.fpv(settings.mode())) {
            mountedSubject=BoatSubjects.resolve(clip==null?state.target:clip.target);
            mountedBody=a.entity()==null?null:a.entity().getUUID();
        }
        applying = true;
        try {
            var handler = new MinecraftKeyframeHandler(mc);
            handler.applyCameraPosition(new Vector3d(view.x(),view.y()-mc.player.getEyeHeight(),view.z()),view.yaw(),view.pitch(),view.roll());
            handler.applyFov(view.fov());
        } finally { applying = false; }
        status = settings.mode()==7 ? "Framing both subjects. Export uses the same camera." : "Following subject. Export uses the same camera.";
    }
    public static double aspect() {
        if (Flashback.isExporting() && Flashback.EXPORT_JOB != null)
            return (double)Flashback.EXPORT_JOB.getSettings().resolutionX()/Math.max(1,Flashback.EXPORT_JOB.getSettings().resolutionY());
        var state = EditorStateManager.getCurrent();
        if (state != null && state.replayVisuals.sizing == com.moulberry.flashback.combo_options.Sizing.CHANGE_ASPECT_RATIO)
            return state.replayVisuals.changeAspectRatio.aspectRatio();
        var window = Minecraft.getInstance().getWindow();
        return (double)window.getWidth()/Math.max(1,window.getHeight());
    }
    public static boolean enabled() { return enabled; }
    public static String status() { return status; }
    public static void toggle() { if (enabled) stop(); else start(); }
    public static void render() { BoatMenu.render(); }
    public static void controls() { BoatMenu.cameraControls(); }
}
