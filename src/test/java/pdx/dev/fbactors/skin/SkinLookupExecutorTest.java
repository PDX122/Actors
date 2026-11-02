package pdx.dev.fbactors.skin;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SkinLookupExecutorTest {
    @Test void workRunsOffTheCallerAndFailuresDoNotKillTheWorker() throws Exception {
        assertNotEquals(Thread.currentThread(),SkinLookupExecutor.submit(Thread::currentThread).get(2,TimeUnit.SECONDS));
        var failure=SkinLookupExecutor.submit(()->{throw new IllegalArgumentException("bad response");});
        assertThrows(java.util.concurrent.ExecutionException.class,()->failure.get(2,TimeUnit.SECONDS));
        assertEquals("ready",SkinLookupExecutor.submit(()->"ready").get(2,TimeUnit.SECONDS));
    }
    @Test void rapidSearchesHaveABoundedQueue() throws Exception {
        var release=new CountDownLatch(1);var jobs=new ArrayList<CompletableFuture<String>>();
        try {
            for(int i=0;i<24;i++)jobs.add(SkinLookupExecutor.submit(()->{
                try { release.await(5,TimeUnit.SECONDS); } catch(InterruptedException ignored){Thread.currentThread().interrupt();}
                return "done";
            }));
            assertTrue(jobs.stream().filter(CompletableFuture::isCompletedExceptionally).count()>=16);
        } finally { release.countDown(); }
        CompletableFuture.allOf(jobs.stream().map(job->job.handle((v,e)->null)).toArray(CompletableFuture[]::new)).get(5,TimeUnit.SECONDS);
    }
}
