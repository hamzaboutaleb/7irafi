package com.herafi.rating_service.config;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Makes two HTTP client builders available to Spring: one for calling a server
 * directly, and one for finding a service through Eureka before calling it.
 */
@Configuration
public class ServiceClientConfig {

    /**
     * The default builder for code that asks for RestClient.Builder without
     * specifying @LoadBalanced. @Bean lets Spring supply it to other objects;
     * @Primary tells Spring to prefer it when no particular builder is requested.
     *
     * The Eureka client running inside this rating service uses this builder to
     * contact its configured registry address directly. Keep this plain builder:
     * using the load-balanced one would ask Eureka to find its own server while
     * Eureka is still starting, preventing the client from fetching the registry.
     */
    @Primary
    @Bean
    RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }

    /**
     * The builder requested by JobClient and UserClient through their
     * constructor parameters marked @LoadBalanced. Spring adds service lookup and load
     * balancing, so an address such as http://JOB-SERVICE can be resolved to an
     * available instance instead of requiring a fixed server address and port.
     * The timeouts and internal key are shared by these service calls. Clients
     * clone this builder before setting their own address, so they do not change
     * each other's settings. Eureka's plain builder above stays separate.
     * The @LoadBalanced constructor parameter selects this configured builder;
     * the parameter annotation alone does not configure a plain builder.
     */
    @LoadBalanced
    @Bean
    RestClient.Builder loadBalancedRestClientBuilder(
            @Value("${services.http.connect-timeout}") Duration connectTimeout,
            @Value("${services.http.read-timeout}") Duration readTimeout,
            @Value("${service.security.internal-api-key}") String internalApiKey) {
        HttpClient httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        // The factory adapts the JDK HTTP client to Spring's RestClient.
        // These settings limit connection and response waits; they are not added
        // together to form a fixed total time limit for every call.
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(readTimeout);
        return RestClient.builder()
                .requestFactory(factory)
                .defaultHeader("X-Internal-Api-Key", internalApiKey);
    }
}
