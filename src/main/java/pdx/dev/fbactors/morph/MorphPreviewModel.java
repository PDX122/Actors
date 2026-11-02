package pdx.dev.fbactors.morph;

import java.util.List;

public interface MorphPreviewModel extends AutoCloseable {
    List<String> stages();
    default void rotation(float degrees) {}
    default void pose(String pose) {}
    MorphThumbnail render(MorphPreviewPlayback.Frame frame);
    @Override void close();
}
