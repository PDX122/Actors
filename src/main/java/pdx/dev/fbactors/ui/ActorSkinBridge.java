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
        List<GameProfile> profiles = new ArrayList<>();
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        if (connection == null) {
            return profiles;
        }
        for (PlayerInfo info : connection.getOnlinePlayers()) {
            GameProfile profile = info.getProfile();
            String name = Compat.profileName(profile);
            if (Compat.profileId(profile).equals(actor.take.id) || name == null || name.isBlank()) {
                continue;
            }
            profiles.add(profile);
        }
        profiles.sort(Comparator.comparing(Compat::profileName, String.CASE_INSENSITIVE_ORDER));
        if (profiles.size() > MAX_ENTRIES) {
            return profiles.subList(0, MAX_ENTRIES);
        }
        return profiles;
    }
}

