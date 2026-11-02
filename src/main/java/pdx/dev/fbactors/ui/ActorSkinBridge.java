package pdx.dev.fbactors.ui;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import pdx.dev.fbactors.actor.Actor;
import pdx.dev.fbactors.actor.ActorSkins;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import pdx.dev.fbactors.compat.Compat;

public final class ActorSkinBridge {

    private static final int MAX_ENTRIES = 24;

    private ActorSkinBridge() {
    }

    public static void clear(Actor actor) {
        ActorSkins.clear(actor);
    }

    public static void applyFile(Actor actor, String path) {
        ActorSkins.applyFile(actor, path);
    }

    public static void applyName(Actor actor, String name) {
        ActorSkins.applyName(actor, name);
    }

    public static void command(String command) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection != null) {
            connection.sendCommand(command);
        }
    }

    public static List<GameProfile> profiles(Actor actor) {
        return profiles(actor, "");
    }

    public static List<GameProfile> profiles(Actor actor, String filter) {
        List<GameProfile> profiles = new ArrayList<>();
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return profiles;
        }
        var excluded = new java.util.HashSet<java.util.UUID>();
        excluded.add(actor.take.id);
        var replay = com.moulberry.flashback.Flashback.getReplayServer();
        if (replay != null) pdx.dev.fbactors.skin.SkinPlayers.exclude(excluded,replay.getReplayViewers(),viewer -> viewer.getUUID());
        pdx.dev.fbactors.skin.SkinPlayers.exclude(excluded,pdx.dev.fbactors.FlashbackActors.ACTORS.actors(),clone -> clone.take.id);
        for (PlayerInfo info : connection.getOnlinePlayers()) {
            GameProfile profile = info.getProfile();
            String name = Compat.profileName(profile);
            if (!pdx.dev.fbactors.skin.SkinPlayers.visible(Compat.profileId(profile), name, excluded, filter)) continue;
            profiles.add(profile);
        }
        profiles.sort(Comparator.comparing(Compat::profileName, String.CASE_INSENSITIVE_ORDER));
        if (profiles.size() > MAX_ENTRIES) {
            return profiles.subList(0, MAX_ENTRIES);
        }
        return profiles;
    }
}
