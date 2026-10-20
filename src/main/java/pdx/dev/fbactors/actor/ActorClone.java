package pdx.dev.fbactors.actor;

import com.mojang.authlib.GameProfile;
import com.moulberry.flashback.playback.ReplayServer;
import pdx.dev.fbactors.FlashbackActors;
import pdx.dev.fbactors.take.ActorFrame;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import pdx.dev.fbactors.compat.Compat;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class ActorClone {

    private static final int ID_BLOCK_START = -981_000_000;
    private static int nextId = ID_BLOCK_START;

    private ActorClone() {
    }

    private static synchronized int allocateId() {
        return nextId++;
    }

    @Nullable
    public static ActorClonePlayer spawn(ReplayServer server, ServerLevel level,
                                     GameProfile profile, ActorFrame frame) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(profile, false);

        ActorClonePlayer clone = new ActorClonePlayer(server, level,
            cookie.gameProfile(), cookie.clientInformation());

        ServerPlayer existingPlayer = server.getPlayerList().getPlayer(clone.getUUID());
        if (existingPlayer != null) {
            existingPlayer.discard();
        }

        int entityId = allocateId();
        ClientboundAddEntityPacket addPacket = new ClientboundAddEntityPacket(
            entityId, Compat.profileId(profile),
            frame.x(), frame.y(), frame.z(),
            frame.xRot(), frame.yRot(),
            Compat.playerEntityType(), 0, Vec3.ZERO, frame.yHeadRot()
        );

        Entity existingEntity = level.getEntity(entityId);
        if (existingEntity != null) {
            existingEntity.discard();
        }

        Connection connection = Compat.silentConnection();

        clone.recreateFromPacket(addPacket);
        try {
            server.getPlayerList().placeNewPlayer(connection, clone, cookie);
        } catch (Exception e) {
            FlashbackActors.LOGGER.error("Failed to spawn actor clone {}", Compat.profileName(profile), e);
            return null;
        }


        clone.setGameMode(GameType.CREATIVE);

        clone.getAbilities().flying = false;
        clone.getAbilities().mayfly = false;
        clone.onUpdateAbilities();

        if (clone.isRemoved()) {
            FlashbackActors.LOGGER.error("Actor clone {} was removed while spawning", Compat.profileName(profile));
            return null;
        }
        return clone;
    }
}

