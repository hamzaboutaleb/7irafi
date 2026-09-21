package com.herafi.job_service.service;

import java.io.InputStream;

public interface ImageStorageService {

    void upload(
        String objectKey,
        InputStream inputStream,
        long contentLength,
        String contentType
    );

    String generateDownloadUrl(String objectKey);

    void markCommitted(String objectKey);

    void markPendingCleanup(String objectKey);
}
