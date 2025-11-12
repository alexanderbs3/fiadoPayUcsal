package edu.ucsal.fiadopay.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;


@Configuration
public class AsyncConfig {


    @Bean
    public ExecutorService paymentExecutor() {
        ThreadFactory factory = Executors.defaultThreadFactory();
        return Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors(),
                r -> {
                    Thread t = factory.newThread(r);
                    t.setName("pay-worker-" + t.getId());
                    return t;
                }
        );
    }


    @Bean
    public ExecutorService webhookExecutor() {
        ThreadFactory factory = Executors.defaultThreadFactory();
        return Executors.newFixedThreadPool(10, r -> {
            Thread t = factory.newThread(r);
            t.setName("webhook-" + t.getId());
            return t;
        });
    }
}
