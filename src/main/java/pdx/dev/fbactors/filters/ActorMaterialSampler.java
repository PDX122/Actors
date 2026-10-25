package pdx.dev.fbactors.filters;

import java.util.HashMap;
import java.util.Map;
import java.util.function.IntPredicate;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.system.MemoryStack;

/** Finds private actor materials even when RenderPearl renames shader uniforms. */
public final class ActorMaterialSampler {
    private static final Map<Integer, int[]> LOCATIONS = new HashMap<>();
    private ActorMaterialSampler() {}

    public static int findTexture(int program, IntPredicate isMaterial) {
        if (program <= 0) return 0;
        int active = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        try {
            for (int location : LOCATIONS.computeIfAbsent(program, ActorMaterialSampler::locations)) {
                int sampler = GL20.glGetUniformi(program, location);
                GL13.glActiveTexture(GL13.GL_TEXTURE0 + sampler);
                int texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
                if (isMaterial.test(texture)) return texture;
            }
            return 0;
        } finally { GL13.glActiveTexture(active); }
    }

    private static int[] locations(int program) {
        int legacy = GL20.glGetUniformLocation(program, "Sampler0");
        if (legacy >= 0) return new int[] {legacy};
        // 26.3 compiles Sampler0 to a binding-derived name such as
        // _uniform_01_03. Inspect linked sampler types instead of guessing it.
        var result = new java.util.ArrayList<Integer>();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var size = stack.mallocInt(1);
            var type = stack.mallocInt(1);
            int count = GL20.glGetProgrami(program, GL20.GL_ACTIVE_UNIFORMS);
            int length = GL20.glGetProgrami(program, GL20.GL_ACTIVE_UNIFORM_MAX_LENGTH);
            for (int i = 0; i < count; i++) {
                String name = GL20.glGetActiveUniform(program, i, length, size, type);
                if (type.get(0) == GL20.GL_SAMPLER_2D && size.get(0) == 1) {
                    int location = GL20.glGetUniformLocation(program, name);
                    if (location >= 0) result.add(location);
                }
            }
        }
        return result.stream().mapToInt(Integer::intValue).toArray();
    }

    public static void clear() { LOCATIONS.clear(); }
}
