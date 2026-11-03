package pdx.dev.fbactors.filters;

import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.frontend.FrontendRenderPipeline;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeActorMaskBindingsTest {
    private FrontendRenderPipeline pipeline(String name, String... samplers) {
        var uniforms = java.util.Arrays.stream(samplers)
            .map(sampler -> new BindGroupLayout.UniformDescription(sampler, UniformType.COMBINED_IMAGE_SAMPLER)).toList();
        return new FrontendRenderPipeline(name, null, List.of(), new Object2IntOpenHashMap<>(), uniforms, List.of(), false, 0);
    }

    @Test void cloudsCannotInheritThePreviousActorsTextureBinding() {
        Object skin = new Object(), clouds = new Object();
        var retained = Map.of("Sampler0", skin, "CloudFaces", clouds);
        assertEquals(Map.of("Sampler0", skin), NativeActorMasks.boundUniforms(pipeline("Actor", "Sampler0"), retained));
        assertEquals(Map.of("CloudFaces", clouds), NativeActorMasks.boundUniforms(pipeline("Clouds", "CloudFaces"), retained));
        assertTrue(NativeActorMasks.boundUniforms(pipeline("Untextured clouds"), retained).isEmpty());
        assertEquals(2, retained.size());
    }

    @Test void captureRetainsAllDeclaredBindingsAndDropsUnusedOnes() {
        Object skin = new Object(), lightmap = new Object(), oldSkin = new Object();
        var retained = Map.of("Skin", skin, "Lightmap", lightmap, "OldActorSkin", oldSkin);
        assertEquals(Map.of("Skin", skin, "Lightmap", lightmap),
            NativeActorMasks.boundUniforms(pipeline("Renamed actor samplers", "Skin", "Lightmap"), retained));
        assertTrue(NativeActorMasks.boundUniforms(null, retained).isEmpty());
    }
}
