package pdx.dev.fbactors.camera;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.Entity;
import pdx.dev.fbactors.compat.Compat;
import java.util.concurrent.atomic.AtomicReference;

/** Vanilla boat geometry and paddle strokes, without an extra render target. */
public final class CameraPreviewBoat {
    public final Entity entity;
    private final ModelPart root;
    public CameraPreviewBoat() {
        entity=Compat.cameraPreviewBoatEntity();root=Compat.cameraPreviewBoatRoot();
        if(entity==null) throw new IllegalStateException("Could not create preview boat");
        entity.setNoGravity(true);entity.setSilent(true);entity.setYRot(180);entity.yRotO=180;
        if(root.hasChild("water_patch")) root.getChild("water_patch").visible=false;
        if(entity instanceof pdx.dev.fbactors.actor.BoatPhysics.Boat boat) boat.fbactors$paddles(true,true);
    }
    public CameraPreviewCapture.Mesh sample(double seconds) {
        entity.setPos(0,64,-seconds*8);entity.tickCount=(int)(seconds*20);
        float stroke=(float)(seconds*20*Math.PI/8);
        paddle(root.getChild("left_paddle"),stroke,false);
        paddle(root.getChild("right_paddle"),stroke,true);
        return capture();
    }
    private static void paddle(ModelPart part,float stroke,boolean right) {
        // Minecraft's AbstractBoatModel.animatePaddle, sampled at fractional preview time.
        part.xRot=(float)(-Math.PI/3+(Math.sin(-stroke)+1)*Math.PI/8);
        part.yRot=(float)(Math.sin(-stroke+1)*Math.PI/4);
        if(right) part.yRot=(float)Math.PI-part.yRot;
    }
    private CameraPreviewCapture.Mesh capture() {
        var result=new AtomicReference<CameraPreviewCapture.Mesh>();
        CameraPreviewCapture.render(result::set,()->{
            var pose=new PoseStack();CameraPreviewCapture.reference(pose);
            CameraPreviewCapture.material(pdx.dev.fbactors.actor.BoatTextures.preview("minecraft:textures/entity/boat/oak.png"));
            CameraPreviewCapture.model(root);
            pose.translate(0,.375,0);pose.scale(-1,-1,1);
            pose.last().pose().rotateY((float)Math.PI/2);pose.last().normal().rotateY((float)Math.PI/2);
            root.render(pose,CameraPreviewCapture.wrap(pose,new Sink()),0xf000f0,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
            return null;
        });
        return result.get();
    }
    private static final class Sink implements VertexConsumer {
        public VertexConsumer addVertex(float x,float y,float z) { return this; }
        public VertexConsumer setColor(int r,int g,int b,int a) { return this; }
        public VertexConsumer setColor(int color) { return this; }
        public VertexConsumer setUv(float u,float v) { return this; }
        public VertexConsumer setUv1(int u,int v) { return this; }
        public VertexConsumer setUv2(int u,int v) { return this; }
        public VertexConsumer setUv3(float u,float v) { return this; }
        public VertexConsumer setNormal(float x,float y,float z) { return this; }
        public VertexConsumer setLineWidth(float width) { return this; }
    }
}
