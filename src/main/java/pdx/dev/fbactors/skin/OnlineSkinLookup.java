package pdx.dev.fbactors.skin;

import com.mojang.authlib.GameProfile;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

public final class OnlineSkinLookup {
    private OnlineSkinLookup() {}
    public static CompletableFuture<GameProfile> find(SkinId source) {
        var name = source.isUsername() ? SkinNames.findAsync(source.value())
            : CompletableFuture.completedFuture(source.profileId());
        var texture = new AtomicReference<CompletableFuture<GameProfile>>();
        var cancelled = new java.util.concurrent.atomic.AtomicBoolean();
        var result = name.thenCompose(id -> {
            var next = id == null ? CompletableFuture.<GameProfile>completedFuture(null) : SkinTextures.findAsync(id);
            texture.set(next);
            if (cancelled.get()) next.cancel(true);
            return next;
        });
        result.orTimeout(30, java.util.concurrent.TimeUnit.SECONDS).whenComplete((value, failure) -> {
            if (failure != null) {
                cancelled.set(true);
                name.cancel(true);
                var next = texture.get(); if (next != null) next.cancel(true);
            }
        });
        return result;
    }
}
