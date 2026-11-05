package pdx.dev.fbactors.camera;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CameraPreviewMotionTest {
    private CameraPreviewCapture.Mesh quad(float y,float z,float normalY) {
        var vertices=new ArrayList<CameraPreviewCapture.Vertex>();
        for(int i=0;i<4;i++) vertices.add(new CameraPreviewCapture.Vertex(i%3==0?-.2f:.2f,y,z+(i<2?-.1f:.1f),
            i%3==0?0:1,i<2?0:1,0,normalY,0,-1,null));
        return new CameraPreviewCapture.Mesh(vertices);
    }
    @Test void capturedPosesInterpolateBetweenAnimationFrames() {
        var poses=new CameraPreviewPose();poses.accept(quad(0,0,1),10);poses.accept(quad(1,0,1),10.1);
        assertEquals(.25,poses.at(10.025).vertices().getFirst().y(),1e-5);
        assertEquals(.5,poses.at(10.05).vertices().getFirst().y(),1e-5);
        assertEquals(.75,poses.at(10.075).vertices().getFirst().y(),1e-5);
    }
    @Test void seekingAndTextureChangesDoNotBlendUnrelatedGeometry() {
        var poses=new CameraPreviewPose();poses.accept(quad(0,0,1),10);
        var sought=quad(2,0,1);poses.accept(sought,2);assertSame(sought,poses.at(2));
        var vertices=new ArrayList<>(sought.vertices());var a=vertices.getFirst();
        vertices.set(0,new CameraPreviewCapture.Vertex(a.x(),a.y(),a.z(),.2f,a.v(),a.nx(),a.ny(),a.nz(),a.color(),a.texture()));
        var changed=new CameraPreviewCapture.Mesh(vertices);poses.accept(changed,2.1);
        assertSame(changed,poses.at(2.05));
    }
    @Test void nativePassengerOffsetsAreConvertedIntoThePreviewHeading() {
        var seat=CameraPreviewRiding.local(.2,.85,-.1,180);
        assertEquals(-.2,seat.x(),1e-6);assertEquals(.85,seat.y(),1e-6);assertEquals(.1,seat.z(),1e-6);
        var turned=CameraPreviewRiding.local(.2,.85,-.1,90);
        assertEquals(-.1,turned.x(),1e-6);assertEquals(-.2,turned.z(),1e-6);
    }
    @Test void elytraAndUavReturnWithoutRenumberingSavedCameraIds() {
        assertEquals(38,CameraModesCatalog.available().size());
        for(int mode:new int[]{29,36,37,38}) {
            assertFalse(CameraModesCatalog.available(mode));assertEquals(mode,CameraModesCatalog.ENTRIES.get(mode).mode());
        }
        assertTrue(CameraModesCatalog.available(41));assertTrue(CameraModesCatalog.available(30));assertTrue(CameraModesCatalog.available(39));
        assertNull(CameraModeClips.create("fpv_acro",UUID.randomUUID(),null,0,100,CameraModesCatalog.recommended(36)));
        var legacy=new BoatTimeline.Clip();legacy.target=UUID.randomUUID();legacy.end=100;legacy.settings=CameraModesCatalog.recommended(36);
        var timeline=new BoatTimeline();timeline.clips.add(legacy);timeline.normalize();
        assertEquals(1,timeline.clips.size());assertNull(timeline.active(20));
        assertEquals(new CameraExport.Range(-1,-1),CameraExport.range(timeline,-1,-1));
    }
}
