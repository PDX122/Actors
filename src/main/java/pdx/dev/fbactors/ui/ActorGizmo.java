package pdx.dev.fbactors.ui;

import com.moulberry.flashback.editor.ui.ReplayUI;
import imgui.moulberry90.ImDrawList;
import imgui.moulberry90.ImGui;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import pdx.dev.fbactors.FlashbackActors;
import pdx.dev.fbactors.actor.Actor;
import pdx.dev.fbactors.compat.Compat;
import pdx.dev.fbactors.take.ActorFrame;
import pdx.dev.fbactors.take.ActorTake;
import pdx.dev.fbactors.take.TransformBake;

import java.util.UUID;


public final class ActorGizmo {

    private static final int HANDLE_X = 0;
    private static final int HANDLE_Y = 1;
    private static final int HANDLE_Z = 2;
    private static final int HANDLE_YAW = 3;

    private static final int COLOR_X = 0xFF0000FF;
    private static final int COLOR_Y = 0xFF00FF00;
    private static final int COLOR_Z = 0xFFFF0000;
    private static final int COLOR_RING = 0xFFFFFF00;
    private static final int COLOR_ACTIVE = 0xFFFFFFFF;
    private static final int COLOR_TEXT = 0xFFFFFFFF;

    private static boolean visible = true;

    private static int dragHandle = -1;
    private static int hoverHandle = -1;
    private static double dragT0;
    private static double dragAngle0;
    private static double grabAnchorX;
    private static double grabAnchorY;
    private static double grabAnchorZ;

    private static double pendingDx;
    private static double pendingDy;
    private static double pendingDz;
    private static double pendingYaw;

    private static UUID previewTake;
    private static double previewDx;
    private static double previewDy;
    private static double previewDz;
    private static double previewYaw;
    private static double previewPivotX;
    private static double previewPivotZ;

    private ActorGizmo() {
    }


    public static void toggle() {
        visible = !visible;
        if (!visible) {
            cancel();
        }
    }


    public static boolean isVisible() {
        return visible;
    }


    public static void cancel() {
        dragHandle = -1;
        hoverHandle = -1;
        pendingDx = 0.0;
        pendingDy = 0.0;
        pendingDz = 0.0;
        pendingYaw = 0.0;
        clearPreview();
    }


    public static double[] previewFor(UUID takeId) {
        if (previewTake == null || takeId == null || !previewTake.equals(takeId)) {
            return null;
        }
        if (previewDx == 0.0 && previewDy == 0.0 && previewDz == 0.0 && previewYaw == 0.0) {
            return null;
        }
        return new double[] {previewDx, previewDy, previewDz, previewYaw, previewPivotX, previewPivotZ};
    }


    public static double[] adjustPreview(UUID takeId, double x, double y, double z,
                                         float yaw, float pitch) {
        double[] preview = previewFor(takeId);
        if (preview == null) {
            return new double[] {x, y, z, yaw, pitch};
        }
        double px = x + preview[0];
        double py = y + preview[1];
        double pz = z + preview[2];
        float outYaw = yaw;
        if (preview[3] != 0.0) {
            double radians = Math.toRadians(preview[3]);
            double cos = Math.cos(radians);
            double sin = Math.sin(radians);
            double ox = x - preview[4];
            double oz = z - preview[5];
            px = preview[4] + ox * cos - oz * sin + preview[0];
            pz = preview[5] + ox * sin + oz * cos + preview[2];
            outYaw = TransformBake.wrapYaw(yaw + preview[3]);
        }
        return new double[] {px, py, pz, outYaw, pitch};
    }


    public static void snapPreview(net.minecraft.world.entity.Entity entity, UUID takeId,
                                     double x, double y, double z, float yaw, float pitch) {
        double[] adjusted = adjustPreview(takeId, x, y, z, yaw, pitch);
        Compat.snapTo(entity, adjusted[0], adjusted[1], adjusted[2],
            (float) adjusted[3], (float) adjusted[4]);
    }


