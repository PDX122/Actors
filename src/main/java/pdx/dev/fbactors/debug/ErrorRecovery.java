package pdx.dev.fbactors.debug;

import pdx.dev.fbactors.FlashbackActors;

public final class ErrorRecovery {
    public static final FailureRegistry FAILURES=new FailureRegistry((feature,error) ->
        FlashbackActors.LOGGER.error("Stopped {} after a failure; open Actors > Error report for details",feature,error));
    private ErrorRecovery() {}
    public static boolean run(String feature,Runnable action) { return FAILURES.run(feature,action); }
    public static void report(String feature,Throwable error) { FAILURES.report(feature,error); }
    public static void random() {
        FAILURES.retry("Random test");
        run("Random test",() -> { throw new IllegalStateException("Intentional Random test failure. The error handler caught this successfully."); });
    }
}
