package com.psh.diet_planner.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class TaskExecutorConfig {

    @Bean(name = "importTrainingTaskExecutor")
    public TaskExecutor importTrainingTaskExecutor(
            @Value("${app.task.executor.core-pool-size:4}") int corePoolSize,
            @Value("${app.task.executor.max-pool-size:8}") int maxPoolSize,
            @Value("${app.task.executor.queue-capacity:200}") int queueCapacity,
            @Value("${app.task.executor.keep-alive-seconds:60}") int keepAliveSeconds,
            @Value("${app.task.executor.thread-name-prefix:diet-task-}") String threadNamePrefix) {

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(Math.max(1, corePoolSize));
        executor.setMaxPoolSize(Math.max(corePoolSize, maxPoolSize));
        executor.setQueueCapacity(Math.max(10, queueCapacity));
        executor.setKeepAliveSeconds(Math.max(10, keepAliveSeconds));
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
