package pdx.dev.fbactors.camera;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;

class CameraModesTest {
    private BoatCam.Settings tuning() { return new BoatCam.Settings(2,5,2,3,2,20,0,70,0,.3,false); }
    @Test void droppedPresetUsesTheReceivingActorAndKeepsTuning() {
        var actor=UUID.randomUUID(); var second=UUID.randomUUID();
        var clip=CameraModeClips.create("side",actor,second,40,240,tuning());
        assertEquals(actor,clip.target); assertEquals(40,clip.start); assertEquals(240,clip.end);
        assertEquals(1,clip.settings.mode()); assertEquals(5,clip.settings.distance());
        assertEquals(.3,clip.settings.lag()); assertEquals(second,clip.rival);
        assertEquals(2,tuning().mode());
    }
    @Test void invalidDropsCannotCreateBrokenCameraClips() {
        var actor=UUID.randomUUID();
        assertNull(CameraModeClips.create("unknown",actor,null,0,100,tuning()));
        assertNull(CameraModeClips.create("follow",null,null,0,100,tuning()));
        assertNull(CameraModeClips.create("follow",actor,null,100,100,tuning()));
        assertNull(CameraModeClips.create("pair",actor,null,0,100,tuning()));
        assertNull(CameraModeClips.create("pair",actor,actor,0,100,tuning()));
        assertNotNull(CameraModeClips.create("pair",actor,UUID.randomUUID(),0,100,tuning()));
    }
    @Test void existingModeIndicesRemainStable() {
        assertEquals(42,CameraModesCatalog.ENTRIES.size());
        assertEquals("dolly_zoom",CameraModesCatalog.ENTRIES.get(13).id());
        assertEquals("ice_chase",CameraModesCatalog.ENTRIES.get(14).id());
        assertEquals("hero_orbit",CameraModesCatalog.ENTRIES.get(23).id());
        assertEquals("follow",CameraModesCatalog.ENTRIES.get(0).id());
        assertEquals("pair",CameraModesCatalog.ENTRIES.get(7).id());
        for(int mode=0;mode<CameraModesCatalog.ENTRIES.size();mode++) assertEquals(mode,CameraModesCatalog.ENTRIES.get(mode).mode());
    }
    @Test void previewCameraMathStaysFiniteForEveryModeAndAspect() {
        for(var entry:CameraModesCatalog.ENTRIES) for(double aspect:new double[]{.6,1,16.0/9}) for(int time=0;time<12;time++) {
            var settings=CameraModesCatalog.withMode(tuning(),entry.mode());
            var camera=CameraModePreviewScene.camera(settings,time,aspect);
            assertTrue(camera.valid(),entry.name());
            var subject=CameraModePreviewScene.subject(time);
            var pixel=CameraModePreviewScene.project(camera,new CameraModePreviewScene.Point(subject.x(),1,subject.z()),240,140);
            if(pixel!=null) { assertTrue(Float.isFinite(pixel.x())); assertTrue(Float.isFinite(pixel.y())); assertTrue(pixel.depth()>.07); }
        }
        var camera=new BoatCameraModel.Shot(0,0,0,0,0,0,70);
        assertNull(CameraModePreviewScene.project(camera,new CameraModePreviewScene.Point(0,0,-1),240,140));
        assertNull(CameraModePreviewScene.project(camera,new CameraModePreviewScene.Point(Double.NaN,0,2),240,140));
    }
}
