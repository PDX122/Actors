package pdx.dev.fbactors.morph;


public record MorphRenderInfo(String morphId, float scale, float alpha) {


    public boolean isPlayer() {
        return "player".equals(this.morphId);
    }
}
