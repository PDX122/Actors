package pdx.dev.fbactors.input;

import com.moulberry.flashback.Flashback;
import com.moulberry.flashback.playback.ReplayServer;
import com.moulberry.flashback.record.ReplayMarker;
import imgui.moulberry90.ImGui;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import pdx.dev.fbactors.FlashbackActors;
import pdx.dev.fbactors.actor.Actor;
import pdx.dev.fbactors.actor.ActorMode;
import pdx.dev.fbactors.compat.Compat;
import pdx.dev.fbactors.settings.FbSettings;
import pdx.dev.fbactors.take.ActorTake;
import pdx.dev.fbactors.take.TakeIO;
import pdx.dev.fbactors.take.TakeStore;
import pdx.dev.fbactors.ui.ActorTrack;
import pdx.dev.fbactors.ui.ReplayUiInput;
import pdx.dev.fbactors.undo.UndoStack;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

public final class Hotkey {

    private static final boolean SDL_LAYOUT = !classExists("org.lwjgl.glfw.GLFW");

    private static final int SHIFT = SDL_LAYOUT ? 3 : 1;
    private static final int CONTROL = SDL_LAYOUT ? 192 : 2;
    private static final int ALT = SDL_LAYOUT ? 768 : 4;
    private static final int SUPER = SDL_LAYOUT ? 3072 : 8;

    private static final boolean MAC =
        System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");

    private static final int MARKER_COLOUR = 0xFFFFFF55;

    private static final int RE_RECORD_DELAY_SECONDS = 3;

    private static boolean installed;
    private static boolean helpOpen;
    private static boolean helpPositioned;

    @Nullable
    private static ActorTake clipboard;

    private Hotkey() {
    }

    private record Shortcut(String id, String defaultKey, int defaultChord,
                            String description, Runnable action) {
    }

    private static final List<Shortcut> SHORTCUTS = new ArrayList<>();

    private static final Map<String, KeyMapping> MAPPINGS = new HashMap<>();

    private static final Map<String, Integer> DEFAULT_CODES = new HashMap<>();

    public static void install() {
        if (installed) {
            return;
        }
        installed = true;

        add("deleteSelected", "key.keyboard.delete", 0, "Delete selected actor", Hotkey::deleteSelected);
        add("renameSelected", "key.keyboard.f2", 0, "Rename selected actor", Hotkey::renameSelected);
        add("copyKeys", "key.keyboard.c", ALT, "Copy camera keys", Hotkey::copyKeys);
        add("pasteKeys", "key.keyboard.v", ALT, "Paste camera keys", Hotkey::pasteKeys);
        add("rippleDelete", "key.keyboard.delete", SHIFT, "Ripple-delete selection", Hotkey::rippleDelete);
        add("toggleHelp", "key.keyboard.f1", 0, "Open this help", Hotkey::toggleHelp);
    }

    public static void renderHelp() {
        if (!helpOpen) {
            return;
        }
        if (!helpPositioned) {
            helpPositioned = true;
            ImGui.setNextWindowSize(460, 420);
            ImGui.setNextWindowPos(
                ImGui.getIO().getDisplaySize().x * 0.5f - 230,
                ImGui.getIO().getDisplaySize().y * 0.5f - 210);
        }
        if (!ImGui.begin("Flashback Actors shortcuts")) {
            ImGui.end();
            return;
        }
        try {
            for (Shortcut shortcut : SHORTCUTS) {
                ImGui.textUnformatted(shortcut.description() + " ..... " + bindText(shortcut));
            }
            ImGui.textUnformatted("");
            ImGui.textDisabled("Press the help key again to close");
        } finally { ImGui.end(); }
    }

    private static void add(String id, String defaultKey, int chord, String description,
                            Runnable action) {
        SHORTCUTS.add(new Shortcut(id, defaultKey, chord, description, action));
        try {
            DEFAULT_CODES.put(id, Compat.key(defaultKey));
        } catch (Exception | LinkageError ignored) {

        }
        try {
            MAPPINGS.put(id, Compat.registerKey("fbactors", "key.fbactors." + id,
                "key.keyboard.unknown"));
        } catch (Exception | LinkageError e) {
            FlashbackActors.LOGGER.warn("Could not register the {} hotkey", id, e);
        }
    }

