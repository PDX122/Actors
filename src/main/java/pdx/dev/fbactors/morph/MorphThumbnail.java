package pdx.dev.fbactors.morph;

public record MorphThumbnail(long texture, AutoCloseable resource) implements AutoCloseable {

    private static final AutoCloseable RETAINED = () -> {};

    public static MorphThumbnail view(long texture) { return new MorphThumbnail(texture, RETAINED); }

    @Override public void close() {
        if (resource == null) return;
        try { resource.close(); } catch (Exception ignored) {}
    }
}
