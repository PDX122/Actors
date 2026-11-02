package pdx.dev.fbactors.filters;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PixelSortGpuTest {
    @Test void sortsRealPixelsOnBothAxesAndHonorsThresholdAndReplayTime() {
        assumeTrue("1".equals(System.getenv("FBACTORS_GL_TESTS")));
        assertTrue(GLFW.glfwInit());
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        long window = GLFW.glfwCreateWindow(64,64,"Pixel sorting verification",0,0);
        assertNotEquals(0,window);
        GLFW.glfwMakeContextCurrent(window); GL.createCapabilities();
        int texture=GL11.glGenTextures(), framebuffer=GL30.glGenFramebuffers();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_RGBA32F,64,64,0,GL11.GL_RGBA,GL11.GL_FLOAT,(FloatBuffer)null);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,framebuffer);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,texture,0);
        GL11.glViewport(0,0,64,64);
        try (var pipeline=new FilterGpuPipeline()) {
            for (boolean vertical : new boolean[]{false,true}) {
                FloatBuffer source=BufferUtils.createFloatBuffer(64*64*4);
                for(int y=0;y<64;y++)for(int x=0;x<64;x++){
                    float value=value(vertical?y:x);
                    source.put(value).put(value).put(value).put(.75f);
                }
                source.flip();
                for(float threshold:new float[]{0,.5f}){
                    var look=new LookSettings();look.add("pixel_sort");
                    tune(look,"Span",16);tune(look,"Threshold",threshold);tune(look,"Speed",0);
                    tune(look,"Vertical",vertical?1:0);
                    upload(texture,source);pipeline.render(texture,64,64,look,0);
                    FloatBuffer output=read();
                    var sorted=new ArrayList<Float>();
                    for(int i=0;i<16;i++)if(value(i)>=threshold)sorted.add(value(i));
                    sorted.sort(Float::compare);
                    for(int y=0;y<64;y++)for(int x=0;x<64;x++){
                        int position=(vertical?y:x)%16, rank=0;
                        for(int i=0;i<position;i++)if(value(i)>=threshold)rank++;
                        float expected=value(position)<threshold?value(position):sorted.get(rank);
                        int at=(y*64+x)*4;
                        for(int c=0;c<3;c++)assertEquals(expected,output.get(at+c),.002f,x+","+y);
                        assertEquals(.75f,output.get(at+3),.00001f);
                    }
                    tune(look,"Speed",1);
                    upload(texture,source);pipeline.render(texture,64,64,look,.7f);
                    FloatBuffer animated=read(); double difference=0;
                    for(int i=0;i<output.limit();i++)difference+=Math.abs(output.get(i)-animated.get(i));
                    assertTrue(difference>1,"Animation must move eligible span boundaries (vertical="+vertical+", threshold="+threshold+")");
                    upload(texture,source);pipeline.render(texture,64,64,look,.7f);
                    FloatBuffer paused=read();
                    for(int i=0;i<paused.limit();i++)assertEquals(animated.get(i),paused.get(i),0,"Paused replay must hold the sort");
                }
            }
            assertEquals(GL11.GL_NO_ERROR,GL11.glGetError());
        } finally {
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER,0);
            GL30.glDeleteFramebuffers(framebuffer);GL11.glDeleteTextures(texture);
            GL.setCapabilities(null);GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate();
        }
    }
    private static float value(int i){return ((i%16*5+3)%16)/15f;}
    private static void tune(LookSettings look,String id,float value){
        var parameter=FilterCatalog.params("pixel_sort").stream().filter(p->p.id().equals(id)).findFirst().orElseThrow();
        look.parameter("pixel_sort",parameter,value);
    }
    private static void upload(int texture,FloatBuffer pixels){
        GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);pixels.rewind();
        GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D,0,0,0,64,64,GL11.GL_RGBA,GL11.GL_FLOAT,pixels);
    }
    private static FloatBuffer read(){
        var pixels=BufferUtils.createFloatBuffer(64*64*4);
        GL11.glReadPixels(0,0,64,64,GL11.GL_RGBA,GL11.GL_FLOAT,pixels);return pixels;
    }
}
