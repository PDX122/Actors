package pdx.dev.fbactors.selftest;

import java.nio.file.*;
import java.util.Comparator;
import java.util.function.Consumer;
import org.lwjgl.opengl.*;

final class ReleaseFrameCapture {
    static void capture(Path output,Consumer<String> complete) {
        try {
            Object composite=null;
            try { composite=com.moulberry.flashback.editor.ui.ReplayUI.class.getField("compositeOnTop").get(null); }
            catch(NoSuchFieldException legacy) { }
            boolean gl;
            try { gl=GL.getCapabilities()!=null; } catch(IllegalStateException nativeBackend) { gl=false; }
            if(gl) {
                captureGl(output,composite);
                complete.accept(null);
            } else {
                if(composite==null) throw new IllegalStateException("No editor composite target");
                Path directory=output.getParent().resolve("native-readback"); Files.createDirectories(directory);
                net.minecraft.client.Screenshot.grab(directory.toFile(),(com.mojang.blaze3d.pipeline.RenderTarget)composite,message -> {
                    try(var files=Files.list(directory.resolve("screenshots"))) {
                        Path file=files.filter(p -> p.toString().endsWith(".png"))
                            .max(Comparator.comparingLong(p -> p.toFile().lastModified())).orElseThrow();
                        Files.copy(file,output,StandardCopyOption.REPLACE_EXISTING); complete.accept(null);
                    } catch(Exception failure) { complete.accept(failure.toString()); }
                });
            }
        } catch(Exception | LinkageError failure) { complete.accept(failure.toString()); }
    }
    static void captureWorld(Path output, Consumer<String> complete) {
        try {
            Path directory=output.getParent().resolve("world-readback");
            Files.createDirectories(directory);
            var client=net.minecraft.client.Minecraft.getInstance();
            com.mojang.blaze3d.pipeline.RenderTarget target;
            try { target=(com.mojang.blaze3d.pipeline.RenderTarget)client.getClass().getMethod("getMainRenderTarget").invoke(client); }
            catch(NoSuchMethodException modern) {
                target=(com.mojang.blaze3d.pipeline.RenderTarget)client.gameRenderer.getClass().getMethod("mainRenderTarget").invoke(client.gameRenderer);
            }
            net.minecraft.client.Screenshot.grab(directory.toFile(),target, message -> {
                    try(var files=Files.list(directory.resolve("screenshots"))) {
                        Path file=files.filter(p -> p.toString().endsWith(".png"))
                            .max(Comparator.comparingLong(p -> p.toFile().lastModified())).orElseThrow();
                        Files.move(file,output,StandardCopyOption.REPLACE_EXISTING);
                        complete.accept(null);
                    } catch(Exception failure) { complete.accept(failure.toString()); }
                });
        } catch(Exception | LinkageError failure) { complete.accept(failure.toString()); }
    }
    private static void captureGl(Path output,Object composite) throws Exception {
        var draw=imgui.moulberry90.ImGui.getDrawData();
        int width=Math.round(draw.getDisplaySizeX()*draw.getFramebufferScaleX());
        int height=Math.round(draw.getDisplaySizeY()*draw.getFramebufferScaleY());
        int framebuffer=GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int readBuffer=GL11.glGetInteger(GL11.GL_READ_BUFFER);
        int packBuffer=GL11.glGetInteger(GL21.GL_PIXEL_PACK_BUFFER_BINDING);
        int alignment=GL11.glGetInteger(GL11.GL_PACK_ALIGNMENT);
        int rowLength=GL11.glGetInteger(GL11.GL_PACK_ROW_LENGTH);
        int skipRows=GL11.glGetInteger(GL11.GL_PACK_SKIP_ROWS);
        int skipPixels=GL11.glGetInteger(GL11.GL_PACK_SKIP_PIXELS);
        int temporary=0;
        var pixels=org.lwjgl.system.MemoryUtil.memAlloc(Math.multiplyExact(Math.multiplyExact(width,height),4));
        try {
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,0);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT,1);
            GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH,0);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS,0);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS,0);
            if(composite!=null) {
                Object texture=composite.getClass().getMethod("getColorTexture").invoke(composite);
                int id=((Number)texture.getClass().getMethod("glId").invoke(texture)).intValue();
                temporary=GL30.glGenFramebuffers();
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,temporary);
                GL30.glFramebufferTexture2D(GL30.GL_READ_FRAMEBUFFER,GL30.GL_COLOR_ATTACHMENT0,GL11.GL_TEXTURE_2D,id,0);
                if(GL30.glCheckFramebufferStatus(GL30.GL_READ_FRAMEBUFFER)!=GL30.GL_FRAMEBUFFER_COMPLETE)
                    throw new IllegalStateException("Editor readback framebuffer is incomplete");
                GL11.glReadBuffer(GL30.GL_COLOR_ATTACHMENT0);
            } else {
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,0); GL11.glReadBuffer(GL11.GL_BACK);
            }
            GL11.glReadPixels(0,0,width,height,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);
            var image=new java.awt.image.BufferedImage(width,height,java.awt.image.BufferedImage.TYPE_INT_ARGB);
            for(int y=0;y<height;y++) for(int x=0;x<width;x++) {
                int i=((height-1-y)*width+x)*4;
                image.setRGB(x,y,0xff000000 | (pixels.get(i)&255)<<16 | (pixels.get(i+1)&255)<<8 | pixels.get(i+2)&255);
            }
            javax.imageio.ImageIO.write(image,"png",output.toFile());
            Files.writeString(output.resolveSibling(output.getFileName()+".json"),
                "{\"width\":"+width+",\"height\":"+height+",\"packBufferBefore\":"+packBuffer
                +",\"rowLengthBefore\":"+rowLength+",\"source\":\""+(composite==null?"backbuffer":"editor-composite")+"\"}");
        } finally {
            org.lwjgl.system.MemoryUtil.memFree(pixels);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER,framebuffer); GL11.glReadBuffer(readBuffer);
            if(temporary!=0) GL30.glDeleteFramebuffers(temporary);
            GL15.glBindBuffer(GL21.GL_PIXEL_PACK_BUFFER,packBuffer);
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT,alignment);
            GL11.glPixelStorei(GL11.GL_PACK_ROW_LENGTH,rowLength);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_ROWS,skipRows);
            GL11.glPixelStorei(GL11.GL_PACK_SKIP_PIXELS,skipPixels);
        }
    }
}
