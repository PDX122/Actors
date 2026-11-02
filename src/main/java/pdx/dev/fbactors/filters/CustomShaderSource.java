package pdx.dev.fbactors.filters;

import java.util.List;
import java.util.regex.Pattern;

public final class CustomShaderSource {
    public static final int MAX_BYTES=65_536;
    private CustomShaderSource() {}
    public static String validate(String source) {
        if(source==null || source.isBlank() || source.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>MAX_BYTES)
            throw new IllegalArgumentException("Use a nonempty fragment shader smaller than 64 KiB.");
        source=source.replace("\r\n","\n");
        String code=source.replaceAll("(?s)/\\*.*?\\*/","").replaceAll("(?m)//.*$","");
        if(code.contains("#include") || code.contains("#extension") || code.matches("(?s).*\\b(buffer|imageStore|imageLoad|atomic_uint)\\b.*"))
            throw new IllegalArgumentException("Use a standalone GLSL fragment effect without includes, extensions or storage buffers.");
        var version=Pattern.compile("(?m)^\\s*#version\\s+([^\\n]+)").matcher(code);
        if(version.find() && !version.group(1).strip().matches("330(?:\\s+core)?"))
            throw new IllegalArgumentException("Custom Looks use GLSL 330 core.");
        if(!code.contains("#version")) source="#version 330 core\n"+source;
        if(!Pattern.compile("\\bvoid\\s+main\\s*\\(\\s*(?:void)?\\s*\\)").matcher(code).find())
            throw new IllegalArgumentException("The shader needs void main().");
        for(String declaration:List.of("uniform sampler2D InSampler;","in vec2 texCoord;","out vec4 fragColor;"))
            if(!Pattern.compile(declaration.replace(" ","\\s+").replace(";","\\s*;")).matcher(code).find())
                throw new IllegalArgumentException("Missing shader declaration: "+declaration);
        var uniforms=Pattern.compile("\\buniform\\s+(\\w+)\\s+(\\w+)\\s*;").matcher(code);
        while(uniforms.find()) {
            String type=uniforms.group(1),name=uniforms.group(2);
            boolean allowed=type.equals("sampler2D")&&name.equals("InSampler") || type.equals("vec2")&&name.equals("InputSize")
                || type.equals("float")&&List.of("Time","Amount","Speed").contains(name);
            if(!allowed) throw new IllegalArgumentException("Unsupported uniform: "+name+". Use InSampler, InputSize, Time, Amount or Speed.");
        }
        if(Pattern.compile("\\buniform\\b").matcher(uniforms.replaceAll("")).find())
            throw new IllegalArgumentException("Use separate declarations for the supported shader uniforms.");
        source=source.replaceAll("uniform\\s+vec2\\s+InputSize\\s*;","uniform vec2 InputSize;")
            .replaceAll("in\\s+vec2\\s+texCoord\\s*;","in vec2 texCoord;")
            .replaceAll("out\\s+vec4\\s+fragColor\\s*;","out vec4 fragColor;");
        return source;
    }
    public static List<FilterCatalog.Param> params(String source) {
        source=source.replaceAll("(?s)/\\*.*?\\*/","").replaceAll("(?m)//.*$","");
        var params=new java.util.ArrayList<FilterCatalog.Param>();
        if(Pattern.compile("uniform\\s+float\\s+Amount\\s*;").matcher(source).find()) params.add(new FilterCatalog.Param("Amount","Strength",0,1,1));
        if(Pattern.compile("uniform\\s+float\\s+Speed\\s*;").matcher(source).find()) params.add(new FilterCatalog.Param("Speed","Animation speed",0,3,1));
        return List.copyOf(params);
    }
}
