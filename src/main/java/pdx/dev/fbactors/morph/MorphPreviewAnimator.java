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
    private final List<Channel> channels = new ArrayList<>();
    private final List<Stage> stages = new ArrayList<>();
    private int lastTick = -1;
    private int lastStage = -1;
    private int lastStart = -1;

    public MorphPreviewAnimator(LivingEntity mob, String id) {
        this.mob = mob;
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

    public void apply(MorphPreviewPlayback.Frame frame) {
        var stage = stages.get(Math.max(0, Math.min(frame.stage(), stages.size() - 1)));
        boolean restart = lastStage != frame.stage() || lastStart != frame.startTick();
        if (restart) {
            channels.forEach(channel -> channel.state().stop());
            if (stage.channel() >= 0) channels.get(stage.channel()).state().start(frame.startTick());
            else if (stage.action().equals("idle")) channels.stream().filter(channel -> channel.label().equalsIgnoreCase("Idle")).forEach(channel -> channel.state().start(frame.startTick()));
            lastStage = frame.stage();
            lastStart = frame.startTick();
        }
        if (mob instanceof Mob living) {
            if (living.isBaby() != stage.baby()) living.setBaby(stage.baby());
            living.setAggressive(stage.action().equals("attack"));
        }
        boolean swimming = stage.action().equals("swim"), flying = stage.action().equals("fly");
        mob.setSwimming(swimming);
        mob.setSprinting(stage.action().equals("run"));
        mob.setShiftKeyDown(stage.action().equals("crouch"));
        mob.setOnGround(!swimming && !flying);
        mob.setPose(swimming ? Pose.SWIMMING : flying ? Pose.FALL_FLYING : stage.action().equals("crouch") ? Pose.CROUCHING : Pose.STANDING);
        mob.setDeltaMovement(0, 0, swimming || flying ? 0.15 : 0);
        MorphPreviewVariants.apply(mob, stage.channel() == -2 ? stage.label() : "");
        float health = switch (stage.action()) { case "death" -> 0; case "worn" -> 0.7f; case "cracked" -> 0.4f; case "critical" -> 0.15f; default -> 1; };
        mob.setHealth(mob.getMaxHealth() * health);
        mob.hurtTime = stage.action().equals("hurt") ? 10 - (int) frame.localTicks() % 20 : 0;
        mob.hurtTime = Math.max(0, mob.hurtTime);
        mob.deathTime = stage.action().equals("death") ? Math.min(20, (int) frame.localTicks()) : 0;
        float speed = switch (stage.action()) { case "walk", "swim" -> 0.45f; case "run" -> 0.9f; case "crouch" -> 0.18f; default -> 0; };
        WalkDriver.driveTo(mob, speed, speed, frame.localTicks() * speed);
        int tick = (int) frame.ticks();
        for (int value = Math.max(lastTick + 1, tick - 5); value <= tick; value++) {
            mob.tickCount = value;
            // Preview-only swing: previewSwing starts the swing on the preview
            // mob's own fields without the ServerLevel broadcast branch of
            // swing(hand, true), so a never-added ClientLevel preview mob
            // cannot leak packets or level events into world state. The world
            // puppet path (Compat.swing via mirrorSwing) is deliberately untouched.
            if (stage.action().equals("attack") && (restart || value % 20 == 0)) { Compat.previewSwing(mob, net.minecraft.world.InteractionHand.MAIN_HAND); restart = false; }
            // Local-only: updateSwingTime()/SwingState.tick() advance the mob's
            // own swing progress fields; no packets, no game events, no level access.
            Compat.tickSwing(mob);
        }
        mob.tickCount = tick;
        lastTick = tick;
    }
}
