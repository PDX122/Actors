package pdx.dev.fbactors.filters;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.device.GpuDebugOptions;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.backend.vulkan.VulkanBackend;
import java.util.Optional;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;
import org.lwjgl.sdl.SDLInit;
import static org.junit.jupiter.api.Assertions.*;

class PearlLookPipelineTest {
    @Test void executesEveryOriginalFilterOnTheNativeVulkanDevice(@org.junit.jupiter.api.io.TempDir java.nio.file.Path shaderDirectory) throws Exception {
        net.minecraft.SharedConstants.tryDetectVersion();
        com.mojang.blaze3d.platform.NativeLibrariesBootstrap.loadLibraries();
        assertTrue(SDLInit.SDL_Init(SDLInit.SDL_INIT_VIDEO));
        var backend = new VulkanBackend();
        backend.loadLibrary();
        var device = backend.createDevice(new GpuDebugOptions(1, true, true, false));
        com.mojang.blaze3d.systems.RenderSystem.initRenderThread();
        com.mojang.blaze3d.systems.RenderSystem.initRenderer(device);
        try {
            try (var sharp = device.createSampler(com.mojang.renderpearl.api.textures.AddressMode.REPEAT,
                    com.mojang.renderpearl.api.textures.AddressMode.REPEAT,
                    com.mojang.renderpearl.api.textures.FilterMode.NEAREST,
                    com.mojang.renderpearl.api.textures.FilterMode.NEAREST,1,java.util.OptionalDouble.empty());
                 var material = new NativeActorLooks.Material(2,1,GpuFormat.RGBA8_UNORM,sharp)) {
                assertSame(sharp,material.getSampler(),"Actor looks must preserve the skin sampler instead of introducing linear magnification");
                assertSharpMagnification(device,material);
            }
            int usage = GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_COPY_DST
                | GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT;
            try (var source = device.createTexture("Filter test source", usage, GpuFormat.RGBA8_UNORM, 64, 64, 1, 1);
                 var output = device.createTexture("Filter test output", usage, GpuFormat.RGBA8_UNORM, 64, 64, 1, 1);
                 var view = device.createTextureView(output);
                 var pipeline = new PearlLookPipeline(device)) {
                var encoder = device.createCommandEncoder();
                encoder.clearColorTexture(source, new Vector4f(.2f, .4f, .8f, 1));
                encoder.submit();
                var grade = new LookSettings();
                grade.enabled = true;
                grade.exposure = 1;
                pipeline.render(source, view, grade, 0);
                assertPixel(device, output, 102, 204, 255);
                assertEvents(device,pipeline,source,view,usage);
                assertCustomShader(device,pipeline,source,view,shaderDirectory);
                for (var entry : FilterCatalog.entries()) {
                    var look = new LookSettings();
                    look.add(entry.id());
                    assertDoesNotThrow(() -> pipeline.render(source, view, look, 2.5f), entry.id());
                }
                int maskUsage = GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_COPY_DST;
                try (var actor = device.createTexture("Actor depth", maskUsage, GpuFormat.D32_FLOAT, 64, 64, 1, 1);
                     var scene = device.createTexture("Scene depth", maskUsage, GpuFormat.D32_FLOAT, 64, 64, 1, 1);
                     var entities = device.createTexture("Entity depth", maskUsage, GpuFormat.D32_FLOAT, 64, 64, 1, 1);
                     var bounds = device.createTexture("Bounds", maskUsage, GpuFormat.RGBA16_FLOAT, 1, 1, 1, 1);
                     var actorView = device.createTextureView(actor);
                     var sceneView = device.createTextureView(scene);
                     var entityView = device.createTextureView(entities);
                     var boundsView = device.createTextureView(bounds);
                     var sourceView = device.createTextureView(source)) {
                    var mosaic = new LookSettings(); mosaic.add("tessera");
                    for (float actorDepth : new float[]{.4f, .9f}) {
                        encoder = device.createCommandEncoder();
                        encoder.clearColorTexture(output, new Vector4f(.9f, .1f, .1f, 1));
                        encoder.clearDepthTexture(actor, actorDepth);
                        encoder.clearDepthTexture(scene, .8);
                        encoder.clearColorTexture(bounds, new Vector4f(0,0,1,1));
                        pipeline.draw(encoder, "actor_mosaic", java.util.Map.of("InSampler", sourceView,
                            "ActorDepthSampler", actorView, "SceneDepthSampler", sceneView, "ExtentSampler", boundsView),
                            view, mosaic, 0, java.util.Map.of("ReverseDepth", 1f, "BlockSize", 32f));
                        encoder.submit();
                        if (actorDepth < .8f) assertPixel(device, output, 230, 26, 26);
                        else assertPixel(device, output, 51, 102, 204);
                    }
                    for (float actorDepth : new float[]{0f, .4f, .9f}) {
                        for (var entry : FilterCatalog.entries()) {
                            var look = new LookSettings(); look.add(entry.id());
                            if (!ActorEffectPlan.usesScreen(look)) continue;
                            encoder = device.createCommandEncoder();
                            encoder.clearColorTexture(output, new Vector4f(.9f,.1f,.1f,1));
                            encoder.clearDepthTexture(actor, actorDepth);
                            encoder.submit();
                            pipeline.renderActor(source, view, actorView, sceneView, boundsView, look, 2.5f);
                            if (actorDepth < .8f) assertPixel(device, output, 230,26,26);
                            else if (entry.id().equals("ascii")) assertGlyphs(device, output);
                        }
                    }

                    var blockLook = new LookSettings(); blockLook.add("ascii");
                    encoder = device.createCommandEncoder();
                    encoder.clearColorTexture(output,new Vector4f(.9f,.1f,.1f,1));
                    encoder.clearDepthTexture(actor,.6);
                    encoder.clearDepthTexture(scene,.8);
                    encoder.clearDepthTexture(entities,.6);
                    encoder.submit();
                    pipeline.renderActor(source,view,actorView,sceneView,entityView,boundsView,blockLook,0);
                    assertPixel(device,output,230,26,26);
                    encoder=device.createCommandEncoder();encoder.clearDepthTexture(scene,0);encoder.submit();
                    assertPixel(device,output,230,26,26);

                    pipeline.renderActor(source,view,actorView,sceneView,entityView,boundsView,blockLook,0);
                    assertGlyphs(device,output);

                    for (float frontDepth : new float[]{.8f, Math.nextUp(.6f)}) {
                        for (String id : new String[]{"ascii","tessera"}) {
                            var look = new LookSettings(); look.add(id);
                            encoder = device.createCommandEncoder();
                            encoder.clearColorTexture(output, new Vector4f(.9f,.1f,.1f,1));
                            encoder.clearDepthTexture(actor,.6);
                            encoder.clearDepthTexture(scene,.1);
                            encoder.clearDepthTexture(entities,frontDepth);
                            encoder.submit();
                            pipeline.renderActor(source,view,actorView,sceneView,entityView,boundsView,look,0);
                            assertPixel(device,output,230,26,26);
                        }
                    }
                    encoder = device.createCommandEncoder();
                    encoder.clearDepthTexture(entities,.6);
                    encoder.submit();
                    var visible = new LookSettings(); visible.add("ascii");
                    pipeline.renderActor(source,view,actorView,sceneView,entityView,boundsView,visible,0);
                    assertGlyphs(device,output);
                    var mixed = new LookSettings(); mixed.add("ember"); mixed.add("ascii"); mixed.add("tessera");
                    pipeline.renderActor(source, view, actorView, sceneView, boundsView, mixed, 2.5f);

                }
            }
        } finally {
            device.close();
            backend.unloadLibrary();
            SDLInit.SDL_Quit();
        }
    }

