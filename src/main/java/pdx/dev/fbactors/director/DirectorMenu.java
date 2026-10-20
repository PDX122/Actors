package pdx.dev.fbactors.director;

import imgui.moulberry90.ImGui;
import imgui.moulberry90.type.ImBoolean;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import pdx.dev.fbactors.FlashbackActors;
import pdx.dev.fbactors.actor.Actor;
import pdx.dev.fbactors.take.ActorTake;
import pdx.dev.fbactors.take.CameraHeight;
import pdx.dev.fbactors.take.CameraKeyframe;
import pdx.dev.fbactors.take.CameraMove;
import pdx.dev.fbactors.take.DirectorShot;
import pdx.dev.fbactors.take.CameraTrack;
import pdx.dev.fbactors.take.DirectorConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;


public final class DirectorMenu {


    private static final String ICON_STAR = "\ue838";


    private static final String ICON_STAR_BORDER = "\ue83a";

    private static final float PANEL_WIDTH = 380.0f;
    private static final float PANEL_HEIGHT = 500.0f;

    private static boolean open;


    private static final ImBoolean OPEN = new ImBoolean();


    private static final Set<UUID> selected = new LinkedHashSet<>();


    @Nullable
    private static UUID primary;


    private static final Map<UUID, float[]> weights = new HashMap<>();


    private static final Map<UUID, ImBoolean> boxes = new HashMap<>();

    private static DirectorConfig.DirectorStyle style = DirectorConfig.DirectorStyle.SPIELBERG;

    private static boolean handheld;


    private static final float[] anticipation = {0.0f};


    private static final float[] holdTicks = {40.0f};


    private static final String AUTO_LABEL = "Auto";


    @Nullable
    private static CameraMove move;


    @Nullable
    private static CameraHeight height;


    @Nullable
    private static CameraKeyframe.ShotType shotType;


    @Nullable
    private static DirectorConfig.DirectorStyle syncedStyle;

    @Nullable
    private static String status;


    @Nullable
    private static DirectorConfig pending;


    private static final int ACTION_RELEASE = 1;


    private static final int ACTION_ENGAGE = 2;


    private static int pendingCameraAction;

    private DirectorMenu() {
    }

    public static boolean isOpen() {
        return open;
    }


    public static void open() {
        open = true;
        OPEN.set(true);
        status = null;
        DirectorConfig saved = firstSavedConfig();
        if (saved == null) {
            return;
        }
        selected.clear();
        weights.clear();
        primary = null;
        for (DirectorConfig.ActorSelection selection : saved.actors()) {
            selected.add(selection.actorId());
            weights.put(selection.actorId(), new float[] {selection.weight() * 100.0f});
            if (selection.primary()) {
                primary = selection.actorId();
            }
        }
        style = saved.style();
        handheld = saved.handheld();
        anticipation[0] = saved.anticipationTicks();
        holdTicks[0] = saved.holdTicks();


        syncedStyle = saved.style();
        move = saved.move();
        height = saved.height();
        shotType = saved.shotType();
        DirectorShotsWindow.setShots(saved.shots());
    }


    @Nullable
    static UUID primaryOrFirst(List<DirectorSubjects.Subject> subjects) {
        if (primary != null) {
            return primary;
        }
        return subjects.isEmpty() ? null : subjects.get(0).id();
    }


    public static void close() {
        open = false;
        OPEN.set(false);
    }


    @Nullable
    private static DirectorConfig firstSavedConfig() {
        for (Actor actor : FlashbackActors.ACTORS.actors()) {
            DirectorConfig config = actor.take.directorConfig;
            if (config != null && config.valid()) {
                return config;
            }
        }
        return null;
    }


    public static void renderMenuItem() {
        if (ImGui.menuItem("Director##DirectorOpen", open)) {
            open();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setItemTooltip("Auto-generate a cinematic camera from the recorded actors");
        }
    }


    public static void renderShotsMenuItem() {
        if (ImGui.menuItem("Shots##DirectorShotsOpen", DirectorShotsWindow.isOpen())) {
            DirectorShotsWindow.open();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setItemTooltip("Direct the shot list: who, how, framed how, from when");
        }
    }


