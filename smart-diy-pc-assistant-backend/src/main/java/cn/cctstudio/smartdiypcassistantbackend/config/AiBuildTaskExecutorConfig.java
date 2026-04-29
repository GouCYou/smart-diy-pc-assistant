package cn.cctstudio.smartdiypcassistantbackend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class AiBuildTaskExecutorConfig {

    @Bean(name = "aiBuildTaskExecutor")
    public Executor aiBuildTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(3);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("ai-build-task-");
        executor.initialize();
        return executor;
    }
}
