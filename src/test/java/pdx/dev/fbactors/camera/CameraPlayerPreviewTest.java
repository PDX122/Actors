package pdx.dev.fbactors.camera;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

class CameraPlayerPreviewTest {
    @Test void walkingKeepsTheBaseFeetOnTheGround() {
        for(int frame=0;frame<120;frame++) {
            var mesh=CameraPreviewMesh.player(9,false,new CameraModePreviewScene.Point(0,0,0),37,frame/60.0);
            double lowest=mesh.subList(24,36).stream().flatMap(f -> f.vertices().stream()).mapToDouble(v -> v.point().y()).min().orElseThrow();
            assertEquals(0,lowest,1e-9,"Frame "+frame);
        }
    }
    @Test void headingFollowsTravelAndStrideIsContinuousAcrossCycles() {
        for(double t:new double[]{0,1,3,8,100}) {
            var a=CameraModePreviewScene.subject(t); var b=CameraModePreviewScene.subject(t+.00001);
            assertEquals(Math.toDegrees(Math.atan2(a.x()-b.x(),b.z()-a.z())),CameraModePreviewScene.heading(t),.001);
        }
        double period=Math.PI/.55;
        assertEquals(CameraPreviewMesh.gaitPhase(period-.00001),CameraPreviewMesh.gaitPhase(period+.00001),.001);
    }
    @Test void lightingFollowsWorldNormalsAndHiddenFacesAreCulled() {
        var mesh=CameraPreviewMesh.player(9,false,new CameraModePreviewScene.Point(0,0,0),0,0);
        var view=new CameraModePreviewScene.View(new BoatCameraModel.Shot(0,1,5,180,0,0,60),240,140);
        assertTrue(view.visible(mesh.get(0).vertices()));
        assertFalse(view.visible(mesh.get(1).vertices()));
        assertNotEquals(mesh.get(4).color(),mesh.get(5).color());
        var rotated=CameraPreviewMesh.player(9,false,new CameraModePreviewScene.Point(0,0,0),180,0);
        assertNotEquals(mesh.get(0).color(),rotated.get(0).color());
    }
    @Test void playerUsesCompleteSkinLayoutAndAnimatedLimbs() {
        var origin=new CameraModePreviewScene.Point(0,0,0);
        var normal=CameraPreviewMesh.player(9,false,origin,0,0);
        var walking=CameraPreviewMesh.player(9,false,origin,0,.2);
        var slim=CameraPreviewMesh.player(9,true,origin,0,0);
        assertEquals(72,normal.size()); assertNotEquals(normal,walking); assertNotEquals(normal,slim);
        double top=0;
        for(var face:normal) for(var vertex:face.vertices()) {
            assertTrue(vertex.u()>=0 && vertex.u()<=1); assertTrue(vertex.v()>=0 && vertex.v()<=1);
            top=Math.max(top,vertex.point().y());
        }
        assertTrue(top>1.8 && top<1.85);
    }
    @Test void floorCrossingTheCameraPlaneIsClippedWithoutHolesOrInvalidCoordinates() {
        var view=new CameraModePreviewScene.View(new BoatCameraModel.Shot(0,1,0,0,0,0,60),240,140);
        var face=List.of(new CameraPreviewMesh.Vertex(new CameraModePreviewScene.Point(-1,0,-1),0,0),
            new CameraPreviewMesh.Vertex(new CameraModePreviewScene.Point(1,0,-1),1,0),
            new CameraPreviewMesh.Vertex(new CameraModePreviewScene.Point(1,0,2),1,1),
            new CameraPreviewMesh.Vertex(new CameraModePreviewScene.Point(-1,0,2),0,1));
        var clipped=CameraPreviewMesh.clip(face,view);
        assertEquals(4,clipped.size());
        for(var v:clipped) { assertTrue(view.depth(v.point())>=.079); assertNotNull(view.project(v.point())); }
    }
    @Test void newModesHaveDistinctStableComposition() {
        var side=CameraModeModel.shot(0,0,0,0,0,.1,0,0,1.62,CameraModesCatalog.recommended(1));
        assertTrue(Math.abs(side.x())>Math.abs(side.z())*4);
        var orbit=CameraModesCatalog.recommended(2);
        assertEquals(CameraModeModel.shot(0,0,0,0,0,.1,0,2,1.62,orbit),CameraModeModel.shot(0,0,0,180,0,.1,0,2,1.62,orbit));
        var pov=CameraModeModel.shot(0,3,0,0,0,.1,100,0,1.62,CameraModesCatalog.recommended(3));
        assertEquals(4.62,pov.y(),1e-9); assertEquals(0,pov.pitch(),1e-9); assertEquals(0,pov.roll());
        var overhead=CameraModesCatalog.recommended(5);
        assertEquals(CameraModeModel.shot(0,0,0,0,0,.1,0,0,1.62,overhead),CameraModeModel.shot(0,0,0,180,.1,0,100,9,1.62,overhead));
    }
}
