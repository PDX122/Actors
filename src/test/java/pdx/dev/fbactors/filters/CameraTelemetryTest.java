package pdx.dev.fbactors.filters;

import org.junit.jupiter.api.Test;
import pdx.dev.fbactors.camera.BoatSubjects;
import pdx.dev.fbactors.camera.BoatCameraModel;
import static org.junit.jupiter.api.Assertions.*;

class CameraTelemetryTest {
    @Test void flightReadoutsUseMotionAndPortableUniformsReceiveLiveValues() {
        var subject=new BoatSubjects.Sample(120,82,-240,-180,12,.3,-.2,.4,2,null,null);
        var camera=new BoatCameraModel.Shot(0,0,0,0,14,-25,90);
        CameraTelemetry.update(subject,camera);
        try {
            assertEquals(10,CameraTelemetry.value("LiveSpeed"),1e-6);
            assertEquals(82,CameraTelemetry.value("LiveAltitude"));
            assertEquals(-4,CameraTelemetry.value("LiveClimb"),1e-6);
            assertEquals(0,CameraTelemetry.value("LiveHeading"));
            assertEquals(-25,CameraTelemetry.value("LiveRoll"));
            for(String id:new String[]{"fpv_analog","action_camera","fpv_digital","uav_feed"}) {
                var shader=PortableLookShader.from(FilterGpuPipeline.source(id));
                var look=new LookSettings();look.add(id);
                var packed=shader.uniforms(id,look,65.25f);
                assertEquals(10,packed.getFloat(shader.floats().indexOf("LiveSpeed")*4),1e-6);
                assertEquals(65.25f,packed.getFloat(shader.floats().indexOf("Time")*4));
                for(String param:new String[]{"Hud","ClockOffset","ShowTelemetry","ShowHorizon"})
                    assertTrue(FilterCatalog.params(id).stream().anyMatch(p -> p.id().equals(param)),id+" "+param);
            }
        } finally {CameraTelemetry.update(null,null);}
    }
    @Test void uavReportsActualSensorGeometryAndPacksItForBothBackends() {
        var subject=new BoatSubjects.Sample(1000,50,-2000,0,0,0,0,0,null,null);
        var camera=new BoatCameraModel.Shot(1030,91.1664,-2000,90,20,0,8);
        CameraTelemetry.update(subject,camera);
        try {
            assertEquals(50,CameraTelemetry.value("LiveRange"),1e-4);
            assertEquals(91.1664,CameraTelemetry.value("LiveSensorAltitude"),1e-4);
            assertEquals(270,CameraTelemetry.value("LiveAzimuth"));
            assertEquals(1,CameraTelemetry.value("LiveTracking"),"Stationary subjects can be tracked");
            var shader=PortableLookShader.from(FilterGpuPipeline.source("uav_feed"));
            var look=new LookSettings();look.add("uav_feed");
            var packed=shader.uniforms("uav_feed",look,10);
            for(String name:new String[]{"LiveRange","LiveFov","LiveAzimuth","LiveSensorAltitude","LiveTracking"})
                assertEquals(CameraTelemetry.value(name),packed.getFloat(shader.floats().indexOf(name)*4));
        } finally {CameraTelemetry.update(null,null);}
        assertEquals(0,CameraTelemetry.value("LiveTracking"));
        assertEquals(0,CameraTelemetry.value("LiveRange"));
    }
}
