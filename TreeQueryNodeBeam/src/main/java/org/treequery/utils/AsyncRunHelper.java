package org.treequery.utils;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.joda.time.Duration;

import org.treequery.exception.TimeOutException;
import org.treequery.service.StatusTreeQueryCluster;

import java.util.concurrent.atomic.AtomicInteger;
@Slf4j
public class AsyncRunHelper {
    private final Object __object;
    private final AtomicInteger count;
    private final long WAIT_MS = Duration.standardHours(1).getMillis();
    private StatusTreeQueryCluster status;

    @Getter
    private volatile boolean isError;

    private AsyncRunHelper(Object object){
        __object = object;
        count = new AtomicInteger(0);
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
        synchronized (__object){

            if (__status.getStatus() != StatusTreeQueryCluster.QueryTypeEnum.SUCCESS){
                isError = true;
                status = __status;
            }else if (!isError) {
                status = __status;
            }
            __object.notifyAll();
            count.incrementAndGet();
        }
    }

    public StatusTreeQueryCluster waitFor() throws TimeOutException {
        return this.waitFor(WAIT_MS);
    }

    public StatusTreeQueryCluster waitFor(long milliseconds )throws TimeOutException{
        synchronized (__object){
            try {
                __object.wait(milliseconds);
            }catch(InterruptedException ie){
                log.error(ie.getMessage());
                throw new IllegalStateException(ie.getMessage());
            }
            if (count.get()==0){
                log.error("Time out");
                throw new TimeOutException(String.format("Timeout of run:%s",__object.toString()));
            }
            return this.status;
        }
    }

}
