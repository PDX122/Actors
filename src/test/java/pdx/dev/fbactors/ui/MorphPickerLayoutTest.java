package pdx.dev.fbactors.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MorphPickerLayoutTest {
    @Test void defaultWindowStaysCompactAtDifferentResolutions() {
        for (float[] viewport : new float[][]{{1920,1080},{1280,720},{800,600},{3840,2160}}) {
            var size = MorphPickerLayout.windowSize(viewport[0], viewport[1]);
            assertTrue(size.width() <= 560 && size.height() <= 460);
            assertTrue(size.width() <= viewport[0] * .55f && size.height() <= viewport[1] * .55f);
        }
        assertTrue(MorphPickerLayout.fit(540, 300, 26, 6, 5).beside());
    }
    @Test void widePickerUsesTwoPanesWithinItsWorkspace() {
        var layout = MorphPickerLayout.fit(700, 450, 20, 8, 4);
        assertTrue(layout.beside());
        assertEquals(700, layout.listWidth() + layout.inspectorWidth() + 8);
        assertEquals(446, layout.listHeight());
    }
    @Test void narrowPickerStacksScrollablePanesWithoutNegativeClipRects() {
        for (float width : new float[]{0, 1, 100, 300}) {
            var layout = MorphPickerLayout.fit(width, 40, 20, 8, 4);
            assertFalse(layout.beside());
            assertTrue(layout.listWidth() > 0 && layout.listHeight() > 0);
            assertTrue(layout.inspectorWidth() > 0 && layout.inspectorHeight() > 0);
        }
    }
    @Test void largerUiScaleCollapsesTheSplitEarlier() {
        assertTrue(MorphPickerLayout.fit(700, 450, 20, 8, 4).beside());
        assertFalse(MorphPickerLayout.fit(700, 450, 60, 14, 7).beside());
    }
}
