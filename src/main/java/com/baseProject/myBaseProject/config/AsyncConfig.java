package com.baseProject.myBaseProject.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String CV_PARSE_EXECUTOR = "cvParseExecutor";
    public static final String JD_PROCESS_EXECUTOR = "jobDescriptionExecutor";
    public static final String INTERVIEW_PREPARATION_EXECUTOR = "interviewPreparationExecutor";

    @Bean(CV_PARSE_EXECUTOR)
    public ThreadPoolTaskExecutor cvParseExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // Giới hạn cả số worker và hàng đợi để upload lớn không làm cạn tài nguyên server.
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("cv-parse-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }

    @Bean(JD_PROCESS_EXECUTOR)
    public ThreadPoolTaskExecutor jobDescriptionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("jd-process-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }

    @Bean(INTERVIEW_PREPARATION_EXECUTOR)
    public ThreadPoolTaskExecutor interviewPreparationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("interview-prepare-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }
}
