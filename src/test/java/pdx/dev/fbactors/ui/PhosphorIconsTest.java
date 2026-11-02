package pdx.dev.fbactors.ui;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PhosphorIconsTest {
    @Test void everySkinActionUsesABundledPhosphorAsset() {
        for(String label:new String[]{"Players","Saved","Files","Find","Front","Back","Walk","Save skin","More...","Apply skin","Import PNG##file"}) {
            String icon=PhosphorIcons.forLabel(label);
            assertNotNull(icon,label);assertTrue(PhosphorIcons.contains(icon),label);
        }
    }
}
