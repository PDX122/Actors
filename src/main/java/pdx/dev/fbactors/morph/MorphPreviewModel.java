package pdx.dev.fbactors.morph;

import java.util.List;

public interface MorphPreviewModel extends AutoCloseable {
    List<String> stages();
    default void rotation(float degrees) {}
    default void pose(String pose) {}
    default void capture(java.util.function.Consumer<pdx.dev.fbactors.camera.CameraPreviewCapture.Mesh> output) {}
    default void cameraAction(String action) {}
    default net.minecraft.world.entity.LivingEntity cameraEntity() { return null; }
    default void cameraMount(net.minecraft.world.entity.Entity mount) {}
    MorphThumbnail render(MorphPreviewPlayback.Frame frame);
    @Override void close();
}
