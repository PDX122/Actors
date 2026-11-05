package pdx.dev.fbactors.camera;

public final class CameraExport {
    public record Range(int start,int end) {}
    private CameraExport() {}
    public static double replayTick(double previewTick,boolean exporting,int exportStart,double exportTick) {
        return exporting?(double)exportStart+exportTick:previewTick;
    }
    public static Range range(BoatTimeline timeline,int start,int end) {
        if(timeline.enabled) for(var clip:timeline.clips) {
            if(!clip.enabled || clip.target==null || clip.settings==null || !CameraModesCatalog.available(clip.settings.mode()) || clip.end<=clip.start) continue;
            start=start<0?clip.start:Math.min(start,clip.start);

            end=Math.max(end,clip.end);
        }
        return new Range(start,end);
    }
    public static Range events(pdx.dev.fbactors.events.EventTimeline timeline,int start,int end) {
        if(timeline.enabled)for(var clip:timeline.clips) {
            if(!clip.enabled || !clip.eventCam || !clip.kind.available() || !clip.valid())continue;
            start=start<0?clip.start:Math.min(start,clip.start);end=Math.max(end,clip.end);
        }
        return new Range(start,end);
    }
}
