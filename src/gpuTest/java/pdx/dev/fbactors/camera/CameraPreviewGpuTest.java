package pdx.dev.fbactors.camera;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CameraPreviewGpuTest {
    @Test void litStudioRendersEveryModeWithoutGameAssets() throws Exception {
        assumeTrue("1".equals(System.getenv("FBACTORS_GL_TESTS")));
        assertTrue(GLFW.glfwInit());GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE,GLFW.GLFW_FALSE);
        long window=GLFW.glfwCreateWindow(640,360,"Camera studio verification",0,0);
        assertNotEquals(0,window);GLFW.glfwMakeContextCurrent(window);GL.createCapabilities();
        try {
            GL11.glViewport(0,0,640,360);
            GL11.glMatrixMode(GL11.GL_PROJECTION);GL11.glLoadIdentity();GL11.glOrtho(0,640,360,0,-1,1);
            GL11.glMatrixMode(GL11.GL_MODELVIEW);GL11.glLoadIdentity();
            GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
            var root=Path.of(System.getProperty("user.dir")).getParent().getParent();
            var directory=root.resolve("build/camera-previews");Files.createDirectories(directory);
            var sheet=new BufferedImage(2240,1200,BufferedImage.TYPE_INT_RGB);
            for(var entry:CameraModesCatalog.ENTRIES) {
                int mode=entry.mode();
                GL11.glClearColor(.92f,.93f,.94f,1);GL11.glClear(GL11.GL_COLOR_BUFFER_BIT);
                var triangles=CameraPreviewStudio.render(CameraModesCatalog.recommended(mode),3.5,640,360,3200);
                assertFalse(triangles.isEmpty());
                GL11.glBegin(GL11.GL_TRIANGLES);
                for(var t:triangles) { vertex(t.a(),t.ca());vertex(t.b(),t.cb());vertex(t.c(),t.cc()); }
                GL11.glEnd();
                var pixels=BufferUtils.createByteBuffer(640*360*4);
                GL11.glReadPixels(0,0,640,360,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
                var image=new BufferedImage(640,360,BufferedImage.TYPE_INT_RGB);
                int min=255,max=0;
                for(int y=0;y<360;y++) for(int x=0;x<640;x++) {
                    int i=((359-y)*640+x)*4,r=pixels.get(i)&255,g=pixels.get(i+1)&255,b=pixels.get(i+2)&255;
                    image.setRGB(x,y,r<<16|g<<8|b);min=Math.min(min,g);max=Math.max(max,g);
                }
                assertTrue(max-min>25,"Scene lost its depth cues in "+entry.id());
                ImageIO.write(image,"png",directory.resolve(entry.id()+".png").toFile());
                var g=sheet.createGraphics();int x=mode%7*320,y=mode/7*200;
                g.drawImage(image,x,y,320,180,null);g.setColor(Color.WHITE);g.drawString(entry.name(),x+8,y+195);g.dispose();
                assertEquals(GL11.GL_NO_ERROR,GL11.glGetError());
            }
            ImageIO.write(sheet,"png",root.resolve("build/camera-preview-review.png").toFile());
        } finally { GL.setCapabilities(null);GLFW.glfwDestroyWindow(window);GLFW.glfwTerminate(); }
    }
    private static void vertex(CameraModePreviewScene.Pixel p,int c) {
        GL11.glColor4f((c&255)/255f,(c>>8&255)/255f,(c>>16&255)/255f,(c>>>24)/255f);GL11.glVertex2f(p.x(),p.y());
    }
}
