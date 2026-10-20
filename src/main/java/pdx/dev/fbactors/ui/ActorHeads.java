package pdx.dev.fbactors.ui;

import imgui.moulberry90.ImDrawList;
import pdx.dev.fbactors.compat.Compat;

import java.util.UUID;

public final class ActorHeads {

    private static final float FACE_U0 = 8.0f / 64.0f;
    private static final float FACE_U1 = 16.0f / 64.0f;
    private static final float HAT_U0 = 40.0f / 64.0f;
    private static final float HAT_U1 = 48.0f / 64.0f;
    private static final float V0 = 8.0f / 64.0f;
    private static final float V1 = 16.0f / 64.0f;

    private ActorHeads() {
    }

    public static void draw(ImDrawList drawList, UUID id, float x, float y, float size, int tint) {
        long handle = 0L;
        try {
            handle = Compat.headTexture(id);
        } catch (Exception ignored) {
        }

        if (handle == 0L) {
            drawList.addRectFilled(x, y, x + size, y + size,
                ActorColours.withAlpha(ActorColours.of(id), tint >>> 24));
            return;
        }

        Compat.sampleNearest(handle);
        drawList.addImage(handle, x, y, x + size, y + size, FACE_U0, V0, FACE_U1, V1, tint);
        drawList.addImage(handle, x, y, x + size, y + size, HAT_U0, V0, HAT_U1, V1, tint);
    }
}