    public static void render() {
        try {
            renderInner();
        } catch (RuntimeException | LinkageError e) {
            cancel();
        }
    }


    private static void renderInner() {
        if (!visible) {
            clearPreview();
            return;
        }
        int tick = FlashbackActors.replayTick();
        if (tick < 0) {
            cancel();
            return;
        }
        syncViewportSelection();
        Actor actor = ActorTrack.selected();
        if (actor == null || actor.take == null || actor.take.frames.isEmpty()) {
            clearPreview();
            return;
        }
        ActorFrame frame = actor.take.frameAt(tick);
        if (frame == null) {
            clearPreview();
            return;
        }
        double anchorX = frame.x();
        double anchorY = frame.y();
        double anchorZ = frame.z();

        Camera camera = camera();
        if (camera == null) {
            clearPreview();
            return;
        }
        float mouseX = ImGui.getMousePosX();
        float mouseY = ImGui.getMousePosY();
        if (!insideViewport(mouseX, mouseY)) {
            if (dragHandle < 0) {
                hoverHandle = -1;
            }
        }

        double dist = Math.sqrt(
            (anchorX - camera.x) * (anchorX - camera.x)
                + (anchorY - camera.y) * (anchorY - camera.y)
                + (anchorZ - camera.z) * (anchorZ - camera.z));
        double handleLen = Math.max(1.0, Math.min(3.0, dist * 0.15));
        double ringR = handleLen * 0.8;

        if (dragHandle < 0) {
            if (insideViewport(mouseX, mouseY)) {
                hoverHandle = hitTest(camera, anchorX, anchorY, anchorZ,
                    handleLen, ringR, mouseX, mouseY);
            }
            if (hoverHandle >= 0) {
                ImGui.setMouseCursor(7);
            }
            if (hoverHandle >= 0 && ImGui.isMouseClicked(0) && !ReplayUI.isMovingCamera()) {
                dragHandle = hoverHandle;
                grabAnchorX = anchorX;
                grabAnchorY = anchorY;
                grabAnchorZ = anchorZ;
                pendingDx = 0.0;
                pendingDy = 0.0;
                pendingDz = 0.0;
                pendingYaw = 0.0;
                double[] ray = mouseRay();
                if (ray == null) {
                    dragHandle = -1;
                } else if (dragHandle == HANDLE_YAW) {
                    Double angle = planeAngle(camera, ray, anchorY, anchorX, anchorZ);
                    if (angle == null) {
                        dragHandle = -1;
                    } else {
                        dragAngle0 = angle;
                    }
                } else {
                    dragT0 = axisParam(camera, ray, anchorX, anchorY, anchorZ, dragHandle);
                }
            }
        } else {
            if (ImGui.isMouseClicked(1)) {
                cancel();
                return;
            }
            if (!ImGui.isMouseDown(0)) {
                finishDrag(actor, tick);
                return;
            }
            double[] ray = mouseRay();
            if (ray != null) {
                if (dragHandle == HANDLE_YAW) {
                    Double angle = planeAngle(camera, ray, grabAnchorY, grabAnchorX, grabAnchorZ);
                    if (angle != null) {
                        double delta = angle - dragAngle0;
                        while (delta > 180.0) {
                            delta -= 360.0;
                        }
                        while (delta < -180.0) {
                            delta += 360.0;
                        }
                        pendingYaw = -delta;
                    }
                } else {
                    double t = axisParam(camera, ray, grabAnchorX, grabAnchorY, grabAnchorZ, dragHandle);
                    double delta = t - dragT0;
                    if (dragHandle == HANDLE_X) {
                        pendingDx = delta;
                    } else if (dragHandle == HANDLE_Y) {
                        pendingDy = delta;
                    } else {
                        pendingDz = delta;
                    }
                }
            }
        }

        setPreview(actor.take.id, pendingDx, pendingDy, pendingDz, pendingYaw,
            grabAnchorX, grabAnchorZ);

        drawHandles(camera, anchorX, anchorY, anchorZ, handleLen, ringR);
    }


