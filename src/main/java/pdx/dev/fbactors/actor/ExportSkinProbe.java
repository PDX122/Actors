package pdx.dev.fbactors.actor;

import com.moulberry.flashback.FilePlayerSkin;
import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import pdx.dev.fbactors.FlashbackActors;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;


public final class ExportSkinProbe {


    private static boolean probedThisExport = false;


    private static boolean probeArmed = false;


    private static long probeArmedAtMillis = 0L;


    public static void arm() {
        probeArmed = true;
        probeArmedAtMillis = System.currentTimeMillis();
        probedThisExport = false;
    }

    private ExportSkinProbe() {
    }


    public static void clientTick() {
        boolean exporting;
        try {
            exporting = Flashback.isExporting();
        } catch (Exception e) {
            return;
        }
        if (!exporting) {


            if (!probeArmed) {
                probedThisExport = false;
            }
            if (probeArmed && !probedThisExport
                && System.currentTimeMillis() - probeArmedAtMillis >= 30000) {
                probeArmed = false;
            }
        }
        long now = System.currentTimeMillis();
        boolean dueToExport = exporting && !probedThisExport;
        boolean dueToArm = probeArmed && !probedThisExport
            && now - probeArmedAtMillis < 30000;
        if (!dueToExport && !dueToArm) {
            return;
        }
        probedThisExport = true;
        probeArmed = false;
        try {
            probeOnce();
        } catch (Exception e) {
            FlashbackActors.LOGGER.warn("Export skin render-path probe failed", e);
        }
    }


