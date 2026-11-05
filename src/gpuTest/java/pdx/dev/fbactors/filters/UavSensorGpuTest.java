package pdx.dev.fbactors.filters;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.*;
import pdx.dev.fbactors.camera.BoatCameraModel;
import pdx.dev.fbactors.camera.BoatSubjects;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class UavSensorGpuTest {
    private static void tune(LookSettings look,String name,float value) {
        look.parameter("uav_feed",FilterCatalog.params("uav_feed").stream().filter(p -> p.id().equals(name)).findFirst().orElseThrow(),value);
    }
    @Test void sensorPolarityHudAndZeroStrengthRenderCorrectly() throws Exception {
        assumeTrue("1".equals(System.getenv("FBACTORS_GL_TESTS")));
        assertTrue(GLFW.glfwInit());
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR,3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR,3);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE,GLFW.GLFW_OPENGL_CORE_PROFILE);
        long window=GLFW.glfwCreateWindow(960,540,"EO/IR verification",0,0);
        assertNotEquals(0,window);
        GLFW.glfwMakeContextCurrent(window); GL.createCapabilities();
        int texture=GL11.glGenTextures(),fbo=GL30.glGenFramebuffers();
        int w=960,h=540;
        try(var pipeline=new FilterGpuPipeline();var stream=getClass().getResourceAsStream("/assets/fbactors/filters/demo.jpg")) {
            var photo=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);
            var g=photo.createGraphics();g.drawImage(ImageIO.read(stream),0,0,w,h,null);g.dispose();
            var pixels=BufferUtils.createFloatBuffer(w*h*4);
            for(int y=h-1;y>=0;y--) for(int x=0;x<w;x++) {
                int rgb=photo.getRGB(x,y);
                pixels.put((rgb>>16&255)/255f).put((rgb>>8&255)/255f).put((rgb&255)/255f).put(.75f);
            }
            pixels.flip();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_RGBA32F,w,h,0,GL11.GL_RGBA,GL11.GL_FLOAT,pixels);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,fbo);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,texture,0);
            assertEquals(GL30.GL_FRAMEBUFFER_COMPLETE,GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER));
            CameraTelemetry.update(new BoatSubjects.Sample(125,64,-220,0,0,0,0,0,null,null),
                new BoatCameraModel.Shot(125,134,-310,0,38,0,8));
            var look=new LookSettings();look.add("uav_feed");
            tune(look,"Hud",0);tune(look,"SensorNoise",0);
            FloatBuffer white=render(pipeline,texture,w,h,pixels,look);
            tune(look,"SensorMode",2);
            FloatBuffer black=render(pipeline,texture,w,h,pixels,look);
            for(int i=0;i<w*h;i++) {
                for(int c=0;c<3;c++) assertEquals(1,white.get(i*4+c)+black.get(i*4+c),.003,"Polarity must invert the same sensor signal");
                assertEquals(white.get(i*4),white.get(i*4+1),.001);
                assertEquals(.75f,black.get(i*4+3),.002);
            }
            tune(look,"Amount",0);
            var off=render(pipeline,texture,w,h,pixels,look);
            for(int i=0;i<pixels.limit();i++) assertEquals(pixels.get(i),off.get(i),.001);
            tune(look,"Amount",1);tune(look,"Hud",1);
            var blank=BufferUtils.createFloatBuffer(w*h*4);
            for(int i=0;i<w*h;i++) blank.put(0).put(0).put(0).put(.75f);
            blank.flip(); tune(look,"SensorMode",0);
            var lettering=render(pipeline,texture,w,h,blank,look);
            int softenedEdges=0;
            for(int y=h-100;y<h-10;y++) for(int x=10;x<230;x++) {
                float value=lettering.get((y*w+x)*4);
                if(value>.03 && value<.85) softenedEdges++;
            }
            assertTrue(softenedEdges>25,"Sensor lettering must have antialiased vector edges instead of a bitmap grid");
            var montage=new BufferedImage(w,(h+24)*3,BufferedImage.TYPE_INT_RGB);
            for(int mode=0;mode<3;mode++) {
                tune(look,"SensorMode",mode);
                var rendered=render(pipeline,texture,w,h,pixels,look);
                var image=new BufferedImage(w,h,BufferedImage.TYPE_INT_RGB);
                for(int y=0;y<h;y++) for(int x=0;x<w;x++) {
                    int at=((h-1-y)*w+x)*4,rgb=0;
                    for(int c=0;c<3;c++) {
                        float value=rendered.get(at+c);assertTrue(Float.isFinite(value));
                        rgb=rgb<<8|Math.round(Math.clamp(value,0,1)*255);
                    }
                    image.setRGB(x,y,rgb);
                }
                g=montage.createGraphics();g.drawImage(image,0,mode*(h+24),null);g.setColor(Color.WHITE);
                g.drawString(new String[]{"EO","IR WHITE HOT","IR BLACK HOT"}[mode],12,mode*(h+24)+h+17);g.dispose();
            }
            assertEquals(GL11.GL_NO_ERROR,GL11.glGetError());
            Path root=Path.of("").toAbsolutePath();while(!Files.exists(root.resolve("settings.gradle"))) root=root.getParent();
            ImageIO.write(montage,"png",root.resolve("build/uav-eoir-preview.png").toFile());
        } finally {
            CameraTelemetry.update(null,null);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,0);GL30.glDeleteFramebuffers(fbo);GL11.glDeleteTextures(texture);
            GL.setCapabilities(null);GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();
        }
    }
    private static FloatBuffer render(FilterGpuPipeline pipeline,int texture,int w,int h,FloatBuffer source,LookSettings look) {
        GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);source.rewind();
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,w,h,GL11.GL_RGBA,GL11.GL_FLOAT,source);
        pipeline.render(texture,w,h,look,872);
        var result=BufferUtils.createFloatBuffer(w*h*4);GL11.glReadPixels(0,0,w,h,GL11.GL_RGBA,GL11.GL_FLOAT,result);
        return result;
    }
}
