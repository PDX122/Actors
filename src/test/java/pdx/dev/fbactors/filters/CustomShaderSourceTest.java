package pdx.dev.fbactors.filters;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CustomShaderSourceTest {
    @Test void canonicalizesDeclarationsForBothBackends() {
        String source=ShaderLibraryTest.SOURCE.replace("#version 330 core\n","")
            .replace("uniform vec2 InputSize;","uniform   vec2   InputSize ;");
        source=CustomShaderSource.validate(source);
        assertTrue(source.startsWith("#version 330 core"));
        assertTrue(PortableLookShader.from(source).source().contains("#define InputSize"));
        assertEquals(1,CustomShaderSource.params(source).size());
    }
    @Test void rejectsWrongShaderKindsAndUnknownUniforms() {
        for(String source:new String[]{"",ShaderLibraryTest.SOURCE.replace("330 core","450"),
            ShaderLibraryTest.SOURCE+"\nuniform sampler2D Extra;",ShaderLibraryTest.SOURCE+"\n#include \"file.glsl\"",
            ShaderLibraryTest.SOURCE.replace("void main()","void renamed()"),"x".repeat(70_000),
            ShaderLibraryTest.SOURCE+"\nuniform float Hidden[4];",ShaderLibraryTest.SOURCE+"\nuniform float A,B;"})
            assertThrows(IllegalArgumentException.class,()->CustomShaderSource.validate(source));
    }
    @Test void commentsDoNotCreatePhantomParametersOrUniformBindings() {
        String source=ShaderLibraryTest.SOURCE+"\n// uniform float Speed;\n/* uniform float Exposure; */";
        source=CustomShaderSource.validate(source);
        assertEquals(1,CustomShaderSource.params(source).size());
        assertFalse(PortableLookShader.from(source).floats().contains("Exposure"));
        assertFalse(PortableLookShader.from(source).floats().contains("Speed"));
    }
}
