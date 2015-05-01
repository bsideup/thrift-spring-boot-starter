package ru.trylogic.spring.boot.thrift;

import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.apache.thrift.TBaseAsyncProcessor;
import org.apache.thrift.TProcessor;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.server.TAsyncHttpServlet;
import org.apache.thrift.server.TServlet;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.aop.target.SingletonTargetSource;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.metrics.GaugeService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.embedded.RegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import ru.trylogic.spring.boot.thrift.annotation.ThriftHandler;
import ru.trylogic.spring.boot.thrift.aop.ExceptionsThriftMethodInterceptor;
import ru.trylogic.spring.boot.thrift.aop.MetricsThriftMethodInterceptor;

import javax.servlet.*;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

@Configuration
@ConditionalOnClass(ThriftHandler.class)
@ConditionalOnWebApplication
public class ThriftAutoConfiguration {

    public interface ThriftConfigurer {
        void configureProxyFactory(ProxyFactory proxyFactory);
    }
    
    @Bean
    @ConditionalOnMissingBean(ThriftConfigurer.class)
    ThriftConfigurer thriftConfigurer() {
        return new DefaultThriftConfigurer();
    }
    
    @Bean
    @ConditionalOnMissingBean(TProtocolFactory.class)
    TProtocolFactory thriftProtocolFactory() {
        return new TBinaryProtocol.Factory();
    }
    
    public static class DefaultThriftConfigurer implements ThriftConfigurer {
        @Autowired(required = false)
        GaugeService gaugeService;

        public void configureProxyFactory(ProxyFactory proxyFactory) {
            proxyFactory.setOptimize(true);

            if(gaugeService != null) {
                proxyFactory.addAdvice(new MetricsThriftMethodInterceptor(gaugeService));
            }

            proxyFactory.addAdvice(new ExceptionsThriftMethodInterceptor());
        }
    }

    @Configuration
    public static class Registrar extends RegistrationBean implements ApplicationContextAware {
        
        public static String SYNC_INTERFACE_POSTFIX = "$Iface";
        
        public static String ASYNC_INTERFACE_POSTFIX = "$AsyncIface";
        
        public static String SYNC_PROCESSOR_INTERFACE_POSTFIX = "$Processor";
        
        public static String ASYNC_PROCESSOR_INTERFACE_POSTFIX = "$AsyncProcessor";
        
        @Getter
        @Setter
        ApplicationContext applicationContext;

        @Autowired
        TProtocolFactory protocolFactory;
        
        @Autowired
        ThriftConfigurer thriftConfigurer;

        @Override
        @SneakyThrows({NoSuchMethodException.class, ClassNotFoundException.class})
        public void onStartup(ServletContext servletContext) throws ServletException {
            for (String beanName : applicationContext.getBeanNamesForAnnotation(ThriftHandler.class)) {
                ThriftHandler annotation = applicationContext.findAnnotationOnBean(beanName, ThriftHandler.class);

                registerHandler(servletContext, annotation.value(), applicationContext.getBean(beanName));
            }
        }
        
        protected List<Class<?>> getThriftInterfaces(Object handler) {
            Class<?>[] handlerInterfaces = handler.getClass().getInterfaces();
            
            List<Class<?>> result = new ArrayList<>();
            for (Class<?> handlerInterfaceClass : handlerInterfaces) {
                if (handlerInterfaceClass.getName().endsWith(SYNC_INTERFACE_POSTFIX) || handlerInterfaceClass.getName().endsWith(ASYNC_INTERFACE_POSTFIX)) {
                    result.add(handlerInterfaceClass);
                }
            }
            
            return result;
        }
        
        protected Class<TProcessor> getProcessorClass(Class serviceClass, boolean async) {
            String postfix = async ? ASYNC_PROCESSOR_INTERFACE_POSTFIX : SYNC_PROCESSOR_INTERFACE_POSTFIX;
            for (Class<?> innerClass : serviceClass.getDeclaredClasses()) {
                if (innerClass.getName().endsWith(postfix)) {
                    return (Class<TProcessor>) innerClass;
                }
            }
            
            return null;
        }

        protected void registerHandler(ServletContext servletContext, String[] urls, Object handler) throws ClassNotFoundException, NoSuchMethodException {
            List<Class<?>> thriftInterfaces = getThriftInterfaces(handler);
            
            if(thriftInterfaces.size() == 0) {
                throw new IllegalStateException("No Thrift Ifaces found on handler");
            }
            
            if(thriftInterfaces.size() > 1) {
                throw new IllegalStateException("Multiple Thrift Ifaces defined on handler");
            }
            Class ifaceClass = thriftInterfaces.get(0);
            
            Class serviceClass = ifaceClass.getDeclaringClass();

            boolean async = ifaceClass.getName().endsWith(ASYNC_INTERFACE_POSTFIX);
            
            Class<TProcessor> processorClass = getProcessorClass(serviceClass, async);

            handler = wrapHandler(ifaceClass, handler);

            Constructor<TProcessor> processorConstructor = processorClass.getConstructor(ifaceClass);

            TProcessor processor = BeanUtils.instantiateClass(processorConstructor, handler);

            Servlet servlet = getServlet(processor, protocolFactory, async);

            String servletBeanName = ifaceClass.getDeclaringClass().getSimpleName() + (async ? "AsyncServlet" : "Servlet");

            ServletRegistration.Dynamic registration = servletContext.addServlet(servletBeanName, servlet);

            if(urls != null && urls.length > 0) {
                registration.addMapping(urls);
            } else {
                registration.addMapping("/" + serviceClass.getSimpleName());
            }

            registration.setAsyncSupported(async);
        }

        protected Servlet getServlet(final TProcessor processor, final TProtocolFactory protocolFactory, boolean async) {
            if(!async) {
                return new TServlet(processor, protocolFactory);
            }

            return new TAsyncHttpServlet((TBaseAsyncProcessor<?>) processor, protocolFactory);
        }

        protected <T> T wrapHandler(Class<T> interfaceClass, T handler) {
            ProxyFactory proxyFactory = new ProxyFactory(interfaceClass, new SingletonTargetSource(handler));

            thriftConfigurer.configureProxyFactory(proxyFactory);

            //TODO remove from here?
            proxyFactory.setFrozen(true);
            return (T) proxyFactory.getProxy();
        }
    }
}
