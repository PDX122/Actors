package pdx.dev.fbactors.events;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import net.minecraft.world.phys.Vec3;
import pdx.dev.fbactors.filters.FilterGpuPipeline;

class EventGpuTest {
    @Test void rendersFlightAndExplosionReproduciblyWithWorldOcclusionAndNoDepthWrites() throws Exception {
        assertTrue(GLFW.glfwInit());GLFW.glfwDefaultWindowHints();GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,3);GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,3);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE,GLFW.GLFW_OPENGL_CORE_PROFILE);
        long window=GLFW.glfwCreateWindow(640,360,"Events verification",0,0);assertNotEquals(0,window);
        GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();
        int w=640,h=360;int color=GL11.glGenTextures(),depth=GL11.glGenTextures(),fb=GL30.glGenFramebuffers();
        var source=BufferUtils.createFloatBuffer(w*h*4);
        for(int y=0;y<h;y++)for(int x=0;x<w;x++) {
            float sky=y/(float)h;source.put(.09f+.1f*sky).put(.13f+.12f*sky).put(.18f+.15f*sky).put(.65f);
        }
        source.flip();GL11.glBindTexture(GL11.GL_TEXTURE_2D,color);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_RGBA16F,w,h,0,GL11.GL_RGBA,GL11.GL_FLOAT,source);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
        var depths=BufferUtils.createFloatBuffer(w*h);for(int i=0;i<w*h;i++)depths.put(1);depths.flip();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D,depth);GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_DEPTH_COMPONENT32F,w,h,0,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,depths);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_NEAREST);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_NEAREST);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fb);GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,color,0);
        var clip=new EventClip();clip.launch=new EventClip.Point(0,0,0);clip.impact=new EventClip.Point(70,0,0);
        var rotation=new Matrix4f().lookAt(85,55,125,35,23,0,0,1,0).getUnnormalizedRotation(new Quaternionf()).conjugate();
        var camera=new EventCamera(new Vec3(85,55,125),rotation,new Matrix4f().perspective((float)Math.toRadians(55),w/(float)h,.05f,1024));
        var montage=new BufferedImage(w*3,(h+24)*2,BufferedImage.TYPE_INT_RGB);
        var phases=new double[]{.015,.07,.19,.69,.725,.88};var labels=new String[]{"IGNITION","BOOST","STAGE SEPARATION / COAST","RE-ENTRY","IMPACT FLASH","MUSHROOM CLOUD / SHOCKWAVE"};
        try(var pipeline=new FilterGpuPipeline()) {
            for(int frame=0;frame<phases.length;frame++) {
                source.rewind();GL11.glBindTexture(GL11.GL_TEXTURE_2D,color);GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,w,h,GL11.GL_RGBA,GL11.GL_FLOAT,source);
                GL11.glEnable(GL11.GL_SCISSOR_TEST);GL11.glScissor(1,2,3,4);GL11.glColorMask(true,false,true,false);
                var view=camera;
                if(frame<4 && frame>0) {
                    var shot=EventCameraModel.shot(clip,phases[frame]*clip.end,w/(double)h,null);
                    var orientation=new Quaternionf().rotationYXZ((float)Math.toRadians(-shot.yaw()),(float)Math.toRadians(-shot.pitch()),0);
                    // lookAt avoids relying on Minecraft yaw conventions in this standalone fixture.
                    double yaw=Math.toRadians(shot.yaw()),pitch=Math.toRadians(shot.pitch());
                    orientation=new Matrix4f().lookAt((float)shot.x(),(float)shot.y(),(float)shot.z(),
                        (float)(shot.x()-Math.sin(yaw)*Math.cos(pitch)),(float)(shot.y()-Math.sin(pitch)),(float)(shot.z()+Math.cos(yaw)*Math.cos(pitch)),0,1,0).getUnnormalizedRotation(new Quaternionf()).conjugate();
                    view=new EventCamera(new Vec3(shot.x(),shot.y(),shot.z()),orientation,camera.projection());
                }
                var uniforms=EventRenderer.uniforms(clip,phases[frame]*clip.end,view);
                pipeline.worldEvent(color,depth,w,h,uniforms);
                assertTrue(GL11.glIsEnabled(GL11.GL_SCISSOR_TEST));assertEquals(fb,GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING));
                assertEquals(GL11.GL_NO_ERROR,GL11.glGetError());
                var pixels=BufferUtils.createFloatBuffer(w*h*4);GL11.glReadPixels(0,0,w,h,GL11.GL_RGBA,GL11.GL_FLOAT,pixels);
                int changed=0;
                for(int y=0;y<h;y++)for(int x=0;x<w;x++) {
                    int i=(y*w+x)*4;float r=pixels.get(i),g=pixels.get(i+1),b=pixels.get(i+2);
                    assertTrue(Float.isFinite(r) && Float.isFinite(g) && Float.isFinite(b));assertEquals(.65,pixels.get(i+3),.002);
                    if(Math.abs(r-source.get(i))+Math.abs(g-source.get(i+1))+Math.abs(b-source.get(i+2))>.025)changed++;
                    montage.setRGB(frame%3*w+x,frame/3*(h+24)+h-1-y,(Math.min(255,(int)(r*255))<<16)|(Math.min(255,(int)(g*255))<<8)|Math.min(255,(int)(b*255)));
                }
                assertTrue(changed>15,"No visible event at phase "+phases[frame]);
                var graphics=montage.createGraphics();graphics.setColor(java.awt.Color.WHITE);graphics.drawString(labels[frame],frame%3*w+10,frame/3*(h+24)+h+17);graphics.dispose();
                // A second identical sample produces identical pixels (no accumulated particle state).
                source.rewind();GL11.glBindTexture(GL11.GL_TEXTURE_2D,color);GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,w,h,GL11.GL_RGBA,GL11.GL_FLOAT,source);
                pipeline.worldEvent(color,depth,w,h,uniforms);var repeated=BufferUtils.createFloatBuffer(w*h*4);GL11.glReadPixels(0,0,w,h,GL11.GL_RGBA,GL11.GL_FLOAT,repeated);
                assertEquals(pixels,repeated);
            }
            verifyPreviews(pipeline);
            verifyGallery(pipeline);
            verifyBombs(pipeline);
            verifyDistantModels(pipeline);
            verifyEndings(pipeline);
            verifyNuclearWorld(pipeline,color,depth,fb,w,h,source);
            verifyDistantDepthBypass(pipeline,color,depth,fb,w,h,source);
            verifyNaturalWorld(pipeline,color,depth,fb,w,h,source);
            // Near-plane terrain occludes every part of the distant event.
            depths.rewind();for(int i=0;i<w*h;i++)depths.put(0);depths.flip();GL11.glBindTexture(GL11.GL_TEXTURE_2D,depth);
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,w,h,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,depths);
            source.rewind();GL11.glBindTexture(GL11.GL_TEXTURE_2D,color);GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,w,h,GL11.GL_RGBA,GL11.GL_FLOAT,source);
            pipeline.worldEvent(color,depth,w,h,EventRenderer.uniforms(clip,clip.end*.80,camera));
            var occluded=BufferUtils.createFloatBuffer(w*h*4);GL11.glReadPixels(0,0,w,h,GL11.GL_RGBA,GL11.GL_FLOAT,occluded);
            for(int i=0;i<w*h*4;i++)assertEquals(source.get(i),occluded.get(i),.001);
            var unchanged=BufferUtils.createFloatBuffer(w*h);GL11.glBindTexture(GL11.GL_TEXTURE_2D,depth);GL11.glGetTexImage(GL11.GL_TEXTURE_2D,0,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,unchanged);
            for(int i=0;i<w*h;i++)assertEquals(0,unchanged.get(i));
            var output=Path.of(System.getProperty("user.dir"),"../../build/events-preview.png").normalize();javax.imageio.ImageIO.write(montage,"png",output.toFile());
        } finally {GL30.glDeleteFramebuffers(fb);GL11.glDeleteTextures(color);GL11.glDeleteTextures(depth);GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();}
    }
    private static void verifyPreviews(FilterGpuPipeline pipeline) throws Exception {
        int w=EventPreview.WIDTH,h=EventPreview.HEIGHT;
        var montage=new BufferedImage(w*3,(h+22)*2,BufferedImage.TYPE_INT_RGB);
        double[] times={0,5.5,13.3333,28.6667,39,52.6667};
        java.nio.FloatBuffer previous=null;
        for(int frame=0;frame<times.length;frame++) {
            int texture=pipeline.eventPreview(w,h,EventPreview.uniforms(times[frame],true));
            var pixels=BufferUtils.createFloatBuffer(w*h*4);GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
            GL11.glGetTexImage(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA,GL11.GL_FLOAT,pixels);assertEquals(GL11.GL_NO_ERROR,GL11.glGetError());
            if(previous!=null)assertNotEquals(previous,pixels,"Sequence frames must move");previous=pixels;
            for(int y=0;y<h;y++)for(int x=0;x<w;x++) {
                int i=(y*w+x)*4;float r=pixels.get(i),g=pixels.get(i+1),b=pixels.get(i+2);
                assertTrue(Float.isFinite(r) && Float.isFinite(g) && Float.isFinite(b));assertEquals(1,pixels.get(i+3),.001);
                montage.setRGB(frame%3*w+x,frame/3*(h+22)+h-y-1,(Math.min(255,(int)(r*255))<<16)|(Math.min(255,(int)(g*255))<<8)|Math.min(255,(int)(b*255)));
            }
            var graphics=montage.createGraphics();graphics.setColor(java.awt.Color.WHITE);graphics.drawString(String.format(java.util.Locale.ROOT,"%.2f s",times[frame]),frame%3*w+8,frame/3*(h+22)+h+16);graphics.dispose();
        }
        var directory=Path.of(System.getProperty("user.dir"),"../../build").normalize();javax.imageio.ImageIO.write(montage,"png",directory.resolve("events-3d-preview.png").toFile());
        int mw=1024,mh=640;var model=new BufferedImage(mw,mh,BufferedImage.TYPE_INT_RGB);
        int texture=pipeline.eventPreview(mw,mh,EventPreview.uniforms(1.5,false));
        var pixels=BufferUtils.createFloatBuffer(mw*mh*4);GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);GL11.glGetTexImage(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA,GL11.GL_FLOAT,pixels);
        for(int y=0;y<mh;y++)for(int x=0;x<mw;x++) {
            int i=(y*mw+x)*4;model.setRGB(x,mh-y-1,((int)(pixels.get(i)*255)<<16)|((int)(pixels.get(i+1)*255)<<8)|(int)(pixels.get(i+2)*255));
        }
        javax.imageio.ImageIO.write(model,"png",directory.resolve("events-model-preview.png").toFile());
        int detailHeight=mh/2;var details=new BufferedImage(mw,detailHeight,BufferedImage.TYPE_INT_RGB);
        for(int detail=1;detail<=2;detail++) {
            int dw=mw/2;texture=pipeline.eventPreview(dw,detailHeight,EventPreview.uniforms(1.5,false,detail));
            var data=BufferUtils.createFloatBuffer(dw*detailHeight*4);GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);GL11.glGetTexImage(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA,GL11.GL_FLOAT,data);
            for(int y=0;y<detailHeight;y++)for(int x=0;x<dw;x++) {
                int i=(y*dw+x)*4;details.setRGB((detail-1)*dw+x,detailHeight-y-1,((int)(data.get(i)*255)<<16)|((int)(data.get(i+1)*255)<<8)|(int)(data.get(i+2)*255));
            }
        }
        javax.imageio.ImageIO.write(details,"png",directory.resolve("events-model-details.png").toFile());
        var before=BufferUtils.createFloatBuffer(mw*mh*4);before.put(pixels).flip();
        texture=pipeline.eventPreview(mw,mh,EventPreview.uniforms(5.5,false));GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);pixels.rewind();GL11.glGetTexImage(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA,GL11.GL_FLOAT,pixels);
        assertNotEquals(before,pixels,"Turntable must reveal different model surfaces");
    }
    private static void verifyGallery(FilterGpuPipeline pipeline) throws Exception {
        int w=512,h=320;
        var directory=Path.of(System.getProperty("user.dir"),"../../build/events-gallery").normalize();java.nio.file.Files.createDirectories(directory);
        for(var kind:EventKind.LIBRARY) {
            var values=EventPreview.uniforms(kind,1.5,false,0);
            int query=GL15.glGenQueries();GL15.glBeginQuery(GL33.GL_TIME_ELAPSED,query);
            int texture=pipeline.eventPreview(w,h,values);GL15.glEndQuery(GL33.GL_TIME_ELAPSED);
            double milliseconds=GL33.glGetQueryObjectui64(query,GL15.GL_QUERY_RESULT)/1e6;GL15.glDeleteQueries(query);
            System.out.println(kind+" gallery GPU ms: "+milliseconds);
            var data=BufferUtils.createFloatBuffer(w*h*4);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);GL11.glGetTexImage(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA,GL11.GL_FLOAT,data);
            var image=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);
            for(int y=0;y<h;y++)for(int x=0;x<w;x++) {
                int at=(y*w+x)*4;int rgb=0;
                for(int channel=0;channel<3;channel++) {float value=data.get(at+channel);assertTrue(Float.isFinite(value));rgb=(rgb<<8)|Math.min(255,Math.max(0,Math.round(value*255)));}
                assertEquals(1,data.get(at+3),.001);image.setRGB(x,h-y-1,rgb);
            }
            javax.imageio.ImageIO.write(image,"png",directory.resolve(kind.name().toLowerCase(java.util.Locale.ROOT)+".png").toFile());
            if(kind.natural()) {
                // Same camera and studio with the retired, invisible type is the reference.
                var reference=new java.util.HashMap<>(values);reference.put("Type",2f);
                texture=pipeline.eventPreview(w,h,reference);var background=BufferUtils.createFloatBuffer(w*h*4);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);GL11.glGetTexImage(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA,GL11.GL_FLOAT,background);
                int changed=0;for(int i=0;i<w*h*4;i+=4)if(Math.abs(data.get(i)-background.get(i))+Math.abs(data.get(i+1)-background.get(i+1))+Math.abs(data.get(i+2)-background.get(i+2))>.035)changed++;
                assertTrue(changed>40,kind+" must show actual visible 3D effects");
                texture=pipeline.eventPreview(w,h,values);var again=BufferUtils.createFloatBuffer(w*h*4);GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);GL11.glGetTexImage(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA,GL11.GL_FLOAT,again);
                assertEquals(data,again,kind+" must scrub deterministically");
            }
        }
    }
    private static void verifyNaturalWorld(FilterGpuPipeline pipeline,int color,int depth,int fb,int w,int h,java.nio.FloatBuffer source) {
        for(var kind:EventKind.LIBRARY)if(kind.natural()) {
            var values=EventPreview.uniforms(kind,1.5,false,0);values.put("Preview",0f);
            for(float depthValue:new float[]{1,0}) {
                var depths=BufferUtils.createFloatBuffer(w*h);while(depths.hasRemaining())depths.put(depthValue);depths.flip();
                GL11.glBindTexture(GL11.GL_TEXTURE_2D,depth);GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,w,h,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,depths);
                source.rewind();GL11.glBindTexture(GL11.GL_TEXTURE_2D,color);GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,w,h,GL11.GL_RGBA,GL11.GL_FLOAT,source);
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fb);pipeline.worldEvent(color,depth,w,h,values);
                var pixels=BufferUtils.createFloatBuffer(w*h*4);GL11.glReadPixels(0,0,w,h,GL11.GL_RGBA,GL11.GL_FLOAT,pixels);
                int changed=0;for(int i=0;i<w*h*4;i+=4) {
                    for(int j=0;j<3;j++)assertTrue(Float.isFinite(pixels.get(i+j)));assertEquals(.65,pixels.get(i+3),.002);
                    if(Math.abs(pixels.get(i)-source.get(i))+Math.abs(pixels.get(i+1)-source.get(i+1))+Math.abs(pixels.get(i+2)-source.get(i+2))>.025)changed++;
                }
                if(depthValue==0)assertEquals(0,changed,kind+" must be occluded by nearby terrain");else assertTrue(changed>15,kind+" must render in the world");
            }
        }
    }

    private static void verifyBombs(FilterGpuPipeline pipeline) throws Exception {
        int w=640,h=400;var image=new BufferedImage(w*3,(h+22)*3,BufferedImage.TYPE_INT_RGB);
        EventKind[] kinds={EventKind.NUCLEAR,EventKind.NUCLEAR,EventKind.NUCLEAR,EventKind.NUCLEAR,EventKind.NUCLEAR,EventKind.CARPET,EventKind.CARPET,EventKind.CARPET,EventKind.CARPET};
        double[] times={1.5,3.0,6.25,12,23,1.5,4.5,8,15};
        for(int frame=0;frame<times.length;frame++) {
            boolean sequence=frame!=0 && frame!=5;
            int texture=pipeline.eventPreview(w,h,EventPreview.uniforms(kinds[frame],times[frame],sequence,0));
            var data=BufferUtils.createFloatBuffer(w*h*4);GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);GL11.glGetTexImage(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA,GL11.GL_FLOAT,data);
            assertEquals(GL11.GL_NO_ERROR,GL11.glGetError());int dark=0;
            for(int y=0;y<h;y++)for(int x=0;x<w;x++) {
                int i=(y*w+x)*4;float r=data.get(i),g=data.get(i+1),b=data.get(i+2);
                assertTrue(Float.isFinite(r) && Float.isFinite(g) && Float.isFinite(b));assertEquals(1,data.get(i+3),.001);
                if(r<.30 && g<.30 && b<.30)dark++;
                image.setRGB(frame%3*w+x,frame/3*(h+22)+h-y-1,(Math.min(255,(int)(Math.max(0,r)*255))<<16)|(Math.min(255,(int)(Math.max(0,g)*255))<<8)|Math.min(255,(int)(Math.max(0,b)*255)));
            }
            if(frame==0)assertTrue(dark>1000,"Imported nuclear bomb must show actual textured geometry");
            var graphics=image.createGraphics();graphics.setColor(java.awt.Color.WHITE);graphics.drawString(kinds[frame].label+" / "+(sequence?times[frame]+" s":"model"),frame%3*w+8,frame/3*(h+22)+h+16);graphics.dispose();
        }
        javax.imageio.ImageIO.write(image,"png",Path.of(System.getProperty("user.dir"),"../../build/bomb-events-preview.png").normalize().toFile());
        assertTrue(BombModel.info(BombModel.ICBM_ROOT).triangles()<6000,"Imported ICBM exceeds mesh budget");
        assertTrue(BombModel.info().triangles()<=8000,"Bomb triangle budget regressed");
        assertFalse(EventAudio.crossed(100,100,100));assertFalse(EventAudio.crossed(100,90,95));assertFalse(EventAudio.crossed(0,100,50));assertTrue(EventAudio.crossed(99,101,100));
        var active=EventPlacement.class.getDeclaredField("active");active.setAccessible(true);active.setBoolean(null,true);
        assertTrue(pdx.dev.fbactors.input.Hotkey.endRunOnEscape(pdx.dev.fbactors.compat.Compat.key("key.keyboard.escape")),"Placement Escape must consume the key before Minecraft can open its menu");
        assertFalse(EventPlacement.active());
        EventPreview.select(EventKind.NUCLEAR); // Safe before an ImGui context exists during client startup.
    }

    private static void verifyNuclearWorld(FilterGpuPipeline pipeline,int color,int depth,int fb,int w,int h,java.nio.FloatBuffer source) {
        var times=new double[]{3,10,23};
        for(double time:times) {
            var values=EventPreview.uniforms(EventKind.NUCLEAR,time,true,0);values.put("Preview",0f);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fb);
            source.rewind();GL11.glBindTexture(GL11.GL_TEXTURE_2D,color);GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,w,h,GL11.GL_RGBA,GL11.GL_FLOAT,source);
            int query=GL15.glGenQueries();GL15.glBeginQuery(GL33.GL_TIME_ELAPSED,query);
            pipeline.worldEvent(color,depth,w,h,values);
            GL15.glEndQuery(GL33.GL_TIME_ELAPSED);
            double ms=GL33.glGetQueryObjectui64(query,GL15.GL_QUERY_RESULT)/1e6;GL15.glDeleteQueries(query);
            System.out.println("Detailed nuclear world / "+time+"s / "+w+"x"+h+": "+ms+" GPU ms");
            var pixels=BufferUtils.createFloatBuffer(w*h*4);GL11.glReadPixels(0,0,w,h,GL11.GL_RGBA,GL11.GL_FLOAT,pixels);
            int changed=0;for(int i=0;i<w*h;i++) {
                assertEquals(source.get(i*4+3),pixels.get(i*4+3),.002,"Full-resolution scene alpha must survive resolve");
                for(int channel=0;channel<3;channel++)assertTrue(Float.isFinite(pixels.get(i*4+channel)));
                if(Math.abs(source.get(i*4)-pixels.get(i*4))>.02)changed++;
            }
            assertTrue(changed>50,"Detailed nuclear event disappeared");assertEquals(GL11.GL_NO_ERROR,GL11.glGetError());
        }
    }
    private static void verifyDistantDepthBypass(FilterGpuPipeline pipeline,int color,int depth,int fb,int w,int h,java.nio.FloatBuffer source) {
        var base=EventPreview.uniforms(EventKind.NUCLEAR,12,true,0);base.put("Preview",0f);
        var ro=new org.joml.Vector3f(base.get("CameraX"),base.get("CameraY"),base.get("CameraZ"));
        var forward=new org.joml.Vector3f(base.get("ForwardX"),base.get("ForwardY"),base.get("ForwardZ"));
        var right=new org.joml.Vector3f(base.get("RightX"),base.get("RightY"),base.get("RightZ"));
        var up=new org.joml.Vector3f(base.get("UpX"),base.get("UpY"),base.get("UpZ"));
        int[] changed=new int[4];
        for(int mode=0;mode<4;mode++) {
            var values=new java.util.HashMap<>(base);values.put("CloudBase",192f);values.put("CloudThickness",mode==1?5f:0f);values.put("TerrainRange",mode>=2?16f:0f);
            var field=BufferUtils.createFloatBuffer(w*h);
            for(int y=0;y<h;y++)for(int x=0;x<w;x++) {
                var ray=new org.joml.Vector3f(forward).add(new org.joml.Vector3f(right).mul(((x+.5f)/w*2-1)*base.get("ProjectionX")))
                    .add(new org.joml.Vector3f(up).mul(((y+.5f)/h*2-1)*base.get("ProjectionY"))).normalize();
                double view=mode>=2?(mode==3?.05:50):(192-ro.y)/ray.y*ray.dot(forward);
                float ndc=(float)(base.get("DepthB")/view-base.get("DepthA"));
                field.put(view>0?(ndc+1)*.5f:1);
            }
            field.flip();GL11.glBindTexture(GL11.GL_TEXTURE_2D,depth);GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,w,h,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,field);
            source.rewind();GL11.glBindTexture(GL11.GL_TEXTURE_2D,color);GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,w,h,GL11.GL_RGBA,GL11.GL_FLOAT,source);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fb);pipeline.worldEvent(color,depth,w,h,values);
            var pixels=BufferUtils.createFloatBuffer(w*h*4);GL11.glReadPixels(0,0,w,h,GL11.GL_RGBA,GL11.GL_FLOAT,pixels);
            for(int i=0;i<w*h;i++)if(Math.abs(pixels.get(i*4)-source.get(i*4))>.02)changed[mode]++;
        }
        assertTrue(changed[1]>changed[0]+50,"Minecraft's distant cloud plane still cuts the blast");
        assertTrue(changed[2]>50,"Distant terrain/far-depth bypass failed");
        assertEquals(0,changed[3],"Bypass must preserve foreground wall occlusion");
        var clear=BufferUtils.createFloatBuffer(w*h);for(int i=0;i<w*h;i++)clear.put(1);clear.flip();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D,depth);GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,w,h,GL11.GL_DEPTH_COMPONENT,GL11.GL_FLOAT,clear);
    }
    private static void verifyDistantModels(FilterGpuPipeline pipeline) throws Exception {
        int w=512,h=320;var image=new BufferedImage(w*3,(h+22)*2,BufferedImage.TYPE_INT_RGB);
        for(int row=0;row<2;row++)for(int column=0;column<3;column++) {
            var c=new EventClip();c.kind=row==0?EventKind.NUCLEAR:EventKind.ICBM;c.launch=new EventClip.Point(0,0,0);c.impact=new EventClip.Point(95,0,0);
            double distance=(row==0?new double[]{40,160,480}:new double[]{120,360,900})[column],focus=row==0?5:15;
            float x=(float)(Math.sin(.7)*distance),y=(float)(focus+distance*.08),z=(float)(Math.cos(.7)*distance);
            var rotation=new Matrix4f().lookAt(x,y,z,0,(float)focus,0,0,1,0).getUnnormalizedRotation(new Quaternionf()).conjugate();
            var camera=new EventCamera(new Vec3(x,y,z),rotation,new Matrix4f().perspective((float)Math.toRadians(43),w/(float)h,.05f,100000));
            var values=EventRenderer.uniforms(c,0,camera);values.put("Preview",1f);
            assertEquals(0,values.get("ReducedResolution"),.001,"Small distant objects must not enter the reduced-resolution path");
            int texture=pipeline.eventPreview(w,h,values);var pixels=BufferUtils.createFloatBuffer(w*h*4);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);GL11.glGetTexImage(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA,GL11.GL_FLOAT,pixels);
            int dark=0,visible=0;for(int py=0;py<h;py++)for(int px=0;px<w;px++) {
                int i=(py*w+px)*4;float r=pixels.get(i),g=pixels.get(i+1),b=pixels.get(i+2);
                assertTrue(Float.isFinite(r+g+b));if(r<.3 && g<.3 && b<.3)dark++;if(r<.5 && g<.5 && b<.5)visible++;
                image.setRGB(column*w+px,row*(h+22)+h-1-py,(Math.min(255,(int)(r*255))<<16)|(Math.min(255,(int)(g*255))<<8)|Math.min(255,(int)(b*255)));
            }
            assertTrue(visible>1,"Model vanished at distance "+distance);
            var graphics=image.createGraphics();graphics.setColor(java.awt.Color.WHITE);graphics.drawString(c.kind.label+" / "+distance+" blocks",column*w+8,row*(h+22)+h+16);graphics.dispose();
        }
        javax.imageio.ImageIO.write(image,"png",Path.of(System.getProperty("user.dir"),"../../build/events-distance-preview.png").normalize().toFile());
        assertEquals(13,BombModel.levels(BombModel.ICBM_ROOT,"albedo.png"));
        assertEquals(7,BombModel.levels(BombModel.ICBM_ROOT,"normal.png"));
        assertEquals(11,BombModel.levels(BombModel.ROOT,"albedo.png"));
        try(var stream=BombModel.resource(EventEarth.ROOT,"surface_3.png")) {
            var atlas=javax.imageio.ImageIO.read(stream);
            int land=atlas.getRGB((int)((-101+180)/360.*atlas.getWidth()),(int)((90-39)/180.*atlas.getHeight()));
            assertTrue(((land>>16)&255)>50 && ((land>>8)&255)>50,"Ocean-mask mip filtering erased the land RGB channels");
        }
    }
    private static void verifyEndings(FilterGpuPipeline pipeline) throws Exception {
        int w=640,h=400;var c=new EventClip();c.eventCam=true;c.ending=3;
        int texture=pipeline.eventPreview(w,h,EventPreview.uniforms(c,c.end/20.*.82,true,0));
        var data=BufferUtils.createFloatBuffer(w*h*4);GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);GL11.glGetTexImage(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA,GL11.GL_FLOAT,data);
        for(int i=0;i<w*h;i++) {assertEquals(0,data.get(i*4),.001);assertEquals(0,data.get(i*4+1),.001);assertEquals(0,data.get(i*4+2),.001);}
        c.ending=1;c.normalize();assertEquals(0,c.ending,"Retired map endings must become world blasts");
    }

}