    private static void probeOnce() {
        Set<UUID> takeIds;
        try {
            takeIds = ActorSkins.fileSkinTakeIds();
        } catch (Exception e) {
            FlashbackActors.LOGGER.warn("Export skin render-path probe: "
                + "could not read persisted file skins", e);
            return;
        }
        if (takeIds.isEmpty()) {
            FlashbackActors.LOGGER.info("Export skin render-path probe: "
                + "no persisted file skins, nothing to check");
            return;
        }
        EditorState editorState = null;
        try {
            editorState = EditorStateManager.getCurrent();
        } catch (Exception ignored) {

        }
        ClientLevel level = null;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null) {
                level = mc.level;
            }
        } catch (Exception ignored) {

        }
        int inMap = 0;
        int clientFound = 0;
        int builtOk = 0;
        int builtTex = 0;
        int builtCape = 0;
        int renderedMatch = 0;
        int renderedChecked = 0;
        int stale = 0;
        int keyMiss = 0;
        int buildFail = 0;
        List<String> notes = new ArrayList<>();
        for (UUID takeId : takeIds) {
            String verdict;
            try {
                verdict = probeOne(takeId, editorState, level);
            } catch (Exception e) {
                verdict = "probe-error";
            }
            switch (verdict) {
                case "ok" -> {
                    inMap++;
                    clientFound++;
                    builtOk++;
                    builtTex++;
                    builtCape++;
                    renderedChecked++;
                    renderedMatch++;
                }
                case "ok-no-cape" -> {
                    inMap++;
                    clientFound++;
                    builtOk++;
                    builtTex++;
                    renderedChecked++;
                    renderedMatch++;
                }
                case "stale-cache" -> {
                    inMap++;
                    clientFound++;
                    builtOk++;
                    builtTex++;
                    renderedChecked++;
                    stale++;
                }
                case "key-miss" -> keyMiss++;
                case "build-fail" -> {
                    inMap++;
                    buildFail++;
                }
                case "no-client-player" -> {
                    inMap++;
                }
                case "no-state" -> {

                }
                default -> {

                }
            }
            notes.add(shortId(takeId) + "=" + verdict);
            if (verdict.startsWith("ok")) {
                continue;
            }


            if (editorState != null && verdict.equals("no-client-player")) {
                try {
                    FilePlayerSkin fromFile =
                        editorState.skinOverrideFromFile.get(takeId);
                    if (fromFile != null) {
                        Object skin = fromFile.getSkin();
                        if (skin != null) {
                            builtOk++;
                            if (readTextureId(skin) != null) {
                                builtTex++;
                            }
                            if (readCapePresent(skin)) {
                                builtCape++;
                            }
                        } else {
                            buildFail++;
                        }
                    }
                } catch (Exception ignored) {
                    buildFail++;
                }
            }
            if (verdict.equals("stale-cache")) {

                try {
                    FilePlayerSkin fromFile =
                        editorState.skinOverrideFromFile.get(takeId);
                    if (fromFile != null && readCapePresent(fromFile.getSkin())) {
                        builtCape++;
                    }
                } catch (Exception ignored) {

                }
            }
        }
        String overall;
        if (keyMiss > 0) {
            overall = "B-uuid-key-mismatch";
        } else if (buildFail > 0) {
            overall = "C-file-skin-build-failure";
        } else if (stale > 0) {
            overall = "A-stale-client-cache";
        } else if (renderedChecked > 0 && renderedMatch == renderedChecked) {
            overall = "ok-render-path-intact";
        } else {
            overall = "unknown-no-client-player-to-compare";
        }
        FlashbackActors.LOGGER.info(
            "Export skin render-path probe: takes={} inMap={} clientFound={} "
                + "builtOk={} builtTex={} builtCape={} renderedMatch={}/{} "
                + "verdict={} ({})",
            takeIds.size(), inMap, clientFound, builtOk, builtTex, builtCape,
            renderedMatch, renderedChecked, overall, String.join(" ", notes));
        try {
            censusPlayers(level, editorState);
        } catch (Exception ignored) {

        }
    }


    private static void censusPlayers(@Nullable ClientLevel level,
            @Nullable EditorState editorState) {
        if (level == null) {
            FlashbackActors.LOGGER.info("Export player census: no client level");
            return;
        }
        List<String> parts = new ArrayList<>();
        int count = 0;
        try {
            for (Player player : level.players()) {
                if (count >= 12) {
                    parts.add("...");
                    break;
                }
                count++;
                UUID id;
                try {
                    id = player.getUUID();
                } catch (Exception e) {
                    parts.add("?uuid");
                    continue;
                }
                if (id == null) {
                    parts.add("null-uuid");
                    continue;
                }
                boolean hit = false;
                try {
                    hit = editorState != null
                        && editorState.skinOverrideFromFile.containsKey(id);
                } catch (Exception ignored) {

                }
                String tex = "?";
                try {
                    if (player instanceof AbstractClientPlayer clientPlayer) {
                        String t = readTextureId(clientPlayer.getSkin());
                        if (t != null) {
                            tex = t.length() <= 24 ? t : t.substring(t.length() - 24);
                        } else {
                            tex = "none";
                        }
                    }
                } catch (Exception ignored) {
                    tex = "ex";
                }
                String name;
                try {
                    name = player.getName().getString();
                } catch (Exception e) {
                    name = "?";
                }
                parts.add(shortId(id) + "=" + name + ":"
                    + (hit ? "maphit" : "nomap") + ":" + tex);
            }
        } catch (Exception e) {
            parts.add("census-ex:" + e.getClass().getSimpleName());
        }
        FlashbackActors.LOGGER.info("Export player census ({} bodies): {}",
            count, String.join(" ", parts));
    }


    private static String probeOne(@Nullable UUID takeId, @Nullable EditorState editorState,
            @Nullable ClientLevel level) {
        if (takeId == null) {
            return "probe-error";
        }
        if (editorState == null) {
            return "no-state";
        }
        FilePlayerSkin fromFile;
        try {
            fromFile = editorState.skinOverrideFromFile.get(takeId);
        } catch (Exception e) {
            return "no-state";
        }
        if (fromFile == null) {
            return "key-miss";
        }
        boolean clientPresent = false;
        Player player = null;
        if (level != null) {
            try {
                player = level.getPlayerByUUID(takeId);
            } catch (Exception ignored) {

            }
            clientPresent = player instanceof AbstractClientPlayer;
        }
        Object built;
        try {
            built = fromFile.getSkin();
        } catch (Throwable t) {
            FlashbackActors.LOGGER.info(
                "Export skin probe detail: take={} class={} path={} build=ex:{}:{} client={}",
                shortId(takeId), fromFile.getClass().getName(), readSkinPath(fromFile),
                t.getClass().getName(), String.valueOf(t.getMessage()), clientPresent);
            return "build-fail";
        }
        String builtTex = built == null ? null : readTextureId(built);
        if (built == null || builtTex == null) {
            FlashbackActors.LOGGER.info(
                "Export skin probe detail: take={} class={} path={} build={} client={}",
                shortId(takeId), fromFile.getClass().getName(), readSkinPath(fromFile),
                built == null ? "null" : "no-tex", clientPresent);
            return "build-fail";
        }
        if (!(player instanceof AbstractClientPlayer clientPlayer)) {
            return "no-client-player";
        }
        Object rendered;
        try {
            rendered = clientPlayer.getSkin();
        } catch (Exception e) {
            return "stale-cache";
        }
        if (rendered == null) {
            return "stale-cache";
        }
        String renderedTex = readTextureId(rendered);
        if (renderedTex == null) {
            return "stale-cache";
        }
        if (!renderedTex.equals(builtTex)) {
            return "stale-cache";
        }
        return readCapePresent(built) ? "ok" : "ok-no-cape";
    }


    @Nullable
    private static String readTextureId(@Nullable Object skin) {
        if (skin == null) {
            return null;
        }
        try {
            Object tex = skin.getClass().getMethod("texture").invoke(skin);
            if (tex != null) {
                return tex.toString();
            }
        } catch (Exception ignored) {

        }
        try {
            Object body = skin.getClass().getMethod("body").invoke(skin);
            if (body == null) {
                return null;
            }
            Object path = body.getClass().getMethod("texturePath").invoke(body);
            return path == null ? null : path.toString();
        } catch (Exception ignored) {
            return null;
        }
    }


    private static boolean readCapePresent(@Nullable Object skin) {
        if (skin == null) {
            return false;
        }
        try {
            Object cape = skin.getClass().getMethod("capeTexture").invoke(skin);
            if (cape != null) {
                return true;
            }
        } catch (Exception ignored) {

        }
        try {
            Object cape = skin.getClass().getMethod("cape").invoke(skin);
            return cape != null;
        } catch (Exception ignored) {
            return false;
        }
    }


    private static String shortId(UUID id) {
        String full = id.toString();
        return full.length() <= 8 ? full : full.substring(0, 8);
    }


    @Nullable
    private static String readSkinPath(@Nullable FilePlayerSkin skin) {
        if (skin == null) {
            return null;
        }
        for (String field : new String[]{"pathToSkin", "path", "skinPath"}) {
            try {
                java.lang.reflect.Field f = FilePlayerSkin.class.getDeclaredField(field);
                f.setAccessible(true);
                Object value = f.get(skin);
                if (value != null) {
                    return value.toString();
                }
            } catch (Exception ignored) {

            }
        }
        return "unknown";
    }
}

