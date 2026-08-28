package com.herafi.job_service.storage;

import java.io.ByteArrayInputStream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.herafi.job_service.service.ImageStorageService;

@SpringBootTest
class S3ImageStorageServiceTest {

    @Autowired
    private ImageStorageService imageStorageService;

    @Test
    void shouldUploadImage() throws Exception {

        String objectKey = "test/test-image.txt";
        byte[] content = "hello minio".getBytes();

        imageStorageService.upload(
                objectKey,
                new ByteArrayInputStream(content),
                content.length,
                "text/plain"
        );
    }
}