package pdx.dev.fbactors.director;

import com.moulberry.flashback.state.EditorScene;
import com.moulberry.flashback.state.EditorState;
import com.moulberry.flashback.state.EditorStateManager;
import com.moulberry.flashback.state.KeyframeTrack;
import pdx.dev.fbactors.FlashbackActors;
import pdx.dev.fbactors.compat.Compat;

import java.util.HashSet;
import java.util.Set;


public final class CameraPlayback {


    private static final String[] MOVEMENT_KEYS = {
        "key.keyboard.w",
        "key.keyboard.a",
        "key.keyboard.s",
        "key.keyboard.d",
        "key.keyboard.space",
        "key.keyboard.left.shift"
    };


    private static final int GRACE_POLLS = 30;


    private static final Set<String> heldAtEngage = new HashSet<>();

    private static boolean wasActive;

    private static int gracePolls;


    private static volatile boolean driving;

    private CameraPlayback() {
    }


    public static void arm() {
        gracePolls = GRACE_POLLS;
        wasActive = false;
        driving = true;
        heldAtEngage.clear();
    }


    public static void tick() {
        if (gracePolls > 0) {
            gracePolls--;
            return;
        }

        if (!active()) {


            wasActive = false;
            driving = false;
            heldAtEngage.clear();
            return;
        }

        if (!wasActive) {


            wasActive = true;
            driving = true;
            heldAtEngage.clear();
            for (String name : MOVEMENT_KEYS) {
                if (Compat.keyDown(Compat.key(name))) {
                    heldAtEngage.add(name);
                }
            }
            return;
        }

        for (String name : MOVEMENT_KEYS) {
            if (Compat.keyDown(Compat.key(name)) && !heldAtEngage.contains(name)) {
                if (release()) {
                    wasActive = false;
                    driving = false;
                    FlashbackActors.LOGGER.info(
                        "Director: auto-camera released; press Generate again to take it back");
                }
                return;
            }
        }
    }


    public static boolean driving() {
        return driving;
    }


    public static boolean active() {
        EditorState state = EditorStateManager.getCurrent();
        if (state == null) {
            return false;
        }
        long stamp = state.acquireRead();
        try {
            EditorScene scene = state.getCurrentScene(stamp);
            return scene != null && hasEnabledDirectorTrack(scene);
        } finally {
            state.release(stamp);
        }
    }


    public static boolean release() {
        return setEnabled(false);
    }


    public static boolean reengage() {
        boolean changed = setEnabled(true);
        if (changed) {
            arm();
        }
        return changed;
    }

    private static boolean setEnabled(boolean enabled) {
        EditorState state = EditorStateManager.getCurrent();
        if (state == null) {
            return false;
        }
        long stamp = state.acquireWrite();
        try {
            EditorScene scene = state.getCurrentScene(stamp);
            if (scene == null) {
                return false;
            }
            boolean changed = false;
            for (KeyframeTrack track : scene.keyframeTracks) {
                if (isDirectorTrack(track) && track.enabled != enabled) {
                    track.enabled = enabled;
                    changed = true;
                }
            }
            if (changed) {
                state.markDirty();
            }
            return changed;
        } finally {
            state.release(stamp);
        }
    }

    private static boolean hasEnabledDirectorTrack(EditorScene scene) {
        for (KeyframeTrack track : scene.keyframeTracks) {
            if (isDirectorTrack(track) && track.enabled) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDirectorTrack(KeyframeTrack track) {
        return DirectorGenerator.isDirectorTrackName(track.customName);
    }
}

