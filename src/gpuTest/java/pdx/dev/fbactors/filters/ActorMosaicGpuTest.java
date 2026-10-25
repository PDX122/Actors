package pdx.dev.fbactors.filters;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import java.nio.FloatBuffer;
import org.junit.jupiter.api.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

class ActorMosaicGpuTest {
    @Test void nearAndDistantActorsKeepFlatBlocksWithoutChangingBackgroundOrOccluders() {
        assumeTrue("1".equals(System.getenv("FBACTORS_GL_TESTS")));
        assertTrue(GLFW.glfwInit());
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        long window = GLFW.glfwCreateWindow(256, 144, "Actor mosaic GPU verification", 0, 0);
        assertNotEquals(0, window);
        GLFW.glfwMakeContextCurrent(window);
        GL.createCapabilities();
        int vertex = shader(GL20.GL_VERTEX_SHADER, "#version 330 core\nout vec2 uv;uniform float Depth;void main(){vec2 p=vec2((gl_VertexID<<1)&2,gl_VertexID&2);uv=p;gl_Position=vec4(p*2.0-1.0,Depth*2.0-1.0,1.0);}");
        int fragment = shader(GL20.GL_FRAGMENT_SHADER, "#version 330 core\nin vec2 uv;out vec4 color;uniform vec4 Rect;uniform vec3 Base;void main(){if(uv.x<Rect.x||uv.y<Rect.y||uv.x>=Rect.z||uv.y>=Rect.w)discard;color=vec4(Base+vec3(uv.x*0.3,uv.y*0.3,0.05),0.75);}");
        int program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vertex);
        GL20.glAttachShader(program, fragment);
        GL20.glLinkProgram(program);
        assertEquals(1, GL20.glGetProgrami(program, GL20.GL_LINK_STATUS), GL20.glGetProgramInfoLog(program));
        int vao = GL30.glGenVertexArrays();
        GL30.glBindVertexArray(vao);
        int color = image(GL30.GL_RGBA32F, GL11.GL_RGBA);
        int depth = image(GL30.GL_DEPTH_COMPONENT32F, GL11.GL_DEPTH_COMPONENT);
        int framebuffer = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, color, 0);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, depth, 0);
        assertEquals(GL30.GL_FRAMEBUFFER_COMPLETE, GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER));
        int oitColor = image(GL30.GL_RGBA32F, GL11.GL_RGBA);
        int oitFramebuffer = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, oitFramebuffer);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, oitColor, 0);
        // An unrelated preview target must not be mistaken for world geometry.
        assertFalse(ActorDepthMask.matchesTarget(color, depth));
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, depth, 0);
        assertEquals(GL30.GL_FRAMEBUFFER_COMPLETE, GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER));
        assertTrue(ActorDepthMask.matchesTarget(color, depth));
        for (String name : new String[] {"Sampler0", "_uniform_01_03"}) {
            int materialFragment = shader(GL20.GL_FRAGMENT_SHADER, "#version 330 core\nin vec2 uv;out vec4 color;uniform sampler2D " + name + ";void main(){color=texture(" + name + ",uv);}");
            int materialProgram = GL20.glCreateProgram();
            GL20.glAttachShader(materialProgram, vertex);
            GL20.glAttachShader(materialProgram, materialFragment);
            GL20.glLinkProgram(materialProgram);
            assertEquals(1, GL20.glGetProgrami(materialProgram, GL20.GL_LINK_STATUS), GL20.glGetProgramInfoLog(materialProgram));
            try {
                GL20.glUseProgram(materialProgram);
                GL20.glUniform1i(GL20.glGetUniformLocation(materialProgram, name), 3);
                org.lwjgl.opengl.GL13.glActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0 + 3);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, color);
                org.lwjgl.opengl.GL13.glActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0 + 5);
                assertEquals(color, ActorMaterialSampler.findTexture(materialProgram, texture -> texture == color), name);
                assertEquals(0, ActorMaterialSampler.findTexture(materialProgram, texture -> false));
                assertEquals(org.lwjgl.opengl.GL13.GL_TEXTURE0 + 5, GL11.glGetInteger(org.lwjgl.opengl.GL13.GL_ACTIVE_TEXTURE));
            } finally {
                GL20.glUseProgram(0);
                GL20.glDeleteProgram(materialProgram);
                GL20.glDeleteShader(materialFragment);
                ActorMaterialSampler.clear();
            }
        }
        org.lwjgl.opengl.GL13.glActiveTexture(org.lwjgl.opengl.GL13.GL_TEXTURE0);
        try (FilterGpuPipeline pipeline = new FilterGpuPipeline()) {
            for (boolean reverse : new boolean[] {false, true}) for (boolean distant : new boolean[] {false, true})
                for (boolean oit : new boolean[] {false, true}) {
                int left = distant ? 112 : 64, right = distant ? 136 : 192;
                int bottom = distant ? 60 : 36, top = distant ? 84 : 108;
                int middle = (left + right) / 2;
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
                GL30.glBindVertexArray(vao);
                GL11.glViewport(0, 0, 256, 144);
                GL11.glDisable(GL11.GL_BLEND);
                GL11.glDisable(GL11.GL_SCISSOR_TEST);
                GL11.glEnable(GL11.GL_DEPTH_TEST);
                GL11.glDepthMask(true);
                GL11.glDepthFunc(reverse ? GL11.GL_GEQUAL : GL11.GL_LEQUAL);
                GL11.glClearDepth(reverse ? 0 : 1);
                GL11.glColorMask(true, true, true, true);
                GL11.glClear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT);
                GL20.glUseProgram(program);
                draw(program, reverse ? 0.1f : 0.9f, 0, 0, 1, 1, 0.05f, 0.15f, 0.25f);
                draw(program, reverse ? 0.6f : 0.4f, left / 256f, bottom / 144f, right / 256f, top / 144f, 0.4f, 0.2f, 0.1f);
                try (ActorDepthMask mask = new ActorDepthMask(256, 144, reverse)) {
                    int captureFramebuffer = oit ? oitFramebuffer : framebuffer;
                    GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, captureFramebuffer);
                    assertTrue(ActorDepthMask.matchesTarget(color, depth));
                    mask.capture(() -> draw(program, reverse ? 0.6f : 0.4f, left / 256f, bottom / 144f, right / 256f, top / 144f, 0.4f, 0.2f, 0.1f));
                    assertTrue(mask.seen());
                    assertEquals(captureFramebuffer, GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING));
                    assertEquals(reverse ? GL11.GL_GEQUAL : GL11.GL_LEQUAL, GL11.glGetInteger(GL11.GL_DEPTH_FUNC));
                    assertTrue(GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK));
                    GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, framebuffer);
                    draw(program, reverse ? 0.8f : 0.2f, middle / 256f, bottom / 144f, right / 256f, top / 144f, 0.7f, 0.02f, 0.02f);
                    FloatBuffer before = pixels();
                    pipeline.actorMosaic(color, mask.texture(), depth, 256, 144, 60, 1, reverse);
                    assertEquals(GL11.GL_NO_ERROR, GL11.glGetError());
                    FloatBuffer after = pixels();
                    double changed = 0;
                    for (int y = 0; y < 144; y++) for (int x = 0; x < 256; x++) {
                        int i = (y * 256 + x) * 4;
                        boolean visible = x >= left && x < middle && y >= bottom && y < top;
                        for (int c = 0; c < 4; c++) {
                            if (!visible || c == 3) assertEquals(before.get(i + c), after.get(i + c), 0.000001f, x + "," + y);
                            else changed += Math.abs(before.get(i + c) - after.get(i + c));
                        }
                    }
                    assertTrue(changed > 0.1);
                    for (int c = 0; c < 3; c++) assertEquals(after.get(((bottom + 5) * 256 + left + 3) * 4 + c), after.get(((bottom + 5) * 256 + left + 4) * 4 + c), 0.000001f);
                    if (distant) {
                        float firstBlock = after.get(((bottom + 5) * 256 + left + 3) * 4);
                        float nextBlock = after.get(((bottom + 5) * 256 + left + 7) * 4);
                        assertTrue(Math.abs(firstBlock - nextBlock) > 0.001f, "Distant blocks must shrink with the actor");
                    }
                    pipeline.actorMosaic(color, mask.texture(), depth, 256, 144, 60, 0, reverse);
                    FloatBuffer bypass = pixels();
                    for (int i = 0; i < bypass.limit(); i++) assertEquals(after.get(i), bypass.get(i), 0);
                    mask.beginFrame();
                    assertFalse(mask.seen());
                }
            }
        } finally {
            GL20.glUseProgram(0);
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
            GL30.glDeleteFramebuffers(framebuffer);
            GL30.glDeleteFramebuffers(oitFramebuffer);
            GL11.glDeleteTextures(oitColor);
            GL11.glDeleteTextures(color);
            GL11.glDeleteTextures(depth);
            GL30.glDeleteVertexArrays(vao);
            GL20.glDeleteProgram(program);
            GL20.glDeleteShader(vertex);
            GL20.glDeleteShader(fragment);
            GL.setCapabilities(null);
            GLFW.glfwDestroyWindow(window);
            GLFW.glfwTerminate();
        }
    }

    private static int shader(int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        assertEquals(1, GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS), GL20.glGetShaderInfoLog(shader));
        return shader;
    }

    private static int image(int internal, int format) {
        int texture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, internal, 256, 144, 0, format, GL11.GL_FLOAT, (java.nio.ByteBuffer) null);
        return texture;
    }

    private static void draw(int program, float depth, float x0, float y0, float x1, float y1, float r, float g, float b) {
        GL20.glUniform1f(GL20.glGetUniformLocation(program, "Depth"), depth);
        GL20.glUniform4f(GL20.glGetUniformLocation(program, "Rect"), x0, y0, x1, y1);
        GL20.glUniform3f(GL20.glGetUniformLocation(program, "Base"), r, g, b);
        GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
    }

    private static FloatBuffer pixels() {
        FloatBuffer pixels = BufferUtils.createFloatBuffer(256 * 144 * 4);
        GL11.glReadPixels(0, 0, 256, 144, GL11.GL_RGBA, GL11.GL_FLOAT, pixels);
        return pixels;
    }
}
