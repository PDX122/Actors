package pdx.dev.fbactors.ui;

import imgui.moulberry90.ImDrawList;
import imgui.moulberry90.ImGui;
import imgui.moulberry90.flag.ImGuiCond;
import imgui.moulberry90.type.ImBoolean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.jetbrains.annotations.Nullable;
import pdx.dev.fbactors.actor.Actor;
import pdx.dev.fbactors.actor.ActorSkins;
import pdx.dev.fbactors.compat.Compat;
import pdx.dev.fbactors.skin.SkinId;
import pdx.dev.fbactors.skin.SkinThumbnails;
import pdx.dev.fbactors.skin.Skins;
import pdx.dev.fbactors.skin.SavedSkin;


public final class SkinPicker {


    private static final String ID = "###ActorSkinPicker";


    private static final ImBoolean OPEN = new ImBoolean(true);


    private static final float HEAD = 64.0f;


    private static final float FACE_U0 = 8.0f / 64.0f;
    private static final float FACE_U1 = 16.0f / 64.0f;
    private static final float HAT_U0 = 40.0f / 64.0f;
    private static final float HAT_U1 = 48.0f / 64.0f;
    private static final float V0 = 8.0f / 64.0f;
    private static final float V1 = 16.0f / 64.0f;


    @Nullable
    private static Actor target;


    @Nullable
    private static Actor pending;


    @Nullable
    private static String browseDir;


    @Nullable
    private static java.util.concurrent.CompletableFuture<String> pendingDialog;


    @Nullable
    private static java.util.concurrent.CompletableFuture<String> pendingCapeDialog;


    @Nullable
    private static String status;

    private SkinPicker() {
    }


    public static void open(Actor actor) {
        pending = actor;
    }


    public static void close() {
        pending = null;
        target = null;
        OPEN.set(false);
    }


    public static boolean isOpen(@Nullable Actor actor) {
        return target != null && target == actor;
    }


    private static void begin(Actor actor) {
        target = actor;
        OPEN.set(true);
        browseDir = null;
        pendingDialog = null;
        pendingCapeDialog = null;
        status = null;
    }


    public static void render() {
        try {
            renderInner();
        } catch (Exception | LinkageError e) {


            pdx.dev.fbactors.FlashbackActors.LOGGER.warn("Skin picker failed; closing it", e);
            close();
        }
    }


    private static void renderInner() {
        Actor requested = pending;
        if (requested != null) {
            pending = null;
            begin(requested);
        }


        ActorSkins.sweepOverrides();

        Actor actor = target;
        if (actor == null) {
            return;
        }

        ImGui.setNextWindowSize(300.0f, 280.0f, ImGuiCond.FirstUseEver);
        ImGui.setNextWindowPos(140.0f, 140.0f, ImGuiCond.FirstUseEver);

        boolean begun = ImGui.begin("Change skin - " + actor.displayName() + ID, OPEN);
        try {
            if (begun) {
                drawWindowBody(actor);
            }
        } finally {
            ImGui.end();
        }

        if (!OPEN.get()) {
            pending = null;
            target = null;
        }
    }


    private static void drawWindowBody(Actor actor) {
        currentSkin(actor);
        ImGui.separator();
        fileSection(actor);
        ImGui.separator();
        capeSection(actor);
        ImGui.separator();

        if (status != null) {
            ImGui.textDisabled(status);
        }
        if (ImGui.button("Default##ActorSkinDefault")) {
            ActorSkins.clear(actor);
            status = "Cleared. " + actor.displayName() + " uses the default skin again.";
        }
        ImGui.sameLine();
        if (ImGui.button("Close##ActorSkinClose")) {
            close();
        }
    }


    private static void currentSkin(Actor actor) {
        ImDrawList drawList = ImGui.getWindowDrawList();
        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY();
        drawHead(drawList, currentHandle(actor), x, y, HEAD, 0xFFFFFFFF);
        ImGui.dummy(HEAD, HEAD);
        ImGui.sameLine();
        ImGui.textUnformatted(currentLabel(actor));
    }


    private static void fileSection(Actor actor) {
        ImGui.textUnformatted("From file");
        modelRow(actor);
        if (ImGui.button("Browse files...##ActorSkinFile")) {
            if (pendingDialog == null) {
                try {
                    pendingDialog =
                        Compat.openSkinFile(Skins.directory().toString());
                } catch (LinkageError e) {
                    if (browseDir == null) {
                        browseDir = Skins.directory().toString();
                    }
                }
            }
        }
        java.util.concurrent.CompletableFuture<String> waiting = pendingDialog;
        if (waiting != null && waiting.isDone()) {
            pendingDialog = null;
            String picked = waiting.getNow(null);
            if (picked != null && !picked.isBlank()) {
                importFile(actor, new java.io.File(picked));
            }
        }
        if (browseDir != null) {
            browserPanel(actor);
        }
    }


    private static void modelRow(Actor actor) {
        String model = ActorSkins.modelName(actor);
        ImGui.textDisabled(model == null ? "Arms: auto" : "Arms: " + model);
        if (model != null && ImGui.button("Auto##ActorModelAuto")) {
            if (ActorSkins.setModel(actor, null)) {
                status = actor.displayName() + " uses the skin's own arm model.";
            }
        }
        ImGui.sameLine();
        if (!"Slim".equals(model) && ImGui.button("Slim##ActorModelSlim")) {
            if (ActorSkins.setModel(actor, Boolean.TRUE)) {
                status = actor.displayName() + " wears slim arms.";
            } else {
                status = "Pick a file skin first.";
            }
        }
        ImGui.sameLine();
        if (!"Classic".equals(model) && ImGui.button("Classic##ActorModelClassic")) {
            if (ActorSkins.setModel(actor, Boolean.FALSE)) {
                status = actor.displayName() + " wears classic arms.";
            } else {
                status = "Pick a file skin first.";
            }
        }
    }


