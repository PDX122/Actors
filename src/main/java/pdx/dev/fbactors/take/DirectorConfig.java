package pdx.dev.fbactors.take;

import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;


public record DirectorConfig(
    List<ActorSelection> actors,
    DirectorStyle style,
    boolean handheld,
    float anticipationTicks,
    int holdTicks,
    @Nullable CameraMove move,
    @Nullable CameraHeight height,
    @Nullable CameraKeyframe.ShotType shotType,
    List<DirectorShot> shots
) {


    public static final float MAX_ANTICIPATION_TICKS = 20.0f;


    public static final int MIN_HOLD_TICKS = 10;


    public static final int MAX_HOLD_TICKS = 200;


    public enum DirectorStyle {
        SPIELBERG,
        NOLAN,
        TARANTINO,
        KUBRICK,
        SCORSESE,
        FINCHER,
        VILLENEUVE,
        CAMERON,
        WES_ANDERSON
    }


    public record ActorSelection(UUID actorId, boolean primary, float weight) {
        public ActorSelection {
            weight = Mth.clamp(Float.isFinite(weight) ? weight : 1.0f, 0.0f, 1.0f);
        }
    }

    public DirectorConfig {
        actors = actors == null ? List.of() : List.copyOf(actors);
        if (style == null) {
            style = DirectorStyle.SPIELBERG;
        }
        anticipationTicks = Mth.clamp(
            Float.isFinite(anticipationTicks) ? anticipationTicks : 0.0f,
            0.0f, MAX_ANTICIPATION_TICKS);
        holdTicks = Mth.clamp(holdTicks, MIN_HOLD_TICKS, MAX_HOLD_TICKS);
        shots = shots == null ? List.of() : List.copyOf(shots);
    }


    public static DirectorConfig empty() {
        return new DirectorConfig(List.of(), DirectorStyle.SPIELBERG, false, 0.0f,
            40, null, null, null, List.of());
    }


    public boolean valid() {
        return !this.actors.isEmpty();
    }


    @Nullable
    public ActorSelection primary() {
        for (ActorSelection selection : this.actors) {
            if (selection.primary()) {
                return selection;
            }
        }
        return null;
    }


    @Nullable
    public ActorSelection selectionFor(UUID actorId) {
        for (ActorSelection selection : this.actors) {
            if (selection.actorId().equals(actorId)) {
                return selection;
            }
        }
        return null;
    }
}

