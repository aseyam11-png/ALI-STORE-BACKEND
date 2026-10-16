package com.store.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@EnableAsync
public class AuthEmailAsyncConfiguration {

    /**
     * Bounded executor prevents mail traffic from creating unbounded threads.
     * Rejecting a full queue is preferable to running SMTP on the request thread;
     * task submission then fails synchronously and the challenge-creating transaction
     * can roll back rather than silently switching back to synchronous delivery.
     */
    @Bean(name = "authEmailExecutor")
    public ThreadPoolTaskExecutor authEmailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("ali-auth-mail-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        return executor;
    }
}
