package pdx.dev.fbactors.morph;

public final class ModdedMobs {
    private static Object level;
    private ModdedMobs() {}
    public static void tick() {
        var current=net.minecraft.client.Minecraft.getInstance().level;
        if(current==level)return;level=current;CustomMobs.clearInstances();
        if(current!=null)pdx.dev.fbactors.compat.Compat.discoverModdedMobs();
    }
    public static MorphDefinition definition(String id,String name,float width,float height) {
        return new MorphDefinition(id,name,"modded",1,width,height,Math.max(.1,height*.85),0,MorphStance.HUMANOID,false,false,false);
    }
}