    private static void finishDrag(Actor actor, int tick) {
        int handle = dragHandle;
        double dx = pendingDx;
        double dy = pendingDy;
        double dz = pendingDz;
        double yaw = pendingYaw;
        dragHandle = -1;
        hoverHandle = -1;
        pendingDx = 0.0;
        pendingDy = 0.0;
        pendingDz = 0.0;
        pendingYaw = 0.0;
        clearPreview();
        if (handle < 0) {
            return;
        }
        if (dx == 0.0 && dy == 0.0 && dz == 0.0 && yaw == 0.0) {
            return;
        }
        UUID replayId = FlashbackActors.loadedReplayIdOrNull();
        TransformBake.bakeWithUndo(actor, dx, dy, dz, yaw, grabAnchorX, grabAnchorZ, replayId);
        try {
            if (actor.clone() != null && actor.take != null) {
                int takeTick = tick - actor.take.offsetTicks;
                ActorFrame frame = actor.take.frameAt(tick);
                if (frame != null) {
                    Compat.snapTo(actor.clone(), frame.x(), frame.y(), frame.z(),
                        frame.yRot(), frame.xRot());
                    actor.markApplied(takeTick);
                }
            }
        } catch (RuntimeException | LinkageError e) {
            FlashbackActors.LOGGER.warn("Gizmo re-snap failed", e);
        }
    }


    private static void setPreview(UUID takeId, double dx, double dy, double dz,
                                    double yaw, double pivotX, double pivotZ) {
        if (dx == 0.0 && dy == 0.0 && dz == 0.0 && yaw == 0.0) {
            clearPreview();
            return;
        }
        previewTake = takeId;
        previewDx = dx;
        previewDy = dy;
        previewDz = dz;
        previewYaw = yaw;
        previewPivotX = pivotX;
        previewPivotZ = pivotZ;
    }


    private static void clearPreview() {
        previewTake = null;
        previewDx = 0.0;
        previewDy = 0.0;
        previewDz = 0.0;
        previewYaw = 0.0;
    }


    private static void syncViewportSelection() {
        UUID selected;
        try {
            selected = ReplayUI.getSelectedEntity();
        } catch (RuntimeException | LinkageError e) {
            return;
        }
        if (selected == null) {
            return;
        }
        Actor current = ActorTrack.selected();
        if (current != null && selected.equals(current.take.id)) {
            return;
        }
        Actor actor = FlashbackActors.ACTORS.byUuid(selected);
        if (actor != null) {
            ActorTrack.selectViewport(actor);
        }
    }


    private static void drawHandles(Camera camera, double anchorX, double anchorY,
                                    double anchorZ, double handleLen, double ringR) {
        double[] origin = project(camera, anchorX, anchorY, anchorZ);
        if (origin == null) {
            return;
        }
        ImDrawList draw = ImGui.getForegroundDrawList();
        if (draw == null) {
            return;
        }
        drawAxis(draw, camera, anchorX, anchorY, anchorZ, 1.0, 0.0, 0.0,
            handleLen, origin, hoverHandle == HANDLE_X || dragHandle == HANDLE_X, COLOR_X);
        drawAxis(draw, camera, anchorX, anchorY, anchorZ, 0.0, 1.0, 0.0,
            handleLen, origin, hoverHandle == HANDLE_Y || dragHandle == HANDLE_Y, COLOR_Y);
        drawAxis(draw, camera, anchorX, anchorY, anchorZ, 0.0, 0.0, 1.0,
            handleLen, origin, hoverHandle == HANDLE_Z || dragHandle == HANDLE_Z, COLOR_Z);

        boolean ringHot = hoverHandle == HANDLE_YAW || dragHandle == HANDLE_YAW;
        int segments = 40;
        double[] prev = null;
        for (int i = 0; i <= segments; i++) {
            double angle = (i / (double) segments) * Math.PI * 2.0;
            double wx = anchorX + Math.sin(angle) * ringR;
            double wz = anchorZ + Math.cos(angle) * ringR;
            double[] screen = project(camera, wx, anchorY, wz);
            if (screen == null) {
                prev = null;
                continue;
            }
            if (prev != null) {
                draw.addLine((float) prev[0], (float) prev[1],
                    (float) screen[0], (float) screen[1],
                    ringHot ? COLOR_ACTIVE : COLOR_RING, ringHot ? 3.0f : 2.0f);
            }
            prev = screen;
        }
        double[] label = project(camera, anchorX, anchorY + 0.4, anchorZ);
        if (label != null && (pendingDx != 0.0 || pendingDy != 0.0
            || pendingDz != 0.0 || pendingYaw != 0.0)) {
            String text = String.format("%.2f, %.2f, %.2f  %.1f",
                pendingDx, pendingDy, pendingDz, pendingYaw);
            draw.addText((float) label[0] + 12.0f, (float) label[1] - 12.0f, COLOR_TEXT, text);
        }
    }