    public static void render() {
        if (!open) {
            return;
        }
        ImGui.setNextWindowSize(PANEL_WIDTH, PANEL_HEIGHT);
        OPEN.set(open);
        if (!ImGui.begin("Director", OPEN)) {


            ImGui.end();
            return;
        }
        open = OPEN.get();

        renderActors();
        ImGui.separator();
        renderPresets();
        ImGui.separator();
        renderStyle();
        ImGui.separator();
        renderMoveChoices();
        ImGui.separator();
        renderFooter();
        ImGui.end();
        open = OPEN.get();
    }


    private static void renderPresets() {
        ImGui.text("Presets");
        DirectorPresets.Preset[] presets = DirectorPresets.all();
        for (int i = 0; i < presets.length; i++) {
            DirectorPresets.Preset preset = presets[i];
            float width = (PANEL_WIDTH - 40.0f - 8.0f) / 2.0f;
            if (ImGui.button(preset.label() + "##DirectorPreset" + i, width, 0.0f)) {
                applyPreset(preset);
            }
            if (ImGui.isItemHovered()) {
                ImGui.setItemTooltip(preset.tooltip());
            }
            if (i % 2 == 0) {
                ImGui.sameLine();
            }
        }
    }

    private static void renderActors() {
        ImGui.text("Actors");
        List<Actor> actors = FlashbackActors.ACTORS.actors();
        if (actors.isEmpty()) {
            ImGui.textDisabled("No actors in this replay.");
        }
        for (Actor actor : actors) {
            ImGui.pushID(actor.take.id.hashCode());
            renderActorRow(actor);
            ImGui.popID();
        }
        ImGui.spacing();
        ImGui.text("Players");
        List<DirectorSubjects.PlayerSubject> players = DirectorSubjects.players();
        if (players.isEmpty()) {
            ImGui.textDisabled("No other players in this replay.");
            return;
        }
        ImGui.textDisabled("Framed where they stand when you generate.");
        for (DirectorSubjects.PlayerSubject player : players) {
            ImGui.pushID(player.id().hashCode());
            renderSubjectRow(player.id(), player.name(), "Player");
            ImGui.popID();
        }
    }


    private static void renderActorRow(Actor actor) {
        renderSubjectRow(actor.take.id, actor.displayName(), "actor");
    }


    private static void renderSubjectRow(UUID id, String name, String kind) {
        boolean isSelected = selected.contains(id);

        ImBoolean box = boxes.computeIfAbsent(id, key -> new ImBoolean());
        box.set(isSelected);
        ImGui.checkbox("##DirectorSelect", box);
        boolean nowSelected = box.get();
        if (nowSelected != isSelected) {
            if (nowSelected) {
                selected.add(id);
            } else {
                selected.remove(id);
                if (id.equals(primary)) {
                    primary = null;
                }
            }
        }

        ImGui.sameLine();
        boolean isPrimary = id.equals(primary);
        if (ImGui.smallButton((isPrimary ? ICON_STAR : ICON_STAR_BORDER) + "##DirectorPrimary")) {
            primary = isPrimary ? null : id;
            if (primary != null) {
                selected.add(id);
                boxes.computeIfAbsent(id, key -> new ImBoolean()).set(true);
            }
        }
        if (ImGui.isItemHovered()) {
            ImGui.setItemTooltip("Primary " + kind + ": the subject the camera is for");
        }

        ImGui.sameLine();
        if (isSelected) {
            ImGui.text(name);
        } else {
            ImGui.textDisabled(name);
        }

        ImGui.sameLine();
        ImGui.setNextItemWidth(96.0f);
        float[] weight = weights.computeIfAbsent(id, key -> new float[] {100.0f});
        ImGui.sliderFloat("##DirectorWeight", weight, 0.0f, 100.0f, "%.0f%%");
        if (ImGui.isItemHovered()) {
            ImGui.setItemTooltip("How much the camera favours this " + kind);
        }
    }


    private static final ImBoolean handheldBox = new ImBoolean();


