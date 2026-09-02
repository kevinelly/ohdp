package com.dsf.feedback.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "feedback.storage")
public class FeedbackStorageProperties {
    private String baseDir = "./storage/feedback";
    private long maxUploadBytes = 10L * 1024 * 1024;
}
