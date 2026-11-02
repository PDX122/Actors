package pdx.dev.fbactors.morph;

import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import java.util.*;
import static pdx.dev.fbactors.morph.GeoModel.*;

public final class GeoModelParts {
    public record Built(ModelPart root,Map<String,ModelPart> bones) {}
    private GeoModelParts() {}
    public static Built bake(GeoModel geometry) {
        var mesh=new MeshDefinition();var root=mesh.getRoot();
        for(String name:List.of("head","hat","body","right_arm","left_arm","right_leg","left_leg"))root.addOrReplaceChild(name,CubeListBuilder.create(),PartPose.ZERO);
        root.getChild("head").addOrReplaceChild("hat",CubeListBuilder.create(),PartPose.ZERO);
        var parts=new HashMap<String,PartDefinition>();var byName=new HashMap<String,Bone>();geometry.bones().forEach(b->byName.put(b.name(),b));
        for(var bone:geometry.bones())build(bone,byName,parts,root.getChild("body"));
        var baked=LayerDefinition.create(mesh,geometry.textureWidth(),geometry.textureHeight()).bakeRoot();
        var result=new HashMap<String,ModelPart>();
        for(var bone:geometry.bones())find(bone,byName,result,baked.getChild("body"));
        return new Built(baked,Map.copyOf(result));
    }
    private static PartDefinition build(Bone bone,Map<String,Bone> all,Map<String,PartDefinition> parts,PartDefinition root) {
        if(parts.containsKey(bone.name()))return parts.get(bone.name());
        var parent=bone.parent()==null?root:build(all.get(bone.parent()),all,parts,root);
        var base=bone.parent()==null?new Vec(0,24,0):all.get(bone.parent()).pivot();var p=bone.pivot();var r=bone.rotation();
        var part=parent.addOrReplaceChild("geo_"+bone.name(),CubeListBuilder.create(),PartPose.offsetAndRotation(
            base.x()-p.x(),base.y()-p.y(),p.z()-base.z(),rad(-r.x()),rad(-r.y()),rad(r.z())));
        int index=0;
        for(var cube:bone.cubes()) {
            var origin=cube.origin();var size=cube.size();var pivot=cube.pivot();var rotation=cube.rotation();
            var builder=CubeListBuilder.create().texOffs(cube.u(),cube.v()).mirror(cube.mirror()).addBox(
                pivot.x()-origin.x()-size.x(),pivot.y()-origin.y()-size.y(),origin.z()-pivot.z(),size.x(),size.y(),size.z(),new CubeDeformation(cube.inflate()));
            part.addOrReplaceChild("cube_"+index++,builder,PartPose.offsetAndRotation(p.x()-pivot.x(),p.y()-pivot.y(),pivot.z()-p.z(),rad(-rotation.x()),rad(-rotation.y()),rad(rotation.z())));
        }
        parts.put(bone.name(),part);return part;
    }
    private static ModelPart find(Bone bone,Map<String,Bone> all,Map<String,ModelPart> parts,ModelPart root) {
        if(parts.containsKey(bone.name()))return parts.get(bone.name());
        var parent=bone.parent()==null?root:find(all.get(bone.parent()),all,parts,root);
        var part=parent.getChild("geo_"+bone.name());parts.put(bone.name(),part);return part;
    }
    private static float rad(float degrees){return (float)Math.toRadians(degrees);}
    public static void animate(Built model,GeoAnimations.Clip clip,float seconds) {
        model.bones().values().forEach(ModelPart::resetPose);
        if(clip==null)return;
        float time=clip.loop()?seconds%clip.length():Math.min(seconds,clip.length());
        for(var entry:clip.bones().entrySet()) {
            var part=model.bones().get(entry.getKey());if(part==null)continue;var motion=entry.getValue();
            if(motion.position()!=null){var p=motion.position().sample(time);part.x-=p.x();part.y-=p.y();part.z+=p.z();}
            if(motion.rotation()!=null){var r=motion.rotation().sample(time);part.xRot+=rad(-r.x());part.yRot+=rad(-r.y());part.zRot+=rad(r.z());}
            if(motion.scale()!=null){var s=motion.scale().sample(time);part.xScale=s.x();part.yScale=s.y();part.zScale=s.z();}
        }
    }
}
