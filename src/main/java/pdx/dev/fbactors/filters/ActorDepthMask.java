package pdx.dev.fbactors.filters;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL30;

public final class ActorDepthMask implements AutoCloseable {
    private final int texture;
    private final int framebuffer;
    private boolean seen;
    public final boolean reverse;
    public final int width;
    public final int height;

    public ActorDepthMask(int width, int height) { this(width, height, false); }

    public ActorDepthMask(int width, int height, boolean reverse) {
        this.reverse = reverse;
        this.width = width;
        this.height = height;
        FilterGpuPipeline.State state = new FilterGpuPipeline.State();
        int image = 0, buffer = 0;
        try {
            org.lwjgl.opengl.GL13.glActiveTexture(GL13.GL_TEXTURE0);
            image = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, image);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL13.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL13.GL_CLAMP_TO_EDGE);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL14.GL_TEXTURE_COMPARE_MODE, GL11.GL_NONE);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_DEPTH_COMPONENT32F, width, height, 0,
                GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, (java.nio.ByteBuffer) null);
            buffer = GL30.glGenFramebuffers();
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, buffer);
            GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, image, 0);
            GL11.glDrawBuffer(GL11.GL_NONE);
            GL11.glReadBuffer(GL11.GL_NONE);
            if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) throw new IllegalStateException("Actor depth mask is incomplete");
        } catch (RuntimeException error) {
            if (image != 0) GL11.glDeleteTextures(image);
            if (buffer != 0) GL30.glDeleteFramebuffers(buffer);
            throw error;
        } finally { state.restore(); }
        texture = image;
        framebuffer = buffer;
    }

    public int texture() { return texture; }
    public boolean seen() { return seen; }
    public void beginFrame() { seen = false; }

    /** Accept world draws, including 26.3's OIT targets sharing world depth. */
    public static boolean matchesTarget(int color, int depth) {
        if (GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) == 0) return false;
        return hasAttachment(GL30.GL_COLOR_ATTACHMENT0, color)
            || hasAttachment(GL30.GL_DEPTH_ATTACHMENT, depth);
    }

    private static boolean hasAttachment(int attachment, int texture) {
        return texture > 0
            && GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, attachment,
                GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE) == GL11.GL_TEXTURE
            && GL30.glGetFramebufferAttachmentParameteri(GL30.GL_DRAW_FRAMEBUFFER, attachment,
                GL30.GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME) == texture;
    }

    public void capture(Runnable draw) {
        FilterGpuPipeline.State state = new FilterGpuPipeline.State();
        int depthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        boolean depthWrite = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        double clearDepth = GL11.glGetDouble(GL11.GL_DEPTH_CLEAR_VALUE);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        try {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
            GL11.glDepthMask(true);
            if (!seen) {
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
                GL11.glClearDepth(reverse ? 0 : 1);
                GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
                if (scissor) GL11.glEnable(GL11.GL_SCISSOR_TEST);
            }
            GL11.glEnable(GL11.GL_DEPTH_TEST);
            GL11.glDepthFunc(reverse ? GL11.GL_GEQUAL : GL11.GL_LEQUAL);
            GL11.glColorMask(false, false, false, false);
            draw.run();
            seen = true;
        } finally {
            GL11.glDepthFunc(depthFunction);
            GL11.glDepthMask(depthWrite);
            GL11.glClearDepth(clearDepth);
            state.restore();
        }
    }

    @Override public void close() { GL30.glDeleteFramebuffers(framebuffer); GL11.glDeleteTextures(texture); }
}
