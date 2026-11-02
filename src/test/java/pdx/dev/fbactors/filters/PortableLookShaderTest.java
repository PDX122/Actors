package pdx.dev.fbactors.filters;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PortableLookShaderTest {
    @Test void portsEveryCatalogShaderWithoutChangingItsFormula() {
        for (var filter : FilterCatalog.entries()) {
            var original = FilterGpuPipeline.source(filter.id());
            var port = PortableLookShader.from(original);
            assertEquals(original.substring(original.indexOf("void main")),
                port.source().substring(port.source().indexOf("void main")), filter.id());
            assertFalse(port.source().contains("uniform float"), filter.id());
            assertTrue(port.samplers().contains("InSampler"), filter.id());
            assertEquals(0, port.uniforms(filter.id(), new LookSettings(), 4).remaining() % 16);
        }
    }

    @Test void packsGradeAndAnimationInShaderDeclarationOrder() {
        var shader = PortableLookShader.from(FilterGpuPipeline.source("compose"));
        var look = new LookSettings();
        look.intensity = .6f; look.exposure = 1.2f; look.grain = .04f;
        var buffer = shader.uniforms("compose", look, 3.5f);
        assertEquals(.6f, buffer.getFloat(shader.floats().indexOf("Intensity") * 4));
        assertEquals(1.2f, buffer.getFloat(shader.floats().indexOf("Exposure") * 4));
        assertEquals(.04f, buffer.getFloat(shader.floats().indexOf("Grain") * 4));
        assertEquals(3.5f, buffer.getFloat(shader.floats().indexOf("Time") * 4));
    }
}
