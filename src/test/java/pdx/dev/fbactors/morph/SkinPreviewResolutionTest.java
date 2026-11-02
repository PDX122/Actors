package pdx.dev.fbactors.morph;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SkinPreviewResolutionTest {
    @Test void skinRendersAtHigherResolutionWithoutChangingItsFraming() {
        assertEquals(512,MorphPreviewLayout.resolution("skin_preview"));
        int normal=MorphPreviewLayout.scale(.6f,1.8f);
        assertEquals(normal*4,MorphPreviewLayout.scale(.6f,1.8f,512));
        assertEquals(128,MorphPreviewLayout.resolution("zombie"));
        assertEquals(normal,MorphPreviewLayout.scale(.6f,1.8f,128));
    }
}
