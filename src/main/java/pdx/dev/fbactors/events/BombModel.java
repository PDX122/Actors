package pdx.dev.fbactors.events;

import com.google.gson.Gson;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.lwjgl.opengl.*;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/** Attribute-simplified credited mesh with source UVs and materials in a baked BVH. */
public final class BombModel implements AutoCloseable {
    public static final String ICBM_ROOT="/assets/fbactors/events/icbm/";
    public static final String ROOT="/assets/fbactors/events/atomic_bomb/";
    public static final String[] SAMPLERS={"BombMeshSampler","BombAlbedoSampler","BombNormalSampler","BombMaterialSampler"};
    public static final String[] IMAGES={"albedo.png","normal.png","material.png"};
    public record Info(int width,int height,int nodes,int triangles,int mips) {}
    private final int[] textures=new int[4];
    private final String root;
    public BombModel() {this(ROOT);}
    public BombModel(String root) {this.root=root;}
    public static InputStream resource(String name) {return resource(ROOT,name);}
    public static InputStream resource(String root,String name) {
        var stream=BombModel.class.getResourceAsStream(root+name);
        if(stream==null)throw new IllegalStateException("Missing event mesh asset: "+name);return stream;
    }
    public static Info info() {return info(ROOT);}
    public static Info info(String root) {
        try(var stream=resource(root,"mesh.json")) {return new Gson().fromJson(new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8),Info.class);}
        catch(IOException error) {throw new IllegalStateException(error);}
    }
    public static ByteBuffer mesh() {return mesh(ROOT);}
    public static ByteBuffer mesh(String root) {
        try(var stream=resource(root,"mesh.bin")) {
            byte[] bytes=stream.readAllBytes();var info=info(root);
            if(bytes.length!=info.width*info.height*16)throw new IllegalStateException("Invalid bomb mesh data size");
            return ByteBuffer.allocateDirect(bytes.length).order(ByteOrder.LITTLE_ENDIAN).put(bytes).flip();
        } catch(IOException error) {throw new IllegalStateException(error);}
    }
    public static int levels(String root,String image) {
        try(var input=new java.io.DataInputStream(resource(root,image))) {
            input.skipNBytes(16);int width=input.readInt(),height=input.readInt();
            // RenderPearl rejects the final rectangular mip once one axis would be zero.
            return Math.min(Math.max(1,info(root).mips()),32-Integer.numberOfLeadingZeros(Math.min(width,height)));
        } catch(IOException error) {throw new IllegalStateException(error);}
    }
    public int texture(int index) {
        if(textures[index]!=0)return textures[index];
        int id=GL11.glGenTextures();textures[index]=id;GL11.glBindTexture(GL11.GL_TEXTURE_2D,id);
        int filter=index==0?GL11.GL_NEAREST:GL11.GL_LINEAR_MIPMAP_LINEAR;
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,filter);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,index==0?GL11.GL_NEAREST:GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL12.GL_CLAMP_TO_EDGE);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);
        if(index==0) {
            var info=info(root);GL11.glTexImage2D(GL11.GL_TEXTURE_2D,0,GL30.GL_RGBA32F,info.width,info.height,0,GL11.GL_RGBA,GL11.GL_FLOAT,mesh(root));
        } else uploadMaterial(root,IMAGES[index-1]);
        return id;
    }
    /** Uploads the bound material texture, including its complete offline-filtered mip chain. */
    public static void uploadMaterial(String root,String file) {
            int levels=levels(root,file);GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL12.GL_TEXTURE_MAX_LEVEL,levels-1);
            for(int level=0;level<levels;level++) {
            String name=file.replace(".png",level==0?".png":"_"+level+".png");
            try(var stream=resource(root,name);var stack=MemoryStack.stackPush()) {
                byte[] bytes=stream.readAllBytes();var encoded=MemoryUtil.memAlloc(bytes.length);
                try {
                    encoded.put(bytes).flip();var w=stack.mallocInt(1);var h=stack.mallocInt(1);var components=stack.mallocInt(1);
                    var pixels=STBImage.stbi_load_from_memory(encoded,w,h,components,4);if(pixels==null)throw new IllegalStateException("Could not decode bomb texture");
                    try {GL11.glTexImage2D(GL11.GL_TEXTURE_2D,level,GL11.GL_RGBA8,w.get(0),h.get(0),0,GL11.GL_RGBA,GL11.GL_UNSIGNED_BYTE,pixels);}
                    finally {STBImage.stbi_image_free(pixels);}
                } finally {MemoryUtil.memFree(encoded);}
            } catch(IOException error) {throw new IllegalStateException(error);}
            }
    }
    @Override public void close() {for(int id:textures)if(id!=0)GL11.glDeleteTextures(id);java.util.Arrays.fill(textures,0);}
}
