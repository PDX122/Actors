package pdx.dev.fbactors.actor;

import com.moulberry.flashback.playback.ReplayServer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundBlockDestructionPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import pdx.dev.fbactors.compat.Compat;
import pdx.dev.fbactors.take.ActorMining;
import pdx.dev.fbactors.take.ActorMiningState;

import java.util.LinkedHashMap;
import java.util.Map;


public final class MiningAnimation {

    private MiningAnimation() {
    }


    public static void update(ReplayServer server, Actor actor, int takeTick) {
        ActorClonePlayer clone = actor.clone();
        if (clone == null) {
            return;
        }


        ActorMiningState state = actor.take.miningStateAt(takeTick);


        Map<BlockPos, Integer> wanted = new LinkedHashMap<>();
        for (ActorMining step : actor.take.miningAt(takeTick)) {
            if (step.clears()) {
                wanted.remove(step.pos());
            } else {
                wanted.put(step.pos(), step.stage());
            }
        }


        for (Map.Entry<BlockPos, Integer> shown : actor.outstandingMining()) {
            if (!wanted.containsKey(shown.getKey())) {
                broadcast(server, new ClientboundBlockDestructionPacket(
                    shown.getValue(), shown.getKey(), -1));
                actor.forgetMining(shown.getKey());
            }
        }


        for (Map.Entry<BlockPos, Integer> entry : wanted.entrySet()) {
            Integer sent = actor.miningStage(entry.getKey());
            if (entry.getValue().equals(sent)) {
                continue;
            }
            Integer id = actor.miningId(entry.getKey());
            if (id == null) {
                id = actor.assignMiningId(entry.getKey());
            }
            broadcast(server, new ClientboundBlockDestructionPacket(
                id, entry.getKey(), entry.getValue()));
            actor.setMiningStage(entry.getKey(), entry.getValue());
        }


        if ((state != null && state.stage() >= 0) || !wanted.isEmpty()) {
            Compat.swing(clone, InteractionHand.MAIN_HAND);
        }
    }


    public static void clear(Actor actor) {
        actor.forgetAllMining();
    }

    private static void broadcast(ReplayServer server, Packet<?> packet) {
        for (ServerPlayer viewer : server.getReplayViewers()) {
            viewer.connection.send(packet);
        }
    }
}
