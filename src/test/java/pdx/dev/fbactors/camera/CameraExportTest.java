package pdx.dev.fbactors.camera;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;

class CameraExportTest {
    private BoatTimeline.Clip clip(int start,int end) {
        var clip=new BoatTimeline.Clip(); clip.start=start; clip.end=end;
        clip.target=UUID.randomUUID(); clip.settings=CameraModesCatalog.recommended(11);
        return clip;
    }
    @Test void nonzeroExportStartUsesReplayTimeIncludingFractionalFrames() {
        var timeline=new BoatTimeline(); var clip=clip(600,800); timeline.clips.add(clip);
        assertEquals(600.5,CameraExport.replayTick(999,true,600,.5));
        assertSame(clip,timeline.active(CameraExport.replayTick(999,true,600,.5)));
        assertSame(clip,timeline.active(CameraExport.replayTick(999,true,600,199.75)));
        assertNull(timeline.active(CameraExport.replayTick(999,true,600,200)));
        assertEquals(999,CameraExport.replayTick(999,false,600,.5));
        assertEquals(.5,CameraExport.replayTick(999,true,0,.5));
    }
    @Test void trimmedExportAndPreviewProduceTheSameCinematicShot() {
        for(var entry:CameraModesCatalog.ENTRIES) {
            var settings=CameraModesCatalog.recommended(entry.mode());
            double preview=725.5,export=CameraExport.replayTick(-1,true,720,5.5);
            double previewTime=entry.mode()>=8?preview-600:preview;
            double exportTime=entry.mode()>=8?export-600:export;
            assertEquals(CameraModeModel.shot(3,2,5,40,.1,.2,1,previewTime/20,1.62,settings),
                CameraModeModel.shot(3,2,5,40,.1,.2,1,exportTime/20,1.62,settings),entry.name());
        }
    }
    @Test void cameraOnlyProjectsContributeToTheNativeExportRange() {
        var timeline=new BoatTimeline(); timeline.clips.add(clip(600,800));
        assertEquals(new CameraExport.Range(600,800),CameraExport.range(timeline,-1,-1));
        assertEquals(new CameraExport.Range(500,900),CameraExport.range(timeline,500,900));
        assertEquals(new CameraExport.Range(600,800),CameraExport.range(timeline,650,700));
        timeline.locked=true;
        assertEquals(new CameraExport.Range(600,800),CameraExport.range(timeline,-1,-1));
    }
    @Test void singleTickCameraClipHasOneFrameInsteadOfAnEmptyExport() {
        var timeline=new BoatTimeline();timeline.clips.add(clip(100,101));
        var range=CameraExport.range(timeline,-1,-1);
        assertEquals(1,range.end()-range.start());
    }
    @Test void mutedClipsAndTracksDoNotExtendTheExport() {
        var timeline=new BoatTimeline(); var muted=clip(10,1000); muted.enabled=false;
        timeline.clips.add(muted); timeline.clips.add(clip(600,800));
        assertEquals(new CameraExport.Range(600,800),CameraExport.range(timeline,-1,-1));
        timeline.enabled=false;
        assertEquals(new CameraExport.Range(-1,-1),CameraExport.range(timeline,-1,-1));
    }
}
