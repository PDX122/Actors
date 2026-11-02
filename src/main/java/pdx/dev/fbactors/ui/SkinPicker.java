package pdx.dev.fbactors.ui;

import imgui.moulberry90.ImGui;
import imgui.moulberry90.flag.ImGuiCond;
import imgui.moulberry90.type.ImBoolean;
import imgui.moulberry90.type.ImString;
import com.mojang.authlib.GameProfile;
import pdx.dev.fbactors.skin.SkinSearch;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.jetbrains.annotations.Nullable;
import pdx.dev.fbactors.actor.Actor;
import pdx.dev.fbactors.actor.ActorSkins;
import pdx.dev.fbactors.compat.Compat;
import pdx.dev.fbactors.skin.SkinId;
import pdx.dev.fbactors.skin.Skins;

public final class SkinPicker {

    private static final String ID = "###ActorSkinPicker";
    private static ImString account = com.moulberry.flashback.editor.ui.ImGuiHelper.createResizableImString("");
    private static int sourceTab;
    private static final float[] turn = {0};
    private static boolean walk;
    private static final SkinSearch<GameProfile> SEARCH = new SkinSearch<>(pdx.dev.fbactors.skin.OnlineSkinLookup::find);
    private static java.util.UUID targetReplay;

    private static final ImBoolean OPEN = new ImBoolean(true);

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
        SEARCH.clear();
        pdx.dev.fbactors.skin.SkinPreviews.close();
        OPEN.set(false);
    }

    public static boolean isOpen(@Nullable Actor actor) {
        return target != null && target == actor;
    }

    private static void begin(Actor actor) {
        target = actor;
        var replay = com.moulberry.flashback.Flashback.getReplayServer();
        targetReplay = replay == null ? null : replay.getMetadata().replayIdentifier;
        OPEN.set(true);
        browseDir = null;
        pendingDialog = null;
        pendingCapeDialog = null;
        status = null;
        SEARCH.clear();
        account = com.moulberry.flashback.editor.ui.ImGuiHelper.createResizableImString("");
        sourceTab = 0; turn[0] = 0; walk = false;
    }

    public static void render() {
        try {
            pdx.dev.fbactors.skin.SkinPreviews.beginFrame();
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
        if (actor == null) return;
        var replay = com.moulberry.flashback.Flashback.getReplayServer();
        if (replay == null || !java.util.Objects.equals(targetReplay, replay.getMetadata().replayIdentifier)
            || !pdx.dev.fbactors.FlashbackActors.ACTORS.actors().contains(actor)) { close(); return; }

        var viewport = ImGui.getMainViewport();
        ImGui.setNextWindowSize(Math.max(1,Math.min(600,viewport.getWorkSizeX()-32)),
            Math.max(1,Math.min(510,viewport.getWorkSizeY()-32)),ImGuiCond.Always);
        ImGui.setNextWindowPos(140.0f, 140.0f, ImGuiCond.FirstUseEver);

        ImGui.setNextWindowBgAlpha(1);
        SkinPickerChrome.push();
        boolean begun = ImGui.begin("Skins - " + actor.displayName() + ID, OPEN,imgui.moulberry90.flag.ImGuiWindowFlags.NoResize);
        try {
            if (begun) {
                drawWindowBody(actor);
            }
        } finally {
            ImGui.end();
            SkinPickerChrome.pop();
        }

        if (!OPEN.get()) {
            pending = null;
            target = null;
            SEARCH.clear();
            pdx.dev.fbactors.skin.SkinPreviews.close();
        }
    }

    private static void drawWindowBody(Actor actor) {
        SEARCH.poll();
        float findWidth = 64;
        ImGui.setNextItemWidth(Math.max(1, ImGui.getContentRegionAvailX()-findWidth-ImGui.getStyle().getItemSpacingX()));
        if (ImGui.inputTextWithHint("##ActorSkinAccount", "Username, UUID or NameMC link", account)) {
            SEARCH.clear(); status = null;
        }
        boolean enter = ImGui.isItemActive() && ImGui.isKeyPressed(imgui.moulberry90.flag.ImGuiKey.Enter);
        ImGui.sameLine();
        boolean find = SkinPickerChrome.button("Find", false, findWidth);
        if (enter || find) { status = null; SEARCH.search(account.get()); }
        ImGui.spacing();
        var layout = SkinPickerLayout.fit(ImGui.getContentRegionAvailX(),
            Math.max(1, ImGui.getContentRegionAvailY()-ImGui.getFrameHeightWithSpacing()*2), ImGui.getStyle().getItemSpacingX());
        {
            boolean listVisible = ImGui.beginChild("##SkinLibrary", layout.listWidth(), layout.listHeight(), false);
            try { if (listVisible) library(actor); } finally { ImGui.endChild(); }
            if (layout.beside()) ImGui.sameLine();
            boolean previewVisible = ImGui.beginChild("##SkinInspector", layout.previewWidth(), layout.previewHeight(), false);
            try { if (previewVisible) inspector(actor); } finally { ImGui.endChild(); }
        }
        ImGui.spacing();
        var selected = SEARCH.result();
        float applyWidth = Math.min(170,ImGui.getContentRegionAvailX()*.43f);
        if (SkinPickerChrome.button("More...", false, 76)) ImGui.openPopup("##SkinMore");
        if (ImGui.beginPopup("##SkinMore")) {
            try {
                if (ImGui.menuItem("Restore default", "", false, !actor.take.locked)) {
                    SEARCH.clear(); ActorSkins.clear(actor); saveTake(actor); status="Default skin restored.";
                }
                if (ImGui.menuItem("Browse NameMC")) {
                    try { Compat.openSkinBrowser("https://namemc.com/"); }
                    catch (RuntimeException | LinkageError failure) { status="Could not open your browser."; }
                }
            } finally { ImGui.endPopup(); }
        }
        ImGui.sameLine();
        ImGui.setCursorPosX(ImGui.getCursorPosX()+Math.max(0,ImGui.getContentRegionAvailX()-applyWidth));
        if (SkinPickerChrome.apply(actor.take.locked || selected==null || !Compat.profileHasTextures(selected),applyWidth)) {
            ActorSkins.applyProfile(actor,selected); saveTake(actor); status="Skin applied.";
        }
        if (actor.take.locked) ImGui.textDisabled("Unlock the actor's track to apply a skin.");
        else if (status != null) ImGui.textWrapped(status);
    }

    private static void library(Actor actor) {
        float width = Math.max(1, (ImGui.getContentRegionAvailX()-ImGui.getStyle().getItemSpacingX()*2)/3);
        String[] sources = {"Players", "Saved", "Files"};
        for (int i=0;i<sources.length;i++) {
            if (i>0) ImGui.sameLine();
            boolean active = sourceTab == i;
            if (SkinPickerChrome.button(sources[i]+"##SkinSource"+i,active,width)) sourceTab=i;
        }
        ImGui.spacing();
        if (sourceTab == 2) {
            ImGui.beginDisabled(actor.take.locked);
            try {
                fileSection(actor);
                if (ImGui.collapsingHeader("Cape & arms")) { modelRow(actor); capeSection(actor); }
            } finally { ImGui.endDisabled(); }
            return;
        }
        String query = account.get().trim().toLowerCase(Locale.ROOT);
        if (sourceTab == 0) {
            var profiles = ActorSkinBridge.profiles(actor, query);
            if (profiles.isEmpty()) ImGui.textWrapped(query.isEmpty() ? "No players in this replay." : "No replay players match. Use Find to search online.");
            for (var profile : profiles) {
                String id = Compat.profileId(profile).toString();
                boolean selected = SEARCH.source() != null && id.equals(SEARCH.source().value());
                if (SkinPickerChrome.row("##ReplaySkin"+id,Compat.profileName(profile),selected,head(Compat.profileId(profile)))) {
                    status = null; SEARCH.search(id); turn[0]=0; walk=false;
                }
            }
        } else {
            var saved = Skins.library().all().stream().filter(item ->
                (item.name()+" "+item.displayName()+" "+item.source().value()).toLowerCase(Locale.ROOT).contains(query)).limit(50).toList();
            if (saved.isEmpty()) ImGui.textWrapped("Save a skin from its preview to keep it here.");
            for (var item : saved) {
                var source = Skins.resolve(item.source());
                boolean selected = source != null && source.equals(SEARCH.source());
                if (SkinPickerChrome.row("##SavedSkin"+item.name(),item.displayName(),selected,source==null?0:head(source.profileId())) && source != null) {
                    status=null; SEARCH.search(source.value()); turn[0]=0; walk=false;
                }
            }
        }
    }

    private static long head(java.util.UUID id) {
        if (id==null || pdx.dev.fbactors.skin.SkinTextures.headLocation(id)==null) return 0;
        Object image=pdx.dev.fbactors.skin.SkinThumbnails.thumbnailForSource(SkinId.parse(id.toString()));
        if (!(image instanceof Long handle)) return 0;
        Compat.sampleNearest(handle); return handle;
    }

    private static void inspector(Actor actor) {
        var profile=SEARCH.result();
        ImGui.textUnformatted(profile==null ? "Preview" : Compat.profileName(profile));
        ImGui.spacing();
        float width=ImGui.getContentRegionAvailX();
        float height=Math.max(100,ImGui.getContentRegionAvailY()-80);
        var image=pdx.dev.fbactors.skin.SkinPreviews.get(profile,turn[0],walk);
        String message=SEARCH.loading()?"Finding skin...":profile==null?"Choose a player":
            pdx.dev.fbactors.skin.SkinPreviews.unavailable()?"Preview could not load":"Preparing preview...";
        float delta=SkinPickerChrome.stage(image==null?0:image.texture(),width,height,message);
        turn[0]=(turn[0]+delta+540)%360-180;
        if (profile==null) {
            if (SEARCH.error()!=null) ImGui.textWrapped(SEARCH.error());
            return;
        }
        float controlWidth=Math.max(1,(width-ImGui.getStyle().getItemSpacingX()*2)/3);
        if (SkinPickerChrome.button("Front",Math.abs(turn[0])<1,controlWidth)) turn[0]=0;
        ImGui.sameLine(); if (SkinPickerChrome.button("Back",Math.abs(turn[0])>179,controlWidth)) turn[0]=180;
        ImGui.sameLine(); if (SkinPickerChrome.button("Walk",walk,controlWidth)) walk=!walk;
        if (SkinPickerChrome.button("Save skin",false,width)) {
            String name=Compat.profileName(profile);
            if (pdx.dev.fbactors.skin.SkinLibrary.isValidName(name)) {
                Skins.library().save(name,SkinId.parse(Compat.profileId(profile).toString())); Skins.save(); status="Saved to your library.";
            }
        }
    }

    private static void saveTake(Actor actor) {
        var replay = com.moulberry.flashback.Flashback.getReplayServer();
        if (replay != null) pdx.dev.fbactors.take.TakeStore.save(replay.getMetadata().replayIdentifier, actor.take);
    }

    private static void fileSection(Actor actor) {
        ImGui.textWrapped("Import a skin PNG from your computer.");
        if (SkinPickerChrome.button("Import PNG##ActorSkinFile",false,ImGui.getContentRegionAvailX())) {
            SEARCH.clear();
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
            SEARCH.clear();
            ActorSkinBridge.applyFile(actor, dest.toString());
            saveTake(actor);
            status = actor.displayName() + " now wears " + source.getName() + ".";
        } catch (Exception e) {
            status = "Could not import " + source.getName() + ".";
        }
    }

}
