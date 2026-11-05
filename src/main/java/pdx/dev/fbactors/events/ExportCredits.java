package pdx.dev.fbactors.events;

import java.util.List;

/** Export progress UI only; this never draws into the encoded image. */
public final class ExportCredits {
    private ExportCredits() {}
    public static boolean add(List<Object> lines,Object value) {
        boolean result=lines.add(value);
        if("Hold [ESC] to cancel".equals(value) && !lines.contains("Flashback Actors")) {
            lines.add("");lines.add("Flashback Actors");
        }
        return result;
    }
}