    private static void renderMoveChoices() {
        ImGui.text("Camera move");
        ImGui.setNextItemWidth(PANEL_WIDTH - 40.0f);
        if (ImGui.beginCombo("##DirectorMove", move == null ? AUTO_LABEL : move.label())) {
            if (ImGui.menuItem(AUTO_LABEL, move == null)) {
                move = null;
            }
            for (CameraMove candidate : CameraMove.values()) {
                if (ImGui.menuItem(candidate.label(), candidate == move)) {
                    move = candidate;
                }
            }
            ImGui.endCombo();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setItemTooltip("What the camera does during each shot. Auto lets the style choose.");
        }

        ImGui.spacing();
        ImGui.text("Camera height");
        ImGui.setNextItemWidth(PANEL_WIDTH - 40.0f);
        if (ImGui.beginCombo("##DirectorHeight", height == null ? AUTO_LABEL : height.label())) {
            if (ImGui.menuItem(AUTO_LABEL, height == null)) {
                height = null;
            }
            for (CameraHeight candidate : CameraHeight.values()) {
                if (ImGui.menuItem(candidate.label(), candidate == height)) {
                    height = candidate;
                }
            }
            ImGui.endCombo();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setItemTooltip("Where the lens sits: at the subject's feet, their eye line, or above them.");
        }

        ImGui.spacing();
        ImGui.text("Framing");
        ImGui.setNextItemWidth(PANEL_WIDTH - 40.0f);
        if (ImGui.beginCombo("##DirectorShot", shotType == null ? AUTO_LABEL : shotName(shotType))) {
            if (ImGui.menuItem(AUTO_LABEL, shotType == null)) {
                shotType = null;
            }
            for (CameraKeyframe.ShotType candidate : CameraKeyframe.ShotType.values()) {
                if (ImGui.menuItem(shotName(candidate), candidate == shotType)) {
                    shotType = candidate;
                }
            }
            ImGui.endCombo();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setItemTooltip("Pin the framing for every shot. Auto lets each moment choose.");
        }
    }