    public static boolean endRunOnEscape(int key) {
        if(key==Compat.key("key.keyboard.escape") && pdx.dev.fbactors.events.EventPlacement.active()) {
            pdx.dev.fbactors.events.EventPlacement.cancelByEscape();return true;
        }
        var replay = Flashback.getReplayServer();
        var phase = ActorMode.phase();
        if (!FbSettings.settings().escapeEndsRun || replay == null || key != Compat.key("key.keyboard.escape")
            || !EscapeRunPolicy.mayFinish(phase == ActorMode.Phase.RECORDING,
                screenOpen(Minecraft.getInstance()), replay.getReplayTick(), replay.getTotalReplayTicks())) return false;
        replay.execute(() -> {
            var viewer = FlashbackActors.viewerOf(replay);
            if (viewer != null && EscapeRunPolicy.mayFinish(ActorMode.phase() == ActorMode.Phase.RECORDING,
                false, replay.getReplayTick(), replay.getTotalReplayTicks())) FlashbackActors.finishTake(replay, viewer);
        });
        return true;
    }

    public static void press(int key, int mods) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        mc.execute(() -> fire(key, mods));
    }

    public static boolean hasModernKeyEvent() {
        return classExists("net.minecraft.client.input.KeyEvent");
    }

    public static int pollModifiers(long window) {
        int mods = 0;
        if (Compat.keyDown(window, Compat.key("key.keyboard.left.shift"))
            || Compat.keyDown(window, Compat.key("key.keyboard.right.shift"))) {
            mods |= SDL_LAYOUT ? 3 : 1;
        }
        if (Compat.keyDown(window, Compat.key("key.keyboard.left.control"))
            || Compat.keyDown(window, Compat.key("key.keyboard.right.control"))) {
            mods |= SDL_LAYOUT ? 192 : 2;
        }
        if (Compat.keyDown(window, Compat.key("key.keyboard.left.alt"))
            || Compat.keyDown(window, Compat.key("key.keyboard.right.alt"))) {
            mods |= SDL_LAYOUT ? 768 : 4;
        }
        return mods;
    }

    private static void fire(int key, int mods) {
        if (Flashback.getReplayServer() == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || screenOpen(mc) || ReplayUiInput.wantsText()) {
            return;
        }

        boolean enabled;
        try {
            enabled = FbSettings.settings().hotkeysEnabled;
        } catch (Exception | LinkageError e) {
            enabled = true;
        }
        if (!enabled) {
            return;
        }

        if (matchesId(key, mods, "toggleHelp")) {
            helpOpen = !helpOpen;
            return;
        }
        if (helpOpen) {
            return;
        }

        for (Shortcut shortcut : SHORTCUTS) {
            if (shortcut.id().equals("toggleHelp")) {
                continue;
            }
            if (matchesShortcut(key, mods, shortcut)) {
                pdx.dev.fbactors.debug.ErrorRecovery.run("Shortcut: "+shortcut.id(),shortcut.action());
                return;
            }
        }
    }

    private static boolean matchesShortcut(int key, int mods, Shortcut shortcut) {
        int want = DEFAULT_CODES.getOrDefault(shortcut.id(), -1);
        KeyMapping mapping = MAPPINGS.get(shortcut.id());
        if (mapping != null) {
            try {
                int bound = Compat.key(mapping.saveString());
                if (bound >= 0) {
                    want = bound;
                }
            } catch (Exception | LinkageError ignored) {

            }
        }
        if (want < 0 || key != want) {
            return false;
        }
        boolean ctrl = (mods & CONTROL) != 0 || (MAC && (mods & SUPER) != 0);
        boolean shift = (mods & SHIFT) != 0;
        boolean alt = (mods & ALT) != 0;
        return ctrl == ((shortcut.defaultChord() & CONTROL) != 0)
            && shift == ((shortcut.defaultChord() & SHIFT) != 0)
            && alt == ((shortcut.defaultChord() & ALT) != 0);
    }

    private static boolean matchesId(int key, int mods, String id) {
        Shortcut shortcut = findShortcut(id);
        return shortcut != null && matchesShortcut(key, mods, shortcut);
    }

    private static String bindText(Shortcut shortcut) {
        String chord = chordText(shortcut.defaultChord());
        KeyMapping mapping = MAPPINGS.get(shortcut.id());
        if (mapping != null) {
            try {
                if (Compat.key(mapping.saveString()) >= 0) {
                    return chord + mapping.getTranslatedKeyMessage().getString();
                }
            } catch (Exception | LinkageError ignored) {

            }
        }
        return chord + prettyKey(shortcut.defaultKey());
    }

    private static String chordText(int chord) {
        StringBuilder out = new StringBuilder();
        if ((chord & CONTROL) != 0) {
            out.append("Ctrl+");
        }
        if ((chord & SHIFT) != 0) {
            out.append("Shift+");
        }
        if ((chord & ALT) != 0) {
            out.append("Alt+");
        }
        return out.toString();
    }

    private static String prettyKey(String name) {
        if (name == null || name.isEmpty()) {
            return "?";
        }
        switch (name) {
            case "key.keyboard.slash": return "/";
            case "key.keyboard.backslash": return "\\";
            case "key.keyboard.comma": return ",";
            case "key.keyboard.period": return ".";
            case "key.keyboard.semicolon": return ";";
            case "key.keyboard.apostrophe": return "'";
            case "key.keyboard.grave": return "`";
            case "key.keyboard.minus": return "-";
            case "key.keyboard.equals": return "=";
            case "key.keyboard.space": return "Space";
            case "key.keyboard.left.bracket": return "[";
            case "key.keyboard.right.bracket": return "]";
            default: {
                String keyName = name.startsWith("key.keyboard.")
                    ? name.substring("key.keyboard.".length())
                    : name;
                StringBuilder out = new StringBuilder();
                boolean upper = true;
                for (int i = 0; i < keyName.length(); i++) {
                    char c = keyName.charAt(i);
                    if (c == '.' || c == '_') {
                        out.append(' ');
                        upper = true;
                    } else if (upper) {
                        out.append(Character.toUpperCase(c));
                        upper = false;
                    } else {
                        out.append(c);
                    }
                }
                return out.toString();
            }
        }
    }

    @Nullable
    private static Shortcut findShortcut(String id) {
        for (Shortcut shortcut : SHORTCUTS) {
            if (shortcut.id().equals(id)) {
                return shortcut;
            }
        }
        return null;
    }

    private static boolean classExists(String name) {
        try {
            Class.forName(name, false, Hotkey.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static boolean screenOpen(Minecraft mc) {
        return Compat.hasOpenScreen(mc);
    }

    private static void toggleRecording() {
        ActorTrack.toggleRecording();
    }

    private static void newActor() {
        if (ActorMode.phase() != ActorMode.Phase.IDLE) {
            feedback(Component.translatable("fbactors.hotkey.busy"));
            return;
        }
        ActorTrack.newActor();
    }

    private static void reRecordLast() {
        ReplayServer replay = Flashback.getReplayServer();
        if (replay == null) {
            return;
        }
        List<Actor> actors = FlashbackActors.ACTORS.actors();
        if (actors.isEmpty()) {
            feedback(Component.translatable("fbactors.hotkey.no_actors"));
            return;
        }
        Actor last = actors.get(actors.size() - 1);
        String name = last.displayName();
        UndoStack.pushRemoval(last.take);
        replay.execute(() -> {
            FlashbackActors.removeActor(last);
            ServerPlayer viewer = FlashbackActors.viewerOf(replay);
            if (viewer != null) {
                ActorMode.enterSetup(replay, viewer);
                ActorMode.setCountdownDelay(RE_RECORD_DELAY_SECONDS);
            }
        });
        feedback(Component.translatable("fbactors.hotkey.rerecord", name));
    }

    private static void discardLast() {
        ReplayServer replay = Flashback.getReplayServer();
        if (replay == null) {
            return;
        }
        List<Actor> actors = FlashbackActors.ACTORS.actors();
        if (actors.isEmpty()) {
            feedback(Component.translatable("fbactors.hotkey.no_actors"));
            return;
        }
        removeWithUndo(actors.get(actors.size() - 1), "fbactors.hotkey.discarded");
    }

    private static void undo() {
        if (UndoStack.undoEdit()) {
            ReplayServer replay = Flashback.getReplayServer();
            if (replay != null) {
                replay.execute(() -> {
                    UUID replayId = FlashbackActors.loadedReplayIdOrNull();
                    if (replayId != null) {
                        for (Actor actor : FlashbackActors.ACTORS.actors()) {
                            TakeStore.save(replayId, actor.take);
                        }
                    }
                });
            }
            feedback(Component.translatable("fbactors.hotkey.undone"));
            return;
        }
        ActorTake take = UndoStack.undo();
        if (take == null) {
            feedback(Component.translatable("fbactors.hotkey.nothing_to_undo"));
            return;
        }
        String name = displayName(take);
        ReplayServer replay = Flashback.getReplayServer();
        if (replay != null) {
            replay.execute(() -> {
                FlashbackActors.ACTORS.add(take);
                UUID replayId = FlashbackActors.loadedReplayIdOrNull();
                if (replayId != null) {
                    TakeStore.save(replayId, take);
                }
            });
        }
        feedback(Component.translatable("fbactors.hotkey.restored", name));
    }

    private static void redo() {
        if (UndoStack.redoEdit()) {
            ReplayServer replay = Flashback.getReplayServer();
            if (replay != null) {
                replay.execute(() -> {
                    UUID replayId = FlashbackActors.loadedReplayIdOrNull();
                    if (replayId != null) {
                        for (Actor actor : FlashbackActors.ACTORS.actors()) {
                            TakeStore.save(replayId, actor.take);
                        }
                    }
                });
            }
            feedback(Component.translatable("fbactors.hotkey.redone_edit"));
            return;
        }
        ActorTake take = UndoStack.redo();
        if (take == null) {
            feedback(Component.translatable("fbactors.hotkey.nothing_to_redo"));
            return;
        }
        String name = displayName(take);
        ReplayServer replay = Flashback.getReplayServer();
        if (replay != null) {
            replay.execute(() -> {
                Actor actor = FlashbackActors.ACTORS.byTakeId(take.id);
                if (actor != null) {
                    FlashbackActors.removeActor(actor);
                }
            });
        }
        feedback(Component.translatable("fbactors.hotkey.redone", name));
    }

    private static void saveAll() {
        UUID replayId = FlashbackActors.loadedReplayIdOrNull();
        if (replayId == null) {
            return;
        }
        List<Actor> actors = FlashbackActors.ACTORS.actors();
        for (Actor actor : actors) {
            TakeStore.save(replayId, actor.take);
            TakeStore.clearDirty(actor.take.id);
        }
        feedback(Component.translatable("fbactors.hotkey.saved", actors.size()));
    }

    private static void duplicateSelected() {
        Actor actor = ActorTrack.selected();
        if (actor == null) {
            feedback(Component.translatable("fbactors.hotkey.no_selection"));
            return;
        }
        String name = actor.displayName();
        ReplayServer replay = Flashback.getReplayServer();
        if (replay != null) {
            replay.execute(() -> {
                Actor copy = FlashbackActors.duplicateActor(actor);
                ActorTake added = copy.take;
                UndoStack.pushEdit(
                    () -> replay.execute(() -> {
                        Actor found = FlashbackActors.ACTORS.byTakeId(added.id);
                        if (found != null) {
                            FlashbackActors.removeActor(found);
                        }
                    }),
                    () -> replay.execute(() -> {
                        if (FlashbackActors.ACTORS.byTakeId(added.id) == null) {
                            FlashbackActors.ACTORS.add(added);
                            FlashbackActors.save(added);
                        }
                    }));
            });
        }
        feedback(Component.translatable("fbactors.hotkey.duplicated", name));
    }

    private static void deleteSelected() {
        Actor actor = ActorTrack.selected();
        if (actor == null) {
            feedback(Component.translatable("fbactors.hotkey.no_selection"));
            return;
        }
        removeWithUndo(actor, "fbactors.hotkey.deleted");
    }

    private static void renameSelected() {
        ActorTrack.renameSelected();
    }

    private static void muteSelected() {
        Actor actor = ActorTrack.selected();
        if (actor == null) {
            feedback(Component.translatable("fbactors.hotkey.no_selection"));
            return;
        }
        boolean enabled = !actor.enabled();
        boolean before = actor.enabled();
        actor.setEnabled(enabled);
        UndoStack.pushEnabled(actor.take, before, enabled);
        ReplayServer replay = Flashback.getReplayServer();
        if (replay != null) {
            replay.execute(() -> FlashbackActors.save(actor.take));
        }
        feedback(Component.translatable(
            enabled ? "fbactors.hotkey.unmuted" : "fbactors.hotkey.muted", actor.displayName()));
    }

    private static void soloSelected() {
        Actor actor = ActorTrack.selected();
        if (actor == null) {
            feedback(Component.translatable("fbactors.hotkey.no_selection"));
            return;
        }
        boolean solo = !actor.solo;
        UndoStack.pushSolo(UndoStack.cellsOf(new ArrayList<>(FlashbackActors.ACTORS.actors())),
            actor.take.id, solo);
        for (Actor other : FlashbackActors.ACTORS.actors()) {
            other.solo = other == actor && solo;
        }
        if (solo) {
            ReplayServer replay = Flashback.getReplayServer();
            if (replay != null) {
                replay.execute(() -> FlashbackActors.save(actor.take));
            }
        }
        feedback(Component.translatable(
            solo ? "fbactors.hotkey.soloed" : "fbactors.hotkey.unsoloed", actor.displayName()));
    }

    private static void selectRelative(int delta) {
        ActorTrack.selectNext(delta);
    }

    private static void dropMarker() {
        ReplayServer replay = Flashback.getReplayServer();
        if (replay == null) {
            return;
        }
        ServerPlayer viewer = FlashbackActors.viewerOf(replay);
        if (viewer == null) {
            return;
        }
        int tick = replay.getReplayTick();
        Vector3f position = new Vector3f(
            (float) viewer.getX(), (float) viewer.getY(), (float) viewer.getZ());
        String dimension = Compat.dimensionId((ServerLevel) viewer.level());
        replay.getMetadata().replayMarkers.put(tick,
            new ReplayMarker(MARKER_COLOUR,
                new ReplayMarker.MarkerPosition(position, dimension), "Actor"));
        feedback(Component.translatable("fbactors.hotkey.marker", tick));
    }

    private static void cutSelected() {
        Actor actor = ActorTrack.selected();
        if (actor == null) {
            feedback(Component.translatable("fbactors.hotkey.no_selection"));
            return;
        }
        clipboard = actor.take;
        removeWithUndo(actor, "fbactors.hotkey.cut");
    }

    private static void copySelected() {
        Actor actor = ActorTrack.selected();
        if (actor == null) {
            return;
        }
        clipboard = actor.take;
        feedback(Component.translatable("fbactors.hotkey.copied", actor.displayName()));
    }

    private static void paste() {
        ActorTake source = clipboard;
        if (source == null) {
            feedback(Component.translatable("fbactors.hotkey.nothing_to_paste"));
            return;
        }
        ActorTake copy = source.copyAs(UUID.randomUUID(), source.name);
        String name = displayName(copy);
        ReplayServer replay = Flashback.getReplayServer();
        if (replay != null) {
            replay.execute(() -> {
                FlashbackActors.ACTORS.add(copy);
                UUID replayId = FlashbackActors.loadedReplayIdOrNull();
                if (replayId != null) {
                    TakeStore.save(replayId, copy);
                }
                UndoStack.pushEdit(
                    () -> replay.execute(() -> {
                        Actor found = FlashbackActors.ACTORS.byTakeId(copy.id);
                        if (found != null) {
                            FlashbackActors.removeActor(found);
                        }
                    }),
                    () -> replay.execute(() -> {
                        if (FlashbackActors.ACTORS.byTakeId(copy.id) == null) {
                            FlashbackActors.ACTORS.add(copy);
                            UUID redoId = FlashbackActors.loadedReplayIdOrNull();
                            if (redoId != null) {
                                TakeStore.save(redoId, copy);
                            }
                        }
                    }));
            });
        }
        feedback(Component.translatable("fbactors.hotkey.pasted", name));
    }

    private static void razorAtPlayhead() {
        ReplayServer replay = Flashback.getReplayServer();
        if (replay == null) {
            return;
        }
        if (ActorTrack.selected() == null && ActorTrack.selectedIds().isEmpty()) {
            feedback(Component.translatable("fbactors.hotkey.no_selection"));
            return;
        }
        int tick = replay.getReplayTick();
        ActorTrack.razorSelectedAt(tick);
        feedback(Component.translatable("fbactors.hotkey.razored", "selection", tick));
    }

    private static void toggleLock() {
        if (ActorTrack.selected() == null && ActorTrack.selectedIds().isEmpty()) {
            feedback(Component.translatable("fbactors.hotkey.no_selection"));
            return;
        }
        ActorTrack.toggleLockSelected();
        feedback(Component.translatable("fbactors.hotkey.locked", "selection"));
    }

    private static void copyKeys() {
        if (ActorTrack.selected() == null) {
            return;
        }
        ActorTrack.copyKeysSelected();
        feedback(Component.translatable("fbactors.hotkey.keys_copied", "camera"));
    }

    private static void pasteKeys() {
        ReplayServer replay = Flashback.getReplayServer();
        if (replay == null) {
            return;
        }
        if (ActorTrack.selected() == null) {
            feedback(Component.translatable("fbactors.hotkey.no_selection"));
            return;
        }
        ActorTrack.pasteKeysSelected(replay.getReplayTick());
        feedback(Component.translatable("fbactors.hotkey.keys_pasted", "camera",
            replay.getReplayTick()));
    }

    private static void rippleDelete() {
        if (ActorTrack.selected() == null && ActorTrack.selectedIds().isEmpty()) {
            feedback(Component.translatable("fbactors.hotkey.no_selection"));
            return;
        }
        ActorTrack.rippleDeleteSelected();
        feedback(Component.translatable("fbactors.hotkey.ripple_deleted", "selection"));
    }

    private static void selectAll() {
        ActorTrack.selectAll();
    }

    private static void exportSelected() {
        Actor actor = ActorTrack.selected();
        if (actor == null) {
            feedback(Component.translatable("fbactors.hotkey.no_selection"));
            return;
        }
        UUID replayId = FlashbackActors.loadedReplayIdOrNull();
        if (replayId == null) {
            return;
        }
        try {
            Path dir = Flashback.getDataDirectory().resolve("fbactors").resolve("exports");
            Files.createDirectories(dir);
            TakeIO.write(dir.resolve(actor.take.id + ".take"), actor.take);
            feedback(Component.translatable("fbactors.hotkey.exported", actor.displayName()));
        } catch (IOException e) {
            FlashbackActors.LOGGER.error("Failed to export take {}", actor.take.id, e);
            feedback(Component.translatable("fbactors.hotkey.export_failed"));
        }
    }

    private static void importTake() {
        Path dir = Flashback.getDataDirectory().resolve("fbactors").resolve("import");
        try (Stream<Path> files = Files.list(dir)) {
            Path newest = files.filter(p -> p.toString().endsWith(".take"))
                .max(Comparator.comparingLong(p -> p.toFile().lastModified()))
                .orElse(null);
            if (newest == null) {
                feedback(Component.translatable("fbactors.hotkey.nothing_to_import"));
                return;
            }
            ActorTake take = TakeIO.read(newest);
            ActorTake copy = take.copyAs(UUID.randomUUID(), take.name);
            String name = displayName(copy);
            ReplayServer replay = Flashback.getReplayServer();
            if (replay != null) {
                replay.execute(() -> {
                    FlashbackActors.ACTORS.add(copy);
                    UUID replayId = FlashbackActors.loadedReplayIdOrNull();
                    if (replayId != null) {
                        TakeStore.save(replayId, copy);
                    }
                    UndoStack.pushEdit(
                        () -> replay.execute(() -> {
                            Actor found = FlashbackActors.ACTORS.byTakeId(copy.id);
                            if (found != null) {
                                FlashbackActors.removeActor(found);
                            }
                        }),
                        () -> replay.execute(() -> {
                            if (FlashbackActors.ACTORS.byTakeId(copy.id) == null) {
                                FlashbackActors.ACTORS.add(copy);
                                UUID redoId = FlashbackActors.loadedReplayIdOrNull();
                                if (redoId != null) {
                                    TakeStore.save(redoId, copy);
                                }
                            }
                        }));
                });
            }
            feedback(Component.translatable("fbactors.hotkey.imported", name));
        } catch (IOException e) {
            FlashbackActors.LOGGER.error("Failed to import take from {}", dir, e);
            feedback(Component.translatable("fbactors.hotkey.import_failed"));
        }
    }

    private static void toggleHelp() {
        helpOpen = !helpOpen;
        helpPositioned = false;
    }

    private static void removeWithUndo(Actor actor, String key) {
        ReplayServer replay = Flashback.getReplayServer();
        if (replay == null) {
            return;
        }
        UndoStack.pushRemoval(actor.take);
        String name = actor.displayName();
        replay.execute(() -> FlashbackActors.removeActor(actor));
        feedback(Component.translatable(key, name));
    }

    private static String displayName(ActorTake take) {
        return take.name == null || take.name.isBlank() ? "Actor" : take.name;
    }

    private static void feedback(Component text) {
        ReplayServer replay = Flashback.getReplayServer();
        if (replay == null) {
            return;
        }
        ServerPlayer viewer = FlashbackActors.viewerOf(replay);
        if (viewer != null) {
            viewer.sendSystemMessage(text, true);
        }
    }

}
