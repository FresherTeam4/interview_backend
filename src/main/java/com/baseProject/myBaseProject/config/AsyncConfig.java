package com.baseProject.myBaseProject.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
@EnableAsync
public class AsyncConfig {

    public static final String CV_PARSE_EXECUTOR = "cvParseExecutor";
    public static final String INTERVIEW_AI_EXECUTOR = "interviewAiExecutor";
    public static final String INTERVIEW_VOICE_EXECUTOR = "interviewVoiceExecutor";
    public static final String INTERVIEW_WORKFLOW_SCHEDULER = "interviewWorkflowScheduler";

    @Bean(CV_PARSE_EXECUTOR)
    public ThreadPoolTaskExecutor cvParseExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("cv-parse-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }

    @Bean(INTERVIEW_AI_EXECUTOR)
    public ThreadPoolTaskExecutor interviewAiExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("interview-ai-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }

    @Bean(INTERVIEW_VOICE_EXECUTOR)
    public ThreadPoolTaskExecutor interviewVoiceExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix("interview-voice-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }

    @Bean(INTERVIEW_WORKFLOW_SCHEDULER)
    public ThreadPoolTaskScheduler interviewWorkflowScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("interview-recovery-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(10);
        scheduler.setRemoveOnCancelPolicy(true);
        return scheduler;
    }
}
