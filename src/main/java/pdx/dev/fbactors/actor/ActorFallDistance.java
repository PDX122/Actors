package pdx.dev.fbactors.actor;

import pdx.dev.fbactors.take.ActorFrame;


final class ActorFallDistance {

    private ActorFallDistance() {
    }


    static float infer(ActorFrame frame) {
        return frame != null && !frame.onGround() ? FALLING : 0.0f;
    }


    private static final float FALLING = 1.0f;
}
