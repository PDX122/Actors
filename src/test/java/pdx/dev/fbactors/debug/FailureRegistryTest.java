package pdx.dev.fbactors.debug;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.atomic.AtomicInteger;

class FailureRegistryTest {
    @Test void failureIsCaughtOnceAndOnlyTheAffectedFeatureStops() {
        var logs=new AtomicInteger(); var attempts=new AtomicInteger();
        var failures=new FailureRegistry((name,error) -> logs.incrementAndGet());
        Runnable broken=() -> { attempts.incrementAndGet(); throw new IllegalStateException("Broken preview",new IllegalArgumentException("Cause")); };
        for(int frame=0;frame<100;frame++) assertFalse(failures.run("Preview",broken));
        assertEquals(1,attempts.get()); assertEquals(1,logs.get());
        var healthy=new AtomicInteger();
        assertTrue(failures.run("Settings",healthy::incrementAndGet));
        assertEquals(1,healthy.get());
        String report=failures.reportText();
        assertTrue(report.contains("Broken preview")); assertTrue(report.contains("Caused by:"));
        assertTrue(report.contains("FailureRegistryTest")); assertTrue(report.contains(Thread.currentThread().getName()));
    }
    @Test void dismissingAReportDoesNotImplicitlyRetryAndExplicitRetryRetainsTheLog() {
        var failures=new FailureRegistry((name,error) -> {});
        failures.run("Preview",() -> { throw new AssertionError("Test assertion"); });
        failures.history();
        assertTrue(failures.stopped("Preview"));
        failures.retry("Preview");
        assertTrue(failures.run("Preview",() -> {}));
        assertEquals(1,failures.history().size());
        assertTrue(failures.reportText().contains("Test assertion"));
    }
    @Test void linkageFailuresAreRecoverableButJvmFailuresPropagate() {
        var failures=new FailureRegistry((name,error) -> {});
        assertFalse(failures.run("Compatibility",() -> { throw new NoSuchMethodError("Changed API"); }));
        assertThrows(OutOfMemoryError.class,() -> failures.run("Memory",() -> { throw new OutOfMemoryError("Test only"); }));
        assertThrows(StackOverflowError.class,() -> failures.run("Stack",() -> { throw new StackOverflowError("Test only"); }));
        assertFalse(failures.stopped("Memory"));
    }
    @Test void historyIsBoundedButOldFailuresRemainStopped() {
        var failures=new FailureRegistry((name,error) -> {});
        for(int i=0;i<50;i++) failures.report("Feature "+i,new IllegalStateException("Long message "+"x".repeat(30_000)));
        assertEquals(12,failures.history().size());
        assertTrue(failures.stopped("Feature 0"));
        assertTrue(failures.history().getFirst().stack().length()<25_000);
        assertTrue(failures.history().getFirst().summary().length()<1100);
    }
    @Test void failureOfLoggingDoesNotCrashRecovery() {
        var failures=new FailureRegistry((name,error) -> { throw new IllegalStateException("Logger failed"); });
        assertFalse(failures.run("Preview",() -> { throw new IllegalArgumentException("Original"); }));
        assertTrue(failures.history().getFirst().stack().contains("Original"));
    }
    @Test void concurrentDuplicateReportsOnlyCreateOneEntry() throws Exception {
        var logs=new AtomicInteger(); var failures=new FailureRegistry((name,error) -> logs.incrementAndGet());
        var threads=new java.util.ArrayList<Thread>();
        for(int i=0;i<8;i++) threads.add(new Thread(() -> failures.report("Playback",new IllegalStateException("Concurrent"))));
        threads.forEach(Thread::start);
        for(var thread:threads) thread.join();
        assertEquals(1,failures.history().size()); assertEquals(1,logs.get());
    }
}
