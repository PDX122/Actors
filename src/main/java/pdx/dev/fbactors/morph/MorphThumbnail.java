package pdx.dev.fbactors.morph;

public record MorphThumbnail(long texture, AutoCloseable resource) implements AutoCloseable {
    /**
     * Shared no-op close for borrowed handles. Preview models are owned by
     * {@link MorphPreviewCache}: the renderer texture a thumbnail points at
     * dies with the cached model, so a thumbnail must never close it -
     * overwriting the displayed image would otherwise double-close (or leak)
     * the cache's model.
     */
    private static final AutoCloseable RETAINED = () -> {};

    /** A non-owning view of a cache-owned preview texture. Closing is a no-op. */
    public static MorphThumbnail view(long texture) { return new MorphThumbnail(texture, RETAINED); }

    @Override public void close() {
        if (resource == null) return;
        try { resource.close(); } catch (Exception ignored) {}
    }
}
