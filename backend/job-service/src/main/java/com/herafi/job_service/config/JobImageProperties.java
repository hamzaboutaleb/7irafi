package com.herafi.job_service.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "jobs.images")
public class JobImageProperties {

    private DataSize maxFileSize = DataSize.ofMegabytes(10);
    private int maxCount = 10;
    private long cleanupDelayMs = 60000;
    private int cleanupBatchSize = 50;

    public String maxFileSizeLabel() {
        return maxFileSize.toMegabytes() + "MB";
    }
}
