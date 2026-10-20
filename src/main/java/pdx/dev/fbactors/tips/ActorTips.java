package pdx.dev.fbactors.tips;

import net.minecraft.network.chat.Component;
import pdx.dev.fbactors.FlashbackActors;


public final class ActorTips {

    private ActorTips() {
    }

    private static final String[] KEYS = {
        "fbactors.tip1",
        "fbactors.tip2",
        "fbactors.tip3",
        "fbactors.tip4",
        "fbactors.tip5",
        "fbactors.tip6",
        "fbactors.tip7",
    };

    private static boolean installed = false;

    public static void install() {
        if (installed) {
            return;
        }
        installed = true;
        try {
            String[] current =
                com.moulberry.flashback.editor.ui.DailyTips.TIPS;
            if (current != null && current.length > 0
                && current[current.length - 1].equals(resolve(KEYS[KEYS.length - 1]))) {
                return;
            }
            int base = current == null ? 0 : current.length;
            String[] extended = new String[base + KEYS.length];
            if (current != null) {
                System.arraycopy(current, 0, extended, 0, base);
            }
            for (int i = 0; i < KEYS.length; i++) {
                extended[base + i] = resolve(KEYS[i]);
            }
            com.moulberry.flashback.editor.ui.DailyTips.TIPS = extended;
            FlashbackActors.LOGGER.info("Added {} actor tip(s) to the tip of the day", KEYS.length);
        } catch (Throwable t) {
            FlashbackActors.LOGGER.debug("Could not add actor tips", t);
        }
    }

    private static String resolve(String key) {
        return Component.translatable(key).getString();
    }
}

