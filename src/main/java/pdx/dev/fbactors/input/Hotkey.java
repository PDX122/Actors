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
import java.lang.reflect.Field;
import java.lang.reflect.Method;
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

/**
 * The hotkey surface: one entry point, a table of shortcuts, and the context
 * checks that decide whether a key press means anything right now.
 *
 * <p>Every shortcut is a row in {@link #SHORTCUTS} with a stable action id,
 * a built-in key and chord, and the action to run. The mixin in the same
 * package feeds key events in; this class decides which row, if any, a press
 * matches and runs it. The help overlay prints the same table it dispatches,
 * so the two cannot drift apart.
 *
 * <p>Keys are rebindable in Minecraft's own Settings > Controls screen under
 * the Flashback Actors category: each action registers a vanilla
 * {@code KeyMapping} there, and the press matcher reads the mapping's live
 * key, so a rebind applies without a restart. Vanilla mappings cannot express
 * a modifier, so the mappings register unbound and the chord stays fixed per
 * action (shown in brackets in the mapping name): an unbound row fires on its
 * built-in key, a rebound row on the new key with the same chord. The whole
 * surface can also be switched off in FBA Settings; when it is, no press
 * fires anything and the mod is driven from the timeline rows and menus.
 *
 * <p>The raw modifier bitmask means different things on different targets
 * (GLFW bits through 26.2, SDL bits on 26.3), so the layout is detected once
 * and every press is reduced to booleans a shortcut can ask about. On macOS
 * the super bit is accepted wherever the control bit is, so Cmd works as Ctrl.
 * Every press is gated on being in a replay, no GUI screen being open, and no
 * text field being focused - otherwise {@code Ctrl+S} would fire while the
 * user types a name beginning with "s".
 */
public final class Hotkey {

    /**
     * Whether the running game reports SDL modifier bits rather than GLFW's.
     *
     * <p>Detected by the presence of {@code org.lwjgl.glfw.GLFW} on the
     * classpath: it is a dependency of every target that uses GLFW bits and
     * was dropped from 26.3, the first SDL target.
     */
    private static final boolean SDL_LAYOUT = !classExists("org.lwjgl.glfw.GLFW");

    private static final int SHIFT = SDL_LAYOUT ? 3 : 1;
    private static final int CONTROL = SDL_LAYOUT ? 192 : 2;
    private static final int ALT = SDL_LAYOUT ? 768 : 4;
    private static final int SUPER = SDL_LAYOUT ? 3072 : 8;

    /** Whether the user is on macOS, where Cmd stands in for Ctrl. */
    private static final boolean MAC =
        System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");

    /** Colour of a dropped marker: opaque yellow, the format Flashback renders as RGB. */
    private static final int MARKER_COLOUR = 0xFFFFFF55;

    /** Countdown {@code /start} uses when a take is re-recorded from the keyboard. */
    private static final int RE_RECORD_DELAY_SECONDS = 3;

    private static boolean installed;
    private static boolean helpOpen;
    private static boolean helpPositioned;

    /** The take on the clipboard for Ctrl+X / Ctrl+C / Ctrl+V, or null. */
    @Nullable
    private static ActorTake clipboard;

    /** {@code Minecraft.screen}, the current GUI screen through 26.2, or null on 26.3. */
    @Nullable
    private static final Field SCREEN_FIELD = findScreenField();

    /** {@code Gui.screen()}, the current GUI screen from 26.3, or null before it. */
    @Nullable
    private static final Method GUI_SCREEN_METHOD = findGuiScreenMethod();

    private Hotkey() {
    }

    // ---- the shortcut table ---------------------------------------------------

    /**
     * One shortcut: a stable action id, the built-in key and chord, the line
     * the help overlay shows for it, and what it runs.
     *
     * <p>The id names the vanilla mapping ({@code key.fbactors.<id>}) and
     * must never change once shipped: renaming one orphans every player's
     * saved bind for it. The chord is a bitmask in this class's own encoding
     * - {@link #SHIFT}, {@link #CONTROL}, {@link #ALT} - so a row reads as
     * "Ctrl+Shift" without caring which windowing library produced the bits.
     * It stays fixed per action because vanilla mappings cannot express a
     * modifier; only the key is rebindable.
     */
    private record Shortcut(String id, String defaultKey, int defaultChord,
                            String description, Runnable action) {
    }

    private static final List<Shortcut> SHORTCUTS = new ArrayList<>();

