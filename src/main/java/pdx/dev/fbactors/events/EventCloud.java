package pdx.dev.fbactors.events;

import org.lwjgl.opengl.*;

/** A tiny filtered 3D noise lookup, reusing the normal-map binding after impact. */
public final class EventCloud implements AutoCloseable {
    public static final String ROOT="/assets/fbactors/events/clouds/";
    private int texture;
    public int texture() {
        if(texture!=0)return texture;
        texture=GL11.glGenTextures();GL11.glBindTexture(GL11.GL_TEXTURE_2D,texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MIN_FILTER,GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_MAG_FILTER,GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_S,GL11.GL_REPEAT);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D,GL11.GL_TEXTURE_WRAP_T,GL11.GL_REPEAT);
        BombModel.uploadMaterial(ROOT,"noise.png");return texture;
    }
    @Override public void close() {if(texture!=0)GL11.glDeleteTextures(texture);texture=0;}
}
