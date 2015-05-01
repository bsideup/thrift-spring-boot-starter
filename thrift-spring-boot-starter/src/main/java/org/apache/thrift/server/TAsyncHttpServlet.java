package org.apache.thrift.server;

import lombok.RequiredArgsConstructor;
import org.apache.thrift.TBaseAsyncProcessor;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.TIOStreamTransport;
import org.apache.thrift.transport.TTransport;

import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@RequiredArgsConstructor
public class TAsyncHttpServlet extends HttpServlet {

    //TODO remove when there will be "AsyncFrameBuffer" interface.
    public static final AbstractNonblockingServer ABSTRACT_NONBLOCKING_SERVER_STUB = new TNonblockingServer(new TNonblockingServer.Args(null));

    final TBaseAsyncProcessor<?> asyncProcessor;

    final TProtocolFactory protocolFactory;
    
    @Override
    protected void doPost(HttpServletRequest request, final HttpServletResponse res) throws ServletException, IOException {
        try {
            final AsyncContext context = request.startAsync();

                    ServletInputStream inputStream = request.getInputStream();
                    TTransport transport = new TIOStreamTransport(inputStream, context.getResponse().getOutputStream());

                    final TProtocol protocol = protocolFactory.getProtocol(transport);

                    final AbstractNonblockingServer.AsyncFrameBuffer asyncFrameBuffer = createAsyncFrameBuffer(protocol, context);
                    asyncProcessor.process(asyncFrameBuffer);
        } catch (Exception e) {
            throw new ServletException(e);
        }
    }
    
    protected AbstractNonblockingServer.AsyncFrameBuffer createAsyncFrameBuffer(final TProtocol protocol, final AsyncContext context) {
        // Yes, I know, looks terrible. But it works. Will remove this code when there will be "AsyncFrameBuffer" interface.
        return ABSTRACT_NONBLOCKING_SERVER_STUB.new AsyncFrameBuffer(null, null, null) {
            @Override
            public TProtocol getInputProtocol() {
                return protocol;
            }

            @Override
            public TProtocol getOutputProtocol() {
                return protocol;
            }

            @Override
            public void responseReady() {
                ServletResponse response = context.getResponse();
                response.setContentType("application/x-thrift");

                context.complete();
            }

            @Override
            public void invoke() {
            }

            @Override
            public boolean read() {
                return false;
            }

            @Override
            public boolean write() {
                return false;
            }

            @Override
            public void changeSelectInterests() {
            }

            @Override
            public void close() {
                protocol.getTransport().close();
            }

            @Override
            public boolean isFrameFullyRead() {
                return false;
            }

            @Override
            protected void requestSelectInterestChange() {
            }
        };
    }
}
