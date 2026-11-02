package pdx.dev.fbactors.input;

public final class EscapeRunPolicy {
    private EscapeRunPolicy() {}
    public static boolean mayFinish(boolean recording, boolean screenOpen, int tick, int totalTicks) {
        return recording && !screenOpen && totalTicks > 0 && tick >= totalTicks;
    }
}
