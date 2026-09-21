package com.herafi.job_service.service;

import java.io.InputStream;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectTaggingRequest;
import software.amazon.awssdk.services.s3.model.Tag;
import software.amazon.awssdk.services.s3.model.Tagging;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;


@Service
@RequiredArgsConstructor
public class S3ImageStorageService implements ImageStorageService {

        private final S3Client s3Client;
        private final S3Presigner s3Presigner;

        @Value("${aws.s3.bucket}")
        private String bucket;

        @Override
        public void upload(
                        String objectKey,
                        InputStream inputStream,
                        long contentLength,
                        String contentType) {

                PutObjectRequest request = PutObjectRequest.builder()
                                .bucket(bucket)
                                .key(objectKey)
                                .contentType(contentType)
                                .tagging("cleanup=pending")
                                .build();

                s3Client.putObject(
                                request,
                                RequestBody.fromInputStream(inputStream, contentLength));
        }

        @Override
        public String generateDownloadUrl(String objectKey) {

                GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                                .bucket(bucket)
                                .key(objectKey)
                                .build();

                GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                                .signatureDuration(Duration.ofMinutes(15))
                                .getObjectRequest(getObjectRequest)
                                .build();

                return s3Presigner.presignGetObject(presignRequest)
                                .url()
                                .toString();
        }

        @Override
        public void markCommitted(String objectKey) {
                setCleanupTag(objectKey, "committed");
        }

        @Override
        public void markPendingCleanup(String objectKey) {
                setCleanupTag(objectKey, "pending");
        }

        private void setCleanupTag(String objectKey, String state) {
                PutObjectTaggingRequest request = PutObjectTaggingRequest.builder()
                                .bucket(bucket)
                                .key(objectKey)
                                .tagging(Tagging.builder()
                                                .tagSet(Tag.builder().key("cleanup").value(state).build())
                                                .build())
                                .build();
                s3Client.putObjectTagging(request);
        }
}
