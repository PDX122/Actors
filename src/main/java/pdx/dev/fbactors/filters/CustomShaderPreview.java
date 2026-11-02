package pdx.dev.fbactors.filters;

import org.lwjgl.opengl.*;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

public final class CustomShaderPreview {
    private static int original,output,width,height;
    private static FilterGpuPipeline pipeline;
    private static String selected;
    private static float time=Float.NaN;
    private static long handle;
    private CustomShaderPreview() {}
    public static long texture(String id) {
        float nextTime=Math.max(0,pdx.dev.fbactors.FlashbackActors.replayTick())/20f;
        if(!FilterGpuPipeline.isGlAvailable()) return FilterRenderBridge.nativeCustomPreview(id,nextTime);
        var state=new FilterGpuPipeline.State();
        try {
            if(original==0) load();
            if(!id.equals(selected) || nextTime!=time) {
                var look=new LookSettings();look.add(id);
                if(pipeline==null)pipeline=new FilterGpuPipeline();
                pipeline.material(original,output,width,height,look,nextTime);
                handle=FilterRenderBridge.customPreviewHandle(output,width,height);selected=id;time=nextTime;
            }
            return handle;
        }finally{state.restore();}
    }
    private static void load() {
        int alignment=GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT),row=GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH),
            skipRows=GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS),skipPixels=GL11.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS),
            buffer=GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        try(var stream=CustomShaderPreview.class.getResourceAsStream("/assets/fbactors/filters/demo.jpg")) {
            if(stream==null)throw new IllegalStateException("Missing preview photo");
            byte[] bytes=stream.readAllBytes();var encoded=MemoryUtil.memAlloc(bytes.length);
            try(var stack=MemoryStack.stackPush()) {
                encoded.put(bytes).flip();var w=stack.mallocInt(1);var h=stack.mallocInt(1);var c=stack.mallocInt(1);
                var pixels=STBImage.stbi_load_from_memory(encoded,w,h,c,4);
                if(pixels==null)throw new IllegalStateException("Could not decode preview photo");
                try {
                    width=w.get(0);height=h.get(0);original=GL11.glGenTextures();output=GL11.glGenTextures();
                    GL13.glActiveTexture(GL13.GL_TEXTURE0);
                    GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,0);
                    GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT,1);GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH,0);
                    GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS,0);GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS,0);
                    for(int texture:new int[]{original,output}) {
                        GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
                        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR);
                        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);
                        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);
                        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);
                        GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL11.GL_RGBA8,width,height,0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,texture==original?pixels:null);
                    }
                }finally{STBImage.stbi_image_free(pixels);}
            }finally{MemoryUtil.memFree(encoded);}
        }catch(java.io.IOException error){throw new IllegalStateException(error);}
        finally {
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT,alignment);GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH,row);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS,skipRows);GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS,skipPixels);
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER,buffer);
        }
    }
    public static void close() {
        FilterRenderBridge.closeCustomPreview();
        if(FilterGpuPipeline.isGlAvailable()) {
            if(original!=0)GL11.glDeleteTextures(original);if(output!=0)GL11.glDeleteTextures(output);
            if(pipeline!=null)pipeline.close();
        }
        original=output=0;pipeline=null;selected=null;time=Float.NaN;handle=0;
    }
}
