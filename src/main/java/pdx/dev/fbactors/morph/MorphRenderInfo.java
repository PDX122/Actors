package pdx.dev.fbactors.morph;

public record MorphRenderInfo(String morphId, float scale, float alpha, String pose) {
    public MorphRenderInfo(String morphId, float scale, float alpha) {
        this(morphId, scale, alpha, "auto");
    }

    public boolean isPlayer() {
        return "player".equals(this.morphId);
    }
}
