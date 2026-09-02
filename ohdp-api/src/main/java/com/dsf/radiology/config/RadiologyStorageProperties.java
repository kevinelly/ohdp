package com.dsf.radiology.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "radiology.storage")
public class RadiologyStorageProperties {
    private String baseDir = "./storage/radiology";
    private long maxUploadBytes = 25L * 1024 * 1024;
}