    /**
     * The vanilla mapping per action, for the Controls screen.
     *
     * <p>Registered unbound: vanilla shows the row with no key until the
     * player picks one, and the matcher falls back to the built-in key while
     * it stays unbound. Unbound defaults also mean zero conflicts with
     * vanilla's own keys, which plain defaults ({@code S}, {@code E},
     * {@code F1}...) would collide with and double-fire.
     */
    private static final Map<String, KeyMapping> MAPPINGS = new HashMap<>();

    /** The built-in platform code per action, resolved once at install. */
    private static final Map<String, Integer> DEFAULT_CODES = new HashMap<>();

    /**
     * Installs the hotkey map. Idempotent.
     *
     * <p>Builds the shortcut table once and registers one unbound vanilla
     * mapping per action, which is what lists the mod in Minecraft's Controls
     * screen. Called from {@code onInitializeClient}, the point Fabric
     * requires mapping registration at. Built-in key codes are cached here;
     * rebinds are read live off the mappings on every press.
     */
    public static void install() {
        if (installed) {
            return;
        }
        installed = true;

        add("toggleRecording", "key.keyboard.r", CONTROL, "Start / stop recording", Hotkey::toggleRecording);
        add("newActor", "key.keyboard.n", CONTROL, "New actor", Hotkey::newActor);
        add("reRecordLast", "key.keyboard.r", CONTROL | SHIFT, "Re-record last take",
            Hotkey::reRecordLast);
        add("discardLast", "key.keyboard.backspace", CONTROL, "Discard last take",
            Hotkey::discardLast);
        add("undo", "key.keyboard.z", CONTROL, "Undo", Hotkey::undo);
        add("redo", "key.keyboard.y", CONTROL, "Redo", Hotkey::redo);
        add("redoAlt", "key.keyboard.z", CONTROL | SHIFT, "Redo (alt)", Hotkey::redo);
        add("saveAll", "key.keyboard.s", CONTROL, "Save actors to library", Hotkey::saveAll);
        add("duplicateSelected", "key.keyboard.d", CONTROL, "Duplicate selected actor", Hotkey::duplicateSelected);
        add("deleteSelected", "key.keyboard.delete", 0, "Delete selected actor", Hotkey::deleteSelected);
        add("renameSelected", "key.keyboard.f2", 0, "Rename selected actor", Hotkey::renameSelected);
        add("muteSelected", "key.keyboard.m", CONTROL, "Mute selected actor", Hotkey::muteSelected);
        add("soloSelected", "key.keyboard.m", CONTROL | SHIFT, "Solo selected actor",
            Hotkey::soloSelected);
        add("nextActor", "key.keyboard.right.bracket", CONTROL, "Next actor",
            () -> selectRelative(1));
        add("prevActor", "key.keyboard.left.bracket", CONTROL, "Previous actor",
            () -> selectRelative(-1));
        add("dropMarker", "key.keyboard.k", CONTROL, "Drop marker", Hotkey::dropMarker);
        add("cutSelected", "key.keyboard.x", CONTROL, "Cut selected actor", Hotkey::cutSelected);
        add("copySelected", "key.keyboard.c", CONTROL, "Copy selected actor", Hotkey::copySelected);
        add("paste", "key.keyboard.v", CONTROL, "Paste actor", Hotkey::paste);
        add("razorAtPlayhead", "key.keyboard.b", CONTROL, "Razor at playhead", Hotkey::razorAtPlayhead);
        add("toggleLock", "key.keyboard.l", CONTROL, "Lock / unlock track", Hotkey::toggleLock);
        add("copyKeys", "key.keyboard.c", ALT, "Copy camera keys", Hotkey::copyKeys);
        add("pasteKeys", "key.keyboard.v", ALT, "Paste camera keys", Hotkey::pasteKeys);
        add("rippleDelete", "key.keyboard.delete", SHIFT, "Ripple-delete selection", Hotkey::rippleDelete);
        add("selectAll", "key.keyboard.a", CONTROL, "Select all actors", Hotkey::selectAll);
        add("exportSelected", "key.keyboard.e", CONTROL, "Export selected actor", Hotkey::exportSelected);
        add("importTake", "key.keyboard.i", CONTROL, "Import actor", Hotkey::importTake);
        add("toggleHelp", "key.keyboard.f1", 0, "Open this help", Hotkey::toggleHelp);
        add("toggleHelpAlt", "key.keyboard.slash", CONTROL, "Open this help (alt)", Hotkey::toggleHelp);
        add("toggleGizmo", "key.keyboard.g", 0, "Show / hide actor gizmo",
            () -> pdx.dev.fbactors.ui.ActorGizmo.toggle());
    }

