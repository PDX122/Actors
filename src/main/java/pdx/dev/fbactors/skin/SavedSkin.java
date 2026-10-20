package pdx.dev.fbactors.skin;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;


public record SavedSkin(String name, SkinId source, String displayName, String note,
                        long savedAtMillis) {


    public static final int MAX_NAME_LENGTH = 32;


    private static final Pattern NAME = Pattern.compile("^[A-Za-z0-9_-]{1,32}$");


    public SavedSkin {
        if (name == null) {
            throw new IllegalArgumentException("Saved skin name was null");
        }
        if (!isValidName(name)) {
            throw new IllegalArgumentException(
                "Invalid skin name '" + name + "': must be 1-" + MAX_NAME_LENGTH
                    + " characters of letters, digits, underscore or hyphen");
        }
        if (source == null) {
            throw new IllegalArgumentException("Saved skin '" + name + "' has no source");
        }


        source = SkinId.parse(source.value());

        displayName = displayName == null || displayName.isBlank() ? name : displayName.trim();
        note = note == null ? "" : note.trim();
    }


    public static boolean isValidName(@Nullable String name) {
        return name != null && NAME.matcher(name).matches();
    }


    @Nullable
    public static Component nameProblem(@Nullable String name) {
        if (name == null || name.isEmpty()) {
            return Component.translatable("fbactors.skin.name_empty");
        }
        if (!isValidName(name)) {
            return Component.translatable("fbactors.skin.name_invalid",
                Component.literal(name).withStyle(style -> style.withColor(0xFFFF5555)));
        }
        return null;
    }


    public SavedSkin withName(String newName) {
        return new SavedSkin(newName, this.source, this.displayName, this.note,
            this.savedAtMillis);
    }


    public SavedSkin withSource(SkinId newSource) {
        return new SavedSkin(this.name, newSource, this.displayName, this.note,
            this.savedAtMillis);
    }


    public SavedSkin withDisplayName(@Nullable String newDisplayName) {
        return new SavedSkin(this.name, this.source, newDisplayName, this.note,
            this.savedAtMillis);
    }


    public SavedSkin withNote(@Nullable String newNote) {
        return new SavedSkin(this.name, this.source, this.displayName, newNote,
            this.savedAtMillis);
    }


    public boolean hasDrawableSource() {
        return this.source.kind() == SkinId.Kind.UUID;
    }


    @Override
    public String toString() {
        return this.name + " -> " + this.source.value();
    }
}
