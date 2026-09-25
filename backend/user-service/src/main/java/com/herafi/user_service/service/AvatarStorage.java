package com.herafi.user_service.service;

import java.io.InputStream;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * Avatars in object storage.
 *
 * The bucket is private. Nothing ever receives a bucket URL: reads go through a
 * presigned link that expires, so an image cannot be shared or guessed later.
 */
@Service
public class AvatarStorage {

    private static final Duration LINK_LIFETIME = Duration.ofMinutes(15);

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucket;

    public AvatarStorage(
            S3Client s3Client,
            S3Presigner s3Presigner,
            @Value("${aws.s3.bucket}") String bucket) {
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.bucket = bucket;
    }

    public void upload(String objectKey, InputStream content, long contentLength, String contentType) {
        s3Client.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(objectKey)
                        .contentType(contentType)
                        .build(),
                RequestBody.fromInputStream(content, contentLength));
    }

    public void delete(String objectKey) {
        s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(objectKey).build());
    }

    public String downloadUrl(String objectKey) {
        GetObjectRequest get = GetObjectRequest.builder().bucket(bucket).key(objectKey).build();

        return s3Presigner.presignGetObject(
                GetObjectPresignRequest.builder()
                        .signatureDuration(LINK_LIFETIME)
                        .getObjectRequest(get)
                        .build())
                .url()
                .toString();
    }
}
