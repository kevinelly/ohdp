package com.dsf.ohdp.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Getter
@Setter
@Configuration
@ConfigurationProperties(prefix = "ohdp.reference-document")
public class OhdpReferenceDocumentProperties {
    private String storageDir = "./storage/reference-documents";
    private long maxUploadBytes = 50L * 1024 * 1024;
}
