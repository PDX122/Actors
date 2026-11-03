package pdx.dev.fbactors.camera;

import pdx.dev.fbactors.actor.Actor;

public final class CameraModes {
    public static final String PAYLOAD="ACTORS_CAMERA_MODE";
    public static String notice="";
    private CameraModes() {}
    public static void open() { BoatMenu.open(0); }
    public static BoatCam.Settings tuning(int mode) {
        return pdx.dev.fbactors.settings.FbSettings.settings().boatCamMode==mode?BoatCam.snapshot():CameraModesCatalog.recommended(mode);
    }
    public static void select(int mode) { apply(CameraModesCatalog.recommended(mode)); CameraModesPreview.restart(); }
    public static void apply(BoatCam.Settings settings) {
        var s=pdx.dev.fbactors.settings.FbSettings.settings();
        s.boatCamMode=settings.mode(); s.boatCamDistance=(float)settings.distance(); s.boatCamHeight=(float)settings.height();
        s.boatCamSide=(float)settings.side(); s.boatCamLookAhead=(float)settings.ahead(); s.boatCamOrbitSpeed=(float)settings.orbit();
        s.boatCamBank=(float)settings.bank(); s.boatCamFov=(float)settings.fov(); s.boatCamSpeedFov=(float)settings.speedFov();
        s.boatCamSmoothing=(float)settings.lag(); s.boatCamCollision=settings.collision(); BoatCam.restartShot();
    }
    public static boolean attach(String id,Actor actor) {
        if(actor==null || actor.take.locked) { notice="Unlock the actor track before adding a camera mode."; return false; }
        var state=BoatStudioState.get(); var track=state.timeline;
        if(track.locked || track.clips.size()>=256) { notice="Unlock the Camera modes track or remove a clip first."; return false; }
        int start=(int)Math.max(0,Math.min(99_999_999L,(long)actor.take.firstTick()+actor.offset()));
        int end=(int)Math.max(start+1L,Math.min(100_000_000L,(long)actor.take.lastTick()+actor.offset()+1));
        var entry=CameraModesCatalog.find(id);
        var clip=CameraModeClips.create(id,actor.take.id,state.rival,start,end,entry==null?null:tuning(entry.mode()));
        if(clip==null) { notice="Two subjects needs a different second subject. Choose it in Camera modes."; return false; }
        BoatEdits.edit(() -> { track.clips.add(clip); BoatEdits.selected=clip.id; });
        BoatCam.stop(); notice=CameraModesCatalog.find(id).name()+" follows "+actor.displayName()+" for this take.";
        return true;
    }
}
