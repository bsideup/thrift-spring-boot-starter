package ru.trylogic.spring.boot.thrift.examples.async;

import example.*;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.THttpClient;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.IntegrationTest;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;

import java.util.*;
import java.util.concurrent.*;

import static org.junit.Assert.*;

@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = Application.class)
@WebAppConfiguration
@IntegrationTest("server.port:0")
@Slf4j
public class TMatchingServiceTests {

    @Value("${local.server.port}")
    int port;

    @Autowired
    TProtocolFactory protocolFactory;
    
    @Test
    public void testSimpleCall() throws Exception {
        TUser firstUser = new TUser("1" + UUID.randomUUID().toString(), "John Smith");
        TUser secondUser = new TUser("2" + UUID.randomUUID().toString(), "Ivan Fedorov");

        List<Future<TUser>> futures = new ArrayList<>();

        ExecutorService executorService = Executors.newFixedThreadPool(10);
        
        for(final TUser user : Arrays.asList(firstUser, secondUser)) {
            Future<TUser> future = executorService.submit(new Callable<TUser>() {
                @Override
                public TUser call() throws Exception {
                    return getClient().match(user);
                }
            });
            
            futures.add(future);
        }

        assertEquals(2, futures.size());

        assertEquals(secondUser, futures.get(0).get());
        assertEquals(firstUser, futures.get(1).get());
    }
    
    @Test
    public void testStress() throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(100);

        final ConcurrentMap<TUser, TUser> matches = new ConcurrentHashMap<>();
        
        final CountDownLatch latch = new CountDownLatch(1);

        for(int i = 0; i < 50000; i++) {
            executorService.submit(new Runnable() {
                @Override
                public void run() {
                    final TMatchingService.Client client = getClient();
                    try {
                        final TUser user = new TUser(UUID.randomUUID().toString(), UUID.randomUUID().toString());
                        TUser match = client.match(user);
                        matches.putIfAbsent(user, match);
                    } catch (Exception e) {
                        e.printStackTrace();
                    } finally {
                        client.getOutputProtocol().getTransport().close();
                    }
                }
            });
        }

        log.info("Preparations completed. Count down");
        long startTime = System.currentTimeMillis();
        latch.countDown();

        executorService.shutdown();
        executorService.awaitTermination(1, TimeUnit.MINUTES);
        
        log.info("Total time: " + (System.currentTimeMillis() - startTime));
        log.info("Total matches: " + matches.size());

        for (Map.Entry<TUser, TUser> entry : matches.entrySet()) {
            TUser left = entry.getKey();
            TUser right = entry.getValue();
            
            assertNotEquals(left, right);
            
            assertEquals(left, matches.get(right));
        }

    }
    
    @Test(expected = TNotFoundException.class, timeout = 20000L)
    public void testNotFound() throws Exception {
        TUser user = new TUser("1" + UUID.randomUUID().toString(), "John Smith");
        getClient().match(user);
    }
    
    @SneakyThrows
    protected TMatchingService.Client getClient() {

        String url = "http://localhost:" + port + "/" + TMatchingService.class.getSimpleName();
        
        THttpClient transport = new THttpClient(url);
        
        TProtocol protocol = protocolFactory.getProtocol(transport);
        return new TMatchingService.Client(protocol);
    }
}