package pdx.dev.fbactors.ui;

import com.moulberry.flashback.editor.ui.ReplayUI;

public final class ReplayUiInput {

    private ReplayUiInput() {
    }

    public static boolean wantsText() {
        try {
            return ReplayUI.getIO().getWantTextInput();
        } catch (Exception e) {
            return false;
        }
    }

    public static float wheel() {
        try {
            return ReplayUI.getIO().getMouseWheel();
        } catch (Exception e) {
            return 0.0f;
        }
    }

    public static boolean ctrlDown() {
        try {
            return ReplayUI.getIO().getKeyCtrl() || ReplayUI.getIO().getKeySuper();
        } catch (Exception e) {
            return false;
        }
    }
}