    private static void drawAxis(ImDrawList draw, Camera camera,
                                 double anchorX, double anchorY, double anchorZ,
                                 double dirX, double dirY, double dirZ,
                                 double handleLen, double[] origin, boolean hot, int color) {
        double[] end = project(camera, anchorX + dirX * handleLen,
            anchorY + dirY * handleLen, anchorZ + dirZ * handleLen);
        if (end == null) {
            return;
        }
        draw.addLine((float) origin[0], (float) origin[1],
            (float) end[0], (float) end[1], hot ? COLOR_ACTIVE : color, hot ? 4.0f : 3.0f);
        float box = hot ? 6.0f : 5.0f;
        draw.addRectFilled((float) end[0] - box, (float) end[1] - box,
            (float) end[0] + box, (float) end[1] + box, hot ? COLOR_ACTIVE : color);
    }


    private static int hitTest(Camera camera, double anchorX, double anchorY, double anchorZ,
                               double handleLen, double ringR, float mouseX, float mouseY) {
        double[] origin = project(camera, anchorX, anchorY, anchorZ);
        if (origin == null) {
            return -1;
        }
        int best = -1;
        double bestDist = 12.0;
        double[][] ends = {
            project(camera, anchorX + handleLen, anchorY, anchorZ),
            project(camera, anchorX, anchorY + handleLen, anchorZ),
            project(camera, anchorX, anchorY, anchorZ + handleLen)};
        for (int i = 0; i < 3; i++) {
            if (ends[i] == null) {
                continue;
            }
            double dist = pointSegment(origin[0], origin[1], ends[i][0], ends[i][1], mouseX, mouseY);
            if (dist < bestDist) {
                bestDist = dist;
                best = i;
            }
        }
        int segments = 32;
        for (int i = 0; i < segments; i++) {
            double angle = (i / (double) segments) * Math.PI * 2.0;
            double[] screen = project(camera, anchorX + Math.sin(angle) * ringR,
                anchorY, anchorZ + Math.cos(angle) * ringR);
            if (screen == null) {
                continue;
            }
            double dx = screen[0] - mouseX;
            double dy = screen[1] - mouseY;
            double dist = Math.sqrt(dx * dx + dy * dy);
            if (dist < bestDist) {
                bestDist = dist;
                best = HANDLE_YAW;
            }
        }
        return best;
    }


