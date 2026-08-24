package com.polakowski.requestmanagement.infrastructure.config;

import com.polakowski.requestmanagement.domain.workflow.RequestWorkflow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Turns the configured lifecycle into the domain service the application depends on.
 *
 * <p>Publishing it as a bean is what lets every collaborator receive the workflow by injection
 * instead of reaching for a static definition of it.
 */
@Configuration
@Slf4j
public class WorkflowConfiguration {

    @Bean
    public RequestWorkflow requestWorkflow(WorkflowProperties properties) {
        RequestWorkflow workflow = new RequestWorkflow(properties.toDefinition());
        log.info("Request lifecycle in use: {}", workflow);

        return workflow;
    }
}
