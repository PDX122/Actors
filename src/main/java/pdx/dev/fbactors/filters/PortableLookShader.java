package pdx.dev.fbactors.filters;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public record PortableLookShader(String source, List<String> floats, List<String> samplers) {
    private static final Pattern FLOAT = Pattern.compile("uniform\\s+float\\s+(\\w+)\\s*;");
    private static final Pattern SAMPLER = Pattern.compile("uniform\\s+sampler2D\\s+(\\w+)\\s*;");
    public static final String VERTEX = """
        #version 330
        #extension GL_ARB_separate_shader_objects : require
        layout(location=0) out vec2 texCoord;
        void main() {
            vec2 uv=vec2((gl_VertexIndex<<1)&2,gl_VertexIndex&2);
            gl_Position=vec4(uv*2.0-1.0,0.0,1.0);
            texCoord=uv;
        }
        """;

    public static PortableLookShader from(String original) {
        String declarations=original.replaceAll("(?s)/\\*.*?\\*/","").replaceAll("(?m)//.*$","");
        var floats = new ArrayList<String>();
        var samplers = new ArrayList<String>();
        var matcher = FLOAT.matcher(declarations);
        while (matcher.find()) floats.add(matcher.group(1));
        matcher = SAMPLER.matcher(declarations);
        while (matcher.find()) samplers.add(matcher.group(1));
        String body = FLOAT.matcher(original).replaceAll("")
            .replace("uniform vec2 InputSize;", "#define InputSize vec2(textureSize(InSampler,0))")
            .replaceFirst("#version[^\\n]*", "#version 330\n#extension GL_ARB_separate_shader_objects : require")
            .replace("in vec2 texCoord;", "layout(location=0) in vec2 texCoord;")
            .replace("out vec4 fragColor;", "layout(location=0) out vec4 fragColor;");
        if (!floats.isEmpty()) {
            String block = "layout(std140) uniform LookUniforms {\n";
            for (String name : floats) block += "float " + name + ";\n";
            block += "};\n";
            int at = body.indexOf("\n", body.indexOf("#extension")) + 1;
            body = body.substring(0, at) + block + body.substring(at);
        }
        return new PortableLookShader(body, List.copyOf(floats), List.copyOf(samplers));
    }

    public ByteBuffer uniforms(String filter, LookSettings look, float time) {
        return uniforms(filter, look, time, java.util.Map.of());
    }

    public ByteBuffer uniforms(String filter, LookSettings look, float time, java.util.Map<String, Float> overrides) {
        int bytes = Math.max(16, (floats.size() * 4 + 15) / 16 * 16);
        var buffer = ByteBuffer.allocateDirect(bytes).order(ByteOrder.nativeOrder());
        for (String name : floats) buffer.putFloat(overrides.getOrDefault(name, value(filter, name, look, time)));
        while (buffer.position() < bytes) buffer.putFloat(0);
        return buffer.flip();
    }

    private static float value(String filter, String name, LookSettings look, float time) {
        return switch (name) {
            case "Time" -> time;
            case "Intensity" -> look.intensity;
            case "Exposure" -> look.exposure;
            case "Contrast" -> look.contrast;
            case "Saturation" -> look.saturation;
            case "Temperature" -> look.temperature;
            case "Tint" -> look.tint;
            case "Vignette" -> look.vignette;
            case "Grain" -> look.grain;
            default -> FilterCatalog.params(filter).stream().filter(p -> p.id().equals(name))
                .findFirst().map(p -> look.parameter(filter, p)).orElse(0f);
        };
    }
}
