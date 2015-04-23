package ru.trylogic.spring.boot.thrift.examples.simple;

import com.kurento.kms.thrift.api.KmsMediaServerService;
import org.apache.thrift.TException;
import ru.trylogic.spring.boot.thrift.annotation.ThriftHandler;

@ThriftHandler
public class KmsMediaServerServiceHandler implements KmsMediaServerService.Iface {
    @Override
    public String invokeJsonRpc(String request) throws TException {
        return request.toUpperCase();
    }
}
