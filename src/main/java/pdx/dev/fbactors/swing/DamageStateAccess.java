package pdx.dev.fbactors.swing;


public interface DamageStateAccess {

    int fbactors$attackStrengthTicker();

    void fbactors$setAttackStrengthTicker(int ticker);

    float fbactors$lastHurt();

    void fbactors$setLastHurt(float amount);
}
