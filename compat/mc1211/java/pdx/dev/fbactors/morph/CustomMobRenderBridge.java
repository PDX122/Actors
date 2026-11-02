package pdx.dev.fbactors.morph;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.model.ArmorStandArmorModel;

public final class CustomMobRenderBridge {
    private static EntityRendererProvider.Context context;
    private static final Map<String,Renderer> RENDERERS=new HashMap<>();
    private static final Map<String,ResourceLocation> TEXTURES=new HashMap<>();
    private CustomMobRenderBridge() {}
    public static void context(EntityRendererProvider.Context value) {context=value;RENDERERS.clear();}
    public static EntityRenderer route(Object value) {
        String id=value instanceof Entity entity?CustomMobs.entity(entity):null;
        var asset=CustomMobs.asset(id);if(asset==null||context==null)return null;
        return RENDERERS.computeIfAbsent(id,ignored->new Renderer(context,asset));
    }
    private static ResourceLocation texture(CustomMobs.Asset asset) {
        return TEXTURES.computeIfAbsent(asset.id(),id->{
            try(var input=java.nio.file.Files.newInputStream(asset.texture())) {
                var image=com.mojang.blaze3d.platform.NativeImage.read(input);
                var texture=new net.minecraft.client.renderer.texture.DynamicTexture(image);
                texture.setFilter(false,false);
                var location=ResourceLocation.fromNamespaceAndPath("fbactors","custom_mobs/"+id);
                Minecraft.getInstance().getTextureManager().register(location,texture);return location;
            }catch(java.io.IOException error){throw new IllegalStateException("Could not load custom model texture",error);}
        });
    }
    private static final class Renderer extends LivingEntityRenderer<ArmorStand,ArmorStandArmorModel> {
        private final CustomMobs.Asset asset;
        Renderer(EntityRendererProvider.Context context,CustomMobs.Asset asset){super(context,new Model(asset),.3f);this.asset=asset;}
        @Override public ResourceLocation getTextureLocation(ArmorStand entity){return texture(asset);}
    }
    private static final class Model extends ArmorStandArmorModel {
        private final CustomMobs.Asset asset;private final GeoModelParts.Built parts;
        Model(CustomMobs.Asset asset){this(asset,GeoModelParts.bake(asset.model()));}
        Model(CustomMobs.Asset asset,GeoModelParts.Built parts){super(parts.root());this.parts=parts;this.asset=asset;}
        @Override public void setupAnim(ArmorStand entity,float limb,float speed,float age,float yaw,float pitch){
            var clip=CustomMobs.clip(asset.id(),entity.getUUID(),speed);
            GeoModelParts.animate(parts,clip,CustomMobs.time(entity.getUUID(),Math.max(0,pdx.dev.fbactors.FlashbackActors.replayTick())/20f));
        }
    }
}
