package pdx.dev.fbactors.morph;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GeoModelTest {
    static final String MODEL="""
        {"format_version":"1.12.0","minecraft:geometry":[{"description":{"identifier":"geometry.test","texture_width":64,"texture_height":64},
        "bones":[{"name":"root","pivot":[0,0,0],"cubes":[{"origin":[-4,0,-4],"size":[8,16,8],"uv":[0,0]}]},
        {"name":"head","parent":"root","pivot":[0,16,0],"cubes":[{"origin":[-4,16,-4],"size":[8,8,8],"uv":[24,0],"rotation":[0,20,0]}]}]}]}
        """;
    @Test void bakesParentedGeometryIntoVanillaModelParts() {
        var geometry=GeoModel.parse(MODEL);assertEquals(2,geometry.bones().size());
        var model=GeoModelParts.bake(geometry);assertEquals(2,model.bones().size());
        assertEquals(24,model.bones().get("root").y);
        assertEquals(-16,model.bones().get("head").y);
        assertNotNull(model.bones().get("root").getChild("cube_0"));
    }
    @Test void customRendererModelConstructsWithTheCurrentMinecraftModelApi() throws Exception {
        var asset=new CustomMobs.Asset("custom_test","Test",GeoModel.parse(MODEL),java.util.List.of(),java.nio.file.Path.of("unused.png"));
        var type=Class.forName("pdx.dev.fbactors.morph.CustomMobRenderBridge$Model");
        var constructor=type.getDeclaredConstructor(CustomMobs.Asset.class);constructor.setAccessible(true);
        assertNotNull(constructor.newInstance(asset));
    }
    @Test void invalidHierarchyAndUnsupportedUvAreRejected() {
        assertThrows(IllegalArgumentException.class,()->GeoModel.parse(MODEL.replace("\"parent\":\"root\"","\"parent\":\"head\"")));
        assertThrows(IllegalArgumentException.class,()->GeoModel.parse(MODEL.replace("\"parent\":\"root\"","\"parent\":\"missing\"")));
        assertThrows(IllegalArgumentException.class,()->GeoModel.parse(MODEL.replace("\"uv\":[0,0]","\"uv\":{}")));
        assertThrows(IllegalArgumentException.class,()->GeoModel.parse(MODEL.replace("\"size\":[8,16,8]","\"size\":[-8,16,8]")));
    }
    @Test void importedPartsEmitTexturedVerticesThroughTheVanillaRenderer() {
        var model=GeoModelParts.bake(GeoModel.parse(MODEL));
        var vertices=new java.util.ArrayList<float[]>();
        var sink=(com.mojang.blaze3d.vertex.VertexConsumer)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
            new Class<?>[]{com.mojang.blaze3d.vertex.VertexConsumer.class},(proxy,method,args)->{
                if(method.isDefault())return java.lang.reflect.InvocationHandler.invokeDefault(proxy,method,args);
                if(method.getName().equals("addVertex")&&args.length==3&&args[0] instanceof Float)
                    vertices.add(new float[]{(float)args[0],(float)args[1],(float)args[2]});
                return proxy;
            });
        model.root().render(new com.mojang.blaze3d.vertex.PoseStack(),sink,0xF000F0,0);
        assertEquals(48,vertices.size());
        for(var vertex:vertices)for(float value:vertex)assertTrue(Float.isFinite(value));
        assertEquals(1.5,vertices.stream().mapToDouble(v->v[1]).max().orElseThrow(),.001);
    }
    @Test void importedTextureMustBeARealPngWithMatchingDimensions() throws Exception {
        var model=GeoModel.parse(MODEL);
        var bytes=new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(64,64,java.awt.image.BufferedImage.TYPE_INT_ARGB),"png",bytes);
        assertDoesNotThrow(()->CustomMobs.validateTexture(bytes.toByteArray(),model));
        assertThrows(java.io.IOException.class,()->CustomMobs.validateTexture(new byte[30],model));
        var wrong=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(32,32,2),"png",wrong);
        assertThrows(java.io.IOException.class,()->CustomMobs.validateTexture(wrong.toByteArray(),model));
    }
    @Test void numericAnimationInterpolatesAndScrubsDeterministically() {
        String animation="""
            {"animations":{"animation.test.idle":{"loop":true,"animation_length":2,"bones":{"head":{"rotation":{"0":[0,0,0],"1":[0,90,0],"2":[0,0,0]},"position":[1,2,3],"scale":[1,1,1]}}}}}
            """;
        var geometry=GeoModel.parse(MODEL);var clip=GeoAnimations.parse(animation,geometry).getFirst();
        assertEquals(45,clip.bones().get("head").rotation().sample(.5f).y());
        var model=GeoModelParts.bake(geometry);
        GeoModelParts.animate(model,clip,.5f);float rotation=model.bones().get("head").yRot;
        GeoModelParts.animate(model,clip,1);GeoModelParts.animate(model,clip,.5f);
        assertEquals(rotation,model.bones().get("head").yRot);
        GeoModelParts.animate(model,clip,2.5f);assertEquals(rotation,model.bones().get("head").yRot);
        assertEquals(-18,model.bones().get("head").y);
    }
    @Test void unsupportedAnimationExpressionsAreAnImportError() {
        assertThrows(IllegalArgumentException.class,()->GeoAnimations.parse("{\"animations\":{\"idle\":{\"bones\":{\"head\":{\"rotation\":[\"query.life_time\",0,0]}}}}}",GeoModel.parse(MODEL)));
        assertThrows(IllegalArgumentException.class,()->GeoAnimations.parse("{\"animations\":{\"idle\":{\"bones\":{\"unknown\":{\"rotation\":[0,0,0]}}}}}",GeoModel.parse(MODEL)));
    }
}
