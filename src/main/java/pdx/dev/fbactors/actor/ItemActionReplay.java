package pdx.dev.fbactors.actor;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import pdx.dev.fbactors.FlashbackActors;
import pdx.dev.fbactors.compat.Compat;
import pdx.dev.fbactors.take.ActorUse;

public final class ItemActionReplay {
    private static final ThreadLocal<Boolean> MUTING = ThreadLocal.withInitial(() -> false);
    private ItemActionReplay() {}

    public static boolean usesVanilla(String id) {
        return ItemActionPolicy.usesVanilla(id);
    }

    public static boolean suppressSounds() { return MUTING.get(); }

    public static boolean canReachBlock(ServerLevel level, ActorClonePlayer clone, net.minecraft.core.BlockPos pos) {
        var hit = aimedBlock(level, clone);
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK && hit.getBlockPos().equals(pos);
    }

    private static net.minecraft.world.phys.BlockHitResult aimedBlock(ServerLevel level, ActorClonePlayer clone) {
        var from = clone.getEyePosition();
        return level.clip(new net.minecraft.world.level.ClipContext(from,
            from.add(clone.getViewVector(1.0f).scale(clone.blockInteractionRange())),
            net.minecraft.world.level.ClipContext.Block.OUTLINE,
            net.minecraft.world.level.ClipContext.Fluid.NONE, clone));
    }

    public static void use(ServerLevel level, ActorClonePlayer clone, ActorUse use, boolean live) {
        if (!live && use.kind() != ActorUse.Kind.AIR) return;
        if (!live && ItemActionPolicy.hasSpawnTrack(use.itemId())) return;
        if (use.kind() == ActorUse.Kind.AIR && !usesVanilla(use.itemId())) return;
        InteractionHand hand = use.offHand() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        var stack = live ? clone.getItemInHand(hand) : Compat.itemStackFromNbt(level, use.itemId(), 1, use.itemNbt());
        if (!live) clone.setItemInHand(hand, stack);
        MUTING.set(!live);
        BlockEdits.beginApply();
        try {
            switch (use.kind()) {
                case AIR -> { if (!stack.isEmpty()) clone.gameMode.useItem(clone, level, stack, hand); }
                case BLOCK -> {
                    var hit = aimedBlock(level, clone);
                    if (hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK
                        && hit.getBlockPos().equals(new net.minecraft.core.BlockPos(use.x(), use.y(), use.z())))
                        clone.gameMode.useItemOn(clone, level, stack, hand, hit);
                }
                case ENTITY -> {
                    Entity target = use.target() == null ? null : Compat.entityByUuid(level, use.target());
                    if (target != null && Compat.withinAttackRange(clone, target) && clone.hasLineOfSight(target))
                        Compat.interactEntity(clone, target, hand);
                }
            }
        } catch (RuntimeException error) {
            FlashbackActors.LOGGER.warn("Could not replay item use for {}", use.itemId(), error);
        } finally { BlockEdits.endApply(); MUTING.remove(); }
    }

    public static void release(ActorClonePlayer clone) {
        String id = ActorItems.idOf(clone.getUseItem());
        if (usesVanilla(id)) clone.releaseUsingItem();
        else clone.stopUsingItem();
    }

    public static void trackProjectile(Entity entity) {
        if (!(entity instanceof Projectile projectile) || !(ActorWorldSimulation.owner(projectile) instanceof ActorClonePlayer owner)) return;
        var actor = FlashbackActors.ACTORS.byUuid(owner.getUUID());
        if (actor != null) {
            int tick = (int) ((FlashbackActors.replayTick() - actor.take.offsetTicks) * actor.take.getSpeedMultiplier());
            actor.trackSpawn(entity.getUUID(), tick);
            ActorWorldSimulation.track(entity);
        }
    }
}
