package pdx.dev.fbactors.actor;

import com.moulberry.flashback.playback.ReplayServer;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import pdx.dev.fbactors.FlashbackActors;
import pdx.dev.fbactors.mixin.ChunkMapActorAccess;

public final class ActorChunkLoading {
    private ActorChunkLoading() {}

    public static void tick(ReplayServer replay) {
        for (var actor : FlashbackActors.ACTORS.actors()) {
            var clone = actor.clone();
            if (clone == null || clone.isRemoved() || !(clone.level() instanceof ServerLevel level)
                || level.getServer() != replay) continue;

            if (!clone.getLastSectionPos().equals(SectionPos.of(clone)))
                level.getChunkSource().move(clone);
            ((ChunkMapActorAccess)level.getChunkSource().chunkMap).fbactors$updateChunkTracking(clone);
        }
    }
}
