package pdx.dev.fbactors.events;

import org.lwjgl.opengl.*;

/** Credited geographic Earth imagery. Two packed textures with filtered mip chains. */
public final class EventEarth implements AutoCloseable {
    public static final String ROOT="/assets/fbactors/events/earth/";
    public static final String[] SAMPLERS={"EarthSurfaceSampler","EarthWeatherSampler"},IMAGES={"surface.png","weather.png"};
    private final int[] textures=new int[2];
    public int texture(int index) {
        if(textures[index]!=0)return textures[index];
        int id=GL11.glGenTextures();textures[index]=id;GL11.glBindTexture(GL11.GL_TEXTURE_2D,id);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR_MIPMAP_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL11.GL_REPEAT);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL12.GL_CLAMP_TO_EDGE);
        BombModel.uploadMaterial(ROOT,IMAGES[index]);return id;
    }
    @Override public void close() {for(int texture:textures)if(texture!=0)GL11.glDeleteTextures(texture);java.util.Arrays.fill(textures,0);}
}
