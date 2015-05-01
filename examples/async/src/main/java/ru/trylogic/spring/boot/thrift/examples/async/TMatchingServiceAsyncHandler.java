package ru.trylogic.spring.boot.thrift.examples.async;

import example.TMatchingService;
import example.TNotFoundException;
import example.TUser;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.thrift.TException;
import org.apache.thrift.async.AsyncMethodCallback;
import ru.trylogic.spring.boot.thrift.annotation.ThriftHandler;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

@ThriftHandler
@Slf4j
public class TMatchingServiceAsyncHandler implements TMatchingService.AsyncIface {

    volatile BlockingQueue<Request> requestCache = new ArrayBlockingQueue<>(100000, false);

    final Object lock = new Object();
    
    ExecutorService executorService = Executors.newFixedThreadPool(100);
    
    Thread matcherThread = new Thread(new Runnable() {
        @Override
        public void run() {
            while(true) {
                try {
                    synchronized (lock) {
                        lock.wait(1000);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    break;
                }


                List<Request> requests = new ArrayList<>();
                requestCache.drainTo(requests);

                Iterator<Request> iterator = requests.iterator();

                while(true) {
                    if(!iterator.hasNext()) {
                        //log.info("No more requests");
                        break;
                    }

                    final Request first = iterator.next();

                    if(!iterator.hasNext()) {
                        if(System.currentTimeMillis() - first.getTime() >= 3000) {
                            executorService.submit(new Runnable() {
                                @Override
                                public void run() {
                                    first.getCallback().onError(new TNotFoundException());
                                }
                            });

                            log.warn("Match not found for user: {}", first.getUser());
                        } else {
                            requestCache.add(first);
                        }
                        break;
                    }

                    final Request second = iterator.next();

                    executorService.submit(new Runnable() {
                        @Override
                        public void run() {
                            first.getCallback().onComplete(second.getUser());
                            second.getCallback().onComplete(first.getUser());
                        }
                    });

                    //log.info("Matched: {} and {}", first.getUser(), second.getUser());
                }
                
            }
        }
    }, "Matcher-thread");
    
    @PostConstruct
    public void init() {
        matcherThread.start();
    }
    
    @Override
    public void match(final TUser me, AsyncMethodCallback resultHandler) throws TException {
        Request request = new Request(me, System.currentTimeMillis(), resultHandler);
        requestCache.offer(request);

        synchronized (lock) {
            lock.notify();
        }
    }
    
    @Data
    static class Request {
        final TUser user;
        
        final long time;
        
        final AsyncMethodCallback callback;
    }
}
