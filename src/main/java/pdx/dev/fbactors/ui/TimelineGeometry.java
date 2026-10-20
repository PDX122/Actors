package pdx.dev.fbactors.ui;

public record TimelineGeometry(float x, float width, float clipTop, float clipBottom, int middleX,
                               int timelineOffset, int minTicks, float availableTicks,
                               float timelineWidth) {

    public float laneLeft() {
        return this.x + this.middleX + 1.0f;
    }

    public float laneRight() {
        return this.x + this.width;
    }

    public float panelRight() {
        return this.x + this.middleX;
    }

    private boolean valid() {
        return this.availableTicks > 0.0f && this.timelineWidth > 0.0f;
    }

    public float pixelsPerTick() {
        if (!valid()) {
            return 0.0f;
        }
        return this.timelineWidth / this.availableTicks;
    }

    public int deltaTicks(float pixels) {
        float scale = pixelsPerTick();
        if (scale <= 0.0f) {
            return 0;
        }
        return Math.round(pixels / scale);
    }

    public float tickX(int tick) {
        if (!valid()) {
            return this.x + this.timelineOffset;
        }
        return this.x + this.timelineOffset
            + (int) ((tick - this.minTicks) / this.availableTicks * this.timelineWidth);
    }


    public int tickAt(float x) {
        if (!valid()) {
            return this.minTicks;
        }
        return Math.round(this.minTicks
            + (x - this.x - this.timelineOffset) / this.timelineWidth * this.availableTicks);
    }
}

