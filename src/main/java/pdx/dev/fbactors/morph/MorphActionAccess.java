package pdx.dev.fbactors.morph;

public interface MorphActionAccess {
    void fbactors$action(String action);
    String fbactors$action();
    static boolean seated(Object entity) {
        return entity instanceof MorphActionAccess state && "sitting".equals(state.fbactors$action());
    }
}
