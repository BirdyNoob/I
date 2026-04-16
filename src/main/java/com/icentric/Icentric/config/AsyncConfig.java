package com.icentric.Icentric.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "mailTaskExecutor")
    public java.util.concurrent.Executor mailTaskExecutor() {
        org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor executor = new org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("MailTask-");
        executor.initialize();
        return executor;
    }
}
