package pdx.dev.fbactors.ui;

public record SkinPickerLayout(boolean beside, float listWidth, float listHeight, float previewWidth, float previewHeight) {
    public static SkinPickerLayout fit(float width, float height, float gap) {
        width = Math.max(1, width); height = Math.max(1, height); gap = Math.max(0, gap);
        if (width >= 540) {
            float list = Math.max(190, Math.min(240, width * .32f));
            return new SkinPickerLayout(true, list, height, Math.max(1, width-list-gap), height);
        }
        float list = Math.max(1, Math.min(170, height * .34f));
        return new SkinPickerLayout(false, width, list, width, Math.max(1, height-list-gap));
    }
}
