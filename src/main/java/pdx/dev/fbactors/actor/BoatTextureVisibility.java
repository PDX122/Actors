package pdx.dev.fbactors.actor;

import java.util.function.IntBinaryOperator;

public final class BoatTextureVisibility {
    private BoatTextureVisibility() {}

    public static boolean fullyTransparent(int width, int height, IntBinaryOperator alpha) {
        if (width <= 0 || height <= 0) return false;
        for (int y = 0; y < height; y++)
            for (int x = 0; x < width; x++)
                if (alpha.applyAsInt(x, y) != 0) return false;
        return true;
    }
}
