package pdx.dev.fbactors.camera;

import java.util.UUID;

public final class CameraModeClips {
    private CameraModeClips() {}
    public static BoatTimeline.Clip create(String mode, UUID target, UUID second, int start, int end, BoatCam.Settings tuning) {
        var entry=CameraModesCatalog.find(mode);
        if(entry==null || !CameraModesCatalog.available(entry.mode()) || target==null || tuning==null || end<=start || start>=100_000_000 || end<=0) return null;
        if(entry.mode()==7 && (second==null || target.equals(second))) return null;
        var clip=new BoatTimeline.Clip(); clip.target=target; clip.rival=second;
        clip.start=Math.max(0,start); clip.end=Math.min(100_000_000,end);
        clip.settings=CameraModesCatalog.withMode(tuning,entry.mode());
        return clip;
    }
}