    static String shotName(CameraKeyframe.ShotType type) {
        String[] words = type.name().toLowerCase(java.util.Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();
        for (String word : words) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return builder.toString();
    }

    private static void renderStyle() {
        ImGui.text("Style");
        ImGui.setNextItemWidth(PANEL_WIDTH - 40.0f);
        if (ImGui.beginCombo("##DirectorStyle", DirectorGenerator.styleName(style))) {
            for (DirectorConfig.DirectorStyle candidate : DirectorConfig.DirectorStyle.values()) {
                if (ImGui.menuItem(DirectorGenerator.styleName(candidate), candidate == style)) {
                    style = candidate;
                }
            }
            ImGui.endCombo();
        }
        if (ImGui.isItemHovered()) {
            ImGui.setItemTooltip("The camera style the shots are built from");
        }


        if (syncedStyle != style) {
            syncedStyle = style;
            holdTicks[0] = DirectorStyle.of(style).defaultHoldTicks();
        }

        ImGui.spacing();
        handheldBox.set(handheld);
        ImGui.checkbox("Handheld##DirectorHandheld", handheldBox);
        handheld = handheldBox.get();
        if (ImGui.isItemHovered()) {
            ImGui.setItemTooltip("Subtle camera movement. How much depends on the style.");
        }

        ImGui.spacing();
        ImGui.text("Camera lead");
        ImGui.setNextItemWidth(PANEL_WIDTH - 40.0f);
        ImGui.sliderFloat("##DirectorLead", anticipation, 0.0f,
            DirectorConfig.MAX_ANTICIPATION_TICKS, "%.0f ticks early");
        if (ImGui.isItemHovered()) {
            ImGui.setItemTooltip("How early the camera starts moving. Zero uses the style's default.");
        }

        ImGui.spacing();
        ImGui.text("Shot length");
        ImGui.setNextItemWidth(PANEL_WIDTH - 40.0f);
        ImGui.sliderFloat("##DirectorHold", holdTicks,
            DirectorConfig.MIN_HOLD_TICKS, DirectorConfig.MAX_HOLD_TICKS, "%.0f ticks");
        if (ImGui.isItemHovered()) {
            ImGui.setItemTooltip("How long each camera move lasts. Longer means fewer, slower shots.");
        }
    }

    private static void renderFooter() {
        String note = status;
        if (note != null) {
            ImGui.textDisabled(note);
        }
        boolean valid = !selected.isEmpty();
        if (!valid) {
            ImGui.beginDisabled();
        }
        if (ImGui.button("Generate Camera", PANEL_WIDTH - 40.0f, 0.0f) && valid) {
            generate();
        }
        if (!valid) {
            ImGui.endDisabled();
            ImGui.textDisabled("Select at least one actor to generate.");
        }


        ImGui.spacing();
        if (CameraPlayback.driving()) {
            if (ImGui.button("Take camera back", PANEL_WIDTH - 40.0f, 0.0f)) {
                pendingCameraAction = ACTION_RELEASE;
            }
            if (ImGui.isItemHovered()) {
                ImGui.setItemTooltip("Stop the Director camera and fly freely. A movement key does this too.");
            }
        } else {
            if (ImGui.button("Give camera back", PANEL_WIDTH - 40.0f, 0.0f)) {
                pendingCameraAction = ACTION_ENGAGE;
            }
            if (ImGui.isItemHovered()) {
                ImGui.setItemTooltip("Let the Director camera drive the view again.");
            }
        }
    }


    private static DirectorConfig buildConfig() {
        List<DirectorConfig.ActorSelection> selections = new ArrayList<>();
        Set<UUID> takeIds = new HashSet<>();
        for (Actor actor : FlashbackActors.ACTORS.actors()) {
            UUID id = actor.take.id;
            takeIds.add(id);
            if (!selected.contains(id)) {
                continue;
            }
            selections.add(selectionFor(id));
        }


        for (UUID id : selected) {
            if (!takeIds.contains(id)) {
                selections.add(selectionFor(id));
            }
        }
        return new DirectorConfig(selections, style, handheld, anticipation[0],
            Math.round(holdTicks[0]), move, height, shotType,
            DirectorShotsWindow.shots());
    }

    private static DirectorConfig.ActorSelection selectionFor(UUID id) {
        float[] weight = weights.get(id);
        float fraction = weight == null ? 1.0f : weight[0] / 100.0f;
        return new DirectorConfig.ActorSelection(id, id.equals(primary), fraction);
    }


    static void applyPreset(DirectorPresets.Preset preset) {
        style = preset.style();
        move = preset.move();
        height = preset.height();
        shotType = preset.shotType();
        holdTicks[0] = preset.holdTicks();
        syncedStyle = preset.style();
        status = "Preset: " + preset.label() + ". Tweak anything, then Generate Camera.";
    }


    private static void generate() {
        DirectorConfig config = buildConfig();
        if (!config.valid()) {
            status = "Select at least one actor.";
            return;
        }
        pending = config;
        close();
    }


    public static void flushPending() {


        int cameraAction = pendingCameraAction;
        if (cameraAction != 0) {
            pendingCameraAction = 0;
            if (cameraAction == ACTION_RELEASE) {
                CameraPlayback.release();
            } else {
                CameraPlayback.reengage();
            }
        }

        DirectorConfig config = pending;
        if (config == null) {
            return;
        }
        pending = null;

        Level level = Minecraft.getInstance().level;
        CameraTrack track = DirectorGenerator.generate(FlashbackActors.ACTORS.actors(), config, level);
        if (track.isEmpty()) {
            FlashbackActors.LOGGER.warn("Director: no camera could be generated");
            return;
        }
        boolean written = DirectorGenerator.writeToTimeline(track, config);
        storeConfig(config, track);
        if (written) {


            CameraPlayback.arm();
            FlashbackActors.LOGGER.info("Director: generated {} keyframe(s) with {}",
                track.size(), DirectorGenerator.styleName(config.style()));
        } else {
            FlashbackActors.LOGGER.warn(
                "Director: built {} keyframe(s) but there was no editor to write to", track.size());
        }
    }


    private static void storeConfig(DirectorConfig config, CameraTrack track) {
        DirectorConfig.ActorSelection primarySelection = config.primary();
        Actor carrier = null;
        for (Actor actor : FlashbackActors.ACTORS.actors()) {
            if (config.selectionFor(actor.take.id) == null) {
                continue;
            }
            if (carrier == null) {
                carrier = actor;
            }
            if (primarySelection != null && actor.take.id.equals(primarySelection.actorId())) {
                carrier = actor;
                break;
            }
        }
        if (carrier == null) {


            FlashbackActors.LOGGER.info(
                "Director: camera written; config not stored (no actor take to carry it)");
            return;
        }
        ActorTake take = carrier.take;
        take.directorConfig = config;
        take.cameraTrack.replaceWith(track);
        FlashbackActors.save(take);
    }
}

