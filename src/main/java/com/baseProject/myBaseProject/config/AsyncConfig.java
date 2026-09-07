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
    public static final String INTERVIEW_SCORING_EXECUTOR = "interviewScoringExecutor";

    @Bean(CV_PARSE_EXECUTOR)
    public ThreadPoolTaskExecutor cvParseExecutor() {
        return createExecutor("cv-parse-");
    }

    @Bean(JD_PROCESS_EXECUTOR)
    public ThreadPoolTaskExecutor jobDescriptionExecutor() {
        return createExecutor("jd-process-");
    }

    @Bean(INTERVIEW_PREPARATION_EXECUTOR)
    public ThreadPoolTaskExecutor interviewPreparationExecutor() {
        return createExecutor("interview-prepare-");
    }

    @Bean(INTERVIEW_SCORING_EXECUTOR)
    public ThreadPoolTaskExecutor interviewScoringExecutor() {
        return createExecutor("interview-scoring-");
    }

    private ThreadPoolTaskExecutor createExecutor(String threadNamePrefix) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // Giới hạn cả số worker và hàng đợi để tác vụ lớn không làm cạn tài nguyên server.
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(20);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }
}
