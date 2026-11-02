package pdx.dev.fbactors.morph;

import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.resources.Identifier;
import net.minecraft.client.model.object.armorstand.ArmorStandArmorModel;

public final class CustomMobRenderBridge {
    private static EntityRendererProvider.Context context;
    private static final Map<String,Renderer> RENDERERS=new HashMap<>();
    private static final Map<String,Identifier> TEXTURES=new HashMap<>();
    private CustomMobRenderBridge() {}
    public static void context(EntityRendererProvider.Context value) {context=value;RENDERERS.clear();}
    public static EntityRenderer route(Object value) {
        String id=value instanceof Entity entity?CustomMobs.entity(entity):value instanceof State state?state.asset:null;
        var asset=CustomMobs.asset(id);if(asset==null||context==null)return null;
        return RENDERERS.computeIfAbsent(id,ignored->new Renderer(context,asset));
    }
    private static Identifier texture(CustomMobs.Asset asset) {
        return TEXTURES.computeIfAbsent(asset.id(),id->{
            try(var input=java.nio.file.Files.newInputStream(asset.texture())) {
                var image=com.mojang.blaze3d.platform.NativeImage.read(input);
                var texture=new net.minecraft.client.renderer.texture.DynamicTexture(()->"Custom actor model",image) {
                    @Override public com.mojang.renderpearl.api.textures.GpuSampler getSampler() {
                        return com.mojang.blaze3d.systems.RenderSystem.getSamplerCache().getSampler(
                            com.mojang.renderpearl.api.textures.AddressMode.CLAMP_TO_EDGE,com.mojang.renderpearl.api.textures.AddressMode.CLAMP_TO_EDGE,
                            com.mojang.renderpearl.api.textures.FilterMode.NEAREST,com.mojang.renderpearl.api.textures.FilterMode.NEAREST,false);
                    }
                };
                var location=Identifier.fromNamespaceAndPath("fbactors","custom_mobs/"+id);
                Minecraft.getInstance().getTextureManager().register(location,texture);return location;
            }catch(java.io.IOException error){throw new IllegalStateException("Could not load custom model texture",error);}
        });
    }
    public static final class State extends net.minecraft.client.renderer.entity.state.ArmorStandRenderState {
        final String asset;java.util.UUID entity;float seconds;GeoAnimations.Clip clip;
        State(String asset){this.asset=asset;}
    }
    private static final class Renderer extends LivingEntityRenderer<ArmorStand,State,ArmorStandArmorModel> {
        private final CustomMobs.Asset asset;
        Renderer(EntityRendererProvider.Context context,CustomMobs.Asset asset){super(context,new Model(asset),.3f);this.asset=asset;}
        @Override public State createRenderState(){return new State(asset.id());}
        @Override public Identifier getTextureLocation(State state){return texture(asset);}
        @Override public void extractRenderState(ArmorStand entity,State state,float partial){
            super.extractRenderState(entity,state,partial);state.entity=entity.getUUID();
            state.seconds=CustomMobs.time(state.entity,Math.max(0,pdx.dev.fbactors.FlashbackActors.replayTick())/20f);
            state.clip=CustomMobs.clip(asset.id(),state.entity,(float)entity.getDeltaMovement().horizontalDistance());state.nameTag=null;
        }
    }
    private static final class Model extends ArmorStandArmorModel {
        private final GeoModelParts.Built parts;
        Model(CustomMobs.Asset asset){this(GeoModelParts.bake(asset.model()));}
        Model(GeoModelParts.Built parts){super(parts.root());this.parts=parts;}
        @Override public void setupAnim(net.minecraft.client.renderer.entity.state.ArmorStandRenderState state){
            if(state instanceof State custom)GeoModelParts.animate(parts,custom.clip,custom.seconds);
        }
    }
}