    private static void assertCustomShader(GpuDevice device,PearlLookPipeline pipeline,GpuTexture source,
            com.mojang.renderpearl.api.textures.GpuTextureView output,java.nio.file.Path directory) throws Exception {
        String code;
        try(var resource=PearlLookPipelineTest.class.getResourceAsStream("/assets/fbactors/filters/examples/custom-negative.fsh")) {
            assertNotNull(resource);code=CustomShaderSource.validate(new String(resource.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
        }
        var library=new ShaderLibrary(directory,"native-test");var shader=library.add("Negative","Imported Vulkan test",code);
        try {
            ManagedShaders.use(library);FilterGpuPipeline.validateCustom(code);
            var look=new LookSettings();assertTrue(look.add(shader.id()));
            pipeline.render(source,output,look,0);assertPixel(device,output.texture(),204,153,51);
            String invalid=code.replace("1.0 - original.rgb","missingFunction(original.rgb)");
            assertThrows(RuntimeException.class,()->FilterGpuPipeline.validateCustom(invalid));
            int usage=GpuTexture.USAGE_TEXTURE_BINDING|GpuTexture.USAGE_RENDER_ATTACHMENT|GpuTexture.USAGE_COPY_DST;
            try(var actor=device.createTexture("Custom actor depth",usage,GpuFormat.D32_FLOAT,64,64,1,1);
                var scene=device.createTexture("Custom scene depth",usage,GpuFormat.D32_FLOAT,64,64,1,1);
                var entities=device.createTexture("Custom entity depth",usage,GpuFormat.D32_FLOAT,64,64,1,1);
                var bounds=device.createTexture("Custom bounds",usage,GpuFormat.RGBA16_FLOAT,1,1,1,1);
                var av=device.createTextureView(actor);var sv=device.createTextureView(scene);
                var ev=device.createTextureView(entities);var bv=device.createTextureView(bounds)) {
                var encoder=device.createCommandEncoder();encoder.clearDepthTexture(actor,.6);encoder.clearDepthTexture(scene,.1);encoder.clearDepthTexture(entities,.6);encoder.submit();
                pipeline.renderActor(source,output,av,sv,ev,bv,look,0);assertPixel(device,output.texture(),204,153,51);
                encoder=device.createCommandEncoder();encoder.clearDepthTexture(entities,.8);encoder.clearColorTexture(output.texture(),new Vector4f(.2f,.4f,.8f,1));encoder.submit();
                pipeline.renderActor(source,output,av,sv,ev,bv,look,0);assertPixel(device,output.texture(),51,102,204);
            }
        }finally{ManagedShaders.use(null);}
    }

    private static void assertSharpMagnification(GpuDevice device,NativeActorLooks.Material material) {
        var id=net.minecraft.resources.Identifier.fromNamespaceAndPath("fbactors","test/sharp_skin");
        var layout=com.mojang.renderpearl.api.pipeline.BindGroupLayout.builder()
            .withUniform("Skin",com.mojang.renderpearl.api.pipeline.UniformType.COMBINED_IMAGE_SAMPLER).build();
        var definition=com.mojang.renderpearl.api.pipeline.RenderPipeline.builder().withLocation(id).withVertexShader(id).withFragmentShader(id)
            .withBindGroupLayout(layout).withCull(false).withDepthStencilState(Optional.empty())
            .withPrimitiveTopology(com.mojang.renderpearl.api.pipeline.PrimitiveTopology.TRIANGLES)
            .withColorTargetState(new com.mojang.renderpearl.api.pipeline.ColorTargetState(Optional.empty(),GpuFormat.RGBA8_UNORM,
                com.mojang.renderpearl.api.pipeline.ColorTargetState.WRITE_ALL)).build();
        var shader=new com.mojang.renderpearl.api.pipeline.ShaderSource() {
            @Override public String getShader(net.minecraft.resources.Identifier location,com.mojang.renderpearl.api.pipeline.ShaderType type) {
                return type==com.mojang.renderpearl.api.pipeline.ShaderType.VERTEX ? PortableLookShader.VERTEX : """
                    #version 330
                    #extension GL_ARB_separate_shader_objects : require
                    uniform sampler2D Skin;
                    layout(location=0) in vec2 texCoord;
                    layout(location=0) out vec4 fragColor;
                    void main(){fragColor=texture(Skin,texCoord);}
                    """;
            }
            @Override public CachedIncludeSource getInclude(net.minecraft.resources.Identifier location){return null;}
            @Override public void close(){}
        };
        int usage=GpuTexture.USAGE_RENDER_ATTACHMENT|GpuTexture.USAGE_COPY_SRC|GpuTexture.USAGE_TEXTURE_BINDING;
        try (var compiled=device.compilePipeline(definition,shader,Runnable::run).join().finishCompile();
             var pixels=new com.mojang.blaze3d.platform.NativeImage(2,1,false);
             var output=device.createTexture("Magnified actor skin",usage,GpuFormat.RGBA8_UNORM,64,64,1,1);
             var view=device.createTextureView(output);
             var read=device.createBuffer(()->"Skin edge readback",GpuBuffer.USAGE_MAP_READ|GpuBuffer.USAGE_COPY_DST,64*64*4)) {
            pixels.setPixelABGR(0,0,0xFFFFFFFF);pixels.setPixelABGR(1,0,0xFF000000);
            var encoder=device.createCommandEncoder();
            encoder.writeToTexture(material.getTexture(),pixels);
            try(var pass=encoder.createRenderPass(()->"Magnify material skin",view,Optional.empty())) {
                pass.setPipeline(compiled);pass.setUniform("Skin",material.getTextureView(),material.getSampler());pass.draw(3,1,0,0);
            }
            encoder.copyTextureToBuffer(output,read,0,()->{},0);
            try(var fence=encoder.createFence()){encoder.submit();assertTrue(fence.awaitCompletion(10_000_000_000L));}
            try(var mapped=read.map(true,false)) {
                for(int y=0;y<64;y++)for(int x=0;x<64;x++)
                    assertEquals(x<32?255:0,mapped.data().get((y*64+x)*4)&255,"Magnified skin pixels must not blend at their boundary");
            }
        }
    }

    private static void assertGlyphs(GpuDevice device, GpuTexture texture) {
        var encoder = device.createCommandEncoder();
        try (var buffer = device.createBuffer(() -> "ASCII readback", GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST, 64*64*4)) {
            encoder.copyTextureToBuffer(texture, buffer, 0, () -> {}, 0);
            try (var fence = encoder.createFence()) { encoder.submit(); assertTrue(fence.awaitCompletion(10_000_000_000L)); }
            try (var mapped = buffer.map(true, false)) {
                int dark=0, ink=0;
                for (int i=0; i<64*64; i++) {
                    int green=mapped.data().get(i*4+1)&255;
                    if (green<15) dark++;
                    if (green>80) ink++;
                    assertEquals(255, mapped.data().get(i*4+3)&255);
                }
                assertTrue(dark>100, "ASCII must leave space between glyph strokes");
                assertTrue(ink>100, "ASCII must render glyph strokes");
            }
        }
    }

    private static void assertEvents(GpuDevice device,PearlLookPipeline pipeline,GpuTexture source,
                                      com.mojang.renderpearl.api.textures.GpuTextureView output,int usage) {
        var clip=new pdx.dev.fbactors.events.EventClip();clip.launch=new pdx.dev.fbactors.events.EventClip.Point(0,0,0);
        clip.impact=new pdx.dev.fbactors.events.EventClip.Point(70,0,0);
        var rotation=new org.joml.Matrix4f().lookAt(85,55,125,35,23,0,0,1,0).getUnnormalizedRotation(new org.joml.Quaternionf()).conjugate();
        var camera=new pdx.dev.fbactors.events.EventCamera(new net.minecraft.world.phys.Vec3(85,55,125),rotation,
            new org.joml.Matrix4f().perspective((float)Math.toRadians(55),1,1024,.05f,true));
        try(var depth=device.createTexture("Event world depth",usage,GpuFormat.D32_FLOAT,64,64,1,1);
            var depthView=device.createTextureView(depth);
            var read=device.createBuffer(()->"Event readback",GpuBuffer.USAGE_MAP_READ|GpuBuffer.USAGE_COPY_DST,64*64*4)) {
            for(var eventKind:new pdx.dev.fbactors.events.EventKind[]{pdx.dev.fbactors.events.EventKind.ICBM,pdx.dev.fbactors.events.EventKind.NUCLEAR}) for(float depthValue:new float[]{0,1}) {
                clip.kind=eventKind;
                var encoder=device.createCommandEncoder();encoder.clearDepthTexture(depth,depthValue);encoder.submit();
                pipeline.renderEvent(source,output,depthView,pdx.dev.fbactors.events.EventRenderer.uniforms(clip,clip.end*.80,camera));
                encoder=device.createCommandEncoder();encoder.copyTextureToBuffer(output.texture(),read,0,()->{},0);
                try(var fence=encoder.createFence()) {encoder.submit();assertTrue(fence.awaitCompletion(10_000_000_000L));}
                try(var mapped=read.map(true,false)) {
                    int changed=0;var pixels=mapped.data();
                    for(int i=0;i<64*64;i++) {
                        assertEquals(255,pixels.get(i*4+3)&255);
                        if(Math.abs((pixels.get(i*4)&255)-51)>2 || Math.abs((pixels.get(i*4+1)&255)-102)>2 || Math.abs((pixels.get(i*4+2)&255)-204)>2)changed++;
                    }
                    if(depthValue==0)assertTrue(changed>20,"Native event explosion must render");
                    else assertEquals(0,changed,"Near world geometry must occlude the native event");
                }
            }
            pipeline.eventPreview(output,pdx.dev.fbactors.events.EventPreview.uniforms(28.6667,true));
            var spaceEncoder=device.createCommandEncoder();spaceEncoder.copyTextureToBuffer(output.texture(),read,0,()->{},0);
            try(var fence=spaceEncoder.createFence()) {spaceEncoder.submit();assertTrue(fence.awaitCompletion(10_000_000_000L));}
            try(var mapped=read.map(true,false)) {
                int blue=0,dark=0;var pixels=mapped.data();
                for(int i=0;i<64*64;i++) {
                    int r=pixels.get(i*4)&255,g=pixels.get(i*4+1)&255,b=pixels.get(i*4+2)&255;
                    assertEquals(255,pixels.get(i*4+3)&255);
                    if(b>r*1.1 && b>g && b>30)blue++;
                    if(r<25 && g<25 && b<25)dark++;
                }
                assertTrue(blue>100,"Native space view must show the textured blue Earth and atmosphere");
                assertTrue(dark>100,"Native space view must include black space");
            }
            for(int ending:new int[]{3}) {
                var mapClip=new pdx.dev.fbactors.events.EventClip();mapClip.eventCam=true;mapClip.ending=ending;
                var values=pdx.dev.fbactors.events.EventPreview.uniforms(mapClip,mapClip.end/20.*.82,true,0);
                pipeline.eventPreview(output,values);
                var encoder=device.createCommandEncoder();encoder.copyTextureToBuffer(output.texture(),read,0,()->{},0);
                try(var fence=encoder.createFence()) {encoder.submit();assertTrue(fence.awaitCompletion(10_000_000_000L));}
                try(var mapped=read.map(true,false)) {
                    int lit=0;var pixels=mapped.data();
                    for(int i=0;i<64*64;i++) {
                        assertEquals(255,pixels.get(i*4+3)&255);
                        if((pixels.get(i*4)&255)+(pixels.get(i*4+1)&255)+(pixels.get(i*4+2)&255)>40)lit++;
                    }
                    if(ending==3)assertEquals(0,lit,"Cut to black must hide the impact");
                    
                }
            }
            for(int detail=0;detail<3;detail++) {
                pipeline.eventPreview(output,pdx.dev.fbactors.events.EventPreview.uniforms(1.5,false,detail));
                var encoder=device.createCommandEncoder();encoder.copyTextureToBuffer(output.texture(),read,0,()->{},0);
                try(var fence=encoder.createFence()) {encoder.submit();assertTrue(fence.awaitCompletion(10_000_000_000L));}
                try(var mapped=read.map(true,false)) {
                    var pixels=mapped.data();int changed=0,dark=0;
                    for(int i=0;i<64*64;i++) {
                        assertEquals(255,pixels.get(i*4+3)&255);
                        if(Math.abs((pixels.get(i*4)&255)-51)>2)changed++;
                        if((pixels.get(i*4)&255)<95)dark++;
                    }
                    assertTrue(changed>500,"Native 3D preview must replace the source image");
                    assertTrue(dark>10,"Native 3D preview must show model geometry in each detail view");
                }
            }
            for(var kind:pdx.dev.fbactors.events.EventKind.LIBRARY) {
                var eventValues=pdx.dev.fbactors.events.EventPreview.uniforms(kind,1.5,false,0);
                pipeline.eventPreview(output,eventValues);
                var encoder=device.createCommandEncoder();encoder.copyTextureToBuffer(output.texture(),read,0,()->{},0);
                try(var fence=encoder.createFence()) {encoder.submit();assertTrue(fence.awaitCompletion(10_000_000_000L));}
                byte[] snapshot=new byte[64*64*4];
                try(var mapped=read.map(true,false)) {
                    int dark=0;var pixels=mapped.data();
                    for(int i=0;i<64*64;i++) {assertEquals(255,pixels.get(i*4+3)&255);if((pixels.get(i*4)&255)<90)dark++;}
                    if(!kind.natural())assertTrue(dark>10,"Native bomb model must be visible: "+kind);
                    pixels.get(0,snapshot);
                }
                if(kind.natural()) {
                    var reference=new java.util.HashMap<>(eventValues);reference.put("Type",2f);pipeline.eventPreview(output,reference);
                    var compare=device.createCommandEncoder();compare.copyTextureToBuffer(output.texture(),read,0,()->{},0);
                    try(var fence=compare.createFence()) {compare.submit();assertTrue(fence.awaitCompletion(10_000_000_000L));}
                    try(var mapped=read.map(true,false)) {
                        int changed=0;var pixels=mapped.data();for(int i=0;i<snapshot.length;i+=4)if(Math.abs((snapshot[i]&255)-(pixels.get(i)&255))+Math.abs((snapshot[i+1]&255)-(pixels.get(i+1)&255))+Math.abs((snapshot[i+2]&255)-(pixels.get(i+2)&255))>8)changed++;
                        assertTrue(changed>2,"Native 3D event must be visible: "+kind);
                    }
                }
                pipeline.eventPreview(output,pdx.dev.fbactors.events.EventPreview.uniforms(kind,kind==pdx.dev.fbactors.events.EventKind.NUCLEAR?12:8,true,0));
            }

        }
    }

    private static void assertPixel(GpuDevice device, GpuTexture texture, int red, int green, int blue) {
        var encoder = device.createCommandEncoder();
        try (var buffer = device.createBuffer(() -> "Filter readback", GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST, 64 * 64 * 4)) {
            encoder.copyTextureToBuffer(texture, buffer, 0, () -> {}, 0);
            try (var fence = encoder.createFence()) {
                encoder.submit();
                assertTrue(fence.awaitCompletion(10_000_000_000L));
            }
            try (var mapped = buffer.map(true, false)) {
                var pixels = mapped.data();
                assertEquals(red, pixels.get(0) & 255, 1);
                assertEquals(green, pixels.get(1) & 255, 1);
                assertEquals(blue, pixels.get(2) & 255, 1);
                assertEquals(255, pixels.get(3) & 255);
            }
        }
    }
}
