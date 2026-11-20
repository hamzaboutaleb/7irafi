package com.herafi.api_gateway.config;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.github.benmanes.caffeine.cache.Caffeine;

import io.github.bucket4j.caffeine.CaffeineProxyManager;
import io.github.bucket4j.distributed.proxy.AsyncProxyManager;
import io.github.bucket4j.distributed.remote.RemoteBucketState;

/**
 * Where the rate-limit buckets live: one per client IP, in memory.
 *
 * The gateway's rateLimit filter looks up a bean of this type. Counters reset on
 * restart and are not shared between gateway instances.
 */
@Configuration
public class RateLimitConfig {

    @Bean
    @SuppressWarnings({ "rawtypes", "unchecked" })
    AsyncProxyManager<String> rateLimitBuckets() {
        // maximumSize caps memory; buckets unused for a minute are dropped.
        Caffeine<String, RemoteBucketState> cache = (Caffeine) Caffeine.newBuilder().maximumSize(10_000);
        return new CaffeineProxyManager<>(cache, Duration.ofMinutes(1)).asAsync();
    }
}
