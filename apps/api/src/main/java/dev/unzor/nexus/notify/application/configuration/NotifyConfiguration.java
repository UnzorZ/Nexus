package dev.unzor.nexus.notify.application.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/** Habilita {@link NotifySmtpProperties} y el executor asíncrono de envío de emails. */
@Configuration
@EnableConfigurationProperties(NotifySmtpProperties.class)
class NotifyConfiguration {

    /**
     * Executor para el envío asíncrono de emails (offline-notify + transaccionales).
     * SMTP es I/O lento; ejecutar los envíos aquí mantiene ágiles el barrido de registry
     * y los hilos de petición. {@code CallerRunsPolicy} da back-pressure (nunca descarta
     * una notificación: si la cola+límite están llenos, el hilo publicador la envía) y el
     * shutdown graceful drena los envíos pendientes. Tamaño tunable vía
     * {@code nexus.notify.async.*}.
     */
    @Bean(name = "notifyExecutor", destroyMethod = "shutdown")
    ThreadPoolTaskExecutor notifyExecutor(
            @Value("${nexus.notify.async.core-pool-size:2}") int corePoolSize,
            @Value("${nexus.notify.async.max-pool-size:8}") int maxPoolSize,
            @Value("${nexus.notify.async.queue-capacity:100}") int queueCapacity) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("notify-async-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}
