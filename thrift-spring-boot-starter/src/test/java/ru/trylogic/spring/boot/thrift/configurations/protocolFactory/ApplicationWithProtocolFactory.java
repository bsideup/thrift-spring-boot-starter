package ru.trylogic.spring.boot.thrift.configurations.protocolFactory;

import org.apache.thrift.protocol.TCompactProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class ApplicationWithProtocolFactory {
    @Bean
    TProtocolFactory thriftProtocolFactory() {
        return new TCompactProtocol.Factory();
    }
}
