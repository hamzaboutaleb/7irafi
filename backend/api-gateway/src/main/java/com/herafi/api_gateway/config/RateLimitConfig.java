package com.herafi.api_gateway.config;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Caffeine;

import io.github.bucket4j.caffeine.Bucket4jCaffeine;
import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.AsyncProxyManager;

/**
 * Where the rate-limit buckets live: one per client IP, in memory.
 *
 * The gateway's rateLimit filter looks up a bean of this type. Counters reset on
 * restart and are not shared between gateway instances.
 */
@Configuration
public class RateLimitConfig {

    @Bean
    AsyncProxyManager<String> rateLimitBuckets() {
        // maximumSize caps memory; a bucket is dropped one minute after it is full again.
        return Bucket4jCaffeine.<String>builderFor(Caffeine.newBuilder().maximumSize(10_000))
                .expirationAfterWrite(
                        ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(1)))
                .build()
                .asAsync();
    }
}
