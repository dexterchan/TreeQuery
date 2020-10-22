package org.treequery.utils;

import com.google.common.util.concurrent.Uninterruptibles;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.Duration;

import org.treequery.exception.TimeOutException;
import org.treequery.service.StatusTreeQueryCluster;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
@Slf4j
public class AsyncRunHelper {
    private final Object __object;
    private final CountDownLatch countDownLatch;
    private final long WAIT_MS = Duration.standardMinutes(30).getMillis();
    private StatusTreeQueryCluster status;

    @Getter
    private volatile boolean isError;

    private AsyncRunHelper(Object object){
        __object = object;
        countDownLatch = new CountDownLatch(1);

        isError = false;
    }

    public static AsyncRunHelper create(){
        return new AsyncRunHelper(new Object());
    }
    /*
    public static AsyncRunHelper of (Object object){
        return new AsyncRunHelper(object);
    }*/

    public  void continueRun(StatusTreeQueryCluster __status){

            if (__status.getStatus() != StatusTreeQueryCluster.QueryTypeEnum.SUCCESS){
                isError = true;
                status = __status;
            }else if (!isError) {
                status = __status;
            }
            countDownLatch.countDown();
    }

    public StatusTreeQueryCluster waitFor() throws TimeOutException {
        return this.waitFor(WAIT_MS);
    }

    public StatusTreeQueryCluster waitFor(long milliseconds )throws TimeOutException{
        if (!Uninterruptibles.awaitUninterruptibly(countDownLatch, milliseconds, TimeUnit.MILLISECONDS)) {
            throw new TimeOutException(String.format("Timeout of run:%s",__object.toString()));
        }
        return this.status;
    }

}
