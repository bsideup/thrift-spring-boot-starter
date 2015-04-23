package ru.trylogic.spring.boot.thrift.examples.simple;

import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TCompactProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.junit.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import ru.trylogic.spring.boot.thrift.examples.simple.configurations.protocolFactory.ApplicationWithProtocolFactory;
import ru.trylogic.spring.boot.thrift.examples.simple.configurations.withouthandlers.ApplicationWithoutHandlers;

import java.util.Map;

import static org.junit.Assert.*;

public class AutoConfigurationTests {

    @Test
    public void testProtocolFactory() throws Exception {
        ConfigurableApplicationContext applicationContext = new SpringApplicationBuilder(ApplicationWithoutHandlers.class).properties("server.port:0").run();

        assertNotNull(applicationContext);

        Map<String, TProtocolFactory> protocolFactoryBeans = applicationContext.getBeansOfType(TProtocolFactory.class);
        assertEquals(1, protocolFactoryBeans.size());

        assertTrue(protocolFactoryBeans.values().iterator().next() instanceof TBinaryProtocol.Factory);

        applicationContext.close();
    }

    @Test
    public void testProtocolFactoryOverride() throws Exception {
        ConfigurableApplicationContext applicationContext = new SpringApplicationBuilder(ApplicationWithProtocolFactory.class).properties("server.port:0").run();

        assertNotNull(applicationContext);

        Map<String, TProtocolFactory> protocolFactoryBeans = applicationContext.getBeansOfType(TProtocolFactory.class);
        assertEquals(1, protocolFactoryBeans.size());

        assertTrue(protocolFactoryBeans.values().iterator().next() instanceof TCompactProtocol.Factory);

        applicationContext.close();
    }
}
