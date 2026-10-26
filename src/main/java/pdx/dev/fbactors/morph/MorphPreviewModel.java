package pdx.dev.fbactors.morph;

import java.util.List;

public interface MorphPreviewModel extends AutoCloseable {
    List<String> stages();
    MorphThumbnail render(MorphPreviewPlayback.Frame frame);
    @Override void close();
}
