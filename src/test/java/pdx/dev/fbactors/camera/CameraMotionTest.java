package pdx.dev.fbactors.camera;

import org.junit.jupiter.api.Test;
import pdx.dev.fbactors.take.ActorFrame;
import static org.junit.jupiter.api.Assertions.*;

class CameraMotionTest {
    private ActorFrame frame(double x,double y,double z,float yaw,float pitch) {
        return new ActorFrame(x,y,z,yaw,pitch,yaw,(byte)0,(byte)0);
    }
    @Test void liveMotionAdvancesThroughoutTheWholeRenderTick() {
        for(int i=0;i<=8;i++) assertEquals(i/8.0,CameraMotion.liveFraction(99+i/8.0,100),1e-9);
        assertEquals(0,CameraMotion.liveFraction(90,100));
        assertEquals(1,CameraMotion.liveFraction(101,100));
    }
    @Test void flightPositionVelocityAndBankInputAreContinuousAtTickBoundaries() {
        var a=frame(0,100,0,175,10); var b=frame(1,99,2,179,15);
        var c=frame(3,97,5,-177,23); var d=frame(6,94,9,-170,30); var e=frame(10,90,14,-160,36);
        var left=CameraMotion.sample(a,b,c,d,1,1);
        var right=CameraMotion.sample(b,c,d,e,0,1);
        assertEquals(left.x(),right.x(),1e-9); assertEquals(left.y(),right.y(),1e-9); assertEquals(left.z(),right.z(),1e-9);
        assertEquals(left.vx(),right.vx(),1e-9); assertEquals(left.vy(),right.vy(),1e-9); assertEquals(left.vz(),right.vz(),1e-9);
        assertEquals(left.turn(),right.turn(),1e-9);
        assertEquals(0,BoatCameraModel.angle(left.yaw(),right.yaw(),1)-left.yaw(),1e-9);
        for(int mode:new int[]{30,33,36}) {
            var settings=CameraModesCatalog.recommended(mode);
            var x=CameraModeModel.shot(left.x(),left.y(),left.z(),left.yaw(),left.pitch(),left.vx(),left.vy(),left.vz(),left.turn(),0,1.62,settings);
            var y=CameraModeModel.shot(right.x(),right.y(),right.z(),right.yaw(),right.pitch(),right.vx(),right.vy(),right.vz(),right.turn(),0,1.62,settings);
            assertEquals(x.x(),y.x(),1e-8); assertEquals(x.y(),y.y(),1e-8); assertEquals(x.z(),y.z(),1e-8);
            assertEquals(x.roll(),y.roll(),1e-8);
        }
    }
    @Test void curvesPreserveStopsAndDoNotOvershootRecordedPositions() {
        for(double[] positions:new double[][]{{0,1,2,10},{0,1,1,1},{3,2,1,0},{0,3,1,2}})
            for(int frame=0;frame<=120;frame++) {
                double v=CameraMotion.curve(positions[0],positions[1],positions[2],positions[3],frame/120.0);
                assertTrue(v>=Math.min(positions[1],positions[2])-1e-9 && v<=Math.max(positions[1],positions[2])+1e-9);
            }
        assertEquals(0,CameraMotion.derivative(0,1,1,1,.5),1e-9);
    }
    @Test void uavAndExactPovUseTheirOwnFramingAndLens() {
        for(int mode:new int[]{39,40,41}) assertEquals(mode,CameraModesCatalog.recommended(mode).mode());
        var uav=CameraModeModel.shot(0,100,0,0,30,0,0,1,0,0,1.62,CameraModesCatalog.recommended(39));
        assertEquals(170,uav.y()); assertEquals(0,uav.roll());
        var pov=CameraModeModel.shot(0,100,0,40,35,0,0,1,8,0,1.62,CameraModesCatalog.recommended(41));
        assertEquals(40,pov.yaw(),1e-8); assertEquals(35,pov.pitch(),1e-8); assertEquals(0,pov.roll());
    }
    @Test void movingTargetDampingMatchesAt24_60And144Fps() {
        BoatCameraModel.Shot reference=null;
        for(int fps:new int[]{24,60,144}) {
            var previous=new BoatCameraModel.Shot(0,100,0,170,20,0,70);
            var result=previous;
            for(int i=1;i<=fps;i++) {
                double tick=20.0*i/fps;
                var next=new BoatCameraModel.Shot(tick,100,0,170+tick*2,20,0,70);
                result=BoatCam.smooth(result,previous,next,20.0/fps,.2);
                previous=next;
            }
            assertEquals(20-4*(1-Math.exp(-5)),result.x(),1e-9);
            if(reference!=null) {
                assertEquals(reference.x(),result.x(),1e-9);
                assertEquals(reference.yaw(),result.yaw(),1e-9);
            }
            reference=result;
        }
    }
    @Test void recordedVehiclesHaveContinuousPositionVelocityAndAngularVelocity() {
        var id=java.util.UUID.randomUUID();
        var frames=new pdx.dev.fbactors.take.ActorVehicleFrame[5];
        for(int i=0;i<5;i++) frames[i]=new pdx.dev.fbactors.take.ActorVehicleFrame(i,id,"minecraft:oak_boat",i*i,64,i*2,175+i*4,0);
        var left=CameraMotion.sample(frames[0],frames[1],frames[2],frames[3],1,1);
        var right=CameraMotion.sample(frames[1],frames[2],frames[3],frames[4],0,1);
        assertEquals(left.x(),right.x(),1e-9); assertEquals(left.vx(),right.vx(),1e-9);
        assertEquals(left.yaw(),right.yaw(),1e-9); assertEquals(left.turn(),right.turn(),1e-9);
    }
    @Test void cameraDoesNotFlyThroughTheWorldDuringRecordedTeleports() {
        var a=frame(0,64,0,0,0); var b=frame(1000,64,0,90,0);
        assertEquals(0,CameraMotion.sample(a,a,b,b,.99,1).x());
        assertEquals(1000,CameraMotion.sample(a,a,b,b,1,1).x());
        assertEquals(0,CameraMotion.sample(a,a,b,b,.5,1).vx());
        assertEquals(b.x(),CameraMotion.sample(a,b,b,b,.5,1).x());
    }
    @Test void uavPlatformIgnoresSubjectYawAndUsesANarrowSensorLens() {
        for(int mode:new int[]{39,40}) {
            var s=CameraModesCatalog.recommended(mode);
            assertTrue(s.fov()<15);
            assertEquals(CameraModeModel.shot(0,64,0,0,.1,.2,0,5,1.62,s),
                CameraModeModel.shot(0,64,0,135,.1,.2,0,5,1.62,s));
        }
    }
    @Test void uavKeepsFlyingAroundAStationarySubjectWithASteadyGimbal() {
        for(int mode:new int[]{39,40}) {
            var settings=CameraModesCatalog.recommended(mode);
            assertTrue(CameraModesCatalog.timed(mode));
            for(double t:new double[]{0,10,60,120}) {
                var a=CameraModeModel.shot(0,64,0,0,0,0,0,t,1.62,settings);
                var b=CameraModeModel.shot(0,64,0,0,0,0,0,t+1.0/60,1.62,settings);
                double step=Math.hypot(b.x()-a.x(),b.z()-a.z());
                assertTrue(step>.005 && step<.15,"Airframe must neither hover nor jump");
                assertEquals(a.y(),b.y()); assertEquals(0,a.roll());
                var centre=CameraModePreviewScene.project(a,new CameraModePreviewScene.Point(0,64+CameraModeModel.aimHeight(1.62),0),640,360);
                assertNotNull(centre); assertEquals(320,centre.x(),1e-4); assertEquals(180,centre.y(),1e-4);
            }
        }
    }
}
