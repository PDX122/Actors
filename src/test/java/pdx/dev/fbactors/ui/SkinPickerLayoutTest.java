package pdx.dev.fbactors.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SkinPickerLayoutTest {
    @Test void wideWindowsFitTheListAndPreviewBesideEachOther() {
        for (float width:new float[]{540,632,900,1600}) {
            var layout=SkinPickerLayout.fit(width,380,8);
            assertTrue(layout.beside());
            assertEquals(width,layout.listWidth()+layout.previewWidth()+8,.01);
            assertTrue(layout.listWidth()>=190 && layout.listWidth()<=240);
            assertEquals(380,layout.previewHeight());
        }
    }
    @Test void narrowDocksStackWithoutLosingThePreview() {
        for (float width:new float[]{240,400,539}) {
            var layout=SkinPickerLayout.fit(width,430,8);
            assertFalse(layout.beside()); assertEquals(width,layout.previewWidth());
            assertEquals(430,layout.listHeight()+layout.previewHeight()+8,.01);
            assertTrue(layout.previewHeight()>layout.listHeight());
        }
    }
    @Test void collapsedPanelsNeverProduceNegativeChildSizes() {
        var layout=SkinPickerLayout.fit(0,-20,8);
        assertTrue(layout.listWidth()>0 && layout.listHeight()>0 && layout.previewWidth()>0 && layout.previewHeight()>0);
    }
}
