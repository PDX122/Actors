package pdx.dev.fbactors.camera;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CameraPreviewCaptureTest {
    private static final class Sink implements VertexConsumer {
        int vertices;
        public VertexConsumer addVertex(float x,float y,float z) { vertices++;return this; }
        public VertexConsumer setColor(int r,int g,int b,int a) { return this; }
        public VertexConsumer setColor(int argb) { return this; }
        public VertexConsumer setUv(float u,float v) { return this; }
        public VertexConsumer setUv1(int u,int v) { return this; }
        public VertexConsumer setUv2(int u,int v) { return this; }
        public VertexConsumer setUv3(float u,float v) { return this; }
        public VertexConsumer setLineWidth(float width) { return this; }
        public VertexConsumer setNormal(float x,float y,float z) { return this; }
    }
    @Test void capturePreservesNativeUvsColorsAndMaterialsAndRemovesTheGuiTransform() {
        var sink=new Sink();var result=new AtomicReference<CameraPreviewCapture.Mesh>();
        assertSame(sink,CameraPreviewCapture.wrap(new PoseStack(),sink));
        CameraPreviewCapture.render(result::set,()->{
            var pose=new PoseStack();pose.translate(5,3,2);
            CameraPreviewCapture.material("minecraft:textures/entity/player/wide/steve.png");
            CameraPreviewCapture.wrap(pose,sink).addVertex(6,4,2).setColor(17,34,51,255).setUv(.25f,.5f).setNormal(0,1,0);
            return null;
        });
        assertEquals(1,sink.vertices);
        var v=result.get().vertices().getFirst();
        assertEquals(1,v.x(),1e-6);assertEquals(.5f,v.y(),1e-6);assertEquals(0,v.z(),1e-6);
        assertEquals(.25f,v.u());assertEquals(.5f,v.v());assertEquals(0xff332211,v.color());
        assertEquals("minecraft:textures/entity/player/wide/steve.png",v.texture());
        assertSame(sink,CameraPreviewCapture.wrap(new PoseStack(),sink));
    }
    @Test void failedPreviewsNeverLeaveCaptureEnabledForTheWorldRenderer() {
        var sink=new Sink();
        assertThrows(IllegalStateException.class,()->CameraPreviewCapture.render(mesh->{},()->{throw new IllegalStateException("test");}));
        assertSame(sink,CameraPreviewCapture.wrap(new PoseStack(),sink));
    }
    @Test void packAnimationClockFollowsThePreviewAndNeverLeaksIntoTheWorld() {
        java.util.function.Consumer<CameraPreviewCapture.Mesh> output=mesh->{};
        assertTrue(Float.isNaN(CameraPreviewCapture.animationFrameTime()));
        CameraPreviewCapture.render(output,20,()->{assertEquals(.05f,CameraPreviewCapture.animationFrameTime(),1e-6);return null;});
        CameraPreviewCapture.render(output,22,()->{assertEquals(.1f,CameraPreviewCapture.animationFrameTime(),1e-6);return null;});
        assertThrows(IllegalStateException.class,()->CameraPreviewCapture.render(output,1,()->{
            assertEquals(.05f,CameraPreviewCapture.animationFrameTime(),1e-6);throw new IllegalStateException("test");
        }));
        assertTrue(Float.isNaN(CameraPreviewCapture.animationFrameTime()));
    }
    @Test void nativeEntityScaleAndBodyRotationSurviveGuiRemoval() {
        var result=new AtomicReference<CameraPreviewCapture.Mesh>();
        CameraPreviewCapture.render(result::set,()->{
            var pose=new PoseStack();pose.translate(5,3,2);CameraPreviewCapture.reference(pose);
            pose.scale(-.9375f,-.9375f,.9375f);pose.translate(0,-1.501,0);
            var p=pose.last().pose().transformPosition(new org.joml.Vector3f(.2f,.75f,0));
            CameraPreviewCapture.wrap(pose,new Sink()).addVertex(p.x,p.y,p.z).setUv(0,0).setNormal(0,-1,0);
            return null;
        });
        assertTrue(result.get().nativeTransform());var p=result.get().vertices().getFirst();
        assertEquals(.1875,p.x(),1e-5);assertEquals(.751*.9375,p.y(),1e-5);
    }
    @Test void riderStrokeChangesWithTimeAndRestoresThePackPoseEvenAfterFailure() {
        var arm=new net.minecraft.client.model.geom.ModelPart(java.util.List.of(),java.util.Map.of());
        var root=new net.minecraft.client.model.geom.ModelPart(java.util.List.of(),java.util.Map.of("left_arm",arm));
        arm.xRot=.2f;arm.yRot=.3f;arm.zRot=.4f;
        float[] angles=new float[2];
        CameraPreviewCapture.render(mesh->{},20,()->{
            CameraPreviewCapture.rowing(0);CameraPreviewCapture.rowingModel(root);
            CameraPreviewCapture.arm(arm,()->angles[0]=arm.xRot);
            assertEquals(.2f,arm.xRot);
            CameraPreviewCapture.rowing(4);CameraPreviewCapture.rowingModel(root);
            assertThrows(IllegalStateException.class,()->CameraPreviewCapture.arm(arm,()->{
                angles[1]=arm.xRot;throw new IllegalStateException("test");
            }));
            return null;
        });
        assertNotEquals(angles[0],angles[1]);
        assertEquals(.2f,arm.xRot);assertEquals(.3f,arm.yRot);assertEquals(.4f,arm.zRot);
        CameraPreviewCapture.arm(arm,()->assertEquals(.2f,arm.xRot));
    }
    @Test void bodyAndEquipmentBuffersKeepTheirOwnMaterials() {
        var result=new AtomicReference<CameraPreviewCapture.Mesh>();
        CameraPreviewCapture.render(result::set,()->{
            CameraPreviewCapture.reference(new PoseStack());
            var body=new Object();var saddle=new Object();
            CameraPreviewCapture.material(body,"body.png");
            var a=CameraPreviewCapture.buffer(new Sink(),body);
            CameraPreviewCapture.material(saddle,"saddle.png");CameraPreviewCapture.material("saddle.png");
            var b=CameraPreviewCapture.buffer(new Sink(),saddle);
            a.addVertex(0,0,0).setNormal(0,1,0);b.addVertex(0,0,0).setNormal(0,1,0);
            return null;
        });
        assertEquals("body.png",result.get().vertices().get(0).texture());
        assertEquals("saddle.png",result.get().vertices().get(1).texture());
    }
}