    /**
     * Draws the shortcut table, if it is open. A no-op otherwise.
     *
     * <h2>Why this must be called from the render pass and not the client tick</h2>
     *
     * <p>It used to be called from an {@code END_CLIENT_TICK} handler, and that
     * crashes the game outright. ImGui only has a valid context and a valid frame
     * between {@code newFrame} and {@code render}; the client tick runs outside
     * both. The first ImGui call made from there - {@code setNextWindowSize} -
     * faults inside the native library with an access violation, which takes the
     * whole process down with no Java exception and no Minecraft crash report,
     * because the fault is in native code rather than in anything the JVM is
     * watching. The mod's replay overlay already draws its own windows once per
     * frame, so the help window is drawn from there instead, which is the only
     * place it can safely live.
     */
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
        for (Shortcut shortcut : SHORTCUTS) {
            ImGui.textUnformatted(shortcut.description() + " ..... " + bindText(shortcut));
        }
        ImGui.textUnformatted("");
        ImGui.textDisabled("Press the help key again to close");
        ImGui.end();
    }

    /**
     * Appends one row to the shortcut table and registers its vanilla
     * mapping for the Controls screen.
     *
     * <p>The mapping registers unbound ({@code key.keyboard.unknown} is not
     * a real key on any target, so it resolves to no code): the Controls
     * screen lists the action with no key until the player picks one, and
     * the matcher falls back to the built-in key meanwhile. Unbound defaults
     * also mean no conflicts with vanilla's own keys.
     *
     * @param id the stable action id, naming the {@code key.fbactors.<id>} mapping
     * @param defaultKey the built-in key translation name
     * @param chord the fixed modifier chord
     * @param description what the shortcut does, as shown in the help overlay
     * @param action what the shortcut runs
     */
    private static void add(String id, String defaultKey, int chord, String description,
                            Runnable action) {
        SHORTCUTS.add(new Shortcut(id, defaultKey, chord, description, action));
        try {
            DEFAULT_CODES.put(id, Compat.key(defaultKey));
        } catch (Exception | LinkageError ignored) {
            // A name this target does not know never matches a press.
        }
        try {
            MAPPINGS.put(id, Compat.registerKey("fbactors", "key.fbactors." + id,
                "key.keyboard.unknown"));
        } catch (Exception | LinkageError e) {
            FlashbackActors.LOGGER.warn("Could not register the {} hotkey", id, e);
        }
    }

    /**
     * Feeds a key press in from the mixin.
     *
     * <p>Marshals onto the client main thread first: on 1.21.1 through 1.21.8
     * the keyboard handler runs on the GLFW thread, and from 1.21.10 the game
     * has already marshalled - but {@link Minecraft#execute} is correct from
     * either, and the actions below all assume the main thread.
     *
     * @param key the key code, as {@link Compat#key} resolves it
     * @param mods the raw modifier bitmask from the windowing library
     */
    public static void press(int key, int mods) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        mc.execute(() -> fire(key, mods));
    }

    /**
     * Whether the running game has the 1.21.10+ {@code KeyEvent} type.
     *
     * <p>The modern keyboard handler is declared with a {@code (long, int)}
     * prefix so it compiles before that class exists, and on the older targets
     * it matches the five-argument {@code keyPress} as a prefix too. This is
     * how the handler tells the two apart at runtime: the class exists exactly
     * on the targets whose {@code keyPress} carries a {@code KeyEvent}.
     */
    public static boolean hasModernKeyEvent() {
        return classExists("net.minecraft.client.input.KeyEvent");
    }

    /**
     * Rebuilds the raw modifier bitmask by polling the modifier keys.
     *
     * <p>The modern {@code keyPress} does not hand the handler the {@code KeyEvent}
     * (see {@link #hasModernKeyEvent}), so the modifiers are reconstructed from
     * the window's live key state. The bits are ORed in with the layout the
     * running game uses - GLFW's through 26.2, SDL's on 26.3 - so the result
     * is a bitmask {@link #fire} can decode exactly as if the event had
     * provided it.
     *
     * @param window the window handle, from {@link Compat#currentWindowHandle}
     * @return the raw modifier bitmask
     */
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

    /**
     * Runs the action for a key press, if the context allows one.
     *
     * <p>Three gates, in order: a replay must be open (every action is about
     * the actors of the current replay), no GUI screen may be in front (a
     * screen owns the keyboard while it is open), and no ImGui text field may
     * hold focus (otherwise the keys are text, not shortcuts). When the whole
     * surface is switched off nothing fires at all. The help toggle is
     * checked before the "help is open" block so it can always close.
     */
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

        if (matchesId(key, mods, "toggleHelp") || matchesId(key, mods, "toggleHelpAlt")) {
            helpOpen = !helpOpen;
            return;
        }
        if (helpOpen) {
            return;
        }

        for (Shortcut shortcut : SHORTCUTS) {
            if (shortcut.id().equals("toggleHelp") || shortcut.id().equals("toggleHelpAlt")) {
                continue;
            }
            if (matchesShortcut(key, mods, shortcut)) {
                shortcut.action().run();
                return;
            }
        }
    }

    /**
     * Whether a press is exactly a shortcut's bind.
     *
     * <p>Exact, not "at least": {@code Ctrl+Shift+R} must not also fire the
     * {@code Ctrl+R} row, or re-recording a take would first toggle the
     * recording it is about to re-record. The key is the vanilla mapping's
     * live key once rebound (read through its save string, the same value
     * vanilla persists to options.txt), else the built-in one; the chord is
     * always the fixed one, because vanilla mappings cannot express a
     * modifier.
     */
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
                // Fall through to the built-in key.
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

    /** Whether a press matches an action by id. */
    private static boolean matchesId(int key, int mods, String id) {
        Shortcut shortcut = findShortcut(id);
        return shortcut != null && matchesShortcut(key, mods, shortcut);
    }

    /**
     * The bind text for the help overlay: the fixed chord plus the live key.
     *
     * <p>The key half comes from the vanilla mapping's own translated message
     * once rebound (which also covers mouse buttons and unbound rows), else
     * the built-in key name. The chord half is fixed and always shown, so a
     * rebound row still reads correctly.
     */
    private static String bindText(Shortcut shortcut) {
        String chord = chordText(shortcut.defaultChord());
        KeyMapping mapping = MAPPINGS.get(shortcut.id());
        if (mapping != null) {
            try {
                if (Compat.key(mapping.saveString()) >= 0) {
                    return chord + mapping.getTranslatedKeyMessage().getString();
                }
            } catch (Exception | LinkageError ignored) {
                // Fall through to the built-in key.
            }
        }
        return chord + prettyKey(shortcut.defaultKey());
    }

    /** The fixed chord as text, e.g. {@code "Ctrl+Shift+"} or {@code ""}. */
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

    /**
     * A readable key name: {@code "key.keyboard.r"} reads {@code "R"},
     * {@code "key.keyboard.right.bracket"} reads {@code "Right Bracket"}.
     */
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

    /** The shortcut row for an action id, or null. */
    @Nullable
    private static Shortcut findShortcut(String id) {
        for (Shortcut shortcut : SHORTCUTS) {
            if (shortcut.id().equals(id)) {
                return shortcut;
            }
        }
        return null;
    }

    /** Whether a class is on the classpath, for the SDL/GLFW layout probe. */
    private static boolean classExists(String name) {
        try {
            Class.forName(name, false, Hotkey.class.getClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Nullable
    private static Field findScreenField() {
        try {
            return Minecraft.class.getField("screen");
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    @Nullable
    private static Method findGuiScreenMethod() {
        try {
            return Class.forName("net.minecraft.client.gui.Gui").getMethod("screen");
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    /**
     * Whether a GUI screen is currently in front of the game.
     *
     * <p>The current screen lived in {@code Minecraft.screen} through 26.2 and
     * moved to {@code Gui.screen()} on 26.3, so neither direct reference
     * compiles everywhere. Both lookups are cached once; whichever exists on
     * the running target is the one that answers.
     */
    private static boolean screenOpen(Minecraft mc) {
        Field field = SCREEN_FIELD;
        if (field != null) {
            try {
                return field.get(mc) != null;
            } catch (ReflectiveOperationException e) {
                return false;
            }
        }
        Method method = GUI_SCREEN_METHOD;
        if (method != null) {
            try {
                return method.invoke(mc.gui) != null;
            } catch (ReflectiveOperationException e) {
                return false;
            }
        }
        return false;
    }

    // ---- actions: recording ---------------------------------------------------

    /**
     * {@code Ctrl+R} - starts a take when idle, finishes the current one when
     * not. The same toggle the timeline's record button runs.
     */
    private static void toggleRecording() {
        ActorTrack.toggleRecording();
    }

    /**
     * {@code Ctrl+N} - enters actor setup, the first half of recording a new
     * take. Refused while a take is already running: entering setup mid-take
     * would pause the replay and yank the viewer out of survival under the
     * performer's fingers.
     */
    private static void newActor() {
        if (ActorMode.phase() != ActorMode.Phase.IDLE) {
            feedback(Component.translatable("fbactors.hotkey.busy"));
            return;
        }
        ActorTrack.newActor();
    }

    /**
     * {@code Ctrl+Shift+R} - discards the last take and sets up its replacement.
     *
     * <p>The discard is undoable, because "re-record" pressed a beat too late
     * is the most likely mispress on the keyboard. The replacement then runs
     * through the ordinary setup-and-countdown path rather than a shortcut of
     * it, so the voice prompt and the 3-2-1 behave exactly as they do for
     * {@code /start}.
     */
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

    /**
     * {@code Ctrl+Backspace} - discards the last take, undoably.
     *
     * <p>"Last" means the most recently recorded, which is the end of the
     * manager's list: takes are appended as they are saved, so the last entry
     * is the one a performer most likely meant.
     */
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

    /**
     * {@code Ctrl+Z} - restores the most recently discarded take.
     *
     * <p>Timeline edits (moves, trims, razor, ripple, lock, key paste) undo
     * first: they are the most recent thing the user did with the timeline.
     * Removals undo when no edit is pending. The take is re-added to the
     * manager and written back to disk, so the actor reappears in the panel
     * on the next tick and survives a reload.
     */
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

    /**
     * {@code Ctrl+Y} / {@code Ctrl+Shift+Z} - re-applies the most recently
     * undone edit or removal. Edits redo first, removals when no edit is
     * pending, mirroring {@link #undo()}.
     */
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

    /**
     * {@code Ctrl+S} - writes every actor's take to disk now.
     *
     * <p>The ordinary save is throttled and deferred, which is right for
     * editing and wrong for a performer who is about to close the replay: this
     * is the "I'm done, keep it" key, so it flushes everything unconditionally.
     */
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

    /**
     * {@code Ctrl+D} - duplicates the selected actor as a new take.
     */
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

    /**
     * {@code Delete} - removes the selected actor, undoably so no confirm is
     * needed.
     */
    private static void deleteSelected() {
        Actor actor = ActorTrack.selected();
        if (actor == null) {
            feedback(Component.translatable("fbactors.hotkey.no_selection"));
            return;
        }
        removeWithUndo(actor, "fbactors.hotkey.deleted");
    }

    /**
     * {@code F2} - opens the panel's inline rename field for the selected actor.
     */
    private static void renameSelected() {
        ActorTrack.renameSelected();
    }

    // ---- actions: the selected actor ------------------------------------------

    /**
     * {@code Ctrl+M} - mutes or unmutes the selected actor.
     *
     * <p>Mute is the panel's visibility toggle: a muted actor is despawned and
     * stops playing, but its take is untouched, so unmuting is always available.
     */
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

    /**
     * {@code Ctrl+Shift+M} - toggles solo on the selected actor.
     *
     * <p>Solo is exclusive: turning it on for one actor turns it off for
     * every other, because the playback rule is "when any actor is soloed,
     * only soloed actors play". Two actors soloed at once would be a state
     * the user cannot see from the panel.
     */
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

    /**
     * {@code Ctrl+]} / {@code Ctrl+[} - moves the selection to the next or
     * previous actor in the panel.
     *
     * @param delta +1 for next, -1 for previous
     */
    private static void selectRelative(int delta) {
        ActorTrack.selectNext(delta);
    }

    /**
     * {@code Ctrl+K} - drops a Flashback marker at the playhead.
     *
     * <p>The marker is written straight into the replay's metadata, the same
     * store Flashback's own marker keys use, so it shows up in the timeline
     * and survives a reload. It is positioned at the viewer and tagged with
     * the current dimension, which is what makes a marker findable when the
     * performer scrubs back to it later.
     */
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

    // ---- actions: clipboard ----------------------------------------------------

    /**
     * {@code Ctrl+X} - cuts the selected actor onto the take clipboard.
     *
     * <p>The removal is undoable, like every other removal here. The clipboard
     * holds the take itself, not the actor: pasting builds a fresh actor with
     * a fresh take id, so the same take can be pasted twice without two
     * actors sharing an id.
     */
    private static void cutSelected() {
        Actor actor = ActorTrack.selected();
        if (actor == null) {
            feedback(Component.translatable("fbactors.hotkey.no_selection"));
            return;
        }
        clipboard = actor.take;
        removeWithUndo(actor, "fbactors.hotkey.cut");
    }

    /**
     * {@code Ctrl+C} - copies the selected actor onto the take clipboard
     * without removing it.
     */
    private static void copySelected() {
        Actor actor = ActorTrack.selected();
        if (actor == null) {
            return;
        }
        clipboard = actor.take;
        feedback(Component.translatable("fbactors.hotkey.copied", actor.displayName()));
    }

    /**
     * {@code Ctrl+V} - pastes the clipboard as a new actor.
     *
     * <p>The paste gets a new take id even when the clipboard holds an
     * untouched copy, so pasting the same take twice yields two independent
     * actors rather than two rows resolving to one.
     */
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

    // ---- actions: phase 1 timeline ops ----------------------------------------

    /** {@code Ctrl+B} - razors every selected take at the playhead. */
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

    /** {@code Ctrl+L} - locks or unlocks every selected track. */
    private static void toggleLock() {
        if (ActorTrack.selected() == null && ActorTrack.selectedIds().isEmpty()) {
            feedback(Component.translatable("fbactors.hotkey.no_selection"));
            return;
        }
        ActorTrack.toggleLockSelected();
        feedback(Component.translatable("fbactors.hotkey.locked", "selection"));
    }

    /** {@code Alt+C} - copies the selected take's camera keys. */
    private static void copyKeys() {
        if (ActorTrack.selected() == null) {
            return;
        }
        ActorTrack.copyKeysSelected();
        feedback(Component.translatable("fbactors.hotkey.keys_copied", "camera"));
    }

    /** {@code Alt+V} - pastes camera keys at the playhead. */
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

    /** {@code Shift+Delete} - ripple-deletes the selection and closes gaps. */
    private static void rippleDelete() {
        if (ActorTrack.selected() == null && ActorTrack.selectedIds().isEmpty()) {
            feedback(Component.translatable("fbactors.hotkey.no_selection"));
            return;
        }
        ActorTrack.rippleDeleteSelected();
        feedback(Component.translatable("fbactors.hotkey.ripple_deleted", "selection"));
    }

    /** {@code Ctrl+A} - selects every actor. */
    private static void selectAll() {
        ActorTrack.selectAll();
    }

    // ---- actions: export, import ----------------------------------------------

    /**
     * {@code Ctrl+E} - exports the selected actor's take to the exports folder.
     *
     * <p>The take is written beside the other global libraries rather than into
     * the current replay's folder, so an export is a take that travels: drop it
     * in another replay's folder, or keep it as a backup, without opening the
     * replay it came from.
     */
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

    /**
     * {@code Ctrl+I} - imports the newest take from the import folder.
     *
     * <p>The import folder is {@code fbactors/import} under Flashback's data
     * directory; the most recently modified {@code .take} file in it is read
     * and added as a new actor. "Newest" is the only ordering a folder of
     * files has, and it is the one a user who just exported something expects.
     */
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

    /** {@code F1} / rebound help key - opens or closes the shortcut help overlay. */
    private static void toggleHelp() {
        helpOpen = !helpOpen;
        helpPositioned = false;
    }

    // ---- helpers ---------------------------------------------------------------

    /**
     * Removes an actor and records the removal as undoable.
     *
     * @param actor the actor to remove
     * @param key   the feedback message, taking the actor's name
     */
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

    /** The name a take should be shown under, defaulting like {@link Actor}. */
    private static String displayName(ActorTake take) {
        return take.name == null || take.name.isBlank() ? "Actor" : take.name;
    }

    /**
     * Puts a message on the viewer's action bar.
     *
     * <p>Routed through the replay's viewer rather than the local player
     * because the viewer is a {@link ServerPlayer} on every target - the
     * two-argument {@code sendSystemMessage} that carries an action bar flag
     * exists there from 1.21.1 through 26.3, while on the client player it
     * does not survive 26.3. This is the same channel {@code ActorMode} uses
     * for "Take saved", so hotkey feedback lands exactly where the performer
     * is already looking.
     */
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

    // ---- the help overlay -----------------------------------------------------

}