package pdx.dev.fbactors.camera;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import com.mojang.blaze3d.platform.NativeImage;
import pdx.dev.fbactors.compat.Compat;
import pdx.dev.fbactors.FlashbackActors;
import java.util.*;

public final class CameraPreviewAssets {
    private record Floor(Object source,DynamicTexture texture) {}
    private static final Map<String,Floor> floor=new HashMap<>();
    private static final Set<String> failed=new HashSet<>();
    private static final Set<UUID> requestedSkins=new HashSet<>();
    public static final int FLOOR_REPEAT=64;
    private CameraPreviewAssets() {}
    public static void retryFailed() { failed.clear(); requestedSkins.clear(); }
    public static UUID subject() {
        return Minecraft.getInstance().getUser().getProfileId();
    }
    public static long skin(UUID id) {
        if(requestedSkins.add(id)) pdx.dev.fbactors.skin.SkinTextures.findAsync(id);
        var downloaded=pdx.dev.fbactors.skin.SkinTextures.headLocation(id);
        var handle=downloaded==null?Compat.headTexture(id):Compat.cameraPreviewTexture(downloaded.toString());
        if(handle!=0) Compat.sampleNearest(handle);
        return handle;
    }
    public static boolean slim(UUID id) { return Compat.cameraPreviewSlim(id); }
    public static long floor(String name) {
        var mc=Minecraft.getInstance();
        String id="fbactors:camera_preview/"+name;
        var resource=mc.getResourceManager().getResource(Compat.boatTextureId("minecraft:textures/block/"+name+".png")).orElse(null);
        if(resource==null) return 0;
        var cached=floor.get(name);
        if(cached!=null && cached.source()==resource.source()
            && mc.getTextureManager().getTexture(Compat.boatTextureId(id))==cached.texture()) {
            long handle=Compat.cameraPreviewTexture(id); if(handle!=0) Compat.sampleNearest(handle); return handle;
        }
        if(failed.contains(name)) return 0;
        try {
            if(cached!=null) mc.getTextureManager().release(Compat.boatTextureId(id));
            NativeImage image;

            try(var input=resource.open()) {
                var tile=javax.imageio.ImageIO.read(input);
                if(tile==null) throw new java.io.IOException("Invalid floor image");
                var atlas=new java.awt.image.BufferedImage(tile.getWidth()*FLOOR_REPEAT,tile.getWidth()*FLOOR_REPEAT,java.awt.image.BufferedImage.TYPE_INT_ARGB);
                var graphics=atlas.createGraphics();
                try {
                    for(int row=0;row<FLOOR_REPEAT;row++) for(int col=0;col<FLOOR_REPEAT;col++)
                        graphics.drawImage(tile,col*tile.getWidth(),row*tile.getWidth(),(col+1)*tile.getWidth(),(row+1)*tile.getWidth(),0,0,tile.getWidth(),tile.getWidth(),null);
                } finally { graphics.dispose(); }
                var png=new java.io.ByteArrayOutputStream();
                javax.imageio.ImageIO.write(atlas,"png",png);
                try(var encoded=new java.io.ByteArrayInputStream(png.toByteArray())) { image=NativeImage.read(encoded); }
            }
            DynamicTexture texture;
            try { texture=Compat.registerBoatTexture(id,image); }
            catch(RuntimeException | LinkageError error) { image.close(); throw error; }
            floor.put(name,new Floor(resource.source(),texture));
            long handle=Compat.cameraPreviewTexture(id); if(handle!=0) Compat.sampleNearest(handle); return handle;
        } catch(Exception | LinkageError error) {
            failed.add(name); FlashbackActors.LOGGER.warn("Camera preview floor texture {} is unavailable",name,error); return 0;
        }
    }
}
