package pdx.dev.fbactors.morph;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.world.entity.AnimationState;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import pdx.dev.fbactors.compat.Compat;

public final class MorphPreviewAnimator {
    private record Channel(String label, AnimationState state) {}
    private record Stage(String label, String action, boolean baby, int channel) {}
    private final LivingEntity mob;
    private final String morphId;
    private String pose="auto";
    private String cameraAction="";
    private net.minecraft.world.entity.Entity previewMount;
    private final List<Channel> channels = new ArrayList<>();
    private final List<Stage> stages = new ArrayList<>();
    private int lastTick = -1;
    private int lastStage = -1;
    private int lastStart = -1;

    public MorphPreviewAnimator(LivingEntity mob, String id) {
        this.mob = mob;
        morphId=id;
        for (Class<?> type = mob.getClass(); type != null && type != LivingEntity.class; type = type.getSuperclass()) {
            for (var field : type.getDeclaredFields()) {
                if (field.getType() != AnimationState.class || Modifier.isStatic(field.getModifiers())) continue;
                try {
                    field.setAccessible(true);
                    var state = (AnimationState) field.get(mob);
                    if (state != null) channels.add(new Channel(label(field.getName(), channels.size()), state));
                } catch (ReflectiveOperationException | RuntimeException ignored) {}
            }
        }
        add("Idle", "idle", false);
        add("Walk", "walk", false);
        add("Run", "run", false);
        var definition = Morph.registry().byId(id);
        if (definition != null && definition.stance() == MorphStance.HUMANOID) add("Crouch", "crouch", false);
        if (definition != null && definition.swimmer()) add("Swim", "swim", false);
        if (Set.of("allay", "bat", "bee", "blaze", "breeze", "ghast", "parrot", "phantom", "vex", "wither", "ender_dragon").contains(id)) add("Flight", "fly", false);
        add("Attack", "attack", false);
        add("Hurt", "hurt", false);
        add("Death", "death", false);
        if (mob instanceof AgeableMob || Set.of("zombie", "husk", "drowned", "zombie_villager", "piglin", "zombified_piglin").contains(id)) {
            add("Baby idle", "idle", true);
            add("Baby walk", "walk", true);
            add("Adult", "idle", false);
        }
        if (id.equals("iron_golem")) {
            add("Healthy", "healthy", false);
            add("Worn", "worn", false);
            add("Cracked", "cracked", false);
            add("Critical", "critical", false);
        }
        for (String variant : MorphPreviewVariants.stages(mob)) stages.add(new Stage(variant, "variant", false, -2));
        for (int i = 0; i < channels.size(); i++) stages.add(new Stage("Native: " + channels.get(i).label(), "native", false, i));
    }

