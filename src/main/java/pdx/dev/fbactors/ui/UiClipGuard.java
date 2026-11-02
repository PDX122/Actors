package pdx.dev.fbactors.ui;

public final class UiClipGuard {
    private boolean visible;
    public boolean scissor(int width,int height) {
        visible=width>0 && height>0;
        return visible;
    }
    public boolean draw(int elements,int instances) { return visible && elements>0 && instances>0; }
}
