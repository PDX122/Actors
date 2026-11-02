package pdx.dev.fbactors.ui;

public record MorphPickerLayout(boolean beside, float listWidth, float listHeight,
                                float inspectorWidth, float inspectorHeight) {
    public record WindowSize(float width, float height) {}

    public static WindowSize windowSize(float viewportWidth, float viewportHeight) {
        return new WindowSize(Math.max(1, Math.min(560, viewportWidth * .55f)),
            Math.max(1, Math.min(460, viewportHeight * .55f)));
    }

    public static MorphPickerLayout fit(float width, float height, float row, float gapX, float gapY) {
        width = Math.max(1, width);
        float body = Math.max(1, height - gapY);
        boolean beside = width >= Math.max(400, row * 13);
        if (beside) {
            float inspector = Math.min(width * .58f, row * 17);
            return new MorphPickerLayout(true, Math.max(1, width - inspector - gapX), body, inspector, body);
        }
        float half = Math.max(1, (body - gapY) / 2);
        return new MorphPickerLayout(false, width, half, width, half);
    }
}