    private void add(String label, String action, boolean baby) { stages.add(new Stage(label, action, baby, -1)); }
    private static String label(String name, int index) {
        if (name.startsWith("field_") || name.matches("[a-zA-Z]{1,3}")) return "Mob animation " + (index + 1);
        String text = name.replace("AnimationState", "").replace("Animation", "").replaceAll("([a-z])([A-Z])", "$1 $2").replace('_', ' ').strip();
        return text.isEmpty() ? "Mob animation " + (index + 1) : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
    public float framingWidth() { return mob.getBbWidth() * (stages.stream().anyMatch(stage -> stage.label().equals("Large")) ? 4 : 1); }
    public float framingHeight() { return mob.getBbHeight() * (stages.stream().anyMatch(stage -> stage.label().equals("Large")) ? 4 : 1); }
    public List<String> stages() { return stages.stream().map(Stage::label).toList(); }
    public void pose(String value) { pose=MorphPoses.normalize(morphId,value); }
    public void cameraAction(String value) { cameraAction=value; }
    public void cameraMount(net.minecraft.world.entity.Entity mount) {
        previewMount=mount;
        if(mount!=null && mob.getVehicle()!=mount) { mob.stopRiding();mob.startRiding(mount); }
    }

    public void apply(MorphPreviewPlayback.Frame frame) {
        int activeStage="auto".equals(pose)?Math.max(0,Math.min(frame.stage(),stages.size()-1)):0;
        var stage = stages.get(activeStage);
        String action=cameraAction.isEmpty()?stage.action():cameraAction;
        if(action.equals("ride") && mob.getVehicle()==null) {
            if(previewMount==null) previewMount=Compat.createMorphPreview("horse");
            if(previewMount!=null) mob.startRiding(previewMount);
        } else if(!action.equals("ride") && previewMount!=null) mob.stopRiding();
        boolean restart = lastStage != activeStage || lastStart != frame.startTick();
        if (restart) {
            channels.forEach(channel -> channel.state().stop());
            if (stage.channel() >= 0) channels.get(stage.channel()).state().start(frame.startTick());
            else if (action.equals("idle")) channels.stream().filter(channel -> channel.label().equalsIgnoreCase("Idle")).forEach(channel -> channel.state().start(frame.startTick()));
            lastStage = activeStage;
            lastStart = frame.startTick();
        }
        if (mob instanceof Mob living) {
            if (living.isBaby() != stage.baby()) living.setBaby(stage.baby());
            living.setAggressive(stage.action().equals("attack"));
        }
        boolean swimming = action.equals("swim"), gliding=action.equals("glide"), flying = gliding || action.equals("fly");
        mob.setSwimming(swimming);
        mob.setSprinting(action.equals("run"));
        mob.setShiftKeyDown(action.equals("crouch"));
        mob.setOnGround(!swimming && !flying);
        mob.setPose(swimming ? Pose.SWIMMING : flying ? Pose.FALL_FLYING : action.equals("crouch") ? Pose.CROUCHING : Pose.STANDING);
        mob.setDeltaMovement(0, 0, swimming || flying ? 0.15 : action.equals("walk") || action.equals("run")?.1:0);
        MorphPreviewVariants.apply(mob, stage.channel() == -2 ? stage.label() : "");
        float health = switch (stage.action()) { case "death" -> 0; case "worn" -> 0.7f; case "cracked" -> 0.4f; case "critical" -> 0.15f; default -> 1; };
        mob.setHealth(mob.getMaxHealth() * health);
        mob.hurtTime = stage.action().equals("hurt") ? 10 - (int) frame.localTicks() % 20 : 0;
        mob.hurtTime = Math.max(0, mob.hurtTime);
        mob.deathTime = stage.action().equals("death") ? Math.min(20, (int) frame.localTicks()) : 0;
        float speed = switch (action) { case "walk", "swim" -> 0.45f; case "run" -> 0.9f; case "crouch" -> 0.18f; default -> 0; };
        WalkDriver.driveTo(mob, speed, speed, frame.localTicks() * speed);
        if(!cameraAction.isEmpty()) {
            ((pdx.dev.fbactors.mixin.CameraPreviewFlightAccess)mob).fbactors$flightFlag(7,gliding);
            ((pdx.dev.fbactors.mixin.CameraPreviewGlideTicks)mob).fbactors$glideTicks(gliding?20+(int)frame.ticks():0);
            if(gliding && restart) for(int i=0;i<12;i++) Compat.previewGlide(mob);
            // Inventory rendering faces the entity toward -Z; pack direction variables need matching motion.
            mob.setPos(0,64,-frame.ticks()*.1*(action.equals("run")?2:1));
            mob.setDeltaMovement(0,0,flying?-.6:action.equals("run")?-.2:action.equals("walk")?-.1:0);
            mob.setYRot(180);mob.yRotO=180;mob.yBodyRot=mob.yBodyRotO=180;mob.yHeadRot=mob.yHeadRotO=180;
            if(gliding) {
                double seconds=frame.ticks()/20.0,horizontal=Math.hypot(Math.cos(seconds*.55)*.22,.3),vertical=Math.cos(seconds*.4)*.1;
                mob.setPos(0,64+Math.sin(seconds*.4)*5,-seconds*6);
                mob.setDeltaMovement(0,vertical,-horizontal);
                mob.setXRot((float)-Math.toDegrees(Math.atan2(vertical,horizontal)));mob.xRotO=mob.getXRot();
            }
            if(action.equals("ride") && previewMount!=null) {
                if(previewMount instanceof pdx.dev.fbactors.actor.BoatPhysics.Boat) pdx.dev.fbactors.camera.CameraPreviewCapture.rowing(frame.ticks());
                var seat=previewMount.getPassengerRidingPosition(mob).subtract(mob.getVehicleAttachmentPoint(previewMount));
                mob.setPos(seat.x,seat.y,seat.z);
            }
        }
        int tick = (int) frame.ticks();
        for (int value = Math.max(lastTick + 1, tick - 5); value <= tick; value++) {
            mob.tickCount = value;
            if(gliding && !cameraAction.isEmpty()) Compat.previewGlide(mob);

            if (stage.action().equals("attack") && (restart || value % 20 == 0)) { Compat.previewSwing(mob, net.minecraft.world.InteractionHand.MAIN_HAND); restart = false; }

            Compat.tickSwing(mob);
        }
        mob.tickCount = tick;
        lastTick = tick;
        MorphPoseApplier.apply(mob,MorphPoses.preview(morphId,pose,stage.action().equals("variant")?stage.label():""),stage.action().equals("crouch"));
    }
}