    private static double pointSegment(double x1, double y1, double x2, double y2,
                                       double px, double py) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double lenSq = dx * dx + dy * dy;
        double t = lenSq > 0.0 ? ((px - x1) * dx + (py - y1) * dy) / lenSq : 0.0;
        t = Math.max(0.0, Math.min(1.0, t));
        double cx = x1 + dx * t;
        double cy = y1 + dy * t;
        double ex = px - cx;
        double ey = py - cy;
        return Math.sqrt(ex * ex + ey * ey);
    }


    private static double axisParam(Camera camera, double[] ray,
                                    double anchorX, double anchorY, double anchorZ, int axis) {
        double dx = axis == HANDLE_X ? 1.0 : 0.0;
        double dy = axis == HANDLE_Y ? 1.0 : 0.0;
        double dz = axis == HANDLE_Z ? 1.0 : 0.0;
        double rx = ray[3];
        double ry = ray[4];
        double rz = ray[5];
        double wx = anchorX - ray[0];
        double wy = anchorY - ray[1];
        double wz = anchorZ - ray[2];
        double a = rx * rx + ry * ry + rz * rz;
        double b = rx * dx + ry * dy + rz * dz;
        double c = 1.0;
        double d = rx * wx + ry * wy + rz * wz;
        double e = dx * wx + dy * wy + dz * wz;
        double denom = a * c - b * b;
        if (Math.abs(denom) < 1.0e-9) {
            return 0.0;
        }
        return (a * e - b * d) / denom;
    }


    private static Double planeAngle(Camera camera, double[] ray,
                                     double planeY, double pivotX, double pivotZ) {
        double ry = ray[4];
        if (Math.abs(ry) < 1.0e-9) {
            return null;
        }
        double t = (planeY - ray[1]) / ry;
        if (t < 0.0) {
            return null;
        }
        double px = ray[0] + ray[3] * t - pivotX;
        double pz = ray[2] + ray[5] * t - pivotZ;
        if (px * px + pz * pz < 1.0e-8) {
            return null;
        }
        return Math.toDegrees(Math.atan2(px, pz));
    }


    private static double[] mouseRay() {
        double[] origin = cameraPos();
        Vec3 direction;
        try {
            direction = ReplayUI.getMouseForwardsVector();
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
        if (origin == null || direction == null) {
            return null;
        }
        double len = direction.length();
        if (len < 1.0e-9) {
            return null;
        }
        return new double[] {origin[0], origin[1], origin[2],
            direction.x / len, direction.y / len, direction.z / len};
    }


    private static boolean insideViewport(float mouseX, float mouseY) {
        try {
            float vx = ImGui.getMainViewport().getPosX() + ReplayUI.frameX;
            float vy = ImGui.getMainViewport().getPosY() + ReplayUI.frameY;
            return mouseX >= vx && mouseX < vx + ReplayUI.frameWidth
                && mouseY >= vy && mouseY < vy + ReplayUI.frameHeight;
        } catch (RuntimeException | LinkageError e) {
            return false;
        }
    }


    private static double[] project(Camera camera, double wx, double wy, double wz) {
        try {
            Matrix4f projection = ReplayUI.lastProjectionMatrix;
            Quaternionf rotation = ReplayUI.lastViewQuaternion;
            if (projection == null || rotation == null) {
                return null;
            }
            Vector3f view = new Vector3f((float) (wx - camera.x),
                (float) (wy - camera.y), (float) (wz - camera.z));
            new Quaternionf(rotation).conjugate().transform(view);
            Vector4f clip = new Vector4f(view.x, view.y, view.z, 1.0f).mul(projection);
            if (clip.w <= 1.0e-6f) {
                return null;
            }
            double ndcX = clip.x / clip.w;
            double ndcY = -clip.y / clip.w;
            float displayW = ImGui.getIO().getDisplaySize().x;
            float displayH = ImGui.getIO().getDisplaySize().y;
            if (displayW <= 0.0f || displayH <= 0.0f) {
                return null;
            }
            float vpX = ImGui.getMainViewport().getPosX() + ReplayUI.frameX;
            float vpY = ImGui.getMainViewport().getPosY() + ReplayUI.frameY;
            double sx = vpX + (ndcX * 0.5 + 0.5) * ReplayUI.frameWidth;
            double sy = vpY + (ndcY * 0.5 + 0.5) * ReplayUI.frameHeight;
            return new double[] {sx, sy, clip.w};
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }


    private static Camera camera() {
        double[] pos = cameraPos();
        if (pos == null) {
            return null;
        }
        return new Camera(pos[0], pos[1], pos[2]);
    }


    private static double[] cameraPos() {
        try {
            return pdx.dev.fbactors.compat.Compat.cameraPos();
        } catch (RuntimeException | LinkageError e) {
            return null;
        }
    }


    private record Camera(double x, double y, double z) {
    }
}
