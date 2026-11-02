package pdx.dev.fbactors.filters;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.Minecraft;

final class NativeLookResources {
    private static PearlLookPipeline scene;
    private static GpuTexture atlas;
    private static GpuTextureView atlasView;
    private static long atlasHandle;
    private static GpuTexture customSource,customOutput;
    private static GpuTextureView customView;
    private static PearlLookPipeline customPipeline;
    private static long customHandle;
    private static String customId;
    private static float customTime=Float.NaN;
    static long customPreview(String id,float time) {
        if(customSource==null) {
            try(var stream=NativeLookResources.class.getResourceAsStream("/assets/fbactors/filters/demo.jpg")) {
                if(stream==null)throw new IllegalStateException("Missing preview photo");
                try(var image=NativeImage.read(stream)) {
                    var device=RenderSystem.getDevice();
                    int usage=GpuTexture.USAGE_COPY_DST|GpuTexture.USAGE_TEXTURE_BINDING|GpuTexture.USAGE_RENDER_ATTACHMENT;
                    customSource=device.createTexture("Custom shader photo",usage,GpuFormat.RGBA8_UNORM,image.getWidth(),image.getHeight(),1,1);
                    customOutput=device.createTexture("Custom shader preview",usage,GpuFormat.RGBA8_UNORM,image.getWidth(),image.getHeight(),1,1);
                    customView=device.createTextureView(customOutput);customPipeline=new PearlLookPipeline(device);
                    var encoder=device.createCommandEncoder();encoder.writeToTexture(customSource,image);encoder.submit();
                    var renderer=com.moulberry.flashback.editor.ui.ReplayUI.imguiRenderer;
                    customHandle=renderer.getTextureId(customView);renderer.setSampleLinear(customHandle);
                }
            }catch(java.io.IOException error){throw new IllegalStateException(error);}
        }
        if(!id.equals(customId)||time!=customTime) {
            var look=new LookSettings();look.add(id);customPipeline.render(customSource,customView,look,time);customId=id;customTime=time;
        }
        return customHandle;
    }
    private static void closeCustom() {
        if(customPipeline!=null)customPipeline.close();if(customView!=null)customView.close();
        if(customSource!=null)customSource.close();if(customOutput!=null)customOutput.close();
        customPipeline=null;customView=null;customSource=customOutput=null;customHandle=0;customId=null;customTime=Float.NaN;
    }
    private NativeLookResources() {}

    static boolean render(LookSettings look, float time) {
        if (scene == null) scene = new PearlLookPipeline(RenderSystem.getDevice());
        var target = FilterRenderBridge.target(Minecraft.getInstance());
        if (target == null || target.width <= 0 || target.height <= 0) return false;
        scene.render(target.getColorTexture(), target.getColorTextureView(), look, time);
        return true;
    }

    static long previews() {
        if (atlasHandle != 0) return atlasHandle;
        try (var stream = NativeLookResources.class.getResourceAsStream("/assets/fbactors/filters/demo-atlas.png")) {
            if (stream == null) throw new IllegalStateException("Missing look preview atlas");
            try (var image = NativeImage.read(stream)) {
                var device = RenderSystem.getDevice();
                atlas = device.createTexture("Actors look library", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING,
                    GpuFormat.RGBA8_UNORM, image.getWidth(), image.getHeight(), 1, 1);
                atlasView = device.createTextureView(atlas);
                var encoder = device.createCommandEncoder();
                encoder.writeToTexture(atlas, image);
                encoder.submit();
                var renderer = com.moulberry.flashback.editor.ui.ReplayUI.imguiRenderer;
                atlasHandle = renderer.getTextureId(atlasView);
                renderer.setSampleLinear(atlasHandle);
                return atlasHandle;
            }
        } catch (java.io.IOException error) { closePreviews(); throw new IllegalStateException(error); }
    }

    static void closePreviews() {
        if (atlasView != null) atlasView.close();
        if (atlas != null) atlas.close();
        atlas = null; atlasView = null; atlasHandle = 0;
    }

    static void close() {
        if (scene != null) scene.close();
        scene = null;
        closePreviews();closeCustom();
    }
}