    private static void browserPanel(Actor actor) {
        java.io.File current = new java.io.File(browseDir);
        ImGui.textDisabled(current.getAbsolutePath());
        if (ImGui.button("Up##ActorSkinUp")) {
            java.io.File parent = current.getParentFile();
            browseDir = parent == null ? null : parent.getAbsolutePath();
            if (browseDir == null) {
                return;
            }
            current = new java.io.File(browseDir);
        }
        ImGui.sameLine();
        if (ImGui.button("Close##ActorSkinBrowseClose")) {
            browseDir = null;
            return;
        }

        String[] names = current.list();
        if (names == null) {
            ImGui.textDisabled("Cannot read that folder.");
            return;
        }
        java.util.Arrays.sort(names, String.CASE_INSENSITIVE_ORDER);

        int shown = 0;
        for (String name : names) {
            if (shown >= 100) {
                ImGui.textDisabled("... first 100 entries shown.");
                break;
            }
            java.io.File entry = new java.io.File(current, name);
            if (entry.isDirectory()) {
                if (ImGui.button(name + "/##ActorSkinDir" + shown)) {
                    browseDir = entry.getAbsolutePath();
                    return;
                }
                shown++;
            }
        }
        for (String name : names) {
            if (shown >= 100) {
                break;
            }
            String lower = name.toLowerCase(Locale.ROOT);
            java.io.File entry = new java.io.File(current, name);
            if (entry.isFile() && lower.endsWith(".png")) {
                if (ImGui.button(name + "##ActorSkinPng" + shown)) {
                    importFile(actor, entry);
                }
                shown++;
            }
        }
        if (shown == 0) {
            ImGui.textDisabled("No folders or PNGs here.");
        }
    }


    private static void capeSection(Actor actor) {
        ImGui.textUnformatted("Cape from file");
        String cape = ActorSkins.capeName(actor);
        if (cape != null) {
            ImGui.textDisabled("Wearing " + cape);
            ImGui.sameLine();
            if (ImGui.button("Clear cape##ActorCapeClear")) {
                ActorSkins.setCape(actor, null);
                status = "Cape cleared.";
            }
        }
        if (ImGui.button("Browse cape...##ActorCapeFile")) {
            if (pendingCapeDialog == null) {
                try {
                    pendingCapeDialog =
                        Compat.openSkinFile(Skins.directory().toString());
                } catch (LinkageError e) {
                    status = "File dialogs are unavailable in this Flashback build.";
                }
            }
        }
        java.util.concurrent.CompletableFuture<String> waiting = pendingCapeDialog;
        if (waiting != null && waiting.isDone()) {
            pendingCapeDialog = null;
            String picked = waiting.getNow(null);
            if (picked != null && !picked.isBlank()) {
                importCapeFile(actor, new java.io.File(picked));
            }
        }
    }


    private static void importCapeFile(Actor actor, java.io.File source) {
        try {
            Path dir = Skins.directory();
            Files.createDirectories(dir);
            Path dest = dir.resolve(source.getName());
            if (!source.getCanonicalPath().equals(dest.toFile().getCanonicalPath())) {
                Files.copy(source.toPath(), dest,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            if (ActorSkins.setCape(actor, dest.toString())) {
                status = actor.displayName() + " now wears the " + source.getName() + " cape.";
            } else {
                status = "Could not apply the cape on this version.";
            }
        } catch (Exception e) {
            status = "Could not import " + source.getName() + ".";
        }
    }


    private static void importFile(Actor actor, java.io.File source) {
        try {
            Path dir = Skins.directory();
            Files.createDirectories(dir);
            Path dest = dir.resolve(source.getName());
            if (!source.getCanonicalPath().equals(dest.toFile().getCanonicalPath())) {
                Files.copy(source.toPath(), dest,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            ActorSkinBridge.applyFile(actor, dest.toString());
            status = actor.displayName() + " now wears " + source.getName() + ".";
        } catch (Exception e) {
            status = "Could not import " + source.getName() + ".";
        }
    }


    @Nullable
    private static SkinId currentSource(Actor actor) {
        String name = actor.take.skinName;
        if (name == null || name.isBlank()) {
            return null;
        }
        SkinId parsed = SkinId.tryParse(name);
        if (parsed != null) {
            return Skins.resolve(parsed);
        }
        SavedSkin named = Skins.library().byName(name);
        return named == null ? null : Skins.resolve(named.source());
    }


    @Nullable
    private static Object currentHandle(Actor actor) {
        SkinId source = currentSource(actor);
        return source == null ? null : SkinThumbnails.thumbnailForSource(source);
    }


    private static String currentLabel(Actor actor) {
        SkinId source = currentSource(actor);
        if (source != null) {
            return source.value();
        }
        String name = actor.take.skinName;
        return name == null || name.isBlank() ? "Default skin" : "Custom file";
    }


    private static void drawHead(ImDrawList drawList, @Nullable Object handle, float x, float y,
                                 float size, int tint) {
        if (handle == null) {
            drawList.addRectFilled(x, y, x + size, y + size, 0x30A0A0A0);
            return;
        }
        Compat.sampleNearest((Long) handle);
        drawList.addImage((Long) handle, x, y, x + size, y + size, FACE_U0, V0, FACE_U1, V1, tint);
        drawList.addImage((Long) handle, x, y, x + size, y + size, HAT_U0, V0, HAT_U1, V1, tint);
    }
}

