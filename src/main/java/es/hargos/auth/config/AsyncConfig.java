package es.hargos.auth.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    /**
     * Configuración del ThreadPool para tareas asíncronas (emails, notificaciones, etc.)
     *
     * Pool pequeño porque los emails van a una API externa (Resend)
     * No necesitamos muchos threads ya que están bloqueados en I/O de red
     */
    @Override
    public Executor getAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Threads base que siempre están activos
        executor.setCorePoolSize(5);

        // Máximo de threads que puede crear bajo alta carga
        executor.setMaxPoolSize(20);

        // Capacidad de la cola de espera
        executor.setQueueCapacity(100);

        // Nombre de los threads para debugging
        executor.setThreadNamePrefix("async-email-");

        // Esperar a que terminen las tareas al hacer shutdown
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        executor.initialize();
        return executor;
    }
}
